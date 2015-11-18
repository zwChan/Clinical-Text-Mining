package com.votors.ml

import java.util
import java.util.concurrent.atomic.AtomicInteger

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

import gov.nih.nlm.nls.lvg.Api.LvgCmdApi

import com.votors.common._

import scala.reflect.io.File

/**
 * Created by Jason on 2015/11/9 0009.
 */
class Nlp {

}

class Lvg(lvgdir: String=Conf.lvgdir) {
  val properties = new util.Hashtable[String, String]()
  properties.put("LVG_DIR", lvgdir)
  println("lvgdir: " + Conf.lvgdir)
  //option see: http://lexsrv3.nlm.nih.gov/LexSysGroup/Projects/lvg/2015/docs/designDoc/UDF/flow/index.html
  val lvgApi = if (File(Conf.lvgdir + "data\\config\\lvg.properties").exists) {
    new LvgCmdApi("-f:g:o:t:l:B", Conf.lvgdir + "data\\config\\lvg.properties", properties)
  }else{
    println("!!!!!lvg config not find, maybe something wrong, use PorterStemmer!!!!!!")
    null
  }

  def getNormTerm(term: String) = {
    var ret = ""
    if (lvgApi != null) {
      val outputFromLvg = lvgApi.MutateToString(term)
      val arrrayRet = outputFromLvg.split("\\|")
      if (arrrayRet.size >= 2)
        ret = arrrayRet(1)
    }
    ret
  }
}

object Nlp {
  final val NotPos = "*"      // the char using indicating this is not a POS tagger, may be a punctuation
  //final val TokenEnd = "$$"
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

  val chunkerModeIn = new FileInputStream(s"${modelRoot}/en-chunker.bin")
  val chunkerModel = new ChunkerModel(chunkerModeIn)
  val chunker = new ChunkerME(chunkerModel)

  def getChunk(words: Array[String], tags: Array[String]) = {
    //chunker.chunk(words,tags)
    chunker.chunkAsSpans(words,tags)
  }

  val parserModeIn = new FileInputStream(s"${modelRoot}/en-parser-chunking.bin")
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
  def normalizeCasePunct(str: String, tokenSt: TokenState): String = {
    var ret = Ngram.Delimiter.matcher(str.toLowerCase()).replaceAll(" ").trim()
    if (ret.length > 0) {
      val str_lps = punctPattern.matcher(ret).replaceAll(" ")
       ret = spacePattern.matcher(str_lps).replaceAll(" ").trim()
    } else {
      // 'str' only contains delimiter, use a special string to indicate it.
      if (tokenSt != null)
        tokenSt.delimiter = true
    }
    ret
  }

  def sortWords(str: Array[String], tokenSt: TokenState)  = {
    str.sortWith(_ < _)
  }
  val lvg = new Lvg()
  def stemWords(str: String,tokenSt: TokenState): String = {
    val ret = lvg.getNormTerm(str)
    if (ret.length > 0) {
      ret
    }else{
      stemWordsPoter(str,null)
    }

  }
  val stemmer = new PorterStemmer()
  def stemWordsPoter(str: String,tokenSt: TokenState): String = {
    stemmer.stem(str)
  }
  def normalizeAll(str: String, tokenSt: TokenState=null, isStem: Boolean=true): String = {
    var ret = normalizeCasePunct(str,tokenSt)
    if (isStem)ret = stemWords(ret,tokenSt)
    ret
  }

  val stopwords = new mutable.HashSet[String]()
  for (line <- scala.io.Source.fromFile(s"${modelRoot}/stopwords.txt").getLines()) {
    if (line.trim.length > 0)
      stopwords.add(Nlp.getToken(line).map(t =>{Nlp.normalizeAll(t)}).mkString(" ").trim)
  }

  def checkStopword(str: String):Boolean ={
    stopwords.contains(str)
  }

  /**
   *
   * @param blogId blogId
   * @param text the content of the blog
   */
  def generateSentence(blogId: Int, text: String, hSents: mutable.LinkedHashMap[(Int,Int),Sentence] = null): Array[Sentence] = {
    // process the newline as configuration. 1: replace with space; 2: replace with '.'; 0: do nothing
    //    val text_tmp = if (Conf.ignoreNewLine == 1) {
    //      target.replace("\r\n", " ").replace("\r", " ").replace("\n", ". ").replace("\"", "\'")
    //    } else if (Conf.ignoreNewLine == 2) {
    //      target.replace("\r\n", ". ").replace("\r", ". ").replace("\n", ". ").replace("\"", "\'")
    //    } else {
    //      target.replace("\"", "\'")
    //    }
    val text_tmp = text
    //segment the target into sentences
    var sentId = 0
    val sents = Nlp.getSent(text_tmp)
    sents.filter(_.length > 0).map(sent => {
      val sent_tmp = new Sentence()
      sent_tmp.sentId = sentId
      sent_tmp.blogId = blogId
      sent_tmp.words = Nlp.getToken(sent)
      sent_tmp.tokenSt = Array.fill(sent_tmp.words.length)(new TokenState())
      var tokenIdx = -1
      sent_tmp.tokens = sent_tmp.words.map(t => {
        tokenIdx += 1; Nlp.normalizeAll(t, sent_tmp.tokenSt(tokenIdx))
      })
      sent_tmp.pos = Nlp.getPos(sent_tmp.words)
      //sent_tmp.chunk = Nlp.getChunk(sent_tmp.words, sent_tmp.pos)
      //sent_tmp.parser = Nlp.getParser(sent_tmp.words)

      if (hSents != null) hSents.put((blogId, sentId), sent_tmp)
      sentId += 1
      sent_tmp
    })
  }
  def generateNgram(sentence: Seq[Sentence], gramId: AtomicInteger, hNgrams: mutable.LinkedHashMap[String,Ngram], ngram: Int = Ngram.N): Unit = {
    sentence.foreach(sent => {
      // process ngram
      var pos = 0
      //val grams = new ArrayBuffer[Ngram]()
      // for each sentence
      while (pos < sent.tokens.length) {
        if (sent.tokens(pos).length > 0) {
          // the start token should not be blank
          // for each stat position, get the ngram
          var hitDelimiter = false  // the ngram should stop at delimiter, e.g. comma.
          Range(0, ngram).foreach(n => {
            if (pos + n < sent.tokens.length && hitDelimiter == false) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                if (sent.tokens(pos + n).length > 0 && Ngram.checkNgram(gram_text)) {
                  // check if the gram is valid. e.g. stop words
                  val gram = Ngram.getNgram(gram_text, hNgrams)
                  if (gram.id <0) gram.id = gramId.getAndAdd(1)
                  gram.updateBlog(sent.blogId, sent.sentId)
                  gram.n = n+1
                }
              } else {
                hitDelimiter = true
              }
            }
          })
        }
        pos += 1
      }
    })
  }


  /**
   * for test
   * @param argv
   */
  def main(argv: Array[String]): Unit = {

    Trace.currLevel = DEBUG
    val lvg =  new Lvg()
    val ret = lvg.getNormTerm("glasses")
    println(s"lvg out put ${ret}")

//    val s1 = Nlp.generateSentence(1,"""Hi, how are you going? My name is Jason, an (international student). Jason! jason? jason;jason:jason.""",Ngram.hSents)
//    val s2 = Nlp.generateSentence(2,"""jason is study in fsu for more then 3 month. His Chinese name is zc..""",Ngram.hSents)
//    val s3 = Nlp.generateSentence(3,"""As a international student, Jason have to study English hard..""",Ngram.hSents)
//    val gramId = new AtomicInteger()
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    gramId.set(0)
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    gramId.set(0)
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    Ngram.hSents.foreach(sent =>{
//      println("#sentence# " + sent._2.toString())
//    })
//
//    Ngram.hNgrams.foreach(gram =>{
//      println("[gram]:" + gram._2.toString())
//    })
    //println(allPosTag.mkString("*"))
  }
}


