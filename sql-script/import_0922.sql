/*=========================1===========================*/
DROP TABLE IF EXISTS `content_tag_ytex_T047_unique`;
CREATE TABLE `content_tag_ytex_T047_unique` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_ytex_T047_unique.csv' 
	into table content_tag_ytex_T047_unique 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*========================2============================*/

DROP TABLE IF EXISTS `content_tag_our_T047_unique`;
CREATE TABLE `content_tag_our_T047_unique` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_our_T047_unique.csv' 
	into table content_tag_our_T047_unique 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*=======================3=============================*/

DROP TABLE IF EXISTS `content_tag_compare_same2`;
CREATE TABLE `content_tag_compare_same2` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_compare_same2.csv' 
	into table content_tag_compare_same2 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*==========================4==========================*/

DROP TABLE IF EXISTS `content_tag_compare_same`;
CREATE TABLE `content_tag_compare_same` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_compare_same.csv' 
	into table content_tag_compare_same 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*=======================5=============================*/

DROP TABLE IF EXISTS `content_tag_compare_only_ytex`;
CREATE TABLE `content_tag_compare_only_ytex` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_compare_only_ytex.csv' 
	into table content_tag_compare_only_ytex 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*======================6==============================*/

DROP TABLE IF EXISTS `content_tag_compare_only_our`;
CREATE TABLE `content_tag_compare_only_our` (
  `blogId` varchar(40)  DEFAULT NULL,   	/* blog id*/
  `target` varchar(300)  DEFAULT NULL,		 /* the term found in the blog*/
  `cui` varchar(45)  DEFAULT NULL,				 /* the cui of the target*/
  `umlsStr` varchar(1000)  DEFAULT NULL,	 /* the STR of the cui of the target*/
  `wordIndex` int(11) DEFAULT NULL,				 /* the position of the term in the blog*/
  `sentence` varchar(1000)  DEFAULT NULL,  /* the sentence of the term found in*/
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the relative diabetes+T047 cui*/
  `rel_aui` varchar(9) NOT NULL DEFAULT '',/* the relative diabetes+T047 aui*/
  `rel_flag` bigint(20) NOT NULL DEFAULT '0', /* If the term relative indirectly to diabetes+T047*/
  `STT` varchar(3)  DEFAULT NULL,             /* The STT of ther re_cui*/
  `rel_str` text,                             /* The STR of ther re_cui*/
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),    /*Just an unique id*/
  `rel_all` varchar(500) /*all relationship between the target and the seed term*/
  ) CHARSET=utf8;
  
load data local infile '/data/ra/DatasetOutput/content_tag_compare_only_our.csv' 
	into table content_tag_compare_only_our 
	fields terminated by ','
	enclosed by '"'
	lines terminated by '\n'
	ignore 1 lines;
	
/*====================================================*/

	