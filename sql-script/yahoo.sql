use ytex;
drop table tmp_org_yahoo;
CREATE TABLE TMP_ORG_yahoo (
`qid` varchar(50),
`id` int(11),
`category` varchar(50),
`categoryId` int(11),
`subject` text,
`content` text,
`day` varchar(22),
`link` varchar(256),
`userid` varchar(50),
`usernick` varchar(200),
`numanswers` int(10),
`numcomments` int(10),
`chosenanswer` text,
`chosenanswererid` varchar(50),
`chosenanswerernick` varchar(200),
`chosenanswertimestamp` varchar(20)
);
load data local infile 'C:\\fsu\\ra\\data\\qdataH.diabetes.all_58425.csv' 
into table TMP_ORG_yahoo
fields terminated by ','
enclosed by '"'
lines terminated by '\r\n'
ignore 1 LINES
;

select * from tmp_org_yahoo ;
select count( distinct qid) from tmp_org_yahoo;

-- ytex using sql:
select distinct id INSTANCE_ID from ytex.TMP_ORG_yahoo where qid is not null;
 select concat(subject, ". ", content, ". ", chosenanswer) note_text from ytex.TMP_ORG_yahoo where id = :instance_id limit 1;
 select chosenanswer note_text from ytex.TMP_ORG_yahoo where id = :instance_id limit 1;

drop table content_tag_ytex_yahoo_answer;
create table ytex.content_tag_ytex_yahoo_answer as 
 select yh.qid, a.anno_text, d.instance_id, c.* from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id
  inner join TMP_ORG_yahoo yh on yh.id = d.instance_id
;
insert into ytex.content_tag_ytex_yahoo 
 select yh.qid, a.anno_text, d.instance_id, c.* from v_document_cui_sent c 
  inner join  v_annotation a on c.anno_base_id = a.anno_base_id
  inner join v_document d on d.document_id = c.document_id
  inner join TMP_ORG_yahoo yh on yh.id = d.instance_id
;
select count(distinct qid) from content_tag_ytex_yahoo where analysis_batch='answer';
-- q: 15378, a:25342, all:40720

select distinct doc_text from document;
select count(distinct anno_base_id) from anno_base;
select * from anno_named_entity;
select count(distinct code) from anno_ontology_concept;
select count(distinct document_id) from content_tag_ytex_yahoo;
select count(distinct qid) from content_tag_ytex_yahoo_answer;
select count(distinct document_id) from v_document_cui_sent;

select concat(subject, ". ", content, ". ", chosenanswer) from TMP_ORG_yahoo;

select * from ytex.TMP_ORG_yahoo where chosenanswer like '%article%';



