  It is tedious to configure Solr, that is why I change it to perform the matching task in Mysql. 
  I do not recommend to use Solr except you has a strong consideration.

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
    
    
6. **Download and Build SolrTextTagger**    
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