USE ytex;
drop table if exists TMP_ORG;
truncate TMP_ORG;
CREATE TABLE TMP_ORG (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`blog_name` varchar(200) DEFAULT NULL, 
`text_link_title` varchar(500) DEFAULT NULL,   
`text_content` text DEFAULT NULL,
`photo_caption`  varchar(300) DEFAULT NULL,  
`photo_link`  varchar(300) DEFAULT NULL,  
`photo_source`  varchar(300) DEFAULT NULL,  
`link_content`  varchar(300) DEFAULT NULL,  
`post_likes`  varchar(300) DEFAULT NULL,  
`post_reblogged`  varchar(300) DEFAULT NULL,  
`post_hashtag`  varchar(300) DEFAULT NULL
);
CREATE TABLE TMP_ORG (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`post_hashtag`  varchar(300) DEFAULT NULL,
`blog_name` varchar(200) DEFAULT NULL, 
`text_link_title` varchar(500) DEFAULT NULL,   
`text_content` text DEFAULT NULL
);
-- first input to TMP_ORG, THEN INSERT into TEMP_ORG_NEW, adding a 'disease' column.
truncate TMP_ORG;
/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\UmlsTagger\\data\\newdataset_chronic\\chronic_newdataset_obesity.csv' 
into table TMP_ORG
fields terminated by ','
enclosed by '"'
lines terminated by '`'
ignore 1 LINES
;

/*
drop table if exists CONTENT_ORG_NEW;
CREATE TABLE CONTENT_ORG_NEW (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`post_hashtag`  varchar(300) DEFAULT NULL,
`blog_name` varchar(200) DEFAULT NULL, 
`text_link_title` varchar(500) DEFAULT NULL,   
`text_content` text DEFAULT NULL,
`disease` varchar(100)
);

INSERT ignore CONTENT_ORG_NEW  (blogId,post_hashtag,blog_name,text_link_title,text_content,disease)
	SELECT distinct o.blogId,o.post_hashtag,o.blog_name,o.text_link_title,o.text_content,'obesity'  FROM TMP_ORG o  ;

select count(distinct disease,blogId) from CONTENT_ORG_NEW;

truncate table content_tag_ytex;
/*
drop table if exists content_tag_ytex;
create table content_tag_ytex as 
 select a.anno_text, d.instance_id, c.*, 'alzheimer' as disease from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id;
  *//*,7230*/

alter table content_tag_ytex add `disease` varchar(100) default null ;
insert content_tag_ytex 
select a.anno_text, d.instance_id, c.*, 'diabetes' as disease from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id;

select count(distinct code) from content_tag_ytex;
show create table v_document_cui_sent;


