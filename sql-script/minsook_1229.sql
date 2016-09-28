select C.sab, C.code, group_concat(distinct C.str), count(*)

from

(select A.code, A.str, B.sab

FROM

  (select distinct c.instance_id, c.sentence_text, c.code, m.str from ret1007.content_tag_ytex c, umls.mrconso m

    where c.code = m.cui and m.lat = 'ENG' and c.disambiguated=1 and c.disease='diabetes'and  m.TS='P' AND m.stt='PF' AND m.ispref='Y') as A,

  (select distinct cui, sab from umls.mrconso where lat='ENG') AS B

WHERE A.code = B.cui) as C

group by C.sab, C.code

order by C.sab, count(*) desc
into outfile '/tmp/minsook_1230.ret' fields terminated by '\t' enclosed by '"' lines terminated by '\n';



select C.sab, C.code, group_concat(distinct C.str), count(*) 

from

(select A.code, A.str, B.sab

FROM

  (select distinct c.instance_id, c.sentence_text, c.code, m.str from retyahoo.content_tag_ytex_yahoo_question c, umls.mrconso m

    where c.code = m.cui and m.lat = 'ENG' and c.disambiguated=1 and  m.TS='P' AND m.stt='PF' AND m.ispref='Y') as A, 

  (select distinct cui, sab from umls.mrconso where lat='ENG') AS B

WHERE A.code = B.cui) as C

group by C.sab, C.code

order by C.sab, count(*) desc 
into outfile '/tmp/minsook_1230_question.ret' fields terminated by '\t' enclosed by '"' lines terminated by '\n';



select C.sab, C.code, group_concat(distinct C.str), count(*) 

from

(select A.code, A.str, B.sab

FROM

  (select distinct c.instance_id, c.sentence_text, c.code, m.str from retyahoo.content_tag_ytex_yahoo_answer c, umls.mrconso m

    where c.code = m.cui and m.lat = 'ENG' and c.disambiguated=1 and  m.TS='P' AND m.stt='PF' AND m.ispref='Y') as A, 

  (select distinct cui, sab from umls.mrconso where lat='ENG') AS B

WHERE A.code = B.cui) as C

group by C.sab, C.code

order by C.sab, count(*) desc 
into outfile '/tmp/minsook_1230_answer.ret' fields terminated by '\t' enclosed by '"' lines terminated by '\n';





