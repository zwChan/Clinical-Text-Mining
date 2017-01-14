create database ner200 char set utf8;
use ner200;
CREATE TABLE bioportal (
`tid` varchar(30),
`org_str` varchar(100),
`sentence` text
);

CREATE TABLE lvalue (
`tid` varchar(30),
`org_str` varchar(100),
`sentence` text
);

CREATE TABLE lvaluerake (
`tid` varchar(30),
`org_str` varchar(100),
`sentence` text
);

CREATE TABLE manual (
`tid` varchar(30),
`major` varchar(100),
`others` text,
`durStr` varchar(100),
`sentence` text
);

 drop table bioportal;
 drop table lvalue;
 drop table lvaluerake;
drop table manual;

load data local infile 'C:\\fsu\\ra\\data\\201601\\split_criteria\\1308_colorectal_trials_criteria_0413_ret.csv.cui' into table cancer_cui fields terminated by '\t' enclosed by '"' lines terminated by '\n' ignore 1 lines;
load data local infile 'C:\\fsu\\ra\\data\\201601\\split_criteria\\1308_colorectal_trials_criteria_0413_ret.csv.mm.cui' into table cancer_mm_cui fields terminated by '\t' enclosed by '"' lines terminated by '\n' ignore 1 lines;

load data local infile 'C:\\Users\\Jason\\Downloads\\bioportal.txt' into table bioportal fields terminated by '\t' enclosed by '"' lines terminated by '\r\n';
load data local infile 'C:\\Users\\Jason\\Downloads\\lvalue.txt' into table lvalue fields terminated by '\t' enclosed by '"' lines terminated by '\r\n';
load data local infile 'C:\\Users\\Jason\\Downloads\\lvaluerake.txt' into table lvaluerake fields terminated by '\t' enclosed by '"' lines terminated by '\r\n';
load data local infile 'C:\\Users\\Jason\\Downloads\\random_200_sentences_cancer_studies.txt' into table manual fields terminated by '\t' enclosed by '"' lines terminated by '\r\n' ignore 1 lines;
select * from cancer_cui where length(cui)>0;
select * from cancer_mm_cui;
select * from bioportal;
select * from lvalue;
select * from lvaluerake;
select * from manual where cui is not null;
delete from manual where length(tid) < 1;
delete from bioportal where length(tid) < 1;
delete from lvalue where length(tid) < 1;
delete from lvaluerake where length(tid) < 1;

alter table manual add column `cui` varchar(20);
alter table bioportal add column `cui` varchar(20);
alter table lvalue add column `cui` varchar(20);
alter table lvaluerake add column `cui` varchar(20);
update manual m set cui=(select u.cui from umls.mrconso u,umls.mrsty s where u.str=m.major and u.cui=s.cui and s.tui in ("T200","T020","T190","T049","T019","T047","T050","T037","T048","T191","T046","T184","T060","T065","T058","T059","T063","T062","T061") limit 1);
update bioportal m set cui=(select u.cui from umls.mrconso u,umls.mrsty s where u.str=m.org_str and u.cui=s.cui and s.tui in ("T200","T020","T190","T049","T019","T047","T050","T037","T048","T191","T046","T184","T060","T065","T058","T059","T063","T062","T061") limit 1);
update lvalue m set cui=(select u.cui from umls.mrconso u,umls.mrsty s where u.str=m.org_str and u.cui=s.cui and s.tui in ("T200","T020","T190","T049","T019","T047","T050","T037","T048","T191","T046","T184","T060","T065","T058","T059","T063","T062","T061") limit 1);
update lvaluerake m set cui=(select u.cui from umls.mrconso u,umls.mrsty s where u.str=m.org_str and u.cui=s.cui and s.tui in ("T200","T020","T190","T049","T019","T047","T050","T037","T048","T191","T046","T184","T060","T065","T058","T059","T063","T062","T061") limit 1);

select distinct tid from cancer_cui where pattern!= 'CUI_ALL';
select distinct pattern from cancer_cui;

select distinct c.tid,c.org_str,c.sentence from cancer_cui c, cancer_mm_cui m  where c.org_str = m.org_str and c.tid = m.tid;
select distinct c.tid,c.org_str,c.sentence from cancer_cui c, bioportal m where c.org_str = m.major and c.tid = m.tid; 

select distinct m.tid,m.major,m.sentence from cancer_cui c, manual m where c.tid = m.tid and length(m.cui)>0 and c.`group`='CUI_DISEASE_MAIN' and  instr(c.org_str,m.major) > 0; 
select distinct m.tid,m.major,m.sentence from cancer_cui c, manual m where c.tid = m.tid and c.cui is not null and m.cui is not null and  instr(c.org_str,m.major) > 0; 
select distinct m.tid,m.major,m.sentence from cancer_mm_cui c, manual m where c.tid = m.tid and c.cui is not null and m.cui is not null and  instr(c.org_str,m.major) > 0; 
select distinct m.tid,m.major,m.sentence from bioportal c, manual m where c.tid = m.tid and c.cui is not null and m.cui is not null and  instr(c.org_str,m.major) > 0; 
select distinct m.tid,m.major,m.sentence from lvalue c, manual m where c.tid = m.tid and c.cui is not null and m.cui is not null and  instr(c.org_str,m.major) > 0; 
select distinct m.tid,m.major,m.sentence from lvaluerake c, manual m where c.tid = m.tid and c.cui is not null and m.cui is not null and  instr(c.org_str,m.major) > 0; 

