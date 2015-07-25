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

 **Concrete functions as flowing:**

 - Extract UMLS terms (AUIs or CUI and so on) from text. For example, in the UMLS, for the CUI C0027051 “Myocardial infarction”
  is the preferred term. It has many synonyms such as “heart attack” (A1303513), “coronary attack” (A18648338).
  Each of the synonyms has a AUI (term unique identifier). From a plain text, it extracts the AUIs like A1303513, A18648338.


 to be continue..

## How to run

 to be continue..

## Dependency
 - Solr
 - SolrTextTagger
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