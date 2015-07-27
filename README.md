# Clinical-Text-Mining

## Overview

 A project focus on mining useful information from clinical text, based on UMLS data. The original
 idea come from [Sujit Pal](https://www.blogger.com/profile/06835223352394332155)'s posts
 [Fuzzy String Matching against UMLS Data](http://sujitpal.blogspot.com/2014/02/fuzzy-string-matching-against-umls-data.html)
 and [Fuzzy String Matching with SolrTextTagger](http://sujitpal.blogspot.com/2014/02/fuzzy-string-matching-with.html).

 To put it simply, Currently plan, Clinical-Text-Mining includes three steps:

 - Normalize the definition(string or other info) of UMLS,  and makes a custom index and tag using Solr, then
 - Takes the tokenization, parsing, chunking steps for the clinical text to get tags(maybe use cTAKES), then
 - Match the tags of clinical text with the tags of UMLS in Solr.

 ## Concrete functions as flowing:

 - Extract UMLS terms (AUIs or CUI and so on) from text. For example, in the UMLS, for the CUI C0027051 “Myocardial infarction”
  is the preferred term. It has many synonyms such as “heart attack” (A1303513), “coronary attack” (A18648338).
  Each of the synonyms has a AUI (term unique identifier). From a plain text, it extracts the AUIs like A1303513, A18648338.
- TO DO

## How to run

1. Download this project use: `git clone https://github.com/zwChan/Clinical-Text-Mining.git`. I recommend
   you use an IDE such as IDEA or Eclipse.
2. **Prepare the UMLS data for test**. **(This step may take lost of time, but if you just try to test
   the basic function of this project, just directly use the example data in data/umls/first_10000.csv)**
   You can follow the Sujit's post [Understanding UMLS](http://sujitpal.blogspot.com/2014/01/understanding-umls.html)
   or the [docs of UMLM](http://www.nlm.nih.gov/research/umls/new_users/online_learning/OVR_001.html).
   At the end, you will import the UMLS data into Mysql.
   Then export the test data from using sql:

   ```
    select CUI, STR from MRCONSO
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
        <field name="descr" type="string" indexed="true" stored="true"/>
        <field name="descr_norm" type="string" indexed="true" stored="true"/>
        <field name="descr_sorted" type="string" indexed="true" stored="true"/>
        <field name="descr_stemmed" type="string" indexed="true" stored="true"/>
        <field name="descr_tagged" type="tag" indexed="true" stored="false"
             omitTermFreqAndPositions="true" omitNorms="true"/>
        <copyField source="descr_norm" dest="descr_tagged"/>
        <dynamicField name="*" type="string" indexed="true" stored="true"/>
        ...
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

## Dependency
 - [Solr](https://github.com/apache/solr)
 - [SolrTextTagger](https://github.com/OpenSextant/SolrTextTagger)
 - UMLS data

## Test Result using first 10,000 record of UMLS
```
///////// testGetFull /////////
Query: Sepsis
[100.00%] (C0243026) (A0000638) Sepsis

Query: Biliary tract disease
[100.00%] (C0005424) (A0001596) Biliary tract disease

Query: Australia Antigen
[100.00%] (C0019168) (A0027970) Australia Antigen

///////// testGetPartial /////////
Query: Heart Attack and diabetes

Query: carcinoma (small-cell) of lung
Suggestion(20.0,Cell,C0007634,A0006517)
[ 20.00%] (C0007634) (A0006517) Cell

Query: side effects of Australia Antigen
Suggestion(40.0,Australia Antigen,C0019168,A0027970)
[ 40.00%] (C0019168) (A0027970) Australia Antigen

///////// testAnnotateConcepts /////////
ery: Lung Cancer

Query: Heart Attack

Query: Diabetes

Query: Heart Attack and diabetes

Query: carcinoma (small-cell) of lung
[ 20.00%] (C0007634) (A0006517) Cell

Query: asthma side effects
[ 33.33%] (C0004096) (A0027330) Asthma

```

## Contributor
  Anyone interested in the project is welcome!