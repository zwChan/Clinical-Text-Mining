
use umls;
/*DELET ALL non-english record*/
-- delete from mrconso where lat <> 'ENG';
drop table IF EXISTS CONTENT_TAG;
/*create table for the original terms from blogs.*/
CREATE TABLE CONTENT_TAG (
`blogId` varchar(40) DEFAULT NULL,  /*----blog id */
`target`  varchar(300) DEFAULT NULL,  /* ----the term found in the content. It much be found in UMLS too. If a term is not found in UMLS, it will be ignored. */
`umlsFlag` varchar(10) DEFAULT NULL, /* ----If it is found in UMLS. This column is used by hashTags.*/
`score` float DEFAULT NULL,                /* ----The similarity metic between the term in the content and the string in UMLS.*/
`CUI` varchar(45) DEFAULT NULL,        /*----- CUI of UMLS*/
`SAB` varchar(45) DEFAULT NULL,       /* ----- SAB of UMLS*/
`AUI` varchar(45) DEFAULT NULL,        /*----- AUI of UMLS */
`umlsStr` varchar(1000) DEFAULT NULL,  /* ---STR of UMLS mrconso table*/
`TUI` varchar(45) DEFAULT NULL,        /*------TUI of UMLS MRSTY table */
`styName` varchar(45) DEFAULT NULL, /*------Semantic name of UMLS MRSTY table*/
`semName` varchar(100) DEFAULT NULL, /*---semantic group name in SemGroup website*/
`tagId` int default 0,               /*----If the term match a hash_tag of the blog, tagId is the index of the tags. if not match any hash_tag, it is 0.*/
`wordIndex` int default 0,            /*----the position of the term in the content*/
`wordIndexInSentence` int default 0,  /*---- the position of the term in the sentence that it is found in. */
`sentenceIndex` int default 0,         /*-- the index of the sentence of the target*/
`targetNorm` varchar(300) default NULL,  /*--- the normalized string of the term.*/
`tags` varchar(500) default NULL,         /*---- all the hash_tags of the blog. */
`sentence` varchar(1000) default NULL    /* The sentence that the target is found in*/
);

/*load result in to the table*/
load data local infile 'C:\\fsu\\ra\\UmlsTagger\\data\\data_content_tag_diabetes_0821_ret.csv' 
into table CONTENT_TAG 
fields terminated by ','
enclosed by '"'
lines terminated by '\n'
ignore 1 lines;

-- select count(*) from mrconso where str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%';
/*4021 result*/

/*find all AUI that is relevant to diabetes*/
create table tmp_diabetes as select distinct AUI from mrconso where LAT='ENG' AND (str like '%diabetes%' or str like '%type 1 diabetes%' or str like '%type 2 diabetes%') ;
/*more than 1k*/

-- select distinct cui from mrconso where str = 'diabetes' or str = 'type 1 diabetes' or str = 'type 2 diabetes';
/*4 results*/

-- select count(*) from mrrel;

-- drop tables if exists tmp_rel_diabetes;
/*find all relationship that is relevant to diabetes*/
create table tmp_rel_diabetes 
	as select r.* 
		from mrrel r inner join tmp_diabetes d
			on d.aui = r.aui1  or d.aui = r. aui2 ;
            
select count(*) from tmp_rel_diabetes;            
-- select * from CONTENT_TAG where length(sentence)<10;            
select count(*) from tmp_rel_diabetes;            
 
/*find all tags that has relationship with diabetes*/ 
create table content_tag_diabetes  as select distinct c.*, r.cui1,r.cui2,r.aui1,r.aui2,r.REL, r.RELA from CONTENT_TAG c inner join tmp_rel_diabetes r 
	on c.aui = r.aui1 COLLATE utf8_unicode_ci or c.aui = r.aui2 COLLATE utf8_unicode_ci;

select * from content_tag_diabetes limit 10;


-- select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence  from content_tag_diabetes where blogId in ('47923544278','60909574649','104422645889' ,'105407381237' ,'105625170923' ,'105943550692' ,'106050694329' ,'107494520338' ,'107512513044' ,'107935674190')      into outfile 'c:\\fsu\\tag_diabetes_first10blog_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';

select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence, rel, rela, rel_str from content_tag_diabetes 
  where REL <>'SIB' or REL <> 'XR'
  order by blogId, wordIndex
  into outfile 'c:\\fsu\\tag_diabetes_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';
  
drop table content_tag_diabetes_part ;
create table content_tag_diabetes_part  select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence , rel, rela, rel_str  from content_tag_diabetes 
  where REL <>'SIB' and REL <> 'XR' 
  order by blogId, wordIndex;
  
/*add a atuo increment primary key for next step. */  
alter table content_tag_diabetes_part add   id Int NOT NULL AUTO_INCREMENT PRIMARY KEY;

/*pick the last row for the same 'original result' based on blogid+wordIndex+CUI*/
drop table if exists content_tag_diabetes_part2;
create table content_tag_diabetes_part2 as 
select cd.* from  content_tag_diabetes_part as cd inner join (select id from content_tag_diabetes_part group by blogId, CUI, wordIndex order by score desc) as cp on cp.id = cd.id;
  
  select * from content_tag_diabetes_part2 order by blogId, wordIndex;

select distinct blogId, target, umlsFlag, score, CUI, SAB, AUI, umlsStr, TUI, styName, semName, tagId, wordIndex, wordIndexInSentence, sentenceIndex, targetNorm, tags, sentence, rel, rela, rel_str,id from content_tag_diabetes_part2 
  order by blogId, wordIndex
  into outfile 'c:\\fsu\\tag_diabetes_distinct.csv' fields terminated by ',' enclosed by '"' lines terminated by '\n';  
  
  /*add a fild of 'rel_str' to the result table. it indicates the 'str' of an AUI has relationship with current AUI */
  alter table content_tag_diabetes add rel_str varchar(1000);
  /*Find out the 'str' of aui that related to current aui. */
  update content_tag_diabetes as cd set cd.rel_str = (select con.str from mrconso con where con.aui = cd.aui1) where cd.aui1 <> cd.aui COLLATE utf8_unicode_ci;
  update content_tag_diabetes as cd set cd.rel_str = (select con.str from mrconso con where con.aui = cd.aui2) where cd.aui2 <> cd.aui COLLATE utf8_unicode_ci;
  
  select count(distinct blogId) from content_tag_diabetes;
  select count(distinct blogId) from content_tag;
  
  
  
  select * from content_tag_diabetes where aui = 'A21144438';
  select * from mrconso where aui = 'A21145265';
  
  
  
use umls;
/*DELET ALL non-english record*/
-- delete from mrconso where lat <> 'ENG';
drop table IF EXISTS CONTENT_TAG_UNIQUE_CUI;
/*create table for the original terms from blogs.*/
CREATE TABLE CONTENT_TAG_UNIQUE_CUI (
`blogId` varchar(40) DEFAULT NULL,  /*----blog id */
`target`  varchar(300) DEFAULT NULL,  /* ----the term found in the content. It much be found in UMLS too. If a term is not found in UMLS, it will be ignored. */
`umlsFlag` varchar(10) DEFAULT NULL, /* ----If it is found in UMLS. This column is used by hashTags.*/
`score` float DEFAULT NULL,                /* ----The similarity metic between the term in the content and the string in UMLS.*/
`CUI` varchar(45) DEFAULT NULL,        /*----- CUI of UMLS*/
`SAB` varchar(45) DEFAULT NULL,       /* ----- SAB of UMLS*/
`AUI` varchar(45) DEFAULT NULL,        /*----- AUI of UMLS */
`umlsStr` varchar(1000) DEFAULT NULL,  /* ---STR of UMLS mrconso table*/
`TUI` varchar(45) DEFAULT NULL,        /*------TUI of UMLS MRSTY table */
`styName` varchar(45) DEFAULT NULL, /*------Semantic name of UMLS MRSTY table*/
`semName` varchar(100) DEFAULT NULL, /*---semantic group name in SemGroup website*/
`tagId` int default 0,               /*----If the term match a hash_tag of the blog, tagId is the index of the tags. if not match any hash_tag, it is 0.*/
`wordIndex` int default 0,            /*----the position of the term in the content*/
`wordIndexInSentence` int default 0,  /*---- the position of the term in the sentence that it is found in. */
`sentenceIndex` int default 0,         /*-- the index of the sentence of the target*/
`targetNorm` varchar(300) default NULL,  /*--- the normalized string of the term.*/
`tags` varchar(500) default NULL,         /*---- all the hash_tags of the blog. */
`sentence` varchar(1000) default NULL,    /* The sentence that the target is found in*/
`rel`     varchar(4)   ,   /*rel field in mrrel*/
`rela`    varchar(100) ,   /*rela fild in mrrel*/
`rel_str` varchar(1000) ,  /*str field  in mrconso for the term relevant to current term */
`id`      int(11)          /* auto_increment primary key for the result table*/
);

/*load result in to the table*/
load data local infile 'C:\\fsu\\tag_diabetes_distinct.csv' 
into table CONTENT_TAG_UNIQUE_CUI 
fields terminated by ','
enclosed by '"'
lines terminated by '\n'
ignore 1 lines;
