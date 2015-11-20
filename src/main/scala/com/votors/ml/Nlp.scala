package com.votors.ml

import java.util
import java.util.concurrent.atomic.AtomicInteger

import opennlp.tools.chunker._
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.parser.{ParserFactory, ParserModel}
import org.apache.spark.broadcast.Broadcast

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
  final val WinLen = 10       // windown lenght for calculate ngram contex
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
    postagger.tag(phraseNorm).map(Nlp.posTransform(_))
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
      sent_tmp.Pos = Nlp.getPos(sent_tmp.words)
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
            if (pos + n < sent.tokens.length && !hitDelimiter) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                if (sent.tokens(pos + n).length > 0 && Ngram.checkNgram(gram_text, sent,pos,pos+n+1)) {
                  // check if the gram is valid. e.g. stop words
                  val gram = Ngram.getNgram(gram_text, hNgrams)
                  // some info have to update when the gram created
                  if (gram.id < 0) {
                    gram.id = gramId.getAndAdd(1)
                    gram.n = n + 1
                    gram.updateOnCreated(sent, pos, pos + n + 1)
                  }
                  // on create or found it again.
                  gram.updateOnHit(sent)
                  if (gram.tfAll == 1) {
                    traceFilter(INFO, gram.text, s"Creating gram ${gram}, sent:${sent}")
                  } else {
                    traceFilter(INFO, gram.text, s"Updating gram ${gram}, sent:${sent}")
                  }
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

  def generateNgramStage2(sentence: Seq[Sentence],
                    gramId: AtomicInteger,
                    hNgrams: mutable.LinkedHashMap[String,Ngram],
                    firstStageNgram: Broadcast[mutable.HashMap[String, Ngram]]=null,
                    ngram: Int = Ngram.N
                    ): Unit = {
    val hPreNgram =  firstStageNgram.value
    //println(s"generateNgramStage2, pre gram # ${hPreNgram.size}")
    sentence.foreach(sent => {
      // process ngram
      var pos = 0
      val gramInSent = new ArrayBuffer[(Int,Ngram,Ngram)]()
      //val grams = new ArrayBuffer[Ngram]()
      // for each sentence
      while (pos < sent.tokens.length) {
        if (sent.tokens(pos).length > 0) {
          // the start token should not be blank
          // for each stat position, get the ngram
          var hitDelimiter = false  // the ngram should stop at delimiter, e.g. comma.
          Range(0, ngram).foreach(n => {
            if (pos + n < sent.tokens.length && !hitDelimiter) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                val pre_gram = hPreNgram.getOrElse(gram_text,null)
                if ( (pre_gram != null) ) {
                  //in stage 2, we can directly use result of stage 1
                  val gram = Ngram.getNgram(gram_text, hNgrams)
                  // some info have to update when the gram created
                  if (gram.id < 0) {
                    gram.id = gramId.getAndAdd(1)
                    gram.n = n + 1
                    gram.updateOnCreated(sent, pos, pos + n + 1)
                    gram.getInfoFromPrevious(pre_gram)
                  }
                  // on create or found it again.
                  gram.updateOnHit(sent,hPreNgram)
                  traceFilter(INFO, gram.text, s"Updating gram ${gram}, sent:${sent}")
                  gramInSent.append((pos,gram,pre_gram))
                }
              } else {
                hitDelimiter = true
              }
            }
          })

        }
        pos += 1
      }
      updateContex(sent,gramInSent)
    })
  }

  def updateContex(sent:Sentence, gramInSent: ArrayBuffer[(Int,Ngram,Ngram)]): Unit = {
    // the 'for' is roughly search for a window,
    // and the (e._1>s._1 && (e._1-s._1+s._2.n)>Nlp.WinLen) will finally determin the actually window.
    //for (win_center <- 0 to gramInSent.size; win_walker <-(win_center-Nlp.WinLen) to (win_center+Nlp.WinLen) if (win_walker>=0 && win_walker < gramInSent.size) ) {
    for (start <- Range(0,gramInSent.size); end <- Range(0,gramInSent.size) if (start != end) ) {
      val c = gramInSent(start)   // center gram in the windows
      val w = gramInSent(end)  // grams walking around the center-gram in the range of window
      val cGram = c._2      // the center gram, which is to be updated
      val wPreGram = w._3  // result of stage 1 for walker gram

      // update in the sentence
      if (wPreGram.umlsScore._2 >Conf.umlsLikehoodLimit) {
        // score of chv
        cGram.context.sent_chvCnt += 1
        cGram.context.sent_umlsCnt += 1
      } else if (wPreGram.umlsScore._1 >Conf.umlsLikehoodLimit) {
        //score of umls
        cGram.context.sent_umlsCnt += 1
      }

      // update in the window
      if ((w._1+w._2.n+Nlp.WinLen < c._1) || (c._1+c._2.n+Nlp.WinLen)<w._1) {
        if (wPreGram.umlsScore._2 >Conf.umlsLikehoodLimit) {
          // score of chv
          cGram.context.win_chvCnt += 1
          cGram.context.win_umlsCnt += 1
        } else if (wPreGram.umlsScore._1 >Conf.umlsLikehoodLimit) {
          //score of umls
          cGram.context.win_umlsCnt += 1
        }
      }
    }
  }


  /**
   * When the gram is create, we get some useful info from the sentence.
   * gram is sentence.tokens[start, end)
   * pos tag see: http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
   * 6.	  IN	Preposition or subordinating conjunction
   * 7.	  JJ	Adjective
   * 8.	  JJR	Adjective, comparative
   * 9.	  JJS	Adjective, superlative
   * 12.	NN	Noun, singular or mass
   * 13.	NNS	Noun, plural
   * 14.	NNP	Proper noun, singular
   * 15.	NNPS	Proper noun, plural
   */
  def posTransform(pos: String) = {
    /**
    0. Noun, basic pattern, name N
    1. Noun+Noun, named NN
    2. (Adj|Noun)+Noun, named ANN
    3. ((Adj|Noun)+|((Adj|Noun)?(NounPrep)?)(Adj|Noun)?)Noun, named ANAN
      */
    val nounPos = Conf.posInclusive.split(" ")
    if (nounPos.contains(pos))
      "N" // noun
    else if (pos == "JJ" || pos == "JJR" || pos == "JJS")
      "A" // adjective
    else if (pos == "IN")
      "P" // preposition or a conjunction that introduces a subordinate clause, e.g., although, because.
    else if (pos == "RB"|pos == "RBR"|pos == "RBS")
      "R" // Adverb
    else if (pos == "VB"|pos == "VBD"|pos == "VBG"|pos=="VBN"|pos=="VBP"|pos=="VBZ")
      "V" // verb
    else if (pos == "TO")
      "T" // to
    else if (pos == "CC")
      "C" // a conjunction placed between words, phrases, clauses, or sentences of equal rank, e.g., and, but, or.
    else
      "O" // others
  }

  /**
   * preprocessing the blog text. e.g. punctuation,
   * @param blogId
   * @param text
   * @return
   */
  def textPreprocess(blogId: Int, text: String) = {
    val ret = text.replaceAll("([~`=+<>,:|;/\"\\[\\]\\(\\)\\{\\}\\.!\\?\\|\\\\])"," $1 ").replaceAll("\\s+"," ")
    (blogId, ret)
  }
  /**
   * for test
   * @param argv
   */
  def main(argv: Array[String]): Unit = {

    Trace.currLevel = DEBUG
//    val lvg =  new Lvg()
//    val ret = lvg.getNormTerm("glasses")
//    println(s"lvg out put ${ret}")
//
    def textPreprocess(blogId: Int, text: String) = {
      val ret = text.replaceAll("([:|;\"\\[\\]\\(\\)\\{\\}\\.!\\?/\\\\])"," $1 ").replaceAll("\\s+"," ")
      (blogId, ret)
    }
    println(textPreprocess(0,"I'm so (happy); ..."))

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


