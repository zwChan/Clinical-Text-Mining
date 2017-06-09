
create database deaf char set utf8;
use deaf;
drop table deaf.dataset_deaf;
create table dataset_deaf (
`PostID`           int(20), 
`UserID`           int(20),
`User`              varchar(100),
`Date`              varchar(100),
`Year`              varchar(100),
`Month`        varchar(100),
`Time`              varchar(100),
`AMPM`             varchar(100),
`Content`           text,
`ThreadTitle`      varchar(500),
`ThreadPath`       varchar(500),
`ThreadNumber`     int(20),
-- `LinkName`         varchar(100),
`Type`              varchar(100)
)
;

drop table dataset_autism;
create table dataset_autism (
`link`           varchar(256), 
`topic`          varchar(256),
`User`              varchar(100),
`userInfo`              varchar(500),
`Content`           text
-- `date`              varchar(100)
);
alter table dataset_autism add column PostID int primary key AUTO_INCREMENT first;

alter table dataset_autism drop column PostID;
create table deaf_cui like cancer.cancer_cui;
create table noncui like cancer.noncui;
create table deaf_metamap like cancer.cancer_metamap_cui;
-- drop table forum_metamap;
create table forum_metamap like deaf_metamap;
create table qa8000_metamap like deaf_metamap;

load data local infile '/tmp/alldeaf_HealthFitness_08_20_2015_Final.txt' into table dataset_deaf fields terminated by '\t' enclosed by '"' lines terminated by '\r\n' ignore 1 lines;
load data local infile '/tmp/autism_health_Final.txt' into table dataset_autism fields terminated by '\t' enclosed by '"' lines terminated by '\r\n' ignore 1 lines;


select distinct PostID,Content,threadNumber from dataset_deaf
	into outfile '/tmp/deaf_dataset.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
select distinct PostID,Content,link from dataset_autism
	into outfile '/tmp/autism_dataset.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';

select distinct tui from umls.mrsty;

delete from dataset_deaf where length(PostID) <2;
select count(distinct tid) from deaf_cui;
select count(*) from deaf_metamap where task='autism';
select count(*) from deaf_metamap where task='deaf';
select distinct sab from umls.mrconso;

select splitType, count(*) from deaf_metamap group by splitType ;
select * from deaf_metamap where length(sty)>6;
alter table deaf_metamap add column sab varchar(100) after preferStr;
-- truncate deaf_metamap;

select task, count(*)/count(distinct tid), count(distinct tid) from deaf_metamap group by task ;
select * from deaf_metamap where task = 'autism';
delete from deaf_metamap where sab='CHV' and sentLen > 51;

select count(*) from deaf_metamap where sab = 'SNOMEDCT_US'; -- 1077473
select count(*) from deaf_metamap_no_thread_id where sab = 'SNOMEDCT_US'; -- 1140048

select count(*) from deaf_metamap where sab = 'SNOMEDCT_US' and task='deaf'; -- 475902
select count(*) from deaf_metamap_no_thread_id where sab = 'SNOMEDCT_US' and task='deaf'; -- 494458

select count(*) from deaf_metamap where sab = 'SNOMEDCT_US' and task='austim'; -- 475902
select count(*) from deaf_metamap_no_thread_id where sab = 'SNOMEDCT_US' and task='austim'; -- 494458

select count(*) from deaf_metamap where sentLen > 51 and sab='SNOMEDCT_US'; -- 0
select count(*) from deaf_metamap where sentLen > 51 and sab='CHV'; -- 76004
select sentLen,count(*) from deaf_metamap where sab='SNOMEDCT_US' group by sentLen;
select sentLen,count(*) from deaf_metamap where sab='CHV' group by sentLen;

select * from qa8000_metamap;
select * from umls.mrconso where cui = 'C1258068';
