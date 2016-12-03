create database if not exists ret1018 ;
USE umls;

/*ret1007.content_tag_ytex is the basic result got from ytex*/
select count(distinct code) from ret1007.content_tag_ytex ;
-- CUI counter:4221
select count(distinct anno_text) from ret1007.content_tag_ytex;
-- target counter: 5978
select count(distinct instance_id) from ret1007.content_tag_ytex;
-- blogid counter: 3711
select count(*) from ret1007.content_tag_ytex;
-- all result counter:81867

/* In umls schema, find all AUI that is relevant to disease.*/
DROP table if exists ret1018.tmp_disease;
create table ret1018.tmp_disease as 
	(select distinct CUI,AUI, 'alzheimer' as disease from mrconso where LAT='ENG' AND str like '%alzheimer%') 
    union
	(select distinct CUI,AUI, 'amnesia' as disease from mrconso where LAT='ENG' AND str like '%amnesia%') 
    union
	(select distinct CUI,AUI, 'asthma' as disease from mrconso where LAT='ENG' AND str like '%asthma%') 
    union
	(select distinct CUI,AUI, 'breastcancer' as disease from mrconso where LAT='ENG' AND (str like '%breast neoplasm%' or str like '%breast cancer%' or str like '%breast carcinoma%'))
    union
	(select distinct CUI,AUI, 'epilepsy' as disease from mrconso where LAT='ENG' AND str like '%epilep%') 
    union
	(select distinct CUI,AUI, 'heart' as disease from mrconso where LAT='ENG' AND str like '%heart%') 
    union
	(select distinct CUI,AUI, 'hypertension' as disease from mrconso where LAT='ENG' AND (str like '%hypertension%' or str like '%high blood pressure%')) 
    union
	(select distinct CUI,AUI, 'obesity' as disease from mrconso where LAT='ENG' AND str like '%obesity%')    
    union
	(select distinct CUI,AUI, 'diabetes' as disease from mrconso where LAT='ENG' AND str like '%diabetes%')    
;

select count(*) from umls.tmp_disease;
-- 32343
select count(distinct cui) from umls.tmp_disease;
-- 13662

-- filter by STY=T047
/*drop table if exists ret1018.tmp_disease_T047;
create table ret1018.tmp_disease_T047 as 
	select distinct td.cui,td.aui,td.disease from umls.tmp_disease as td
	inner join umls.mrsty as sty
		on (sty.tui = 'T047' and sty.cui = td.cui)
        ;
-- 9418
select count(distinct cui) from ret1018.tmp_disease_T047;
-- 2073
*/

/*find all relationship that is relevant to disease and T047, as the seeds*/
drop tables if exists umls.tmp_rel_disease;
create table umls.tmp_rel_disease as 
select distinct *
	from (
		select distinct td.cui,td.aui,td.disease,r.cui2 as rel_cui, r.aui2 as rel_aui
			from umls.mrrel r 
			inner join umls.tmp_disease td
				on td.aui = r.aui1  and td.cui = r. cui1
		union
		select distinct td.cui,td.aui,td.disease,r.cui1 as rel_cui, r.aui1 as rel_aui
			from umls.mrrel r 
			inner join umls.tmp_disease td
				on td.aui = r.aui2  and td.cui = r. cui2
			 ) as tmp
        ;
-- 36913,139386
select count(distinct cui) from umls.tmp_rel_disease;
-- 1915,12853

/*find all tags that has relationship with disease and T047*/ 
drop table if exists umls.content_tag_disease_ytex;
create table umls.content_tag_disease_ytex  as 
	(select distinct c.disease, c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui, td.cui as rel_cui, td.aui as rel_aui, 0 as rel_flag
		from ret1007.content_tag_ytex c 
		inner join umls.tmp_disease td 
			on c.code = td.cui COLLATE utf8_unicode_ci  and td.disease = c.disease
     )	union (
	select distinct c.disease,c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui,  r.cui as rel_cui, r.aui as rel_aui, 1 as rel_flag
	from ret1007.content_tag_ytex c 
	inner join umls.tmp_rel_disease r 
		on c.code = r.rel_cui COLLATE utf8_unicode_ci and r.disease = c.disease
    );
-- 922946,2708965
select count(distinct cui) from content_tag_disease_ytex;
-- 507,773

/*add the stt column, because we want to pick the 'preferred name'.*/
alter table umls.content_tag_disease_ytex add (`STT` varchar(3) default null);
alter table umls.content_tag_disease_ytex add (`id` int auto_increment primary key );
update umls.content_tag_disease_ytex as cd 
	inner join umls.mrconso as con
		on cd.rel_cui = con.cui and cd.rel_aui = con.AUI
	set cd.stt = con.stt;  
    -- 1019939,2708965
    
/* reduce the data based on unique disease/target/cui/rel_cui, we don't have to use cui, because it should be cover by disease/blogId/wordIndex*/
drop table if exists umls.content_tag_disease_ytex_unique;
create table umls.content_tag_disease_ytex_unique as
select cd.* from content_tag_disease_ytex as cd
	inner join 
	(select distinct id from umls.content_tag_disease_ytex group by disease,blogId,target,wordIndex,rel_cui order by stt) as temp
    on cd.id = temp.id
    ;
    -- 618579,2179972
select count(distinct cui) from content_tag_disease_ytex_unique;
-- 507,771
select count(distinct target) from content_tag_disease_ytex_unique;
-- 876,1267
    
/* reduce the data based on unique target/cui, this will delete some cui, because we just pick one of the CUIs
	we should not use this when we try to match the CUI with other table.*/
drop table if exists content_tag_disease_ytex_unique2;
create table content_tag_disease_ytex_unique2 as
select cd.* from content_tag_disease_ytex_unique as cd
	inner join 
	(select distinct id from content_tag_disease_ytex_unique group by disease,blogId,target,wordIndex) as temp
    on cd.id = temp.id
;
-- 15667,24407 //2253sec
select count(distinct cui) from content_tag_disease_ytex_unique2;
-- 542,747
select * from content_tag_disease_ytex_unique2;

drop table if exists ret1018.content_tag_disease_ytex_unique_output;
create table  ret1018.content_tag_disease_ytex_unique_output as 
	select distinct 
		ct.disease,
		ct.blogId,
		ct.target,
		ct.cui,
        org.cui_text as umlsStr,
        ct.wordIndex,
        org.sentence_text as sentence,
        ct.rel_cui,
        ct.rel_aui,
        ct.rel_flag,
        ct.STT
    from umls.content_tag_disease_ytex_unique2 as ct 
	inner join (select * from ret1007.content_tag_ytex as cy
		 group by instance_id, anno_text,anno_base_id,sentence_text) org
	on org.instance_id=ct.blogId and org.anno_text = ct.target and org.anno_base_id=ct.wordIndex 
	;  
    -- 15667,24407

alter table ret1018.content_tag_disease_ytex_unique_output add (`rel_str` text);
update ret1018.content_tag_disease_ytex_unique_output as ct set rel_str = (select STR from umls.mrconso where CUI=ct.rel_cui and AUI=ct.rel_aui);

alter table ret1018.content_tag_disease_ytex_unique_output add (rel_all text default null);  

drop table if exists umls.tmp_pairs;
create table umls.tmp_pairs as select DISTINCT CUI,REL_CUI FROM ret1018.content_tag_disease_ytex_unique_output where rel_all is null;
-- 1022
update ret1018.content_tag_disease_ytex_unique_output as t 
	inner join
	(select r.CUI1,r.CUI2,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from umls.mrrel as r 
		inner join umls.tmp_pairs as ret 
        on  (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci) 
		 or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2
        ) as temp
    on  (temp.CUI1=t.cui COLLATE utf8_unicode_ci and temp.CUI2 = t.rel_cui COLLATE utf8_unicode_ci) 
	 or (temp.CUI2=t.cui COLLATE utf8_unicode_ci and temp.CUI1=t.rel_cui COLLATE utf8_unicode_ci)   
	set t.rel_all = temp.rel_all
    where t.rel_all is null
    ;

use ret1018;

alter table ret1018.content_tag_disease_ytex_unique_output add `tui` varchar(100) default null after `cui`;
alter table ret1018.content_tag_disease_ytex_unique_output add `rel_tui` varchar(100) default null after `rel_cui`;
update ret1018.content_tag_disease_ytex_unique_output as ct set tui = (select group_concat(distinct TUI) from umls.mrsty m where ct.cui = m.cui group by m.cui);
update ret1018.content_tag_disease_ytex_unique_output as ct set rel_tui = (select group_concat(distinct TUI) from umls.mrsty m where ct.rel_cui = m.cui group by m.cui);

select * from ret1018.content_tag_disease_ytex_unique_output where rel_all is  not null;

/* targets that found in ret1018 but not in ret1007*/
drop table if exists content_tag_increased;
create table content_tag_increased as 
  select distinct ct1018.* from ret1018.content_tag_disease_ytex_unique_output as ct1018
	left join ret1007.content_tag_disease_ytex_T047_unique_output as ct1007
		on ct1018.blogId = ct1007.blogId and ct1018.cui = ct1007.cui and ct1018.target = ct1007.target and ct1018.wordIndex=ct1007.wordIndex
	where ct1007.target is null
    ;
    -- 7278
drop table if exists content_tag_increased_cui;
create table content_tag_increased_cui as 
  select distinct ct1018.* from ret1018.content_tag_disease_ytex_unique_output as ct1018
	where ct1018.cui not in (
		select ct1007.cui from ret1007.content_tag_disease_ytex_T047_unique_output as ct1007
        )
    ;
drop table if exists content_tag_increased_target;
create table content_tag_increased_target as 
  select distinct ct1018.* from ret1018.content_tag_disease_ytex_unique_output as ct1018
	where ct1018.target not in (
		select ct1007.target from ret1007.content_tag_disease_ytex_T047_unique_output as ct1007
        )
    ;


/*coocurrence that found in ret1018 but not in ret 1007*/
create table co_occur_increased as 
  select distinct ret1018.co_occur.* from ret1018.co_occur as co1018
		left join ret1007.co_occur as co1007
			on co1018.cui1=co1007.cui1 and co1018.cui2=co1007.cui2 and co1018.target1=co1007.target1 and co1018.target2=co1007.target2
		where co1007.cui1 is null
        ;
        -- 7667
select * from ret1018.co_occur_increased;