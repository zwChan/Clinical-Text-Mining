create database synonym char set utf8;
use synonym;
create table test_term_umls (
term varchar(100),
cui varchar(10),
synonym text
);

set group_concat_max_len=10240;
select * from test_term_umls;
-- synonym with at most 3 words
update test_term_umls t set synonym = (select GROUP_CONCAT(distinct s.descr_stemmed SEPARATOR  '|' ) from umls._target_term_ s where cui=t.cui and (length(descr_stemmed)-length(replace(descr_stemmed,' ', '')))<= 2);

select GROUP_CONCAT(distinct s.str SEPARATOR  ' | ' ) from umls.mrconso s, test_term_umls t where cui=t.cui and (length(str)-length(replace(str,' ', '')))<= 2 group by t.cui;