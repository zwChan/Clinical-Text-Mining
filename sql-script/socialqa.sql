
use socialqa;

select count(*) from socialqa.qdataH; -- 3822256
select distinct top_level_category from socialqa.qdataH; -- 29
select count(*) from socialqa.qdataH where top_level_category='Health'; -- 2820179

create table health_answers_for_8000_questions as (select a.qid,a.content,a.rating, a.userid,a.usernick from adataH a, health_questions_random_8000 q where a.qid=q.qid);
select * from socialqa.qdataH;
select count(*) from `health_questions_random_8000`;

-- split all answers into multiple files.
select  id, replace(concat(subject, ' ', content,' ',chosenanswer),'\r','') from socialqa.qdataH 
    where id > 3822256/4*0 and id <= 3822256/4*1
	into outfile '/tmp/socialqa_dataset1.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
select  id, replace(concat(subject, ' ', content,' ',chosenanswer),'\r','') from socialqa.qdataH 
    where id > 3822256/4*1 and id <= 3822256/4*2
	into outfile '/tmp/socialqa_dataset2.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
select  id, replace(concat(subject, ' ', content,' ',chosenanswer),'\r','') from socialqa.qdataH 
    where id > 3822256/4*2 and id <= 3822256/4*3
	into outfile '/tmp/socialqa_dataset3.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
select  id, replace(concat(subject, ' ', content,' ',chosenanswer),'\r','') from socialqa.qdataH 
    where id > 3822256/4*3 and id <= 3822256/4*4 + 4
	into outfile '/tmp/socialqa_dataset4.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
    
    
    
