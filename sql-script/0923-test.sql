use umls;
/*add a new column for all rel+rela, then get the rel+rela as a string*/  
 -- alter table umls.content_tag_diabetes_T047_unique2_output add (rel_all text default null);  
 -- alter table ytex.content_tag_ytex_T047_unique_output add (rel_all text default null);  
/*
update content_tag_diabetes_T047_unique2_output as ret set rel_all = 
	(select GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') from umls.MRREL as r 
		where (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci) 
		   or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2 limit 1
        )
        where rel_all is null;
*/
drop table umls.tmp_pairs;
create table umls.tmp_pairs as select DISTINCT CUI,REL_CUI FROM umls.content_tag_diabetes_T047_unique2_output where rel_all is null;

update umls.content_tag_diabetes_T047_unique2_output as t 
	inner join
	(select  r.CUI1,r.CUI2,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from 
		tmp_pairs as ret 
		inner join 
        umls.MRREL as r 
        on  (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci) 
		 or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2
        ) as temp
    on  (temp.CUI1=t.cui COLLATE utf8_unicode_ci and temp.CUI2 = t.rel_cui COLLATE utf8_unicode_ci) 
	 or (temp.CUI2=t.cui COLLATE utf8_unicode_ci and temp.CUI1=t.rel_cui COLLATE utf8_unicode_ci)   
	set t.rel_all = temp.rel_all
    where t.rel_all is null
    ;

 select count(*) from     umls.content_tag_diabetes_T047_unique2_output where rel_all is null;
 select count(*) from tmp_pairs;
 
 
 
 
drop table if exists ytex.tmp_pairs;
create table ytex.tmp_pairs as select DISTINCT CUI,REL_CUI FROM ytex.content_tag_ytex_T047_unique_output where rel_all is null;
update ytex.content_tag_ytex_T047_unique_output as t 
	inner join
	(select r.CUI1,r.CUI2,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from umls.MRREL as r 
		inner join ytex.tmp_pairs as ret 
        on  (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci) 
		 or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2
        ) as temp
    on  (temp.CUI1=t.cui COLLATE utf8_unicode_ci and temp.CUI2 = t.rel_cui COLLATE utf8_unicode_ci) 
	 or (temp.CUI2=t.cui COLLATE utf8_unicode_ci and temp.CUI1=t.rel_cui COLLATE utf8_unicode_ci)   
	set t.rel_all = temp.rel_all
    where t.rel_all is null
    ;

 select * from     ytex.content_tag_ytex_T047_unique_output where rel_all is not null;

use umls;
select * from mrconso where SAB='SNOMEDCT_US' AND TTY = 'PT' AND CODE = '251314005';
select * from mrconso where AUI='A3601659';
