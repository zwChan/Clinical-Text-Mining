# Clinical-Text-Mining

## Overview

 A project focus on mining useful information from clinical text, based on UMLS data. The original
 idea come from [Sujit Pal](https://www.blogger.com/profile/06835223352394332155)'s posts
 [Fuzzy String Matching against UMLS Data](http://sujitpal.blogspot.com/2014/02/fuzzy-string-matching-against-umls-data.html)
 and [Fuzzy String Matching with SolrTextTagger](http://sujitpal.blogspot.com/2014/02/fuzzy-string-matching-with.html).

 To put it simply, Currently plan, Clinical-Text-Mining includes steps:

 - Normalize(tokenization, parsing, chunking and so on, maybe use cTAKES or opennlp) 
   the definition(string or other info) of UMLS(or any other metathesaurus) into several features,
   then makes a custom index and tag using Solr, then
 - Takes the same Normalization steps for the clinical text to get the features, then
 - Match features of clinical text with the tags of UMLS in Solr.
 - Evaluate the matching result, and find out the best result(s)

 ## Concrete functions as flowing(Tasks List):

 - [x] Extract UMLS terms (AUIs or CUI and so on) from text, just a basic function as an example.
  For example, in the UMLS, for the CUI C0027051 “Myocardial infarction”
  is the preferred term. It has many synonyms such as “heart attack” (A1303513), “coronary attack” (A18648338).
  Each of the synonyms has a AUI (term unique identifier). In this basic task, it extracts the AUIs
  like A1303513, A18648338 according to the text.
    - [x] Use sentence as the basic unit to apply the n-gram algorithm (currently use a line as unit). Using opennlp.
    - [x] Concern the pos of a word when match.(if pos not match, 70% discount)
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
   you use an IDE such as IDEA or Eclipse.  
   Modify the ${UmlsTagger2Test.rootDir} in the test class to the root directory of the project, e.g. val rootDir = "C:\\fsu\\ra\\UmlsTagger"  
   Modify the file default.properties in /conf to the right property, 
2. **Prepare the UMLS data for test**. **(This step may take lost of time, but if you just try to test
   the basic function of this project, just directly use the example data in data/umls/first_10000.csv)**
   You can follow the Sujit's post [Understanding UMLS](http://sujitpal.blogspot.com/2014/01/understanding-umls.html)
   or the [docs of UMLM](http://www.nlm.nih.gov/research/umls/new_users/online_learning/OVR_001.html).
   At the end, you will import the UMLS data into Mysql.
   Then export the test data from using sql:

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
4. **Download and Customize Solr** (only 4.6.1 is tested, later it will support solr 5.x)
   Solr is available for download [here](https://archive.apache.org/dist/lucene/solr/4.6.1/).
   After downloading you will need to expand it locally, then update the schema.xml and solrconfig.xml
   in the conf subdirectory as shown below:  
  **(Tips: Instead of modify by yourself as following,, you can just copy the config files in
  ${project-root}/conf directory to ${solr-4.6.1}/example/solr/collection1/conf)**

   ```
   tar xvzf solr-4.6.1.tgz
   cd solr-4.6.1/example/solr/collection1/conf
   ```

   Update the schema.xml to replace the field definitions with our own. Our fields list and the definition
   of the field type "tag" (copied from the documentation of SolrTextTagger) is shown. The "id" field is
    just a integer sequence (unique key for Solr), the "cui" and "descr" comes from the CUI and
    STR fields from the UMLS database, and the descr_norm, descr_sorted, descr_stemmed are case/punctuation normalized,
    alpha sorted and stemmed versions of STR. The descr_tagged field is identical to descr_norm but is analyzed differently as specified below.
    (add the new fields to the beginning of the <fields>):

    ```
    <fields>
        <field name="id" type="string" indexed="true" stored="true"
          required="true"/>
        <field name="cui" type="string" indexed="true" stored="true"/>
        <field name="aui" type="string" indexed="true" stored="true"/>
        <field name="sab" type="string" indexed="false" stored="true"/>
        <field name="descr" type="string" indexed="true" stored="true"/>
        <field name="descr_norm" type="string" indexed="true" stored="true"/>
        <field name="descr_sorted" type="string" indexed="true" stored="true"/>
        <field name="descr_stemmed" type="string" indexed="true" stored="true"/>
        <field name="descr_tagged" type="tag" indexed="true" stored="false"
             omitTermFreqAndPositions="true" omitNorms="true"/>
        <copyField source="descr_norm" dest="descr_tagged"/>
        <dynamicField name="*" type="string" indexed="true" stored="true"/>
        
        ...
    </fields>
    ...
    <types>
        <fieldType name="tag" class="solr.TextField" positionIncrementGap="100">
          <analyzer>
            <tokenizer class="solr.StandardTokenizerFactory"/>
            <filter class="solr.EnglishPossessiveFilterFactory"/>
            <filter class="solr.ASCIIFoldingFilterFactory"/>
            <filter class="solr.LowerCaseFilterFactory"/>
          </analyzer>
        </fieldType>
    ...
    </types>        
        
    ```
    We then add in the requestHandler definition for SolrTextTagger's tag service into the solrconfig.xml file (also in conf).
    The definition is shown below(add it above the first exists requestHandler):

    ```
    <requestHandler name="/tag"
         class="org.opensextant.solrtexttagger.TaggerRequestHandler">
        <str name="indexedField">descr_tagged</str>
        <str name="storedField">descr_norm</str>
        <bool name="partialMatches">false</bool>
        <int name="valueMaxLen">5000</int>
        <str name="cacheFile">taggerCache.dat</str>
     </requestHandler>
      ```
     Finally, we create a lib directory and copy over the solr-text-tagger-1.3-SNAPSHOT.jar into it.
     Then go up to the example directory and start Solr. Solr is now listening on port 8983 on localhost.

     ```
     cd solr-4.6.1/example/solr/collection1
     mkdir lib
     cp ${SolrTextTagger-path}/SolrTextTagger/target/*jar lib/
     cd ../..
     java -jar start.jar
     ```
5. Load Data and Build FST
    We use the same cuistr1.csv file that we downloaded from our MySQL UMLS database. I guess I could have
    written custom code to load the data into the index, but I had started experimenting with SolrTextTagger using curl,
    so I just wrote some code that converted the (CUI,STR) CSV format into JSON,
    with additional fields created by our case/punctuation normalization, alpha sort and stemming.
    I used the same Scala code since I already had the transformations coded up from last week.
    Once I generated the JSON file (cuistr1.json), I uploaded it into Solr and built the FST using the following curl commands.

    ```
    cd solr-4.6.1\example\exampledocs
    java -Durl=http://localhost:8983/solr/update -Dtype=application/json \
      -jar post.jar ${your-path}/first_10000.csv
    curl "http://localhost:8983/solr/tag?build=true" (or you can run this url in a browser)
    ```
6. Now, you can run the test functions, and you should get the result as the **Test Result using first 10,000 record of UMLS** show.  
   I suggest using the IDEA as your IDE, and mark the src/main as a source directory and src/test as the test directory. Then you can directly 
   run the junit test function. Before you run it, set the UmlsTagger2Test.dataDir (in class UmlsTagger2Test) to the directory of the "data"
   directory under the project directory.
   
7. Good luck and enjoy it!

## Dependency
 - [Solr](https://github.com/apache/solr)
 - [SolrTextTagger](https://github.com/OpenSextant/SolrTextTagger)
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