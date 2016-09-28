create database if not exists retyahoo character set utf8;
use retyahoo;

select * from content_tag_ytex_yahoo;

select count(distinct instance_id) from content_tag_ytex_yahoo;
select count(distinct id) from org_yahoo;

drop table content_tag_ytex_yahoo;
rename table retyahoo.org_yahoo to ytex.org_yahoo; 


use ytex;
select * from ytex.org_yahoo;
select max(id)  from (select id from org_yahoo where id>= 0 and id < 120753 order by id  limit 5000) a;
-- 120753
select max(id)  from (select id from org_yahoo where id>= 120753 and id < 334572 order by id  limit 5000) a;
-- 334572
select max(id)  from (select id from org_yahoo where id>= 334572 and id < 640612 order by id  limit 5000) a;
-- 640612
select max(id)  from (select id from org_yahoo where id>= 640612 and id < 925081 order by id  limit 5000) a;
-- 925081
select max(id)  from (select id from org_yahoo where id>= 925081 and id < 1219340 order by id  limit 5000) a;
-- 1219340
select max(id)  from (select id from org_yahoo where id>= 1219340 and id < 1664699 order by id  limit 5000) a;
-- 1664699
select max(id)  from (select id from org_yahoo where id>= 1664699 and id < 1994240 order by id  limit 5000) a;
-- 1994240
select max(id)  from (select id from org_yahoo where id>= 1994240 and id < 2685340 order by id  limit 5000) a;
-- 2685340
select max(id)  from (select id from org_yahoo where id>= 2685340 and id < 3079989 order by id  limit 5000) a;
-- 3079989
select max(id)  from (select id from org_yahoo where id>= 3079989 and id < 3440146 order by id  limit 5000) a;
-- 3440146
select max(id)  from (select id from org_yahoo where id>= 3440146 and id < 3680908 order by id  limit 5000) a;
-- 3680908
select max(id)  from (select id from org_yahoo where id>= 3680908 and id < 1000000000 order by id  limit 5000) a;
-- 3822084


select count(id) from org_yahoo where id>= 640612 and id < 925081 order by id;
select count(id) from org_yahoo where id>= 3680908 and id < 1000000000;

select count(distinct document_id) from document;
