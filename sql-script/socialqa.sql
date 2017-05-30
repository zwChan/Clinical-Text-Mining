
use socialqa;

select count(*) from socialqa.qdataH; -- 3822256
select distinct top_level_category from socialqa.qdataH; -- 29
select count(*) from socialqa.qdataH where top_level_category='Health'; -- 2820179

select * from socialqa.qdataH;

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
    
    
    
