
use ret1007;

select * from content_tag_disease_ytex_T047_unique_output order by blogId,sentence 
	into outfile '/tmp/ret-final-1007.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n'; 
select * from content_tag_ytex order by instance_id,sentence_text 
	into outfile '/tmp/ret-basic-1007.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n'; 
    
    
    
    select * from co_occur ;
    
    use ret1018;
    select * from content_tag_disease_ytex_unique_output;
    
    
    select * from ret1018.content_tag_disease_ytex_unique_output;
    
    alter table ret1007.content_tag_ytex add column `sab` varchar(200) after `cui`;
    
    update ret1007.content_tag_ytex as cty 
		set cty.sab= (
			select group_concat(distinct sab separator ',') from umls.mrconso as con 
				where cty.cui = con.cui
                group by cty.cui
        );
        select * from ret1007.content_tag_ytex;
        
 rename table ret.content_tag_increased_target to ret1018.content_tag_increased_target;       
        