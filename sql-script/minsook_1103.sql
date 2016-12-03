use tumblr_db;

select count(distinct cui) from tem_tag_ids;
-- 935
select count(distinct blogId) from tem_tag_ids;
-- 147
select count(distinct code) from content_tag_ytex_noseedtag;
-- 8227
select count(distinct instance_id) from content_tag_ytex_noseedtag;
-- 22171

select count(*) from content_tag_ytex_noseedtag;
-- 265599
select count(*) from tem_tag_ids;
-- 118729

drop table tmp_pairs;
create table tmp_pairs as 
select distinct c.blogId, c.cui,s.cui as cui_tag from (
	select distinct instance_id as blogId, code as cui from content_tag_ytex_noseedtag) as c
		inner join (select distinct blogId,cui from tem_tag_ids) as s
			on c.cui<>s.cui and c.blogId = s.blogId
                ;
-- 6059
select count(distinct blogId) from tmp_pairs;
-- 120
create table tmp_pairs as 
	select distinct c.cui,s.cui as cui_tag from (
		select distinct code as cui from content_tag_ytex_noseedtag) as c
			inner join (select distinct cui from tem_tag_ids) as s
				on c.cui<>s.cui
					;
-- 7692072
alter table content_tag_ytex_noseedtag add (rel_all text default null);  
alter table content_tag_ytex_noseedtag add (cui_tag char(8) default null);  

update content_tag_ytex_noseedtag set rel_all=null;

update content_tag_ytex_noseedtag as t
	inner join
	(select ret.cui,ret.cui_tag,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from umls.mrrel as r 
		inner join tmp_pairs as ret 
        on  (r.CUI1=ret.cui and r.CUI2 = ret.cui_tag ) 
		 or (r.CUI2=ret.cui and r.CUI1=ret.cui_tag )
        GROUP BY cui,cui_tag
        ) as temp
    on  (temp.cui=t.code and temp.blogId=t.instance_id) 
	set t.rel_all = temp.rel_all, t.cui_tag= temp.cui_tag
    where t.rel_all is null
    ;

update content_tag_ytex_noseedtag as t
	inner join
	(select ret.blogId, ret.cui,ret.cui_tag,GROUP_CONCAT(DISTINCT REL,' ',IFNULL(RELA,'null') SEPARATOR ',') as rel_all from umls.mrrel as r 
		inner join tmp_pairs as ret 
        on  (r.CUI1=ret.cui and r.CUI2 = ret.cui_tag ) 
		 or (r.CUI2=ret.cui and r.CUI1=ret.cui_tag )
        GROUP BY blogId,cui,cui_tag
        ) as temp
    on  (temp.cui=t.code and temp.blogId=t.instance_id) 
	set t.rel_all = temp.rel_all, t.cui_tag= temp.cui_tag
    where t.rel_all is null
    ;

select * from content_tag_ytex_noseedtag where cui_tag is not null;
