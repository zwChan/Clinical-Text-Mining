
create database deaf char set utf8;
use deaf;
drop table deaf.deaf;
create table deaf (
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
`LinkName`         varchar(100),
`Type`              varchar(100)
)
;

create table deaf_cui like cancer.cancer_cui;
create table noncui like cancer.noncui;


select count(*) from deaf.dataset;
select * from dataset;

select distinct PostID,Content from dataset
	into outfile '/tmp/deaf_dataset.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';


select distinct tui from umls.mrsty;

select count(distinct tid) from deaf_cui;
select * from deaf_cui;

