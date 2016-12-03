update ret.content_tag_ytex_T047_unique as t
	inner join
	(select r.CUI1,r.CUI2,GROUP_CONCAT(DISTINCT REL,' ',RELA SEPARATOR ',') as rel_all from umls.MRREL as r
		inner join ret.content_tag_ytex_T047_unique as ret
        on  (r.CUI1=ret.cui COLLATE utf8_unicode_ci and r.CUI2 = ret.rel_cui COLLATE utf8_unicode_ci)
		 or (r.CUI2=ret.cui COLLATE utf8_unicode_ci and r.CUI1=ret.rel_cui COLLATE utf8_unicode_ci)
        GROUP BY CUI1,CUI2
        ) as temp
    on  (temp.CUI1=t.cui COLLATE utf8_unicode_ci and temp.CUI2 = t.rel_cui COLLATE utf8_unicode_ci)
	 or (temp.CUI2=t.cui COLLATE utf8_unicode_ci and temp.CUI1=t.rel_cui COLLATE utf8_unicode_ci)
	set t.rel_all = temp.rel_all
    ;