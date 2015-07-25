# Clinical-Text-Mining

## Overview

 A project focus on mining useful information from clinical text, based on UMLS data.

 to be continue..

## How to run

 to be continue..

## Dependency
 - Solr
 - SolrTextTagger
 - UMLS data

## Test Result using first 10,000 record of UMLS
'''
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

'''

## Contributor
  Anyone interested in the project is welcome!