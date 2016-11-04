create database cancerqa char set utf8;
use cancerqa;

create index idx_qid on cancerqa_questions(qid);
create index idx_nick on cancerqa_questions(chosenanswernick);
create index idx_nick2 on cancerqa_answers(usernick);
create index idx_qid2 on cancerqa_answers(qid);

alter table qa_data add column id int not null auto_increment primary key;

select * from cancerqa_questions;
select * from cancerqa_answers;

create table qa_data as select Q.qid, A.usernick, Q.subject, Q.content as question_content, A.content as answer_content from cancerqa_questions Q, cancerqa_answers A where Q.qid=A.qid and Q.chosenanswernick=A.usernick;

select * from qa_data;

select qid,usernick, count(*) as cnt from qa_data group by qid,usernick order by cnt desc;


select Q.qid, A.usernick, Q.subject, Q.content as question_content, A.content as answer_content from cancerqa_questions Q, cancerqa_answers A where Q.qid=A.qid and Q.chosenanswernick=A.usernick;

select count(*) from qa_data;


select * from cancerqa_answers B join (select A.qid as qid, max(A.rating) as maxrating from cancerqa_answers A group by A.qid) M
	on B.qid=M.qid and B.rating=M.maxrating;

create table qa_data2 as 
	select Q.qid, A.usernick, Q.subject, Q.content as question_content, A.content as answer_content, M.maxrating
		from cancerqa_questions Q, cancerqa_answers A,(select qid as qid, max(rating) as maxrating from cancerqa_answers  group by qid) M  			where Q.qid=A.qid and Q.qid=M.qid and Q.chosenanswernick=A.usernick and M.maxrating=A.rating;
select nested, count(*) from 