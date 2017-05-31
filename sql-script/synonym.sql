create database synonym char set utf8;
use synonym;
create table test_term_umls (
term varchar(100),
cui varchar(10),
synonym text
);

create table wiki_ngram like chv.cancer_ngram;

rename table socialqa.wiki_ngram to synonym.wiki_ngram;

set group_concat_max_len=10240;
select * from test_term_umls;
-- synonym with at most 3 words
truncate test_term_umls;
insert into test_term_umls (term,cui) select distinct ngram, cui_umls from wiki_ngram;
update test_term_umls t set synonym = (select GROUP_CONCAT(distinct s.descr SEPARATOR  '|' ) from umls._target_term_ s where cui=t.cui and (length(descr)-length(replace(descr,' ', '')))<= 2);


select * from umls._target_term_ where cui='C0439234';
select * from umls.mrconso where cui='C0439234';
select count(*) from  wiki_ngram;
select * from test_term_umls;

