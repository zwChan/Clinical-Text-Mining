package com.votors.ml

import opennlp.tools.chunker._
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.parser.{ParserFactory, ParserModel}

import scala.collection.mutable.ArrayBuffer
import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.Properties

import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.io.Source
import scala.io.Codec

import org.apache.commons.lang3.StringUtils
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.snowball.SnowballFilter
import org.apache.lucene.util.Version
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.impl.HttpSolrServer
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.common.params.CommonParams
import org.apache.solr.common.params.ModifiableSolrParams
import org.apache.solr.common.util.ContentStreamBase
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._

import opennlp.tools.cmdline.BasicCmdLineTool
import opennlp.tools.cmdline.CLI
import opennlp.tools.cmdline.PerformanceMonitor
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSSample
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.stemmer.PorterStemmer
import opennlp.tools.stemmer.snowball.englishStemmer
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel, WhitespaceTokenizer}
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream
import java.sql.{Statement, Connection, DriverManager, ResultSet}

import org.apache.commons.csv._
import com.votors.ml._

import com.votors.ml
/**
 * Created by Jason on 2015/11/9 0009.
 */
class Nlp {

  def processText(blogId: Int, target: String, ngram: Int = 5) = {
    // process the newline as configuration. 1: replace with space; 2: replace with '.'; 0: do nothing
    val target_tmp = if (Conf.ignoreNewLine == 1) {
      target.replace("\r\n", " ").replace("\r", " ").replace("\n", ". ").replace("\"", "\'")
    } else if (Conf.ignoreNewLine == 2) {
      target.replace("\r\n", ". ").replace("\r", ". ").replace("\n", ". ").replace("\"", "\'")
    } else {
      target.replace("\"", "\'")
    }

    //segment the target into sentences
    var sentId = 0
    val sents = Nlp.getSent(target_tmp)
    sents.filter(_.length>0).map(sent => {
      val sent_tmp = new Sentence()
      sent_tmp.sentId = sentId
      sent_tmp.blogId = blogId
      sent_tmp.words = Nlp.getToken(sent)
      sent_tmp.tokens = sent_tmp.words.map(t => Nlp.normalizeAll(t))
      sent_tmp.pos = Nlp.getPos(sent_tmp.words)
      sent_tmp.chunk = Nlp.getChunk(sent_tmp.words, sent_tmp.pos)
      sent_tmp.parser = Nlp.getParser(sent_tmp.words)

      sentId += 1
      sent_tmp
    })
  }
}

object Nlp {
  final val NotPos = "*"      // the char using indicating this is not a POS tagger, may be a punctuation
  final val Delimiter = ",;:\"!?"   // the char using as delimiter of a Ngram of token, may be ,//;/:/"/!/?
  val punctPattern = Pattern.compile("\\p{Punct}")
  val spacePattern = Pattern.compile("\\s+")
  //val solrServer = new HttpSolrServer(Conf.solrServerUrl)

  //opennlp models path
  val modelRoot = Conf.rootDir + "/data"
  val posModlePath = s"${modelRoot}/en-pos-maxent.bin"
  val sentModlePath = s"${modelRoot}/en-sent.bin"

  //get pos after case/punctuation delete(input)?  // XXX: This may be not a corret approach!
  val sentmodelIn = new FileInputStream(sentModlePath)
  val sentmodel = new SentenceModel(sentmodelIn)
  val sentDetector = new SentenceDetectorME(sentmodel)

  def getSent(phrase: String) = {
    val retSent = sentDetector.sentDetect(phrase)
    trace(DEBUG, retSent.mkString(","))
    retSent
  }

  val tokenModeIn = new FileInputStream(s"${modelRoot}/en-token.bin");
  val model = new TokenizerModel(tokenModeIn);
  val tokenizer = new TokenizerME(model);

  def getToken(str: String) = {
    tokenizer.tokenize(str).filter(_.trim.length>0)
  }

  //get pos after case/punctuation delete(input has done this work)?  // XXX: This may be not a correct approach!
  val posmodelIn = new FileInputStream(posModlePath)
  val posmodel = new POSModel(posmodelIn)
  val postagger = new POSTaggerME(posmodel)
  val allPosTag = postagger.getAllPosTags

  /**
   * If a token have no pos tag, use '*' instead.
   * see: http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html for the meaning of tags.
   * @param phraseNorm
   * @return
   */
  def getPos(phraseNorm: Array[String]) = {
    val retPos = postagger.tag(phraseNorm)
    //trace(DEBUG,phraseNorm + " pos is: " + retPos.mkString(","))
    retPos
  }

  val chunkerModeIn = new FileInputStream(s"${modelRoot}/en-chunker.bin");
  val chunkerModel = new ChunkerModel(chunkerModeIn);
  val chunker = new ChunkerME(chunkerModel);

  def getChunk(words: Array[String], tags: Array[String]) = {
    //chunker.chunk(words,tags)
    chunker.chunkAsSpans(words,tags)
  }

  val parserModeIn = new FileInputStream(s"${modelRoot}/en-parser-chunking.bin");
  val parserModel = new ParserModel(parserModeIn);
  val parser = ParserFactory.create(parserModel)

  def getParser(words: Array[String]) = {
    val topParses = ParserTool.parseLine(words.mkString(" "), parser, 1)
    if (topParses != null) topParses(0) else null
  }

  ///////////// phrase munging methods //////////////

  /**
   * 1. replace all punctuation to space
   * 2. replace all continuous space to one space
   * 3. trim the space at the beginning and the end
   * @param str
   * @return
   */
  def normalizeCasePunct(str: String): String = {
    val str_lps = punctPattern
      .matcher(str.toLowerCase())
      .replaceAll(" ")
    spacePattern.matcher(str_lps).replaceAll(" ").trim()
  }

  def sortWords(str: Array[String])  = {
    str.sortWith(_ < _)
  }

  val stemmer = new PorterStemmer()
  def stemWords(str: String): String = {
    stemmer.stem(str)
  }
  def normalizeAll(str: String, isStem: Boolean=true): String = {
    var ret = normalizeCasePunct(str)
    if (isStem)ret = stemWords(ret)
    ret
  }

  /**
   * for test
   * @param argv
   */
  def main(argv: Array[String]): Unit = {

    Trace.currLevel = DEBUG

    val nlp = new Nlp()
    val sents = nlp.processText(1,"Hi, how are you going? My name is Jason, an (international student).")
    println(sents.mkString("\n#\n"))

    //println(allPosTag.mkString("*"))
  }
}


