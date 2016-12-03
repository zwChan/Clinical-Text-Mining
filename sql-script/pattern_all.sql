use compact_092316;

select D.Disease, count(*) as cnt from cancer_cui C join all_diseases_trials D 
	on C.tid=D.tid and C.month>-1 and nested!='nesting'
    group by D.Disease order by cnt desc;