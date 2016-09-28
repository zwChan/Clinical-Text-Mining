
create database cancer char set utf8;
use cancer;

create table meta (
tid varchar(20),
agency varchar(2000),
overall_status varchar(45),
start_date varchar(45),
gender varchar(45),
minimum_age varchar(45),
maximum_age varchar(45),
enrollment varchar(500),
phase varchar(100),
intervention_type varchar(45),
intervention_name varchar(500),
study_type varchar(200),
agency_type varchar(45),
enrollment_type varchar(45),
intervention_model varchar(200),
allocation varchar(200),
masking varchar(200),
primary_purpose varchar(200)
);
select * from meta;

create table noncui (
`task` varchar(30),
`str` varchar(200),
`freq` int(20),
`ngram` int(8)
);
create index idx_str on noncui (str(32)) using hash;
create index idx_freq on noncui (freq);


-- tid\ttype\tcriteriaId\tpattern\tcui\tcui_str\tduration\tsentence
drop table cancer_cui;
CREATE TABLE cancer_cui (
`task` varchar(30),
`tid` varchar(30),
`type` varchar(50),
`typeDetail` varchar(50),
`criteriaId` int(20),
`splitType` varchar(10),
`sentId` int(20),
`pattern` varchar(50),
`duration` int(20),
`month` int(8),
`neg` int(8),
`negAheadKey` int(8),
`group` varchar(50),
`termId` int(20),
`skipNum` int(8),
`cui` varchar(20),
`sty` varchar(20),
`ngram` int(8),
`org_str` text,
`cui_str` text,
`method` varchar(20),
`nested` varchar(20),
`tags` varchar(50),
`sentLen` int(10),
`sentence` text
);

create index idx_cui on cancer_cui (cui) using hash;
create index idx_org_str on cancer_cui (org_str(32)) using hash;
create index idx_tid on cancer_cui (tid);
create index idx_sid on cancer_cui (sentId);
create index idx_cid on cancer_cui (criteriaId);
create index idx_termid on cancer_cui (termId);

delete from cancer_cui where sty = 'T033'; -- 99002,104777,103562,103383,102505,104535
delete from cancer_cui where sentLen>500; -- 2027,1974,1940,1876,944
delete from cancer_cui where duration<-1; -- 11,10,10,11,11
-- update cancer_cui set month = -1 where duration = -1; -- 456818,329828

select cui,count(*) as cui_freq from cancer_cui group by cui;
select count(*) from cancer_cui where sentLen > 500; -- 1800

  
 select distinct sab from umls.mrconso where sab like '%GO%';
  select * from umls.MRCONSO where cui = 'C0014518';


select count(distinct cui) from cancer_cui;  -- 16443,9687,6620,6437,6434,6434,6435,7071,6637,6552,5127,5114,5105,5103,4639
select count( *) from cancer_cui;  -- 1964495,736162,471374,375966,374944,374943,375021,376239,541486,545457,544044,445042,444495,438812,438064,394785
-- it is less because one cui may occurs multiple times in one criteria.
select count( distinct tid,criteriaId,sentId,termId,cui) from cancer_cui;  -- 1845839,693488,445725,358271,357455,347494,351540,351609,352998,509062,507815,606168,411809,410719,406304,405768,274855

select cui, count(*) as cnt from cancer_cui where pattern like 'HISTORY_WITH%' group by cui order by cnt desc;

-- creat a table for (cui,sty,org_str)
drop table cancer_cui_uniq;
create table cancer_cui_uniq as 
 select * from cancer_cui group by tid,criteriaId,sentId,termId; -- 445727,357456,170349,170378,171534,227621,212665,211699,
 
select *, count(*) cnt from cancer_cui group by tid,criteriaId,sentId,cui,sty,org_str order by cnt desc; 

-- creat frequency table for (cui,sty)
drop table freq_cui; -- 9760,6494,6492,6491,6696,5186,5172,5163,5161,5156
create table freq_cui as select cui,sty,org_str,cui_str,count(*) as freq from cancer_cui group by cui,sty order by freq desc;
-- duration frequency in history pattern
drop table freq_cui_dur; -- 941,623,757,749,896,619,604,631,638,637
create table freq_cui_dur as select cui,sty,duration,org_str,cui_str,count(*) as freq_dur from cancer_cui where pattern like 'HISTORY_WITH%' group by cui,sty,duration order by freq_dur desc;

/*
drop table freq_cui; -- 9760,6494
create table freq_cui as select cui,sty,org_str,cui_str,count(*) as freq from cancer_cui_uniq group by cui,sty order by freq desc;
drop table freq_cui_dur; -- 941,623
create table freq_cui_dur as select cui,sty,duration,org_str,cui_str,count(*) as freq_dur from cancer_cui_uniq where pattern like 'HISTORY_WITH%' group by cui,sty,duration order by freq_dur desc;
*/
--
select sty,sum(freq) as sum from freq_cui group by sty order by sum desc;
select sty,sum(freq_dur) as sum from freq_cui_dur group by sty order by sum desc;


select *, sum(freq_uniq_dur) as sum from freq_cui_uniq_dur group by cui,duration order by freq_uniq_dur desc;

select * from cancer_cui where pattern like 'HISTORY_WITH%' and duration > 0 and cui_str like '%history%';

select sty,cui, org_str, cui_str, count(*) as cnt  from cancer_cui_uniq where pattern != 'None' and sty not in ('T033','T065','T062') group by sty,cui order by cnt desc;

select cui, org_str, cui_str, count(*) as cnt from cancer_cui_uniq where pattern like 'HISTORY_WITH%' and duration > 0 and sty = 'T170' group by cui order by cnt desc;

-- select count(*) from cancer_cui_uniq; -- 358273,357456
 
select * from cancer_cui where pattern like 'HISTORY_WITH%' and duration > 0 group by cui,sty,org_str order by cui,org_str;
select * from cancer_cui where sty is null;
-- delete from cancer_cui where sty is null;

-- sty frequency
select sty,sum(freq) as sum from freq_cui group by sty order by sum desc;
select sty,sum(freq_dur) as sum from freq_cui_dur group by sty order by sum desc;

-- sty frequency
-- whole
select sty, count(*) as cnt from cancer_cui group by sty order by sty; 
-- types
select task,count(*) as cnt from cancer_cui group by task;
select task,sty, count(*) as cnt from cancer_cui group by task,sty order by task,sty,cnt desc; 

-- cui frequency
-- whole
select * from (select cui, cui_str, count(*) as cnt from cancer_cui group by cui) as t where cnt>1500 order by cnt desc;
-- types
select * from (select task,cui, cui_str, count(*) as cnt from cancer_cui group by task,cui) as t where cnt>800 order by task,cnt desc;

-- individul cui distribution
set @cuicui='C0018802'; -- congestive heart failure
set @cuicui='C0038454'; -- stroke
set @cuicui='C0027051'; -- myocardial infarction

-- duration distribution
-- select duration, count(*) as cnt from cancer_cui where cui=@cuicui group by duration order by duration;
select month, count(*) as cnt from cancer_cui where cui=@cuicui group by month order by month;
-- types duration distribution
-- select task,duration, count(*) as cnt from cancer_cui where cui=@cuicui group by task,duration order by task,duration;
select task,month, count(*) as cnt from cancer_cui where cui=@cuicui group by task,month order by task,month;
select task,month, count(*) as cnt from cancer_cui where cui=@cuicui and pattern != 'None' and ((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0)) group by task,month order by task,month;
select task, count(*) as cnt from cancer_cui where cui=@cuicui and pattern = 'None' and ((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0)) group by task order by task;


drop table criteria_uniq;
create table criteria_uniq as select distinct task,tid,type,criteriaId,pattern,neg from cancer_cui; -- 133544,191589,188990,189808
select type,neg>0 as neg, count(*) as cnt from cancer_cui group by type,neg>0 ;
select task,type,neg>0 as neg, count(*) as cnt from cancer_cui group by task,type,neg>0 ;

select * from criteria_uniq where neg>0; --


-- freq pattern -- for a pattern more than one cui, we should only count once.
-- whole
select pattern, count(*) as cnt,sum(type='inclusion') as Inc,sum(type='exclusion') as Exc from criteria_uniq group by pattern order by cnt desc;
-- inlude
select pattern, count(*) as cnt from criteria_uniq where type='inclusion' group by pattern order by cnt desc;
-- exclude
select pattern, count(*) as cnt from criteria_uniq where type = 'exclusion' group by pattern order by cnt desc;

select  type, count(*) from cancer_cui group by type;
select * from cancer_cui where type='PRIOR CONCURRENT THERAPY';



-- drop table cancer_cui;
select distinct sentence from cancer_cui where length(sentence) > 500 ;

select tid,criteriaId,sentence,termId,count(*) as cnt from cancer_cui where sty != 'T033' group by tid,criteriaId,sentence,termId;

-- sentence len distribution
select sentLen, count(distinct tid,criteriaId,sentId) from cancer_cui group by sentLen;

select count( cui) from cancer_cui where method='conjDep';


select cui,cui_str,month,count(*) as num from cancer_cui where month > -1 and pattern != 'None' group by cui, month order by num desc, month ;

select cui,cui_str,count(*) as num from cancer_cui where month > -1 and pattern != 'None' group by cui order by num desc ;

select cui,cui_str,count(*) as num from cancer_cui where pattern != 'None' and month > -1 and ((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0))
group by cui order by num desc ;

-- cui duration distribution. heatmap
select can.cui, can.cui_str, can.month, freq.cui_freq, count(*) as num,can.sty from cancer_cui as can 
	inner join (select cui, count(*) as cui_freq from cancer_cui where pattern != 'None' and duration > -1 and 
	((type='Inclusion' and neg>0) or (type='Exclusion' and neg = 0)) group by cui ) freq
  on can.cui = freq.cui
	where can.pattern != 'None' and can.month > -1 and 
	((can.type='Inclusion' and can.neg>0) or (can.type='Exclusion' and can.neg = 0) )
     and can.cui not in ("C3810814", "C0947630", "C0008972","C1516879","C1696073","C0006111","C0034656","C3161471") 
  group by can.cui,can.month
  order by freq.cui_freq desc,can.month asc, num desc;     
  
  
  -- -------------------------
 select pattern, sentence from cancer_cui where pattern = 'CONFIRMED_MORETHAN' group by sentence; 
