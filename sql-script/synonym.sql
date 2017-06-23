create database synonym char set utf8;
use synonym;
create table test_term_umls (
term varchar(100),
cui varchar(10),
synonym text
);

create table wiki_ngram like chv.cancer_ngram;
create table wiki_ngram_tf5 like wiki_ngram;

rename table socialqa.wiki_ngram to synonym.wiki_ngram;

load data local infile '/tmp/wiki.tf5.ngram.novector' into table wiki_ngram_tf5 fields terminated by '\t' enclosed by '"' lines terminated by '\r\n' ignore 1 lines;


set group_concat_max_len=10240;
select * from test_term_umls;
-- synonym with at most 3 words
truncate test_term_umls;
insert into test_term_umls (term,cui) select distinct ngram, cui_umls from wiki_ngram;
-- update test_term_umls t set synonym = (select GROUP_CONCAT(distinct s.descr SEPARATOR  '|' ) from umls._target_term_ s where cui=t.cui and (length(descr)-length(replace(descr,' ', '')))<= 2);

update test_term_umls t set synonym = (select GROUP_CONCAT(distinct s.str SEPARATOR  '|' ) from umls.mrconso s where s.cui=t.cui and (s.sab like 'SNOMEDCT_US%' or s.sab like 'RXNORM%' OR s.sab like 'ICD%' OR s.sab like 'NCI%' OR s.sab like 'LOINC%') and (length(str)-length(replace(str,' ', '')))<= 3);


select * from umls._target_term_ where cui='C0439234';
select * from umls.mrconso where cui='C0439234';
select count(*) from  wiki_ngram;
select * from test_term_umls 
	into outfile '/tmp/freq_term.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';

select distinct sab from umls.mrconso;
select ngram from wiki_ngram_tf5 where n>1
	into outfile '/tmp/wiki_ngram_tf5.txt';

