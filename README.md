# Clinical-Text-Mining

## Overview

 A project focus on mining potential knowledge from clinical text, based on dataset [UMLS](https://www.nlm.nih.gov/research/umls/)
   and platform [Apcache Spark](http://spark.apache.org/). 
 
## Features
### General text processing
 We call it "general" because anytime given a text dataset, we have to transform the text into
 the form that we can process them, usually it can be a vector of words.
 Currently, we use [Apache openNLP](https://opennlp.apache.org/) to process the text, 
 including sentence detection, tokenization and  Part-of-Speech parsing. We may adopt
  [Stanford NLP](http://nlp.stanford.edu/) in the future since it provide  richer of features.
 We use Lexical Variant Generation (LVG) to convert the variant of a word to its basic form.
 We use a user-defined stop word list to filter out the meaningless words. Note that a term contains
 more than one word, we will filter out it if it begins or ends with a stop word.

### Term identification
 Term identification is a feature that given some texts and a list of term, we identify if the term 
 occurs in the text.
 To put it simply, Clinical-Text-Mining includes steps:

 - For the texts, applay the general process mentioned above, then we obtain a vector of words for every
   text. 
 - For the list of tems, we do the same general process, and then store the result in Mysql (or solr)
 - We construct [N-gram](https://en.wikipedia.org/wiki/N-gram) from the vector, and then execute a 
 query for the Ngram to check if is is found in the term list stored in Mysql (or solr).
 - For a N-gram, it may match more than one term in the list. We will give a similarity score based on
   the [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance), and we also collecting
   other information about the term, such as the semantic type if it is a term in UMLS.

### Tasks List:

 - [x] Extract UMLS terms (AUIs or CUI and so on) from text, just a basic function as an example.
  For example, in the UMLS, for the CUI C0027051 “Myocardial infarction”
  is the preferred term. It has many synonyms such as “heart attack” (A1303513), “coronary attack” (A18648338).
  Each of the synonyms has a AUI (term unique identifier). In this basic task, it extracts the AUIs
  like A1303513, A18648338 according to the text.
    - [x] Use sentence as the basic unit to apply the n-gram algorithm (currently use a line as unit). Using opennlp.
    - [x] Use the pos of a word when match.(if pos not match, 70% discount)
    - [x] Filter the gram (in n-gram algorithm) to search by pos, e.g. ignore the gram without noun
    - [x] Get all the resault from solr, sorted with the score
    - [ ] Choose a best result. It is about Word Sense Disambiguation.
    - [x] The case different should be concern less than other different when compare UMLS term and input term.
 - [x] Map the extracted CUI to a semantic type
    - [x] Get all STY from MRSTY table by cui
    - [x] Get semantic group name from semGroups.txt by TUI
 
## clustering


## How to run

1. Download this project use: `git clone http://somelab08.cci.fsu.edu/zc15d/Clinical-Text-Mining.git`. I recommend
   you use an IDE such as IDEA.  Is is a maven project, so it should be easy to build it.
   Add an environment variable `CTM_ROOT_PATH`, which indicates the root directory of the project. 
   The tool will find the configuration file and resource file in the project directory.
2. **Prepare the UMLS data for test**. **(This step may take lost of time, but if you just try to test
   the basic function of this project, just directly use the example data in data/umls/first_10000.csv)**
   You can follow the Sujit's post [Understanding UMLS](http://sujitpal.blogspot.com/2014/01/understanding-umls.html)
   or the [docs of UMLM](http://www.nlm.nih.gov/research/umls/new_users/online_learning/OVR_001.html).
   At the end, you will import the UMLS data into Mysql.
   Then export the test data  using sql:

   ```
    select CUI, AUI, STR from MRCONSO
        where LAT = 'ENG'
        limit 10000
        into outfile 'your-path/first_10000.csv'
        fields terminated by ','
        enclosed by '"' lines
        terminated by '\n';
   ```
   use the test function `testBuildIndex()` in the project to transform this csv file into json file,
   and you have to modify the file path and name in the test function.
3. **Download and Build SolrTextTagger**    
   The code for SolrTextTagger resides on GitHub, so to download and build the custom Solr JAR,
    execute the following sequence of commands. This will create a solr-text-tagger-1.3-SNAPSHOT.
    jar file in your target subdirectory in the SolrTextTagger project.

   ```
   git clone  https://github.com/OpenSextant/SolrTextTagger.git
   cd SolrTextTagger
   git checkout -b v1x --track origin/v1x
   mvn test
   mvn package
   ```
   
6. Now, you can run the test functions, and you should get the result as the **Test Result using first 10,000 record of UMLS** show.  
   I suggest using the IDEA as your IDE, and mark the src/main as a source directory and src/test as the test directory. Then you can directly 
   run the junit test function. Before you run it, set the UmlsTagger2Test.dataDir (in class UmlsTagger2Test) to the directory of the "data"
   directory under the project directory.
   
7. Good luck and enjoy it!

## Dependency
 - UMLS data

## Test Result using all UMLM record of UMLS 
(if you use first 10,000 record of UMLS, the result should be less then this)
```
Query: Sepsis
select get 19 result for [Sepsis].
[100.00%] (C0243026) (A17943618) Sepsis
[100.00%] (C0243026) (A16979314) Sepsis
[100.00%] (C0243026) (A10862868) Sepsis
[100.00%] (C0036690) (A23920704) Sepsis
[100.00%] (C0036690) (A23943328) Sepsis
[100.00%] (C0243026) (A23449659) Sepsis
[100.00%] (C0243026) (A23635326) Sepsis
[100.00%] (C0243026) (A21145176) Sepsis
[100.00%] (C0243026) (A23037453) Sepsis
[100.00%] (C0036690) (A7567979) Sepsis
[100.00%] (C0243026) (A0000638) Sepsis
[100.00%] (C0243026) (A0000640) Sepsis
[100.00%] (C1090821) (A0000641) Sepsis
[ 90.00%] (C0243026) (A18580070) sepsis
[ 90.00%] (C0243026) (A1203207) sepsis
[ 90.00%] (C0036690) (A23895141) sepsis
[ 16.67%] (C0243026) (A0454607) SEPSIS
[ 16.67%] (C0243026) (A0454608) SEPSIS
[ 16.67%] (C0036690) (A23914386) SEPSIS

Query: Biliary tract disease
select get 7 result for [Biliary tract disease].
[100.00%] (C0005424) (A0001596) Biliary tract disease
[100.00%] (C0005424) (A0001597) Biliary tract disease
[ 95.24%] (C0005424) (A18590143) biliary tract disease
[ 90.48%] (C0005424) (A0394721) Biliary Tract Disease
[ 50.00%] (C0005424) (A18571497) biliary disease tract
[ 15.91%] (C0005424) (A0406938) Disease, Biliary Tract
[ 12.73%] (C0005424) (A1933178) Tract Disease, Biliary

Query: Progressive systemic sclerosis
select get 15 result for [Progressive systemic sclerosis].
[100.00%] (C0036421) (A17314189) Progressive systemic sclerosis
[100.00%] (C1258104) (A17316705) Progressive systemic sclerosis
[100.00%] (C0036421) (A8339669) Progressive systemic sclerosis
[100.00%] (C0036421) (A0001403) Progressive systemic sclerosis
[ 96.67%] (C0036421) (A18631043) progressive systemic sclerosis
[ 96.67%] (C1258104) (A18663298) progressive systemic sclerosis
[ 96.67%] (C0036421) (A4366684) progressive systemic sclerosis
[ 93.33%] (C0036421) (A0449216) Progressive Systemic Sclerosis
[ 93.33%] (C0036421) (A0449217) Progressive Systemic Sclerosis
[ 93.33%] (C1258104) (A2783441) Progressive Systemic Sclerosis
[ 20.32%] (C1258104) (A17921315) Sclerosis, Progressive Systemic
[ 20.32%] (C1258104) (A2782910) Sclerosis, Progressive Systemic
[ 17.74%] (C1258104) (A2782943) Systemic Sclerosis, Progressive
[ 10.00%] (C0036421) (A0443846) PROGRESSIVE SYSTEMIC SCLEROSIS
[ 10.00%] (C0036421) (A0443847) PROGRESSIVE SYSTEMIC SCLEROSIS

```

## Contributor
  Anyone interested in the project is welcome!