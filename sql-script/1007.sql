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
DROP table tmp_disease;
create table tmp_disease as 
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
select count(*) from tmp_disease;
-- 32343
select count(distinct cui) from tmp_disease;
-- 13662

-- filter by STY=T047
drop table if exists tmp_disease_T047;
create table tmp_disease_T047 as 
	select distinct td.cui,td.aui,td.disease from tmp_disease as td
	inner join mrsty as sty
		on (sty.tui = 'T047' and sty.cui = td.cui)
        ;
-- 9418
select count(distinct cui) from tmp_disease_T047;
-- 2073

/*find all relationship that is relevant to disease and T047, as the seeds*/
drop tables if exists tmp_rel_disease_T047;
create table tmp_rel_disease_T047 as 
select distinct *
	from (
		select distinct td.cui,td.aui,td.disease,r.cui2 as rel_cui, r.aui2 as rel_aui
			from mrrel r 
			inner join tmp_disease_T047 td
				on td.aui = r.aui1  and td.cui = r. cui1
		union
		select distinct td.cui,td.aui,td.disease,r.cui1 as rel_cui, r.aui1 as rel_aui
			from mrrel r 
			inner join tmp_disease_T047 td
				on td.aui = r.aui2  and td.cui = r. cui2
			 ) as tmp
        ;
-- 36913
select count(distinct cui) from tmp_rel_disease_T047;
-- 1915

/*find all tags that has relationship with disease and T047*/ 
drop table if exists content_tag_disease_ytex_T047;
create table content_tag_disease_ytex_T047  as 
	(select distinct c.disease, c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui, td.cui as rel_cui, td.aui as rel_aui, 0 as rel_flag
		from ret1007.content_tag_ytex c 
		inner join umls.tmp_disease_T047 td 
			on c.code = td.cui COLLATE utf8_unicode_ci  and td.disease = c.disease
     )	union (
	select distinct c.disease,c.instance_id as blogId, c.anno_text as target, c.anno_base_id as wordIndex, c.code as cui,  r.cui as rel_cui, r.aui as rel_aui, 1 as rel_flag
	from ret1007.content_tag_ytex c 
	inner join umls.tmp_rel_disease_T047 r 
		on c.code = r.rel_cui COLLATE utf8_unicode_ci and r.disease = c.disease
    );
-- 922946
select count(distinct cui) from content_tag_disease_ytex_T047;
-- 507

/*add the stt column, because we want to pick the 'preferred name'.*/
alter table content_tag_disease_ytex_T047 add (`STT` varchar(3) default null);
alter table content_tag_disease_ytex_T047 add (`id` int auto_increment primary key );
update content_tag_disease_ytex_T047 as cd 
	inner join umls.mrconso as con
		on cd.rel_cui = con.cui and cd.rel_aui = con.AUI
	set cd.stt = con.stt;  
    -- 1019939
    
/* reduce the data based on unique disease/target/cui/rel_cui, we don't have to use cui, because it should be cover by disease/blogId/wordIndex*/
drop table if exists content_tag_disease_ytex_T047_unique;
create table content_tag_disease_ytex_T047_unique as
select cd.* from content_tag_disease_ytex_T047 as cd
	inner join 
	(select distinct id from content_tag_disease_ytex_T047 group by disease,blogId,target,wordIndex,rel_cui order by stt) as temp
    on cd.id = temp.id
    ;
    -- 618579
select count(distinct cui) from content_tag_disease_ytex_T047_unique;
-- 507
select count(distinct target) from content_tag_disease_ytex_T047_unique;
-- 876
    
/* reduce the data based on unique target/cui, this will delete some cui, because we just pick one of the CUIs
	we should not use this when we try to match the CUI with other table.*/
drop table if exists content_tag_disease_ytex_T047_unique2;
create table content_tag_disease_ytex_T047_unique2 as
select cd.* from content_tag_disease_ytex_T047_unique as cd
	inner join 
	(select distinct id from content_tag_disease_ytex_T047_unique group by disease,blogId,target,wordIndex) as temp
    on cd.id = temp.id
;
-- 15667
select count(distinct cui) from content_tag_disease_ytex_T047_unique2;
-- 542
select * from content_tag_disease_ytex_T047_unique2;

drop table if exists ret1007.content_tag_disease_ytex_T047_unique_output;
create table  ret1007.content_tag_disease_ytex_T047_unique_output as 
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
    from umls.content_tag_disease_ytex_T047_unique2 as ct 
	inner join (select * from ret1007.content_tag_ytex as cy
		 group by instance_id, anno_text,anno_base_id,sentence_text) org
	on org.instance_id=ct.blogId and org.anno_text = ct.target and org.anno_base_id=ct.wordIndex 
	;  
    -- 15667

alter table ret1007.content_tag_disease_ytex_T047_unique_output add (`rel_str` text);
update ret1007.content_tag_disease_ytex_T047_unique_output as ct set rel_str = (select STR from umls.mrconso where CUI=ct.rel_cui and AUI=ct.rel_aui);

alter table ret1007.content_tag_disease_ytex_T047_unique_output add (rel_all text default null);  

drop table if exists umls.tmp_pairs;
create table umls.tmp_pairs as select DISTINCT CUI,REL_CUI FROM ret1007.content_tag_disease_ytex_T047_unique_output where rel_all is null;
update ret1007.content_tag_disease_ytex_T047_unique_output as t 
	inner join
	(select r.CUI1,r.CUI2,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from umls.mrrel as r 
		inner join umls.tmp_pairs as ret 
        on  (r.CUI1=ret1007.cui COLLATE utf8_unicode_ci and r.CUI2 = ret1007.rel_cui COLLATE utf8_unicode_ci) 
		 or (r.CUI2=ret1007.cui COLLATE utf8_unicode_ci and r.CUI1=ret1007.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2
	) as temp
    on  (temp.CUI1=t.cui COLLATE utf8_unicode_ci and temp.CUI2 = t.rel_cui COLLATE utf8_unicode_ci) 
	 or (temp.CUI2=t.cui COLLATE utf8_unicode_ci and temp.CUI1=t.rel_cui COLLATE utf8_unicode_ci)   
	set t.rel_all = temp.rel_all
    where t.rel_all is null
    ;

select * from ret1007.content_tag_disease_ytex_T047_unique_output where rel_all is  not null;