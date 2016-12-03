use ytex;
CREATE TABLE TMP_ORG_1027 (
`blogId` BIGINT(20) DEFAULT NULL,  /*----blog id */
`text_content` text DEFAULT NULL
);

drop table TMP_ORG_1027;
/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\data\\content_raw_cleaned_ForZhiwei.csv' 
into table TMP_ORG_1027
fields terminated by ','
enclosed by '"'
lines terminated by '`'
ignore 1 LINES
;
select count(distinct blogId) from TMP_ORG_1023 LIMIT 1;
-- 50252
select * from TMP_ORG_1027 where text_content like '%`%';

use ytex;
select * from v_document_ontoanno;
select * from v_corpus_group_class;

create table ytex.content_tag_ytex_1023 as 
 select a.anno_text, d.instance_id, c.* from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id;
  
select * from ytex.content_tag_ytex_1023 order by instance_id, sentence_text
	into outfile 'C:\\fsu\\ra\\data\\content_tag_ytex_1023.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n'; 
  
select count(distinct instance_id) from content_tag_ytex_1023;  


show create table ytex.content_tag_ytex_1023;
