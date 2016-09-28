use ret1007;
select count(distinct instance_id) from content_tag_ytex;


-- 1.	How many blogs has related concepts?
select count(distinct blogid) from content_tag_disease_ytex_T047_unique_output;
-- 2589

-- # of unique blogs by disease types
select disease, count(distinct blogid) from content_tag_disease_ytex_T047_unique_output group by disease;
-- Alzheimer: 108, amnesia: 193, asthma, 378, diabetes: 939, epilepsy: 246, heart: 527, hypertension: 56, obesity: 205
-- In sum: 2661

select count(distinct sentence) from content_tag_disease_ytex_T047_unique_output;
-- 9050
select count(distinct sentence_text) from content_tag_ytex;
-- 25654
-- How many chronic disease-related terms/ concepts in the analyzed in total? 
# of terms in total: 876
select count(distinct target) from content_tag_disease_ytex_T047_unique_output; 
select disease, count(distinct target) from content_tag_disease_ytex_T047_unique_output group by disease; 


select A.target, count(*) from (select distinct blogid, target, umlsStr from content_tag_disease_ytex_T047_unique_output) A group by A.target order by count(*) desc;
select A.target, count(*) from (select distinct blogid, target, wordIndex from content_tag_disease_ytex_T047_unique_output) A group by A.target order by count(*) desc;


-- List of unique terms by dieses type (with no frequency info)
select A.disease, group_concat(distinct A.target) from (select distinct blogid, target, umlsStr, disease from content_tag_disease_ytex_T047_unique_output) A group by A.disease order by A.disease, count(*) desc;
-- List of terms and each term’s frequency	by disease type
select A.disease, group_concat(distinct A.target), count(*) from (select distinct blogid, target, umlsStr, disease from content_tag_disease_ytex_T047_unique_output) A group by A.disease, A.target order by A.disease, count(*) desc;



select count(*) from ret1007.content_tag_disease_ytex_T047_unique_output;

select r.disease, r.cui, group_concat(r.target), count(r.cui)  from (select distinct blogid, disease, target, cui, umlsStr from content_tag_disease_ytex_T047_unique_output) r
where r.cui in (select cui from umls.mrconso where sab like '%CHV%' and lat ='ENG') group by r.disease, r.cui order by count(cui) desc;

-- Total number of concepts from other terminology sources
select con.sab, count(distinct r.cui), group_concat(distinct r.target) from content_tag_disease_ytex_T047_unique_output as r
inner join umls.mrconso as con 
	on con.cui=r.cui and lat ='ENG'
group by con.sab;

-- List of concepts (with associated terms) from other terminology sources
select r.cui, group_concat(r.target), count(r.cui) from (select distinct blogid, target, cui, umlsStr from content_tag_disease_ytex_T047_unique_output) r
where r.cui in (select cui from umls.mrconso where sab='SNOMEDCT_US' and lat ='ENG')
group by r.cui, r.target order by count(r.cui) desc;

select distinct sab from umls.mrconso;

select rel_cui, group_concat(distinct cui), group_concat(distinct target) from content_tag_disease_ytex_T047_unique_output group by rel_cui;

select rel_all, group_concat(distinct target), group_concat(distinct rel_cui), group_concat(distinct cui) 
from content_tag_disease_ytex_T047_unique_output where rel_all like "%isa%" or '%par%' or '%chd%' group by rel_all;







select C.cui1, C.cui2, count(distinct C.blogid) as cnt from (
select distinct A.blogid, A.cui as cui1, B.cui as cui2
from content_tag_disease_ytex_T047_unique_output A, content_tag_disease_ytex_T047_unique_output B
where A.blogid = B.blogid and A.cui < B.cui) as C
group by C.cui1, C.cui2 
order by cnt desc;  
-- 5513 rows returned


select disease, count(distinct cui) from content_tag_disease_ytex_T047_unique_output
 group by disease;


select * from co_occur;
select * from umls.mrsty;

select C.cui1, C.target1, C.cui2, C.target2, count(*) from (
select distinct A.blogid, A.cui as cui1, A.target as target1, B.cui as cui2, B.target as target2 
from content_tag_disease_ytex_unique_output A, content_tag_disease_ytex_unique_output B
where A.blogid = B.blogid and A.cui < B.cui) as C
group by C.target1, C.target2
order by count(*) desc;


select C.cui1,C.target1, C.cui2,C.target2, count(*) from (
select distinct A.blogid, A.cui as cui1, A.target as target1, B.cui as cui2, B.target as target2 
from content_tag_disease_ytex_unique_output A, content_tag_disease_ytex_unique_output B
where A.blogid = B.blogid and A.cui < B.cui) as C
group by C.cui1, C.cui2 
order by count(*) desc; 


create table co_occur as (
select C.cui1,C.target1, C.cui2,C.target2, count(*) from (
select distinct A.blogid, A.cui as cui1, A.target as target1, B.cui as cui2, B.target as target2 
from content_tag_disease_ytex_unique_output A,  content_tag_disease_ytex_unique_output B
where A.blogid = B.blogid and A.cui < B.cui) as C
group by C.cui1, C.cui2
	order by count(*) desc);
    -- 13009

	alter table co_occur add `sab1` varchar(100) default null after `cui1`;
	alter table co_occur add `sab2` varchar(100) default null after `cui2`;

	alter table co_occur add `tui1` varchar(100) default null after `cui1`;
	alter table co_occur add `tui2` varchar(100) default null after `cui2`;
	alter table co_occur add `sty1` varchar(500) default null after `cui1`;
	alter table co_occur add `sty2` varchar(500) default null after `cui2`;

	update co_occur set sab1 = (
	select 'true' from umls.mrconso m where cui1 = m.cui and m.sab='CHV' limit 1);
	update co_occur set sab2 = (
	select 'true' from umls.mrconso m where cui2 = m.cui and m.sab='CHV' limit 1);
    
    update co_occur set tui1 = (select group_concat(distinct TUI) from umls.mrsty m where cui1 = m.cui group by m.cui);
    update co_occur set tui2 = (select group_concat(distinct TUI) from umls.mrsty m where cui2 = m.cui group by m.cui);
    update co_occur set sty1 = (select group_concat(distinct STY) from umls.mrsty m where cui1 = m.cui group by m.cui);
    update co_occur set sty2 = (select group_concat(distinct STY) from umls.mrsty m where cui2 = m.cui group by m.cui);
    
    

select * from co_occur where sab1 is not null and sab2 is null;

select distinct A.target2 from (select * from co_occur where sab1 is not null and sab2 is null) as A; 
select distinct A.target2 from (select * from ret1007.co_occur where sab1 is not null and sab2 is null) as A;

select * from co_occur where sab1 is not null and sab2 is null; 

select distinct A.target2 from (select * from co_occur where sab1='true' and sab2 is null) as A;
select distinct A.cui1, A.target1, cui2, group_concat(distinct target2) from 
(select * from co_occur where sab1='true' and sab2 is null) as A 
group by A.target1 order by count(*) desc;

select * from (select * from co_occur where sab1 is not null and sab2 is null) as A 
group by A.target1 order by count(*) desc; 

select distinct distinct A.cui1, group_concat(distinct A.target1), cui2, A.target2 from 
(select * from co_occur where sab1='true' and sab2 is null) as A 
group by A.target2, A.target1 order by count(*) desc;

select distinct A.target1 from (select * from co_occur where sab1 is null and sab2 is not null) as A;

