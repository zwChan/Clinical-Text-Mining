# CHV terms recommendation

## Overview

 A project focus on recommendation of CHV terms from social media, based on dataset [UMLS](https://www.nlm.nih.gov/research/umls/)
   and platform [Apcache Spark](http://spark.apache.org/).   
 Conceptual idea is shwon in flowing figure. 
 ![Conceptual idea](https://raw.githubusercontent.com/zwChan/Clinical-Text-Mining/chv-term-recommendation/docs/figurs/conceptual.png)
 
- We first extract n-gram from Yahoo!Answers textual dataset (of course, you can
 use any other textual dataset); 
- Second, identify the CHV terms using fuzzy matching method with UMLS database. We also call these terms as seed terms.
   The other terms which do not match with any CHV term is considered as candidate terms.
- Third, we use K-means algorithm to train a model from the identified CHV terms, and we get K centers for the model..
- Fourth, we calculate the distance between a candidate term to all the K centers, and we choose the shortest distance 
  as the score to measure whether we should recommend a term as a CHV term. The smaller the score is, the more likely a candidate 
  term should considered as a CHV term.
 
 
 The workflow of the project is shown in following figure.
 ![workflow](https://raw.githubusercontent.com/zwChan/Clinical-Text-Mining/chv-term-recommendation/docs/figurs/work-flow.png)
 
 For more detail of the methodology, please read our paper.
 
## How to run

1. Download this project use: `git clone https://github.com/zwChan/Clinical-Text-Mining.git`. I recommend
   you use an IDE such as IDEA.  It is a maven project, so it should be easy to build it.
   Add an environment variable `CTM_ROOT_PATH`, which indicates the root directory of the project. 
   The tool will find the configuration file and resource file in the project directory.

2. Download LVG tool from [https://lexsrv3.nlm.nih.gov/LexSysGroup/Projects/lvg/current/web/download.html]
   (https://lexsrv3.nlm.nih.gov/LexSysGroup/Projects/lvg/current/web/download.html), then unzip it to your local
   file system. Configure the root directory of LVG to the configuration file ${CTM_ROOT_PATH}/conf/default.properties
   ```
   #root dir of lvg
   lvgdir=C:\\lvg2015\\
   ```
   
3. Prepare the UMLS data . First, you have to establish the UMLS database. You can follow the Sujit's post 
   [Understanding UMLS](http://sujitpal.blogspot.com/2014/01/understanding-umls.html)
   or the [docs of UMLM](http://www.nlm.nih.gov/research/umls/new_users/online_learning/OVR_001.html).
   At the end, you will import the UMLS data into Mysql.
   Then export the CHV terms using sql query:
   ```
    select CUI, AUI, STR from MRCONSO
        where LAT = 'ENG' and SAB='CHV'
        into outfile 'your-path/output-chv.csv'
        fields terminated by ','
        enclosed by '"' lines
        terminated by '\n';
   ```
   Configure the jdbc of you Mysql in configuration file (conf\default.properties), 
   ```
   jdbcDriver=jdbc:mysql://localhost:3306/umls?user=root&password=root
   ```
   then use the test function `testBuildIndex2db()` in the project to import above csv file into Mysql.
   ```
     @Test
     def testBuildIndex2db(): Unit = {
       val tagger = new UmlsTagger2("",rootDir)
       tagger.buildIndex2db(
         new File("your-path/output-chv.csv"))
     }
   ```
   
4. Prepare you textual dataset. The tool will read the dataset from a configured database, so you have
 to import you dataset into a database (e.g. Mysql), and then configure the database name, table name and 
 other information in the configuration file (conf\default.properties). The table should contain an index column
 to identify a unique text.
    ```
    ########## data source configuration ######################
    # how to get the text to get Ngram; the blogId will select as distict, and the blogTextCol will be limit to 1 row.  
    blogDbUrl=jdbc:mysql://localhost:3306/ytex?user=root&password=root  
    blogTbl=tmp_org_yahoo  
    blogIdCol=id  
    blogTextCol=concat(subject, ". ", content, ". ", chosenanswer)  
    ```
 
5. Take a look at the whole configuration file, and change the configuration based on you need.
   You have to configure the following items properly:
   ```
   #####*_*####get the training data from (previous save) file, do not construct the Ngram again.
   clusteringFromFile=false
   ngramSaveFile=c:\\fsu\\ra\\data\\ngram_yahoo_0211.serd.no_bow
   ```

6. Now you can run the Clustering object in your IDE (e.g. IDEA, and don't forget to setup the env CTM_ROOT_PATH 
   in the Run/Debug configuration of the IDE).
   
7. Good luck and enjoy it!

## Dependency
 - UMLS data

## Test Result

## Contributor
  Anyone interested in the project is welcome!