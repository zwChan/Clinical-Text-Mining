use ytex;

/*
sed -i -- 's/concat(subject, " . ", content)/chosenanswer/g' *.xml
sed -i -- 's/<string>question<\/string>/<string>answer<\/string>/g' *.xml

*/

create table yahootumblr (
  id bigint(20) default null,
  content text default null
  );

insert yahootumblr (id,content) select distinct blogId, text_content from content_org_new;
insert yahootumblr (id,content) select distinct id, concat(subject, ". ", content, ". ", chosenanswer) from org_yahoo;
select * from yahootumblr;
delete from yahootumblr where id is null;

-- question
select count(*) from anno_sentence; -- 267617
select count(sentence_text) from v_document_cui_sent; -- 399076
select count(distinct sentence_text) from v_document_cui_sent; -- 142802

rename table retyahoo.org_yahoo to ytex.org_yahoo;
select  count(distinct id) from org_yahoo;
select  count(distinct blogId) from content_org_new;

-- question
select count(*) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'question'; -- 267617
select count(distinct substr(`d`.`doc_text`,(`b`.`span_begin` + 1),(`b`.`span_end` - `b`.`span_begin`))) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'question'; -- 249013

-- answer
select count(*) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'answer'; -- 428881
select count(distinct substr(`d`.`doc_text`,(`b`.`span_begin` + 1),(`b`.`span_end` - `b`.`span_begin`))) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'answer'; -- 348793

-- blog
select count(*) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'blog'; -- 52551
select count(distinct substr(`d`.`doc_text`,(`b`.`span_begin` + 1),(`b`.`span_end` - `b`.`span_begin`))) from anno_sentence s, anno_base b,document d where s.anno_base_id = b.anno_base_id and b.document_id=d.document_id and d.analysis_batch = 'blog'; -- 47413

select distinct blogId from content_org_new where blogId >=116473932370 order by blogId limit 1000;

