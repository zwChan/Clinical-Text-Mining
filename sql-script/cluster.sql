create database cluster character set utf8;
use cluster;

create table k20all (
	`k`	int(8) DEFAULT NULL,
	`type`	varchar(40) DEFAULT NULL,
	`ngram`	varchar(200) DEFAULT NULL,
	`n`	int(8) DEFAULT NULL,
	`tfdf`	float DEFAULT NULL,
	`tf`	int(8) DEFAULT NULL,
	`df`	int(8) DEFAULT NULL,
	`cvalue`	varchar(40) DEFAULT NULL,
	`nest`	int(8) DEFAULT NULL,
	`nest_tf`	int(8) DEFAULT NULL,
	`umls_score`	int(8) DEFAULT NULL,
	`chv_score`	int(8) DEFAULT NULL,
	`contain_umls`	varchar(8) DEFAULT NULL,
	`contain_chv`	varchar(8) DEFAULT NULL,
	`win_umls`	int(8) DEFAULT NULL,
	`win_chv`	int(8) DEFAULT NULL,
	`sent_umls`  int(8) DEFAULT NULL,
	`sent_chv`	int(8) DEFAULT NULL,
	`umls_dist`	int(8) DEFAULT NULL,
	`chv_dist`	int(8) DEFAULT NULL,
	`sytax`	varchar(40) DEFAULT NULL,
	`nn`	varchar(8) DEFAULT NULL,
	`an`	varchar(8) DEFAULT NULL,
	`pn`	varchar(8) DEFAULT NULL,
	`anpn`  varchar(8)  DEFAULT NULL
    );
    
load data local infile 'C:\\fsu\\ra\\data\\ra-cluster.txt' 
into table k20all
fields terminated by '\t'
-- enclosed by '"'
lines terminated by '\r\n'
ignore 1 lines;
truncate table k20all;

select k,type,count(ngram)  from k20all group by k,type;
select * from k20all where k=18;


select * from umls.mrconso where cui='C1552861';
select  tui from umls.mrsty  where cui='C0018684';

select count(distinct blogId) from ytex.content_org;
select distinct blogId from  ytex.content_org_new;

select * from umls.mrconso where str = 'help';