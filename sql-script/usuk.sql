create database usuk char set utf8;
use usuk;

create table UK_T2DM_Trials AS
(select tid, criteria from compact_092316.trials where
(STR_TO_DATE(start_date,'%M %Y') >= STR_TO_DATE('JANUARY 2005','%M %Y'))
and (STR_TO_DATE(start_date,'%M %Y') <= STR_TO_DATE('September 2016','%M %Y'))
and study_type = 'Interventional' and tid in (select tid from compact_092316.all_diseases_trials where disease ='diabetes-mellitus-type-2')
and tid in (select tid from compact_092316.authority where authority like '%United Kingdom%'));


create table US_T2DM_Trials AS
(select tid, criteria from compact_092316.trials where
(STR_TO_DATE(start_date,'%M %Y') >= STR_TO_DATE('JANUARY 2005','%M %Y'))
and (STR_TO_DATE(start_date,'%M %Y') <= STR_TO_DATE('September 2016','%M %Y'))
and study_type = 'Interventional' and tid in (select tid from compact_092316.all_diseases_trials where disease ='diabetes-mellitus-type-2')
and tid in (select tid from compact_092316.authority where authority like '%United States%'));

select tid,criteria from US_T2DM_Trials;

create table cancer_cui like cancer.cancer_cui;
create table noncui like cancer.noncui;
create table cancer_mm_cui like ner200.cancer_mm_cui;


