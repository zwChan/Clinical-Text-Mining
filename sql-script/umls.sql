use umls;

select  * from MRCONSO where cui = 'C1875802' limit 2000 ;
select count(*) from mrsmap;
select * from mrcols where col='STY';

select count(distinct cui) from mrconso;

SHOW FULL PROCESSLIST;

select * from mrsty;
select * from mrrel where rel is null;

select distinct sab from umls.mrconso;

show processlist;

select CUI, AUI, SAB, STR from MRCONSO where LAT = 'ENG'  into outfile 'c:/fsu/all.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';

select * from mrconso where stt='PF';

select CUI, COUNT(*) FROM MRSTY GROUP BY CUI;

desc mrsty;

select sab, count(*) from mrconso group by sab;

select count(*) from mrconso where str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%';
/*4021 result*/

select distinct cui from mrconso where str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%';
/*more than 1k*/

select * from mrconso where sab ='SNOMEDCT_US' AND ( str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%');

select distinct cui from mrconso where str = 'diabetes' or str = 'type 1 diabetes' or str = 'type 2 diabetes';
/*4 results*/
select count(*) from mrrel where cui1 in ('C0011847','C0011849','C0011854','C0011860') or cui2 in ('C0011847','C0011849','C0011854','C0011860') ;
/*5994 result*/

create view rel_diabetes as select * from mrrel where cui1 in ('C0011847','C0011849','C0011854','C0011860') or cui2 in ('C0011847','C0011849','C0011854','C0011860') ;

create view content_rel as select distinct c.cui from CONTENT_TAG c inner join rel_diabetes r 
on c.cui = r.cui1 COLLATE utf8_unicode_ci or c.cui = r.cui2 COLLATE utf8_unicode_ci;

 select count(distinct CUI) from MRCONSO;
select * from tmp_rel_diabetes;
select * from CONTENT_TAG;
delete  from content_tag where blogId='post_id';



drop table if exists CONTENT_ORG;
CREATE TABLE CONTENT_ORG (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`post_hashtag`  varchar(300) DEFAULT NULL,  
`blog_name` varchar(200) DEFAULT NULL, 
`text_link_title` varchar(500) DEFAULT NULL,   
`text_content` varchar(10000) DEFAULT NULL
);

truncate  CONTENT_ORG;

/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\UmlsTagger\\data\\data_content_tag_diabetes_0821.csv' 
into table CONTENT_ORG
fields terminated by ','
enclosed by '"'
lines terminated by '`'
;

select *, length(text_content) AS len from content_org ;
select count(distinct blogid) from content_org;
desc content_org;

select blogId  instance_id from umls.CONTENT_ORG;
-- select text_content note_text from umls.CONTENT_ORG where blogId = :instance_id;



