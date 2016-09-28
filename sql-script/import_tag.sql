
use umls;
/*DELET ALL non-english record*/
-- delete from mrconso where lat <> 'ENG';
drop table IF EXISTS CONTENT_TAG;
/*create table for the original terms from blogs.*/
CREATE TABLE CONTENT_TAG (
`blogId` varchar(40) DEFAULT NULL,  /*----blog id */
`target`  varchar(300) DEFAULT NULL,  /* ----the term found in the content. It much be found in UMLS too. If a term is not found in UMLS, it will be ignored. */
`umlsFlag` varchar(10) DEFAULT NULL, /* ----If it is found in UMLS. This column is used by hashTags.*/
`score` float DEFAULT NULL,                /* ----The similarity metic between the term in the content and the string in UMLS.*/
`CUI` varchar(45) DEFAULT NULL,        /*----- CUI of UMLS*/
`SAB` varchar(45) DEFAULT NULL,       /* ----- SAB of UMLS*/
`AUI` varchar(45) DEFAULT NULL,        /*----- AUI of UMLS */
`umlsStr` varchar(1000) DEFAULT NULL,  /* ---STR of UMLS mrconso table*/
`TUI` varchar(45) DEFAULT NULL,        /*------TUI of UMLS MRSTY table */
`styName` varchar(45) DEFAULT NULL, /*------Semantic name of UMLS MRSTY table*/
`semName` varchar(100) DEFAULT NULL, /*---semantic group name in SemGroup website*/
`tagId` int default 0,               /*----If the term match a hash_tag of the blog, tagId is the index of the tags. if not match any hash_tag, it is 0.*/
`wordIndex` int default 0,            /*----the position of the term in the content*/
`wordIndexInSentence` int default 0,  /*---- the position of the term in the sentence that it is found in. */
`sentenceIndex` int default 0,         /*-- the index of the sentence of the target*/
`targetNorm` varchar(300) default NULL,  /*--- the normalized string of the term.*/
`tags` varchar(500) default NULL,         /*---- all the hash_tags of the blog. */
`sentence` varchar(5000) default NULL    /* The sentence that the target is found in*/
);

/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\UmlsTagger\\data\\raw_data_CHV_study2_format_ret.csv' 
into table CONTENT_TAG 
fields terminated by ','
enclosed by '"'
lines terminated by '\n'
ignore 1 lines;

select count(distinct cui) from content_tag;
-- 6612

/*find all cui,AUI that is relevant to diabetes*/
drop table if exists tmp_diabetes;
create table tmp_diabetes as select distinct CUI,AUI from MRCONSO where LAT='ENG' AND (str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%') ;
/*4011 results*/

/* filter all cui+aui which belonge to  T047, as part of the seeds*/
create table tmp_diabetes_T047 as 
	select distinct td.CUI,td.AUI from tmp_diabetes as td
	inner join MRSTY as sty
		on (sty.TUI = 'T047' and sty.CUI = td.CUI)
        ;
/*2662 results*/

/*find all relationship that is relevant to diabetes and T047, as the seeds too*/
drop tables if exists tmp_rel_diabetes_T047;
create table tmp_rel_diabetes_T047 as 
select distinct *
	from (
		select distinct td.cui,td.aui,r.cui2 as rel_cui, r.aui2 as rel_aui
			from mrrel r 
			inner join tmp_diabetes_T047 td
				on td.aui = r.aui1  and td.cui = r. cui1
		union
		select distinct td.cui,td.aui,r.cui1 as rel_cui, r.aui1 as rel_aui
			from mrrel r 
			inner join tmp_diabetes_T047 td
				on td.aui = r.aui2  and td.cui = r. cui2
			 ) as tmp
        ;
/*8745 results*/
-- select * from tmp_rel_diabetes_T047;

/*find all tags that has relationship with diabetes and T047 seeds*/ 
drop table if exists content_tag_diabetes_T047;
create table content_tag_diabetes_T047  as 
	(select distinct c.blogId, c.target, c.wordIndex, c.cui, td.cui as rel_cui, td.aui as rel_aui, 0 as rel_flag
		from CONTENT_TAG c 
		inner join tmp_diabetes_T047 td 
			on c.cui = td.cui COLLATE utf8_unicode_ci
     )	union (
	select distinct  c.blogId, c.target, c.wordIndex, c.cui, r.cui as rel_cui, r.aui as rel_aui, 1 as rel_flag
	from CONTENT_TAG c 
	inner join tmp_rel_diabetes_T047 r 
		on c.cui = r.rel_cui COLLATE utf8_unicode_ci
    );
/*718169, 474923*/

/*add the stt column, because we want to pick the 'preferred name'.*/
alter table content_tag_diabetes_T047 add (`STT` varchar(3) default null);
alter table content_tag_diabetes_T047 add (`id` int auto_increment primary key );
update content_tag_diabetes_T047 as cd 
	inner join umls.mrconso as con
		on cd.rel_cui = con.cui and cd.rel_aui = con.AUI
	set cd.stt = con.stt;

/* reduce the data based on unique target/cui/rel_cui*/
drop table if exists content_tag_diabetes_T047_unique;
create table content_tag_diabetes_T047_unique as
select cd.* from content_tag_diabetes_T047 as cd
	inner join 
	(select distinct id from content_tag_diabetes_T047 group by blogId,target,wordIndex,rel_cui order by stt) as temp
    on cd.id = temp.id
;
/*483903,309962*/
/* reduce the data based on unique target/wordIndex/cui*/
drop table if exists content_tag_diabetes_T047_unique2;
create table content_tag_diabetes_T047_unique2 as
select cd.* from content_tag_diabetes_T047_unique as cd
	inner join 
	(select distinct id from content_tag_diabetes_T047_unique group by blogId,target,wordIndex) as temp
    on cd.id = temp.id
;
/*11354，4621 */

select * from content_tag_diabetes_T047;
select distinct * from content_tag_diabetes_T047_unique2 order by blogId,wordIndex;

drop table if exists content_tag_diabetes_T047_unique2_output;
create table  content_tag_diabetes_T047_unique2_output as 
	select distinct ct.blogId,
		ct.target,
		ct.cui,
        org.umlsStr,
        ct.wordIndex,
        org.sentence,
        ct.rel_cui,
        ct.rel_aui,
        ct.rel_flag,
        ct.STT
    from content_tag_diabetes_T047_unique2 as ct 
	inner join (select * from content_tag as ct2
		 group by blogId, target,wordIndex,sentence) org
	on org.blogId=ct.blogId and org.target = ct.target and org.wordIndex=ct.wordIndex 
	;  
    /*11355,4621*/
  
alter table content_tag_diabetes_T047_unique2_output add (`rel_str` text);
update content_tag_diabetes_T047_unique2_output as ct set rel_str = (select STR from umls.MRCONSO where CUI=ct.rel_cui and AUI=ct.rel_aui);
  
select * from umls.content_tag_diabetes_T047_unique2_output order by blogId,sentence 
	into outfile 'c:\\fsu\\content_tag_our_T047_unique.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n'; 

show create table content_tag_diabetes_T047_unique2_output;


  
  drop table tmp_ret;
  create table tmp_ret as 
	select * from content_tag_diabetes_T047_unique2_output limit 10;

/*add a new column for all rel+rela, then get the rel+rela as a string*/  
alter table content_tag_diabetes_T047_unique2_output add (rel_all text default null);  
/*
update content_tag_diabetes_T047_unique2_output as ret set rel_all = 
	(select GROUP_CONCAT(DISTINCT REL,' ',RELA SEPARATOR ',') from umls.MRREL as r 
		where (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci) 
		   or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2 limit 1
        );
*/


select count(*) as cnt from mrrel as r group by cui1,cui2,aui1,aui2 order by cnt;  /*largest is 1*/
select count(*) as cnt from content_tag as c group by blogId,target,wordIndex order by cnt desc;


-- select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence  from content_tag_diabetes where blogId in ('47923544278','60909574649','104422645889' ,'105407381237' ,'105625170923' ,'105943550692' ,'106050694329' ,'107494520338' ,'107512513044' ,'107935674190')      into outfile 'c:\\fsu\\tag_diabetes_first10blog_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';

/*
select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence, rel, rela, rel_str from content_tag_diabetes 
  where REL <>'SIB' or REL <> 'XR'
  order by blogId, wordIndex
  into outfile 'c:\\fsu\\tag_diabetes_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
*/
/*delete part of the relationship type*/  
/*
drop table content_tag_diabetes_part ;
create table content_tag_diabetes_part  select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence , rel, rela, rel_str,cui1,cui2,aui1,aui2  from content_tag_diabetes 
  where REL <>'SIB' and REL <> 'XR' 
  order by blogId, wordIndex;
  */
/*delete part of the relationship type*/  
delete from content_tag_diabetes  where REL = 'SIB' or REL = 'XR' ;

  /*add a fild of 'rel_str' to the result table. it indicates the 'str' of an AUI has relationship with current AUI */
  alter table content_tag_diabetes add rel_str varchar(1000);
  /*Find out the 'str' of aui that related to current aui. */
  update content_tag_diabetes as cd set cd.rel_str = (select con.str from mrconso con where con.aui = cd.aui1) where cd.aui1 <> cd.aui COLLATE utf8_unicode_ci;
  update content_tag_diabetes as cd set cd.rel_str = (select con.str from mrconso con where con.aui = cd.aui2) where cd.aui2 <> cd.aui COLLATE utf8_unicode_ci;
  

select target, count(*) as cnt from content_tag_diabetes_unique cp group by target order by cnt desc;

/*add a atuo increment primary key for next step. */  
alter table content_tag_diabetes add id Int NOT NULL AUTO_INCREMENT PRIMARY KEY;
alter table content_tag_diabetes_unique add rel_cui varchar(45) default null;

create table content_tag_diabetes_T047_unique as 
  select cp.* from content_tag_diabetes_unique as cp 
	inner join mrsty sty on cp.rel_cui = sty.cui COLLATE utf8_unicode_cicontent_tag_ytex_t047content_tag_ytex_t047 and sty.tui = 'T047'
	;


select count(*) from content_tag_diabetes_T047_unique;

update content_tag_diabetes_unique as cp set  cp.rel_cui = cp.cui1
	where cp.cui = cp.cui2 COLLATE utf8_unicode_ci;

/*pick the first row for the same 'original result' based on blogid+wordIndex+CUI*/
drop table if exists content_tag_diabetes_unique;
create table content_tag_diabetes_unique as 
	select cd.* from  content_tag_diabetes as cd 
		inner join (select id from content_tag_diabetes group by blogId, CUI, wordIndex order by score desc) as cp 
        on cp.id = cd.id;
  
  select * from content_tag_diabetes_unique ;

select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence, rel, rela, rel_str,id from content_tag_diabetes_unique 
  order by blogId, wordIndex
  into outfile 'c:\\fsu\\tag_diabetes_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';  
  

  
select count(distinct target) from content_tag;
/*7912*/
select count(distinct cui) from content_tag;
/*10481*/
select count(distinct blogId, sentenceIndex) from content_tag;
select count(distinct sentence) from content_tag;
use umls;
/*DELET ALL non-english record*/
-- delete from mrconso where lat <> 'ENG';
drop table IF EXISTS CONTENT_TAG_UNIQUE_CUI;
/*create table for the original terms from blogs.*/
CREATE TABLE CONTENT_TAG_UNIQUE_CUI (
`blogId` varchar(40) DEFAULT NULL,  /*----blog id */
`target`  varchar(300) DEFAULT NULL,  /* ----the term found in the content. It much be found in UMLS too. If a term is not found in UMLS, it will be ignored. */
`umlsFlag` varchar(10) DEFAULT NULL, /* ----If it is found in UMLS. This column is used by hashTags.*/
`score` float DEFAULT NULL,                /* ----The similarity metic between the term in the content and the string in UMLS.*/
`CUI` varchar(45) DEFAULT NULL,        /*----- CUI of UMLS*/
`SAB` varchar(45) DEFAULT NULL,       /* ----- SAB of UMLS*/
`AUI` varchar(45) DEFAULT NULL,        /*----- AUI of UMLS */
`umlsStr` varchar(1000) DEFAULT NULL,  /* ---STR of UMLS mrconso table*/
`TUI` varchar(45) DEFAULT NULL,        /*------TUI of UMLS MRSTY table */
`styName` varchar(45) DEFAULT NULL, /*------Semantic name of UMLS MRSTY table*/
`semName` varchar(100) DEFAULT NULL, /*---semantic group name in SemGroup website*/
`tagId` int default 0,               /*----If the term match a hash_tag of the blog, tagId is the index of the tags. if not match any hash_tag, it is 0.*/
`wordIndex` int default 0,            /*----the position of the term in the content*/
`wordIndexInSentence` int default 0,  /*---- the position of the term in the sentence that it is found in. */
`sentenceIndex` int default 0,         /*-- the index of the sentence of the target*/
`targetNorm` varchar(300) default NULL,  /*--- the normalized string of the term.*/
`tags` varchar(500) default NULL,         /*---- all the hash_tags of the blog. */
`sentence` varchar(1000) default NULL,    /* The sentence that the target is found in*/
`rel`     varchar(4)   ,   /*rel field in mrrel*/
`rela`    varchar(100) ,   /*rela fild in mrrel*/
`rel_str` varchar(1000) ,  /*str field  in mrconso for the term relevant to current term */
`id`      int(11)          /* auto_increment primary key for the result table*/
);

/*load result in to the table*/
load data local infile 'C:\\fsu\\tag_diabetes_distinct.csv' 
into table CONTENT_TAG_UNIQUE_CUI 
fields terminated by ','
enclosed by '"'
lines terminated by '\n'
ignore 1 lines;

select * from CONTENT_TAG_UNIQUE_CUI where sentence like '%The apparent clear symptoms consists of escalation in dehydration recognized as%';
select * from CONTENT_TAG_UNIQUE_CUI where blogId='115661000000';

