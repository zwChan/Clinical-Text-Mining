 CREATE TABLE `content_tag_compare_only_ytex` (
  `blogId` bigint(20) NOT NULL DEFAULT '0',
  `target` longtext,
  `CUI` varchar(20) DEFAULT NULL,
  `SAB` varchar(40) DEFAULT NULL,
  `umlsStr` longtext,
  `TUI` varchar(4) DEFAULT NULL,
  `styName` varchar(50) DEFAULT NULL,      /* semantic type name*/
  `worldIndex` int(11) NOT NULL DEFAULT '0', /* the position of the target term in the blog content*/
  `sentence` longtext,
  `rel_cui` char(8) NOT NULL DEFAULT '',   /* the cui that relevant to current target term*/
  `rel_str` varchar(1000) DEFAULT NULL,    /* the preferred string of rel_cui that relevant to current target term*/
  `id` int(11) NOT NULL DEFAULT '0'        /* the primary key of this table, it is unique.*/
) ;

| content_tag_compare_only_our | CREATE TABLE `content_tag_compare_only_our` (
  `blogId` varchar(40)  DEFAULT NULL,
  `target` varchar(300)  DEFAULT NULL,
  `umlsFlag` varchar(10)  DEFAULT NULL,
  `score` float DEFAULT NULL,
  `CUI` varchar(45)  DEFAULT NULL,
  `SAB` varchar(45)  DEFAULT NULL,
  `AUI` varchar(45)  DEFAULT NULL,
  `umlsStr` varchar(1000)  DEFAULT NULL,
  `TUI` varchar(45)  DEFAULT NULL,
  `styName` varchar(45)  DEFAULT NULL,
  `semName` varchar(100)  DEFAULT NULL,
  `tagId` int(11) DEFAULT '0',
  `wordIndex` int(11) DEFAULT '0',
  `wordIndexInSentence` int(11) DEFAULT '0',
  `sentenceIndex` int(11) DEFAULT '0',
  `targetNorm` varchar(300)  DEFAULT NULL,
  `tags` varchar(500)  DEFAULT NULL,
  `sentence` varchar(1000)  DEFAULT NULL,
  `cui1` char(8) NOT NULL,
  `cui2` char(8) NOT NULL,
  `aui1` varchar(9) DEFAULT NULL,
  `aui2` varchar(9) DEFAULT NULL,
  `REL` varchar(4) NOT NULL,
  `RELA` varchar(100) DEFAULT NULL,
  `rel_str` varchar(1000)  DEFAULT NULL,   /* the preferred string of rel_cui that relevant to current target term*/
  `id` int(11) NOT NULL DEFAULT '0',
  `rel_cui` varchar(45)  DEFAULT NULL      /* the cui that relevant to current target term*/
) ;

load data local infile 'csv_path'
into table table_name
fields terminated by ','
enclosed by '"'
lines terminated by '\n';