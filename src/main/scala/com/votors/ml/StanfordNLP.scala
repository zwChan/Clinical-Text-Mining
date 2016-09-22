package com.votors.ml


import java.io._
import java.lang.Exception
import java.util
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

import edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation
import edu.stanford.nlp.hcoref.data.CorefChain
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.RelationMentionsAnnotation
import edu.stanford.nlp.ling.CoreAnnotations._
import edu.stanford.nlp.ling.tokensregex.{TokenSequenceMatcher, TokenSequencePattern, MatchedExpression}
import edu.stanford.nlp.parser.lexparser.LexicalizedParser
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory
import edu.stanford.nlp.semgraph.SemanticGraph
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation
import edu.stanford.nlp.tagger.maxent.{MaxentTagger, TaggerConfig}
import edu.stanford.nlp.time.TimeAnnotations.TimexAnnotation
import edu.stanford.nlp.trees.Tree
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation
import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.time.{TimeExpression, TimeAnnotations, Timex}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.collection.JavaConverters._

import breeze.numerics.abs
import com.votors.common.Utils.Trace._
import com.votors.common.Utils._
import com.votors.common._
import com.votors.umls.{CTPattern, ParseSentence, UmlsTagger2}
import edu.stanford.nlp.io.EncodingPrintWriter.out
import gov.nih.nlm.nls.lvg.Api.LvgCmdApi
import opennlp.tools.chunker._
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.parser.{ParserFactory, ParserModel}
import opennlp.tools.postag.{POSModel, POSTaggerME}
import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}
import opennlp.tools.stemmer.PorterStemmer
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel}
import org.apache.spark.broadcast.Broadcast

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.io.File
import scala.util.control.Breaks._

import edu.stanford.nlp.ling.{CoreAnnotations, Word, CoreLabel, HasWord}
;
import edu.stanford.nlp.process.{WordTokenFactory, CoreLabelTokenFactory, DocumentPreprocessor, PTBTokenizer}
;

import scala.util.control.Exception
;


/**
 * Created by Jason on 2016/5/12 0012.
 */
object StanfordNLP {

  def isNoun(pos: String) = pos.startsWith("N")

  def init() = {
    // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
    val props:Properties = new Properties()
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner,parse,depparse")
//    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, parse, depparse");
    props.setProperty("ner.useSUTime","true")
    props.setProperty("ner.applyNumericClassifiers","true")
    props.setProperty("ner.sutime.includeRange","true")
    props.setProperty("ner.sutime.markTimeRanges","true")
    props.setProperty("sutime.binders", "0");
//    props.setProperty("customAnnotatorClass.tokensregex", "edu.stanford.nlp.pipeline.TokensRegexAnnotator")
//    props.setProperty("tokensregexdemo.rules", Conf.stanfordPatternFile)

    val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)
    pipeline
  }
  val pipeline = init()

  def findPattern(text: String) = {
    val retList = new ArrayBuffer[(CoreMap,CTPattern)]()
    // we split using semicolon first, because stanfordNlp doesnot treat semicolon as a delimiter.
    text.split(";").filter(_.trim.length>0).foreach(sent=>{
      val document: Annotation = new Annotation(sent)
      pipeline.annotate(document)
      val sentences = document.get(classOf[SentencesAnnotation])
      var sentId = 0
      for( sentence <- sentences.iterator()) {
        sentId += 1  // sentence id is a index in the criteria
        val retPatterns = ParseSentence(sentence,sentId).getPattern()
        if (retPatterns.size > 0) {
          retList ++= retPatterns.map((sentence, _))
        } else {
          retList.append((sentence, null.asInstanceOf[CTPattern]))
        }
      }
    })
    retList.filter(_._2 != null).foreach(p=>{
      println(s"findPattern: ${p._1.get(classOf[TextAnnotation])}, ${p._2.toString}")
    })
    retList
  }

  def main (args: Array[String]): Unit= {
    // read some text in the text variable
    //val text: String = "No history of prior malignancy within the past 5 years except for curatively treated basal cell carcinoma of the skin." // Add your text here!
    //val text: String = "The girl you love has more than 2 dozens of boy friends in the last 3 years before you met her on January 1, 2010." // Add your text here!
    //val text: String = """you have no history OF hereditary cancer syndrome within 30 days, but you have  history OF hereditary cancer syndrome without 60 days. """+
      //"""History of diabetes within 3 days 5 days""" // Add your text here!
    //val text: String = "History of abdominal fistula, gastrointestinal perforation, or intra-abdominal abscess, within 6 months prior to start of study drug. No prior diabetes for 4 days."
    //val text = "History of red, black or white coffee."
    //val text = "Prior adjuvant therapy, including 5-FU, is allowed if it has been more than 12 months since the last treatment."
    //val text = "No history of myocardial infarction or severe unstable angina within the past 6 months."
    //val text = "Patients with a history of myocardial infarction or stroke within the last 6 months will be excluded."
    val text = "Abdominal imaging negative for metastases within the past 6 months."
    // create an empty Annotation just with the given text

    findPattern(text).foreach(_ => println(""))
    return

  }

}
