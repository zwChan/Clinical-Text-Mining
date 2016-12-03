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

select * from CONTENT_TAG_UNIQUE_CUI;