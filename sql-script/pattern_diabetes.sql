
create database diabetes char set utf8;
use diabetes; 
rename table compact_092316.T2DM_CUI to diabetes.db_cui;
rename table compact_092316.T2DM_0100_0916 to diabetes.meta;
delete from db_cui where cui_str = 'screening';

alter table db_cui add column var boolean after tags;

select count(*) from db_cui where nested='nested'; -- 43411
delete from db_cui where nested = 'nested';

select count(distinct cui) from db_cui;  -- 2668
select count( *) from db_cui;  -- 121341
-- it is less because one cui may occurs multiple times in one criteria.
select count( distinct tid,criteriaId,sentId,termId,cui) from db_cui;  -- 76573


-- creat a table for (cui,sty,org_str)
drop table db_cui_uniq;
create table db_cui_uniq as 
 select * from db_cui group by tid,criteriaId,sentId,termId; -- 55994
 
-- creat frequency table for (cui,sty)
drop table freq_cui; -- 5156,2708
create table freq_cui as select cui,sty,org_str,cui_str,count(*) as freq from db_cui group by cui,sty order by freq desc;
-- duration frequency in history pattern
drop table freq_cui_dur; -- 637, 414
create table freq_cui_dur as select cui,sty,duration,org_str,cui_str,count(*) as freq_dur from db_cui where pattern like 'HISTORY_WITH%' group by cui,sty,duration order by freq_dur desc;

-- sty frequency
select sty,sum(freq) as sum from freq_cui group by sty order by sum desc;
select sty,sum(freq_dur) as sum from freq_cui_dur group by sty order by sum desc;

-- ========== stat tabel ==============
-- sty frequency
-- whole
select sty, count(*) as cnt from db_cui group by sty order by sty; 
-- types
select task,count(*) as cnt from db_cui group by task;
select task,sty, count(*) as cnt from db_cui group by task,sty order by task,sty,cnt desc; 

-- cui frequency
-- whole
select * from (select cui, cui_str, count(*) as cnt from db_cui group by cui) as t where cnt>500 order by cnt desc;
-- types
select * from (select task,cui, cui_str, count(*) as cnt from db_cui group by task,cui) as t where cnt>800 order by task,cnt desc;

-- individul cui distribution
set @cuicui='C0005893'; -- BMI
set @cuicui='C0202054'; -- HBA1C
set @cuicui='C0202098'; -- iNSULIN

-- duration distribution
-- select duration, count(*) as cnt from db_cui where cui=@cuicui group by duration order by duration;
select month, count(*) as cnt from db_cui where cui=@cuicui group by month order by month;
-- types duration distribution

drop table criteria_uniq;
create table criteria_uniq as select distinct task,tid,type,criteriaId,pattern,neg from db_cui; -- 189808, 49422
select type,neg>0 as neg, count(*) as cnt from db_cui group by type,neg>0 ;

select * from criteria_uniq where neg>0; --


-- freq pattern -- for a pattern more than one cui, we should only count once.
-- whole
select pattern, count(*) as cnt,sum(type='inclusion') as Inc,sum(type='exclusion') as Exc from criteria_uniq group by pattern order by cnt desc;
-- inlude
select pattern, count(*) as cnt from criteria_uniq where type='inclusion' group by pattern order by cnt desc;
-- exclude
select pattern, count(*) as cnt from criteria_uniq where type = 'exclusion' group by pattern order by cnt desc;

select  type, count(*) from db_cui group by type;
select * from db_cui where type='PRIOR CONCURRENT THERAPY';



-- drop table db_cui;
select distinct sentence from db_cui where length(sentence) > 500 ;

select tid,criteriaId,sentence,termId,count(*) as cnt from db_cui where sty != 'T033' group by tid,criteriaId,sentence,termId;

-- sentence len distribution
select sentLen, count(distinct tid,criteriaId,sentId) from db_cui group by sentLen;

select count( cui) from db_cui where method='conjDep';


select cui,cui_str,month,count(*) as num from db_cui where month > -1 and pattern != 'None' group by cui, month order by num desc, month ;

select cui,cui_str,count(*) as num from db_cui where month > -1 and pattern != 'None' group by cui order by num desc ;

select cui,cui_str,count(*) as num from db_cui where pattern != 'None' and month > -1 and ((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0))
group by cui order by num desc ;

-- cui duration distribution. heatmap
select can.cui, can.cui_str, can.month, freq.cui_freq, count(*) as num,can.sty from db_cui as can 
	inner join (select cui, count(*) as cui_freq from db_cui where pattern != 'None' and duration > -1 and 
	((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0)) group by cui ) freq
  on can.cui = freq.cui
	where can.pattern != 'None' and can.month > -1 and 
	((can.type='Inclusion' and can.neg>0) or (can.type='Exclusion' and can.neg = 0) )
     and can.cui not in ("C3810814", "C0947630", "C0008972","C1516879","C1696073","C0006111","C0034656","C3161471") 
  group by can.cui,can.month
  order by freq.cui_freq desc,can.month asc, num desc;     
  
  
  -- -------------------------
 select pattern, sentence from db_cui where pattern = 'CONFIRMED_MORETHAN' group by sentence; 
 
 select * from db_cui where pattern = 'CONCURRENT_EF';
 
 
 select V.cui,V.sty,V.cui_str,count(*) as freq from db_cui V, meta T where task = 'Prostate' and  T.tid = V.tid and  (T.phase LIKE '%%') and  (T.overall_status LIKE '%%') and  (T.study_type LIKE '%%') and  (T.intervention_type LIKE '%%') and  (T.agency_type LIKE '%%') and  (T.gender LIKE '%%') and  (T.start_date LIKE '%%') and 1=1 and  (T.intervention_model LIKE '%%') and  (T.allocation LIKE '%%') and  (1=1) and  (V.task='Prostate')  group by V.cui,V.sty order by freq desc limit 10 ;
 
 SELECT T.gender, count(*) FROM meta T WHERE T.tid in (SELECT distinct tid from db_cui where task = 'Lung') GROUP BY T.gender;
 
 select * from db_cui where cui = 'C0005893' and month >= 0;
 
 update db_cui d join compact_092316.variable v on (v.var=d.cui_str or v.var=d.org_str) set d.var = true;
 select var,count(*) from db_cui group by var ;
 select count(*) from db_cui d join compact_092316.variable v on (v.var='BMI' and d.org_str='BMI') ;

select var, count(*) from db_cui group by var;
