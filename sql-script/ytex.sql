CREATE DATABASE ytex CHARACTER SET utf8;
CREATE USER 'ytex'@'localhost' IDENTIFIED BY 'ytex';
GRANT ALL PRIVILEGES ON ytex.* TO 'ytex'@'localhost';
GRANT SELECT on umls.* to 'ytex'@'localhost';

SHOW FULL PROCESSLIST;

use ytex;

select * from ytex.content_tag_ytex;

/*this change to 1004 data set*/
drop table if exists content_tag_ytex;
create table content_tag_ytex as 
 select a.anno_text, d.instance_id, c.* from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id;
  /*,7230*/

select count(*)  from content_tag_ytex;
/*find all tags that has relationship with diabetes and T047*/ 
drop table if exists content_tag_diabetes_ytex_T047;
create table content_tag_diabetes_ytex_T047  as 
	(select distinct c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui, td.cui as rel_cui, td.aui as rel_aui, 0 as rel_flag
		from ytex.content_tag_ytex c 
		inner join umls.tmp_diabetes_T047 td 
			on c.code = td.cui COLLATE utf8_unicode_ci
     )	union (
	select distinct c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui,  r.cui as rel_cui, r.aui as rel_aui, 1 as rel_flag
	from ytex.content_tag_ytex c 
	inner join umls.tmp_rel_diabetes_T047 r 
		on c.code = r.rel_cui COLLATE utf8_unicode_ci
    );
/*219203,38935 results*/
select count(distinct cui) from ytex.content_tag_diabetes_ytex_T047;
-- 128

/*add the stt column, because we want to pick the 'preferred name'.*/
alter table content_tag_diabetes_ytex_T047 add (`STT` varchar(3) default null);
alter table content_tag_diabetes_ytex_T047 add (`id` int auto_increment primary key );
update content_tag_diabetes_ytex_T047 as cd 
	inner join umls.MRCONSO as con
		on cd.rel_cui = con.cui and cd.rel_aui = con.AUI
	set cd.stt = con.stt;  
  
  

/* reduce the data based on unique target/cui/rel_cui*/
drop table if exists content_tag_diabetes_ytex_T047_unique;
create table content_tag_diabetes_ytex_T047_unique as
select cd.* from content_tag_diabetes_ytex_T047 as cd
	inner join 
	(select distinct id from content_tag_diabetes_ytex_T047 group by blogId,target,wordIndex,rel_cui order by stt) as temp
    on cd.id = temp.id
;
/*106004,24066*/

/* reduce the data based on unique target/cui*/
drop table if exists content_tag_diabetes_ytex_T047_unique2;
create table content_tag_diabetes_ytex_T047_unique2 as
select cd.* from content_tag_diabetes_ytex_T047_unique as cd
	inner join 
	(select distinct id from content_tag_diabetes_ytex_T047_unique group by blogId,target,wordIndex) as temp
    on cd.id = temp.id
;
/*5800,1602 */

select distinct target,cui from content_tag_diabetes_ytex_T047_unique2 order by blogId,wordIndex;
  
drop table if exists content_tag_ytex_T047_unique_output;
create table  content_tag_ytex_T047_unique_output as 
	select distinct ct.blogId,
		ct.target,
		ct.cui,
        org.cui_text as umlsStr,
        ct.wordIndex,
        org.sentence_text as sentence,
        ct.rel_cui,
        ct.rel_aui,
        ct.rel_flag,
        ct.STT
    from content_tag_diabetes_ytex_T047_unique2 as ct 
	inner join (select * from content_tag_ytex as cy
		 group by instance_id, anno_text,anno_base_id,sentence_text) org
	on org.instance_id=ct.blogId and org.anno_text = ct.target and org.anno_base_id=ct.wordIndex 
	;  
    /*5800,1602*/
  
alter table content_tag_ytex_T047_unique_output add (`rel_str` text);
update content_tag_ytex_T047_unique_output as ct set rel_str = (select STR from umls.MRCONSO where CUI=ct.rel_cui and AUI=ct.rel_aui);
  
select * from ytex.content_tag_ytex_T047_unique_output order by blogId,sentence 
	into outfile 'c:\\fsu\\content_tag_ytex_T047_unique.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n'; 
  
  
select * from content_tag_ytex_T047_unique_output order by blogId,sentence;
select *, count(*) as cnt from content_tag_ytex group by instance_id, anno_text,anno_base_id,sentence_text order by cnt desc;


/* compare analyzing*/
use ytex;
alter table ytex.content_tag_ytex_T047_unique_output add id int primary key auto_increment after rel_str;
alter table umls.content_tag_diabetes_T047_unique2_output add id int primary key auto_increment after rel_str;
-- truncate table ytex.content_tag_ytex_T047_unique_output;
-- truncate table umls.content_tag_diabetes_T047_unique2_output;
-- alter table ytex.content_tag_ytex_T047_unique_output  drop column id;
-- alter table umls.content_tag_diabetes_T047_unique2_output drop column id;
/* load data local infile 'c:\\fsu\\content_tag_ytex_T047_unique_all.csv' 
	into table ytex.content_tag_ytex_T047_unique_output fields terminated by ',' enclosed by '"' lines terminated by '\n' ignore 1 lines; 
 load data local infile 'c:\\fsu\\content_tag_our_T047_unique_all.csv' 
	into table umls.content_tag_diabetes_T047_unique2_output fields terminated by ',' enclosed by '"' lines terminated by '\n' ignore 1 lines; 
*/
/* find the overlap content tags both in ytex's result and our result. (blogid, cui, target are same)*/
drop table if exists ytex.content_tag_compare_same;
create table content_tag_compare_same as 
	select distinct yctu.* from ytex.content_tag_ytex_T047_unique_output as yctu 
	   inner join (
		select distinct yctu2.id from ytex.content_tag_ytex_T047_unique_output as yctu2
			inner join umls.content_tag_diabetes_T047_unique2_output as uctu
				on yctu2.blogId = uctu.blogId /*and yctu2.cui = uctu.cui COLLATE utf8_unicode_ci*/ and yctu2.target = uctu.target COLLATE utf8_unicode_ci
	   ) as temp 
	   on yctu.id = temp.id 
       order by yctu.blogId, yctu.sentence
       ;
/*5529,1554*/

select count(*) from ytex.content_tag_compare_same;

select s.* /*, 
	(select o.rel_all from ytex.content_tag_ytex_T047_unique_output  o
		where s.cui = o.cui and s.rel_cui = o.rel_cui limit 1) as rel_all */
    from ytex.content_tag_compare_same s
	into outfile 'c:\\fsu\\content_tag_compare_same.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
       
drop table if exists ytex.content_tag_compare_same2;
create table content_tag_compare_same2 as 
	select distinct uctu.* from umls.content_tag_diabetes_T047_unique2_output as uctu
	   inner join (
		select distinct uctu2.id from umls.content_tag_diabetes_T047_unique2_output as uctu2
			inner join ytex.content_tag_ytex_T047_unique_output as yctu 
				on uctu2.blogId = yctu.blogId /*and uctu2.cui = yctu.cui COLLATE utf8_unicode_ci*/ and uctu2.target = yctu.target COLLATE utf8_unicode_ci
	   ) as temp
	   on uctu.id = temp.id 
       order by uctu.blogId, uctu.sentence
       ;
/*5581,1602*/
       
select target, count(*) as cnt from ytex.content_tag_compare_same2 group by target order by cnt desc;

select s.* /*, 
	(select o.rel_all from umls.content_tag_diabetes_T047_unique2_output  o
		where s.cui = o.cui and s.rel_cui = o.rel_cui limit 1) as rel_all */
	from ytex.content_tag_compare_same2 s
	into outfile 'c:\\fsu\\content_tag_compare_same2.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';  ;
  

/* find the content terms only in ytex's result */
drop table if exists content_tag_compare_only_ytex;
create table content_tag_compare_only_ytex as 
	select yctu.* from ytex.content_tag_ytex_T047_unique_output as yctu 
	   where yctu.id not in (
		select distinct same.id from ytex.content_tag_compare_same as same
	   )
       order by yctu.blogId, yctu.sentence
       ;
/*271,124*/

select target, count(*) as cnt from content_tag_compare_only_ytex group by target order by cnt desc;
/*45,53 result*/
select s.* /*, 
	(select o.rel_all from ytex.content_tag_ytex_T047_unique_output  o
		where s.cui = o.cui and s.rel_cui = o.rel_cui limit 1) as rel_all */
        from ytex.content_tag_compare_only_ytex s
	into outfile 'c:\\fsu\\content_tag_compare_only_ytex.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';  ;
  
/* find the content terms only in our result  */
drop table if exists content_tag_compare_only_our;
create table content_tag_compare_only_our as 
	select uctu.* from umls.content_tag_diabetes_T047_unique2_output as uctu 
	   where uctu.id not in (
		  select distinct same.id from ytex.content_tag_compare_same2 as same
	   )
       order by uctu.blogId, uctu.sentence
       ;
/*5774*/
select target, count(*) as cnt from content_tag_compare_only_our group by target order by cnt desc;
/*235 result*/
select s.* /*, 
	(select o.rel_all from umls.content_tag_diabetes_T047_unique2_output  o
		where s.cui = o.cui and s.rel_cui = o.rel_cui limit 1) as rel_all */
        from ytex.content_tag_compare_only_our s
	into outfile 'c:\\fsu\\content_tag_compare_only_our.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';  ;




select distinct our.target from  ytex.content_tag_compare_only_our as our
	where our.target not in (
		select distinct ytex.target COLLATE utf8_unicode_ci from ytex.content_tag_ytex_T047_unique as ytex 
    ) ;

select distinct our.target from  ytex.content_tag_compare_only_our as our
	where our.target not in (
		select distinct ytex.target COLLATE utf8_unicode_ci from ytex.content_tag_ytex_T047_unique as ytex 
    ) ;















alter table document add disease varchar(100);
select distinct blogId as instance_id from umls.CONTENT_ORG limit 1;
select count(*) from anno_sentence;

select * from document;
select * from `anno_base`              ;
select * from `anno_base_sequence`     ;
select * from `anno_contain`           ;
select * from `anno_date`              ;
select * from `anno_link`              ;
select * from `anno_markable`          ;
select * from `anno_med_event`         ;
select * from `anno_mm_acronym`        ;
select * from `anno_mm_candidate`      ;
select * from `anno_mm_cuiconcept`     ;
select * from `anno_mm_negation`       ;
select * from `anno_mm_utterance`      ;
select * from `anno_named_entity`      ;
select * from `anno_ontology_concept`  ;
select * from `anno_segment`           ;
select * from `anno_sentence`          ;
select * from `anno_token`             ;
select * from `anno_treebank_node`     ; 
-- view
select * from v_annotation;
-- select * from v_corpus_goup_class;
select * from v_document;
select * from v_document_cui_sent;
select * from v_document_ontoanno;

use ytex;
truncate `anno_base`                   ;
truncate `anno_base_sequence`          ;
truncate `anno_contain`                ;
truncate `anno_date`                   ;
truncate `anno_link`                   ;
truncate `anno_markable`               ;
truncate `anno_med_event`              ;
truncate `anno_mm_acronym`             ;
truncate `anno_mm_candidate`           ;
truncate `anno_mm_cuiconcept`          ;
truncate `anno_mm_negation`            ;
truncate `anno_mm_utterance`           ;
truncate `anno_named_entity`           ;
truncate `anno_ontology_concept`       ;
truncate `anno_segment`                ;
truncate `anno_sentence`               ;
truncate `anno_token`                  ;
truncate `anno_treebank_node`          ;
truncate document;


select * from ytex.content_org;


select distinct sab from umls.mrconso;

