## Overviw
Given a list of terms T {(tid,term)} and some textual data set D {(did,text)}, identify any of the term in T occurs in data set D.

## Steps of method
* Build the lockup table for the given terms T;
* Convert the text into N-gram, and match the N-gram in the lookup table to see if an N-gram match any of the terms.
 
## steps of operation
* preparation: 
    * Compile the project and get the Jar file of the project.
    * set the alias for tasks run-import-term and run-extract-term:
    ```
    alias run-import-term='spark-submit --master spark://somelab12.cci.fsu.edu:7077 --jars /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar  --driver-class-path /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar --conf 'spark.executor.extraJavaOptions=-DCTM_ROOT_PATH=/tmp/ctm_root' --driver-java-options=-DCTM_ROOT_PATH=/tmp/ctm_root --files /tmp/ctm_root/conf/default.properties --executor-memory 3g --class com.votors.umls.BuildTargetTerm  /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar   '
    alias run-extract-term='spark-submit --master spark://somelab12.cci.fsu.edu:7077 --jars /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar  --driver-class-path /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar --conf 'spark.executor.extraJavaOptions=-DCTM_ROOT_PATH=/tmp/ctm_root' --driver-java-options=-DCTM_ROOT_PATH=/tmp/ctm_root --files /tmp/ctm_root/conf/default.properties --executor-memory 3g   --class com.votors.umls.IdentfyTargetTerm /data/ra/Clinical-Text-Mining/target/Clinical-Text-Mining-0.0.1-SNAPSHOT-jar-with-dependencies.jar  '
    ```
    * Configure the conf/default.properties properly.
    * Store you textual data set in Mysql. Make sure there is a unique integer id for every text.
    
* execution
    * Import the term list to build a lookup table.
    ```
    run-import-term /tmp/supp_list.txt 
    ```
    * Configure the conf/default.properties to tell the tool where to file you textual data set.
    ```
    blogDbUrl=jdbc:mysql://[hostname or IP]:3306/[database name]?user=[username of Mysql]&password=[password of the user]         
    blogTbl=tmp_org_yahoo      
    blogIdCol=id         
    blogTextCol=concat(subject, ". ", content, ". ", chosenanswer)    
    ```
    * Run the identification command. Note that if the data set is large, it will take a long time. 
    So you'd better run this command using screen to avoid the network problem interrupts the processing.
    ```
    run-extract-term /tmp/ret_list.csv
    ```