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
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, depparse");
    props.setProperty("ner.useSUTime","true")
    props.setProperty("ner.applyNumericClassifiers","true")
    props.setProperty("ner.sutime.includeRange","true")
    props.setProperty("ner.sutime.markTimeRanges","true")

    val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)
    pipeline
  }
  val pipeline = init()

  def findPattern(text: String) = {
    val retList = new ArrayBuffer[CTPattern]()
    val document: Annotation = new Annotation(text)
    pipeline.annotate(document)
    val sentences = document.get(classOf[SentencesAnnotation])
    for( sentence <- sentences.iterator()) {
      retList ++= ParseSentence(sentence).getPattern()
    }
    println("matched result: \n" + retList)
    retList
  }

  def main (args: Array[String]): Unit= {
    // read some text in the text variable
    val text: String = "History of hereditary cancer syndrome within 30 days. History of hereditary cancer syndrome within 2 weeks." // Add your text here!
    //val text: String = "The girl you love has more than 2 dozens of boy friends in the last 3 years before you met her on January 1, 2010." // Add your text here!
    //val text: String = """you have no history OF hereditary cancer syndrome within 30 days, but you have  history OF hereditary cancer syndrome without 60 days. """+
      //"""History of diabetes within 3 days 5 days""" // Add your text here!
    //val text: String = "you have no history OF hereditary cancer syndrome within 30 days."
    // create an empty Annotation just with the given text

    findPattern(text)
    return

    val document: Annotation = new Annotation(text);

    // run all Annotators on this text
    pipeline.annotate(document)


    // these are all the sentences in this document
    // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
    val sentences = document.get(classOf[SentencesAnnotation])


    for( sentence <- sentences.iterator()) {
//      val timexAnnsAll = sentence.get(classOf[TimeAnnotations.TimexAnnotations]);
//      if (timexAnnsAll != null)
//      for (cm <- timexAnnsAll.iterator()) {
//        val tokens = cm.get(classOf[CoreAnnotations.TokensAnnotation]);
//        System.out.println(cm + " [from char offset " +
//          tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation]) +
//          " to " + tokens.get(tokens.size() - 1).get(classOf[CoreAnnotations.CharacterOffsetEndAnnotation]) + ']' +
//          " --> " + cm.get(classOf[TimeExpression.Annotation]).getTemporal());
//      }
//      System.out.println("--");

//      val matched = ParseSentence.extractor.extractExpressions(sentence)
//      println("matched result: \n" + matched)

//      println("### sentence\n" + sentence)
      val tokens = sentence.get(classOf[TokensAnnotation])
      println("### tokens\n" + tokens)

      //val retPattern = ParseSentence(sentence).getPattern()
      //println(s"# patternList:\n${retPattern.mkString("\n")}")

//
//      val rels = sentence.get(classOf[RelationMentionsAnnotation ])
//      //println("### rels\n" + rels)
//      // traversing the words in the current sentence
//      // a CoreLabel is a CoreMap with additional token-specific methods
      for (token <- tokens.iterator()) {
        val ta = token.get(classOf[TimexAnnotation])
        if(ta!=null){

          println(s"ta: ${ta},${ta.altVal()},${ta.beginPoint()}")
        }
//        // this is the text of the token
//        val word = token.get(classOf[TextAnnotation])
//        // this is the POS tag of the token
//        val pos = token.get(classOf[PartOfSpeechAnnotation])
//        // this is the NER label of the token
//        val ne = token.get(classOf[NamedEntityTagAnnotation])
//        val lemma = token.get(classOf[LemmaAnnotation])
//        //println(s"${token} lemma is " + lemma)
//        val ner = token.get(classOf[NamedEntityTagAnnotation ])
//        println(s"### ${word} ner ${ner}")
      }

      // this is the parse tree of the current sentence
//      val tree: Tree = sentence.get(classOf[TreeAnnotation])
//      println("### tree\n" + tree)

      // this is the Stanford dependency graph of the current sentence
//      val dependencies: SemanticGraph = sentence.get(classOf[CollapsedCCProcessedDependenciesAnnotation])
//      println("### dependencies\n" + dependencies)

//      val p = ["history"] ["of"] ( []{1,5} ) ["within"] ( [ { ner:DURATION } ]+) =>"HHHH-1" """
//      println(p)
//      val pattern:TokenSequencePattern = TokenSequencePattern.compile(p);
//      val matcher:TokenSequenceMatcher = pattern.getMatcher(tokens);
//
//      while (matcher.find()) {
//        val matchedString = matcher.group();
//        println(s"matchedString:${matchedString}")
//        val matchedTokens = matcher.groupNodes()
//        matchedTokens.iterator().foreach(g=>println(s"group: ${g}"))
//
//      }



//
//      matched.iterator().foreach(r =>{
//        println(s"# ${r}, ${r.getCharOffsets},${r.getChunkOffsets},${r.getAnnotation}")
//        val ann = r.getAnnotation
//
//        for (token <- tokens.iterator()) {
//          // this is the text of the token
//          val word = token.get(classOf[TextAnnotation])
//          // this is the POS tag of the token
//          val pos = token.get(classOf[PartOfSpeechAnnotation])
//          // this is the NER label of the token
//          val ne = token.get(classOf[NamedEntityTagAnnotation])
//          val lemma = token.get(classOf[LemmaAnnotation])
//          //println(s"${token} lemma is " + lemma)
//          val ner = token.get(classOf[NamedEntityTagAnnotation ])
//          println(s"### ${word}  ${ner}")
//        }
//
//
//      })


    }

    // This is the coreference link graph
    // Each chain stores a set of mentions that link to each other,
    // along with a method for getting the most representative mention
    // Both sentence and token offsets start at 1!
    //val graph  = document.get(classOf[CorefChainAnnotation])

  }

}
