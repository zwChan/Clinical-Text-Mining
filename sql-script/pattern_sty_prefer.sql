use cancer;
select * from cancer_cui where skipNum>0 and length(method)>0 and method != 'fullDep'  and sentence not like '% or %';
select distinct method from cancer_cui;
select org_str, sentence from cancer_cui where sentence='Active gastrointestinal tract disease with malabsorption syndrome.';

select * from noncui order by freq desc;
select distinct sentence from cancer_cui where splitType='No';

create database cancer_more_sty char set utf8;
use cancer_more_sty;
create table cancer_cui like cancer.cancer_cui;
create table noncui like cancer.noncui;
create table cancer_metamap_cui like cancer.cancer_metamap_cui;

use ner200;
-- get the sty pairs that a term belongs to both of them
select  A.sty,B.sty, count(*) cnt from cancer_cui A, cancer_cui B where length(A.cui)>0 and length(B.cui)>0 and A.tid=B.tid and A.criteriaId=B.criteriaId and A.sentId=B.sentId and A.org_str=B.org_str and A.sty > B.sty group by A.sty,B.sty order by cnt desc;

select count(*) from cancer_more_sty.cancer_cui;

select * from sty_prefer_cui where sty_prefer_cui.sty1=null ;
select  sty_prefer_cui.sty1=null from sty_prefer_cui;

alter table cancer_cui add column flag int after sty;
create table sty_prefer_orgstr like sty_prefer_cui;

update sty_prefer_orgstr,sty_prefer_cui set sty_prefer_orgstr.prefer=sty_prefer_cui.prefer,sty_prefer_orgstr.reason=sty_prefer_cui.reason where sty_prefer_orgstr.sty1=sty_prefer_cui.sty1 and sty_prefer_orgstr.sty2=sty_prefer_cui.sty2;

select *  from sty_prefer_orgstr;

select  A.sty,B.sty, count(*) cnt from cancer_cui A, cancer_cui B where length(A.cui)>0 and length(B.cui)>0 and A.tid=B.tid and A.criteriaId=B.criteriaId and A.sentId=B.sentId and A.org_str=B.org_str and A.sty > B.sty group by A.sty,B.sty order by cnt desc;

update cancer_cui A,cancer_cui B,cancer_more_sty.sty_prefer_cui C set A.sty_ignored = true where length(A.cui)>0 and length(B.cui)>0 and A.tid=B.tid and A.criteriaId=B.criteriaId and A.sentId=B.sentId and A.org_str=B.org_str and A.sty > B.sty and C.prefer != A.sty and  ((A.sty=C.sty1 and C.sty2=B.sty) or (A.sty=C.sty2 and C.sty2=B.sty));

select * from cancer_cui A where sty='T116';
-- update cancer_cui set flag=null;
select sty, count(*) cnt from cancer_cui group by sty order by cnt desc;
create table sty_prefer_orgstr like cancer_more_sty.sty_prefer_orgstr;
select * from sty_prefer_orgstr where length(prefer)>0;
select * from cancer_more_sty.sty_prefer_orgstr;
update sty_prefer_orgstr A, cancer_more_sty.sty_prefer_cui B set A.prefer=B.prefer,A.reason=B.reason where A.sty1=B.sty1 and A.sty2=B.sty2;

select * from cancer_cui where sty_ignored is null;
select count(distinct org_str) from cancer_cui;
