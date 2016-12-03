
SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='cancer_cui';

use compact_092316;

 alter table cancer_cui add column var boolean after tags;

create table variable (var varchar(200));
create index idx_var on variable(var) using hash;

create index idx_tid on all_diseases_trials(TID) using hash;
create index idx_disease on all_diseases_trials(disease) using hash;
create index idx_sty on cancer_cui(sty) using hash;
create index idx_nested on cancer_cui(nested) using hash;

alter table meta add primary key (tid);
create index idx_status on meta (overall_status);
create index idx_min_age2 on meta (minimum_age_in_year);
create index idx_max_age2 on meta (maximum_age_in_year);
create index idx_phase on meta (phase);
create index idx_int on meta (intervention_type);
create index idx_std on meta (study_type);

select count(*) from cancer_cui; -- 6179540
select count(*) from meta; -- 225364
select count(*) from all_diseases_trials; -- 1016579

select tid, count(*) as cnt from meta group by tid order by cnt desc; -- one to one
select maximum_age_in_year, count(*) as cnt from meta group by maximum_age_in_year order by cnt desc;

select count(*) from meta where study_type ='Interventional' and (STR_TO_DATE(start_date,'%M %Y') >= STR_TO_DATE('JANUARY 2000','%M %Y'))
and (STR_TO_DATE(start_date,'%M %Y') <= STR_TO_DATE('SEPTEMBER 2016','%M %Y'));  -- 172245
select count(distinct tid) from all_diseases_trials where disease ='diabetes-mellitus-type-2'; -- 5000

CREATE TABLE T2DM_0100_0916 AS (select * from meta where study_type ='Interventional' and (STR_TO_DATE(start_date,'%M %Y') >= STR_TO_DATE('JANUARY 2000','%M %Y'))
and (STR_TO_DATE(start_date,'%M %Y') <= STR_TO_DATE('SEPTEMBER 2016','%M %Y')) 
and tid in (select distinct tid from all_diseases_trials where disease ='diabetes-mellitus-type-2'));  -- 4201
create table T2DM_CUI as (select C.* from cancer_cui C join T2DM_0100_0916 T on C.tid=T.tid ); -- 121341

select var, count(*) from cancer_cui group by var;

-- Results to be reported: 
-- Rank of the diseases by the number of criteria with temporal constraints, (# of umls terms with temporal constrains)
select T.disease, count(*) as cnt from cancer_cui C join all_diseases_trials T on C.tid=T.tid and C.month >= 0 and C.nested != 'nested' and C.var is null group by T.disease order by cnt desc;
-- Rank of the diseases by the average number of criteria with temporal constraints per trial
select T.disease, count(distinct tid) as cnt from all_diseases_trials T group by T.disease order by cnt desc; 
-- Distribution of semantic types (overall)
select sty, count(*) as cnt from cancer_cui where nested != 'nested'  and C.var is null group by sty order by cnt desc;
-- Frequency of temporal patterns (overall)
select month, count(*) as cnt from cancer_cui where nested != 'nested'  and C.var is null group by month order by cnt desc;


select * from cancer_cui where month<-1 ;

 update cancer_cui d join compact_092316.variable v on (v.var=d.cui_str or v.var=d.org_str) set d.var = true;








