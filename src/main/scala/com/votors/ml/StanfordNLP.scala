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
import edu.stanford.nlp.ling.tokensregex.{MatchedExpression, TokenSequenceMatcher, TokenSequencePattern}
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
import edu.stanford.nlp.time.{TimeAnnotations, TimeExpression, Timex}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.JavaConverters._
import breeze.numerics.abs
import com.votors.common.Utils.Trace._
import com.votors.common.Utils._
import com.votors.common._
import com.votors.umls._
import edu.stanford.nlp.io.EncodingPrintWriter.out
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
import edu.stanford.nlp.ling.{CoreAnnotations, CoreLabel, HasWord, Word}
import edu.stanford.nlp.process.{CoreLabelTokenFactory, DocumentPreprocessor, PTBTokenizer, WordTokenFactory}

import scala.util.control.Exception
;


/**
 * Created by Jason on 2016/5/12 0012.
 */
object StanfordNLP {

  def isNoun(pos: String) = pos.startsWith("N")

  def init(isLemmaOnlsy:Boolean=false) = {
    // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
    val props:Properties = new Properties()
    if (isLemmaOnlsy) {
      props.setProperty("annotators", "tokenize, ssplit, pos, lemma")
    } else {
      props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner,parse,depparse")

      //    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, parse, depparse");
      props.setProperty("ner.useSUTime", "true")
      props.setProperty("ner.applyNumericClassifiers", "true")
      props.setProperty("ner.sutime.includeRange", "true")
      props.setProperty("ner.sutime.markTimeRanges", "true")
      props.setProperty("sutime.binders", "0");
      //    props.setProperty("customAnnotatorClass.tokensregex", "edu.stanford.nlp.pipeline.TokensRegexAnnotator")
      //    props.setProperty("tokensregexdemo.rules", Conf.stanfordPatternFile)
    }
    val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)
    pipeline
  }
  var pipelineLemma:StanfordCoreNLP = null

  /**
    * Give a string, output its lemma format string
    * @param str input string
    *  return: (text, pos, lemma)
    */
  def getPosLemma(str: String) = {
    if (pipelineLemma == null) pipelineLemma =init(true)
    val document: Annotation = new Annotation(str)
    pipelineLemma.annotate(document)
    val tokens = document.get(classOf[TokensAnnotation])
    val lemmas = tokens.iterator().map(t=>{
      val lemma = t.get(classOf[LemmaAnnotation])
      val text = PTBTokenizer.ptb2Text(t.get(classOf[TextAnnotation]))
      val pos = t.get(classOf[PartOfSpeechAnnotation])
      (text,pos,lemma)
    })
    lemmas
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
    val text = "There are three GIRLS."
    // create an empty Annotation just with the given text
    //findPattern(text).foreach(_ => println(""))
    //println(getPosLemma(text).mkString(" "))
    return

  }

}
