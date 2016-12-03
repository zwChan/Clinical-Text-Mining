show databases;
use ret;
use umls;
use gg;
show tables;
select * from content_tag_compare_same;
select count(distinct cui) from umls.mrconso;

create database gg character set utf8;

drop table social;
CREATE TABLE `social` (
  `stt` varchar(300)  DEFAULT NULL,   
  `sty` varchar(300)  DEFAULT NULL,		
  `ptr` varchar(300)  DEFAULT NULL,			
  `aui` varchar(9)  DEFAULT NULL,	 
  `sid` bigint DEFAULT NULL,				
  `fsn` varchar(500)  DEFAULT NULL
  ) CHARSET=utf8;
  
 load data local infile 'C:\\fsu\\ra\\data\\graph-group\\SNOMED_database\\SNOMEDCT_SOCIAL_CONTEXT_with_PATH.tsv' 
	into table social 
	fields terminated by '\t'
	-- enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines; 

insert observe (stt,sty,ptr,aui) values ('aaa','bbb','111.222.333','444');
insert observe (stt,sty,ptr,aui) values ('aaa','bbb','555.666.777','333');
use gg;

ALTER TABLE observe add ( pt_aui varchar(30) default null);
/*get the preferred term by snomedct code from mrconso. time consuming 4 hours*/
update observe as o
	set o.pt_aui = (
		select aui from umls.mrconso where SAB='SNOMEDCT_US' AND TTY = 'PT' AND CODE = o.sid);
ALTER TABLE social add ( pt_aui varchar(30) default null);
UPDATE social AS s 
	inner join umls.mrconso AS con
		ON con.SAB='SNOMEDCT_US' AND con.TTY = 'PT' AND con.CODE = s.sid
	SET s.pt_aui = con.AUI
    ;
ALTER TABLE observe add ( pt_aui_str varchar(1000) default null);
UPDATE observe AS s 
	inner join umls.mrconso AS con
		ON s.pt_aui = con.AUI
	SET s.pt_aui_str = con.STR
    ;    
ALTER TABLE social add ( pt_aui_str varchar(1000) default null);
UPDATE social AS s 
	inner join umls.mrconso AS con
		ON s.pt_aui = con.AUI
	SET s.pt_aui_str = con.STR
    ;        
    
set group_concat_max_len=102400000;
select count(*) from observe group by stt,sty;

create table observe_group as 
  select o1.stt, o1.sty, count(*) as cnt, group_concat(distinct o1.pt_aui, ' ', IFNULL(o2.pt_aui, 'null') separator ',co_occur') from 
	(select stt,sty,ptr,pt_aui from observe o) o1 
	left join observe o2
	on o1.stt=o2.stt and o1.sty=o2.sty
		and o1.ptr regexp concat('.*',o2.pt_aui,'$')
    group by o1.stt,o1.sty
    ;

/*create the */
drop table if exists observe_group;
create table observe_group as 
  select o1.stt, o1.sty, count(distinct o1.pt_aui) as cnt_all, count(distinct o1.pt_aui,o2.pt_aui) as cnt_parent,
		group_concat(distinct o1.pt_aui, '\t', IFNULL(o2.pt_aui, 'null') separator '`') as pairs,
		group_concat(distinct o1.pt_aui, '\t', IFNULL(o1.pt_aui_str, 'null') separator '`') as pairs_str1,
		group_concat(distinct o2.pt_aui, '\t', IFNULL(o2.pt_aui_str, 'null') separator '`') as pairs_str2
    from  observe o1 
	left join observe o2
	on o1.stt=o2.stt and o1.sty=o2.sty
		and o1.ptr regexp concat('.*',o2.pt_aui,'$')
    group by o1.stt,o1.sty
    ;
drop table if exists social_group;
create table social_group as 
  select o1.stt, o1.sty, count(distinct o1.pt_aui) as cnt_all, count(distinct o1.pt_aui,o2.pt_aui) as cnt_parent,
		group_concat(distinct o1.pt_aui, '\t', IFNULL(o2.pt_aui, 'null') separator '`') as pairs,
		group_concat(distinct o1.pt_aui, '\t', IFNULL(o1.pt_aui_str, 'null') separator '`') as pairs_str1,
		group_concat(distinct o2.pt_aui, '\t', IFNULL(o2.pt_aui_str, 'null') separator '`') as pairs_str2
    from  social o1 
	left join social o2
	on o1.stt=o2.stt and o1.sty=o2.sty
		and o1.ptr regexp concat('.*',o2.pt_aui,'$')
    group by o1.stt,o1.sty
    ;

select * from observe_group order by cnt_parent;
select * from social_group  order by cnt_parent;

select * from observe_group order by cnt_parent into outfile '/tmp/observe_group.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
select * from social_group  order by cnt_parent into outfile '/tmp/social_group.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';


use ret;

select * from content_tag_ytex_T047_unique ;


select distinct A.target2 from (select * from co_occur where sab1 is not null and sab2 is null) A;
select * from co_occur where sab1 is not null and sab2 is null;

select count(distinct target) from content_tag_our_T047_unique;
select count(distinct target) from content_tag_ytex_T047_unique;


















