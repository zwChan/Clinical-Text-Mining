# Clinical-Text-Mining

## Overview

 A project focus on mining potential knowledge from clinical text, based on database [UMLS](https://www.nlm.nih.gov/research/umls/),
   [StanfordNLP](http://nlp.stanford.edu/)/[OpenNLP](https://opennlp.apache.org/) and platform [Apcache Spark](http://spark.apache.org/).
 
## Features
### General text processing
 We call it "general" because anytime given a text dataset, we have to transform the text into
 the form that we can process them, usually it can be a vector of words.
 Currently, we use Apache openNLP or StanfordNLP (based on the configuration; we recommend to use StanfordNLP to process the text,
 including sentence detection, tokenization and  Part-of-Speech parsing.
 We use Lexical Variant Generation (LVG) to convert the variant of a word to its basic form.
 We use a user-defined stop word list to filter out the meaningless words. Note that a term contains
 more than one word, we will filter out it if it begins or ends with a stop word.

### Term identification (fuzzy matching)
 Term identification is a feature that given some texts and a list of term, we identify if the term 
 occurs in the text.
 To put it simply, Clinical-Text-Mining includes steps:

 - For the list of tems, we do the same general process, also remove stop words, normalization and
   resort the order of ther words in the terms, to get the basic form of the term, then store the result in Mysql (or solr)
 - For the texts, applay the general process mentioned above, then we obtain a vector of words for every
   text also the syntax information of the text.  
 - We construct [N-gram](https://en.wikipedia.org/wiki/N-gram) from the vector of the texts and also 
   find its basic for as above description, and then match the Ngram to check if it is found in the term list stored in Mysql (or solr).
 - For a N-gram, it may match more than one term in the list. We will give a similarity score based on
   the [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance), and we also collecting
   other information about the term, such as the semantic type if it is a term in UMLS.

## Prepare to run
1. Download this project use: `git clone https://github.com/zwChan/Clinical-Text-Mining.git`. I recommend
   you use an IDE such as IDEA.  Is is a maven project, so it should be easy to build it.
   Add an environment variable `CTM_ROOT_PATH`, which indicates the root directory of the project. 
   The tool will find the configuration file and resource file in the project directory.
2. **Prepare the UMLS data for test**. **(This step may take lots of time)**
   You can follow the Sujit's post [Understanding UMLS](http://sujitpal.blogspot.com/2014/01/understanding-umls.html)
   or the [docs of UMLM](http://www.nlm.nih.gov/research/umls/new_users/online_learning/OVR_001.html).
   At the end, you will import the UMLS data into Mysql.
3. Build index database for fuzzy matching.
   Run the test function: com.votors.umls.UmlsTagger2Test.testBuildIndex2db, and it will create a index table from UMLS database

4. (Instead of using Mysql, use Solr for fuzzy matching. More complicated, not recommended)
   Then export the test data  using sql:
   ```
    select CUI, AUI, STR from MRCONSO
        where LAT = 'ENG'
        limit 10000  -- No limit if you want to use all the umls terms
        into outfile 'your-path/first_10000.csv'
        fields terminated by ','
        enclosed by '"' lines
        terminated by '\n';
   ```
   Configure the jdbc of you Mysql in configuration file (conf\default.properties), then
   use the test function `testBuildIndex2db()` in the project to import above csv file into Mysql.
   
5. Good luck and enjoy it!

## Dependency
 - UMLS data

## Test Result

## Contributor
  Anyone interested in the project is welcome!