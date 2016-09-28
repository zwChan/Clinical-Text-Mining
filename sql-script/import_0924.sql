USE ytex;
drop table if exists TMP_ORG;
CREATE TABLE TMP_ORG (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`blog_name` varchar(200) DEFAULT NULL, 
`text_link_title` varchar(500) DEFAULT NULL,   
`text_content` varchar(10000) DEFAULT NULL,
`photo_caption`  varchar(300) DEFAULT NULL,  
`photo_link`  varchar(300) DEFAULT NULL,  
`photo_source`  varchar(300) DEFAULT NULL,  
`link_content`  varchar(300) DEFAULT NULL,  
`post_likes`  varchar(300) DEFAULT NULL,  
`post_reblogged`  varchar(300) DEFAULT NULL,  
`post_hashtag`  varchar(300) DEFAULT NULL
);
/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\UmlsTagger\\data\\raw_data_CHV_study2.csv.txt' 
into table TMP_ORG
fields terminated by '\t'
enclosed by '"'
lines terminated by '`'
;

CREATE TABLE CONTENT_ORG_NEW AS 
	SELECT o.blogId,o.post_hashtag,o.blog_name,o.text_link_title,o.text_content FROM TMP_ORG o;
    
select * from CONTENT_ORG_NEW into outfile 'C:\\fsu\\ra\\UmlsTagger\\data\\raw_data_CHV_study2.csv' 
	fields terminated by ',' enclosed by '"' lines terminated by '\n';

select distinct blogId  INSTANCE_ID  from ytex.CONTENT_ORG_NEW ;
select * from ytex.content_org_new where blogId = 0;




