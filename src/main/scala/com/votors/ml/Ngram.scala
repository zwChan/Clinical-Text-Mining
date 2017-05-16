package com.votors.ml

import java.util.concurrent.atomic.AtomicInteger

import com.votors.common.{Conf, Utils}
import com.votors.common.Utils._
import com.votors.umls.UmlsTagger2
import opennlp.tools.parser.Parse
import org.apache.spark.broadcast.Broadcast

import scala.StringBuilder
import scala.collection.mutable.ArrayBuffer
import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.Properties

import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
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
import opennlp.tools.tokenize.WhitespaceTokenizer
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream
import java.sql.{Connection, DriverManager, ResultSet, Statement}

import org.apache.commons.csv._

/**
 * Created by Jason on 2015/11/9 0009.
 */

/**
 * Ngram is the most basic format of a 'term', but a Ngram may be not a 'term'
 *
 * Ngram delimiter: ${Ngram.Delimiter}, Ngram searching should stop at the delimiter
 * the first and the last token can not be punctuation or blank
 *
 */
@SerialVersionUID(-4956580178369639181L)
class Ngram (var text: String) extends java.io.Serializable{
  var id = -1                // identify of this gram  - looks like useless. forget it.
  var textOrg=""
  var isTrain: Boolean = false  // if this gram is chosen as trainning data
  //var text = ""         // final format of this gram. after stemmed/variant...
  var n: Int = 0            // number of word in this gram
  @transient
  var hBlogId = new mutable.HashMap[Long, Stat]()       // save the blog id and one of its sentence id.
  var context = new Context()   // ** context of this gram
  var tfAll:Long = 0     // term frequency of all document
  var df = 0        // document frequency
  var tfdf = 0.0     // ** tfdf of this gram: tf-idf = log(1+ avg(tf)) * log(1+n(t)/N)
  var cvalue = -1.0    // ** c-value
  //var nestedCnt = 0   // the number of term that contains this gram.
  var nestedTf: Long =0     // the total frequency of terms that contains this gram
  //@transient
  var nestTerm = new mutable.HashSet[String]()         // nested term
  var umlsScore = (-1.0,-1.0,"","") // ** (score as UMLS, score as CHV, CUI of the UMLS term, CUI of the CHV term, UMLS string)
  var posString = ""    // **
  var isPosNN = false  // ** sytax pattern :Noun.*Noun
  var isPosAN = false  // ** sytax pattern : (Adj.*Noun) +Noun
  var isPosPN = false  // ** sytax pattern : Pre.*Noun
  var isPosANPN = false  // ** sytax pattern : ((Adj|Noun) +|((Adj|Noun)?(NounP rep) ?)(Adj|Noun)?)Noun
  var isContainInUmls = false // ** if it contains gram that is found in umls
  var isContainInChv = false // ** if it contains gram that is found in chv
  var stys: Array[Boolean] = null // it is update at stage 2 after reduce, don't have to merge
  var capt_first = 0 // it is update at stage 2
  var capt_all= 0    // it is update at stage 2
  var capt_term = 0  // it is update at stage 2
  var sent: Array[String] = null // original words in the sentence. nothing is filtered. the first occurring sentence only
  var indexOfSent = -1

  /* //XXX: ### !!!! add a file should also check it is processed in method merge !!!!####*/

  def key = Ngram.getKey(text,posString)

  def + (other: Ngram) = merge(other)
  def merge (other: Ngram): Ngram = {
    val newNgram = new Ngram(this.text)
    newNgram.id = this.id
    newNgram.textOrg = this.textOrg
    //if (this.n != other.n)  trace(ERROR, s"warn: Not the same n in merge Ngram ${this.text}, ${other.text}, ${this.n}, ${other.n}!")
    if (this.n < other.n)
      newNgram.n = other.n
    else
      newNgram.n = this.n
    // don't need hBlogId, for saving memory
    //other.hBlogId.foreach(kv => newNgram.hBlogId.put(kv._1,kv._2)) //safe, because blogId could not be the same
    //this.hBlogId.foreach(kv => newNgram.hBlogId.put(kv._1,kv._2)) //safe, because blogId could not be the same
    newNgram.context = this.context + other.context
    newNgram.tfAll = this.tfAll + other.tfAll
    newNgram.df = this.df + other.df  // this is safe
    //newNgram.nestedCnt = this.nestedCnt + other.nestedCnt   // not safe to calculate like this. use set operation
    // tfdf   this is calculated after merge
    // cvalue   this is calculated after merge
    newNgram.nestedTf = this.nestedTf + other.nestedTf
    newNgram.nestTerm ++= this.nestTerm
    newNgram.nestTerm ++= other.nestTerm
    newNgram.umlsScore = this.umlsScore  // this is calculated after merge
    newNgram.posString = this.posString
    newNgram.isPosAN = this.isPosAN
    newNgram.isPosNN = this.isPosNN
    newNgram.isPosANPN = this.isPosANPN
    newNgram.isContainInUmls = this.isContainInUmls || other.isContainInUmls
    newNgram.isContainInChv = this.isContainInChv || other.isContainInChv

    newNgram.stys = this.stys // don't have to merge, update after reduce

    newNgram.capt_first = this.capt_first + other.capt_first
    newNgram.capt_all = this.capt_all + other.capt_all
    newNgram.capt_term = this.capt_term + other.capt_term

    newNgram.sent = this.sent

    traceFilter(INFO,this.text,s"Merge!! ${this.key} ${other.key} ${this.id} ${other.id} tfall ${this.tfAll}, ${other.tfAll}")
    newNgram
  }

  // Some feature may be variant in different occurring sentence of the ngram, we should use the information that we detect the ngram first time.
  def getInfoFromPrevious(other: Ngram) = {
    this.umlsScore = other.umlsScore  // To get this info is expensive, it has to access database
    // pos info get from stage 2 is not better than the stage 1
    this.posString = other.posString
    this.isPosNN = other.isPosNN
    this.isPosAN = other.isPosAN
    this.isPosPN = other.isPosPN
    this.isPosANPN = other.isPosANPN

    this.stys = other.stys
    if(other.textOrg.length>0)this.textOrg=other.textOrg
    if(other.sent!=null){
      this.sent=other.sent
      this.indexOfSent = other.indexOfSent
    }

    if (Conf.bagsOfWord)this.context.wordsInbags = Array.fill(Nlp.wordsInbags.size)(0)
  }

  /**
   * Update info on create or found the gram.
   * @param sent
   * @param firstStageNgram
   */
  def updateOnHit(sent: Sentence, start: Int, end: Int,firstStageNgram: mutable.HashMap[String, Ngram]=null): Unit = {
    val stat = hBlogId.getOrElseUpdate(sent.blogId,{df += 1; new Stat(sent.blogId)})
    stat.tf += 1
    tfAll += 1
    // second stage
    if (firstStageNgram != null) {
      val textOrg = sent.words.slice(start,end).mkString(" ").replaceAll("\\p{Punct}", "")
      if (Ngram.CaptAll.matcher(textOrg).matches()) {
        this.capt_all += 1
        this.capt_first += 1
        this.capt_term += 1
      }else if (Ngram.CaptTerm.matcher(textOrg).matches()) {
        this.capt_first += 1
        this.capt_term += 1
      }else if (Ngram.CaptTerm.matcher(textOrg).matches()) {
        this.capt_first += 1
      }
    }
  }
  /**
    * Just mark a Ngram as 'trainning' status, do not remove the non-training Ngram.
    * @return
    */
  def trainSampleMark():Ngram = {
    if (Conf.testSample>0) {
      // take a random number for each Ngram, if the random number is not in the 'test' percentage, it is a training percentage.
      if (Utils.random.nextInt(100000000) >= Conf.testSample*1000000 && (!Conf.trainOnlyChv || isUmlsTerm(true)))
        isTrain = true
      else
        isTrain = false
    }else{
      if (!Conf.trainOnlyChv || isUmlsTerm(true)) {
        isTrain = true
      }else{
        isTrain = false
      }
    }
    this
  }

  /**
   *
   * @param sentence
   * @param start [start, end)
   * @param end [start, end)
   * @return
   */
  def updateOnCreated(sentence: Sentence, start: Int, end: Int) = {
    textOrg = sentence.words.slice(start,end).mkString(" ")
    sent = sentence.words
    indexOfSent = start
    // update the pos pattern syntax of the gram.
    val gramPos = Array() ++ sentence.Pos.slice(start, end)
    /**
    0. Noun, basic pattern, name N
    1. Noun+Noun, named NN
    2. (Adj|Noun)+Noun, named ANN
    3. ((Adj|Noun)+|((Adj|Noun)?(NounPrep)?)(Adj|Noun)?)Noun, named ANAN
      */
    this.posString = gramPos.mkString("")
    traceFilter(INFO, this.text, s"${this.key} 's Pos string for pattern match is ${posString}")
    if (posString.matches(".*N.*N.*")) {
      this.isPosNN = true
    }
    if (posString.matches(".*A.*N.*")) {
      this.isPosAN = true
    }
    if (posString.matches(".*P.*N.*")) {
      this.isPosPN = true
    }
    if (posString.matches("((A|N)+|((A|N)*(NP)?)(A|N)*)N")) {
      this.isPosANPN = true
    }
    traceFilter(INFO, this.text, s"${this.key} 's Pos string for pattern match is NN=${isPosNN},AN=${isPosAN},PN=${isPosPN},ANPN=${isPosANPN},")
  }


  def procTfdf(docNum: Long): Ngram = {
    if (Conf.tfdfLessLog) {
      this.tfdf = log2p1((1.0 * tfAll / Math.sqrt(docNum)) * df) // supress the affect of tfAll
    }else{
      this.tfdf = log2p1((log2p1(1.0 * tfAll / Math.sqrt(docNum)) * df)) // supress the affect of tfAll
    }
    this
  }


  def getNestInfo(hNgram: Seq[Ngram]): Ngram = {
    val Ta = new ArrayBuffer[Ngram]()
    hNgram.foreach(ngram =>{
      // if it is nested
      if (this.n < ngram.n && ngram.text.matches(".*\\b" + this.text + "\\b.*")) {
        Ta.append(ngram)
        if (this.isUmlsTerm()) ngram.isContainInUmls = true  // 'this' is conained by 'ngram'.
        if (this.isUmlsTerm(true)) ngram.isContainInChv = true
      }
    })
    //this.nestedCnt = Ta.size
    this.nestedTf = if (Ta.size > 0) Ta.map(_.tfAll).reduce(_+_) else 0
    this.nestTerm ++= Ta.map(_.text)
    traceFilter(INFO,text,s"\n**** ${text} nested info: ${Ta.size}, ${nestedTf},terms:: ${nestTerm.mkString(",")},gram: ${Ta.mkString(",")}    *****\n")
    this
  }

  // calculate the cValue. Since log(1)==0, we should add 1 to the value put into log()
  def getCValue() = {
    this.cvalue = if (this.nestTerm.size == 0) {
      log2p1(this.n) * this.tfAll
    }else{
      log2p1(this.n) * (this.tfAll - (this nestedTf)/this.nestTerm.size)
    }

    /**
     *  c-value is negative, this is possible, b/c shorter term may be filter out by syntax filter, but the longer term will not.
     *  That means tfAll may be less than nestedTf, so get a negative c-value.
     *  In this case, it means the shorter term are less possible be a independent term(and, c-value=0 has this meaning too.)..
     */
    if (this.cvalue < 0)
      this.cvalue = 0
    this
  }

  def isUmlsTerm(isChv: Boolean=false): Boolean= {
    if (!isChv && this.umlsScore._1 > Conf.umlsLikehoodLimit)
      true
    else if (isChv && this.umlsScore._2 > Conf.umlsLikehoodLimit)
      true
    else
      false
  }
  def getTypeName(): String = {
    if (isUmlsTerm(true))
      "chv"
    else if (isUmlsTerm(false))
      "umls"
    else
      "other"
  }
  override def toString(): String = {
    toString(if (text.matches(Trace.filter)) true else false)
  }
  def toString(detail: Boolean): String = {
    f"[${n}]${key}%-15s|tfdf(${tfdf}%.2f,${tfAll}%2d,${df}%2d),cvalue(${cvalue}%.2f,${nestedTf}%2d),umls(${umlsScore._1}%.2f,${umlsScore._2}%.2f,${umlsScore._3},${umlsScore._4},${bool2Str(isContainInUmls)},${bool2Str(isContainInChv)}),contex:${this.context}" +
     f"pt:(${posString}:${bool2Str(isPosNN)},${bool2Str(isPosAN)},${isPosPN}${bool2Str(isPosANPN)}),train:${isTrain},capt:(${capt_first},${capt_term},${capt_all}),stys:${if(stys!=null)stys.map(bool2Int(_)).mkString("") else null}, "+
    s"textOrg:${textOrg.replaceAll("\\\"","'")},sent:${if(Conf.showSentence)sent.mkString(" ").replaceAll("\\\"","'") else "" }" +
      {if (detail && hBlogId!=null) f"blogs:${hBlogId.size}:${hBlogId.mkString(",")}" else ""}
  }
  def toStringVector(): String = {
    f"${key}\t${bool2Str(isTrain)}\t${n}\t${tfdf}%.2f\t${tfAll}\t${df}\t${cvalue}%.2f\t${nestedTf}\t${umlsScore._1}%.0f\t${umlsScore._2}%.0f\t${umlsScore._3}\t${umlsScore._4}\t${bool2Str(isContainInUmls)}\t${bool2Str(isContainInChv)}\t${this.context.toStringVector()}" +
      s"\t${posString}\t${bool2Str(isPosNN)}\t${bool2Str(isPosAN)}\t${bool2Str(isPosPN)}\t${bool2Str(isPosANPN)}\t${isTrain}\t${capt_first}\t${capt_term}\t${capt_all}\t${if(stys!=null)stys.map(bool2Int(_)).mkString("") else null}\t"+
    f"${textOrg.replaceAll("\\\"","'")}\t${if(Conf.showSentence)sent.mkString(" ").replaceAll("\\\"","'") else "" }"
  }

}

/**
 * context of a Ngram. such as window, syntax tree
 */
@SerialVersionUID(7038468614128803385L)
class Context extends java.io.Serializable{
  //context in the windown
  var win_umlsCnt = 0   // ** number of umls term in its window
  var win_chvCnt = 0    // ** number of chv term in its window
  //var win_nounCnt = 0   // ** number of noun term in its window
  var win_pos= Array.fill(Conf.posInWindown.length)(0)

  var win_prefix= Array.fill(Nlp.prefixs.length)(0)
  var win_suffix= Array.fill(Nlp.suffixs.length)(0)


  //context in the sentent
  var sent_umlsCnt = 0  // ** number of umls term in its sentence
  var sent_chvCnt = 0   // ** number of chv term in its sentence
  //var sent_nounCnt = 0  // ** number of noun term in its sentence

  //context of other
  var umlsDist = 0       // ** the nearest distance with a umls term
  var chvDist = 0        // ** the nearest distance with a chv term

  // bags of words
  var wordsInbags:Array[Int] = null

  def +(other: Context) = merge(other)
  def merge(other: Context) = {
    val newCx = new Context()
    newCx.win_umlsCnt = this.win_umlsCnt+other.win_umlsCnt
    newCx.win_chvCnt = this.win_chvCnt+other.win_chvCnt
    //newCx.win_nounCnt = this.win_nounCnt+other.win_nounCnt
    newCx.sent_umlsCnt = this.sent_umlsCnt+other.sent_umlsCnt
    newCx.sent_chvCnt = this.sent_chvCnt+other.sent_chvCnt
    newCx.umlsDist = this.umlsDist+other.umlsDist
    newCx.chvDist = this.chvDist+other.chvDist
    arrayAddInt(this.win_pos,other.win_pos,newCx.win_pos)
    arrayAddInt(this.win_prefix,other.win_prefix,newCx.win_prefix)
    arrayAddInt(this.win_suffix,other.win_suffix,newCx.win_suffix)
    if(this.wordsInbags!=null) {
      newCx.wordsInbags = Array.fill(this.wordsInbags.size)(0)
      arrayAddInt(this.wordsInbags,other.wordsInbags,newCx.wordsInbags)
    }

    newCx
  }

  override def  toString() = {
    f"win(${win_umlsCnt},${win_chvCnt}), sent(${sent_umlsCnt},${sent_chvCnt}),dist(${umlsDist},${chvDist}),posWin(${win_pos.mkString(",")}}),"+
      s"pre/suffix:(${win_prefix.filter(_>0).mkString(",")},${win_suffix.filter(_>0).mkString(",")}),bow(${if(wordsInbags!=null)wordsInbags.sum},${if(wordsInbags!=null)wordsInbags.count(_>0)}})"
  }
  def  toStringVector() = {
    s"${win_umlsCnt}\t${win_chvCnt}\t${sent_umlsCnt}\t${sent_chvCnt}\t${umlsDist}\t${chvDist}\t${win_pos.mkString(",")}\t"+
      s"${win_prefix.filter(_>0).mkString(",")}\t${win_suffix.filter(_>0).mkString(",")}\t${if(wordsInbags!=null)wordsInbags.sum else 0}\t${if(wordsInbags!=null)wordsInbags.count(_>0) else 0}"
  }
}

class Stat(var blogId: Long = 0, var sentId:Int = 0) extends java.io.Serializable {
  //sentId: Only one sentenid will keep, for debug
  var tf = 0    // term frequency

  override def toString(): String = {
    s"tf:${tf}"
  }
}
class TokenState extends java.io.Serializable {
  var delimiter = false
  override def toString(): String = {
    s"${if(delimiter)"d" else ""}"
  }
}
class Sentence extends java.io.Serializable {
  var blogId = 0L
  var sentId = 0
  var words: Array[String] = null      // original words in the sentence. nothing is filtered.
  var tokens: Array[String] = null     // Token from openNlp
  var tokenSt: Array[TokenState] = null // the special status of the token. e.g. if it is a delimiter
  var Pos :Array[String] = null        // see http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
  //@transient val ngrams= new ArrayBuffer[Ngram]()
  //var chunk:Array[opennlp.tools.util.Span] = null
  //var parser: Parse = null


  override def toString (): String = {
    //val parseStr = new StringBuffer()
    //if (parser != null)parser.show(parseStr)
    s"${blogId}, ${sentId}\t" + "\n" +
      "sentence: " + words.mkString(" ") + "\n" +
      "words: " + words.mkString(" $ ") + "\n" +
      "tokens: " +   tokens.mkString(" $ ") + "\n" +
      "token-State: " + tokenSt.mkString(" $ ") + "\n" +
      "POS: " + Pos.mkString(" $ ") + "\n" +
      s""
      //"chunk: " + chunk.mkString(" $ ") + "\n" +
      //"syntax: " + parseStr
  }

}

case class KV[K,V](val k:K, val v:V) {
  override def equals(that: Any): Boolean = {
    if (that.isInstanceOf[KV[K,V]]) {
      val other = that.asInstanceOf[KV[K,V]]
      other.k.equals(this.k)
    } else {
      false
    }
  }
  override def hashCode(): Int = {
    k.hashCode()
  }
}

object Ngram {
  final val WinLen = Conf.WinLen       // windown lenght for calculate ngram contex
  final val N = Conf.ngramN
  final val Delimiter = Pattern.compile(Conf.delimiter)   // the char using as delimiter of a Ngram of token, may be ,//;/:/"/!/?
  final val CaptFirst = Pattern.compile("[A-Z].*")  // first char has to be uppercase
  final val CaptAll = Pattern.compile("[^a-z]+")  // no lowercase, may be digital or other characters
  final val CaptTerm = Pattern.compile("^([A-Z][\\S]*\\b\\s*)+$")  // the first char of every word is uppercase
  final val ShowOrgNgramOfPosRegex = Pattern.compile(Conf.showOrgNgramOfPosRegex)   // shown ngram pos tagger filter regex
  final val ShowOrgNgramOfTextRegex = Pattern.compile(Conf.showOrgNgramOfTextRegex)   // shown ngram text filter regex
  final val TrainedNgramFilterPosRegex = Pattern.compile(Conf.trainedNgramFilterPosRegex)   // shown ngram text filter regex

  val idCnt = new AtomicInteger()
  final val serialVersionUID:Long = -4956580178369639181L
  // the empty semantic type array for ngram that is not umls. to reduce memory used
  final val stysEmpty = Array.fill(Conf.semanticType.size)(false)

  @transient val  hSents = new mutable.LinkedHashMap[(Int,Int),Sentence]()  //(blogId, sentId)
  @transient val hNgrams = new mutable.LinkedHashMap[String,Ngram]()       // Ngram.text
  /**
   * get a Ngram from the hashtable, add a new one if not exists
   * @param gram
   * @return
   */
  def getNgram (key:String, gram: String, hNgrams: mutable.LinkedHashMap[String,Ngram]): Ngram = {
    hNgrams.getOrElseUpdate(key, {
      new Ngram(gram)
    })
  }
  def getKey(text:String,posString:String) = s"${text} (${posString})"
  def sortNgramByTfAndKey(tf:Long, key:String) = f"${Long.MaxValue-tf}%16d" + key
  /**
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
   *
   * @param gram
   * @param sentence
   * @param start [start, end)
   * @param end [start, end)
   * @return
   */
  def checkNgram(gram: String, sentence: Sentence, start: Int, end: Int): Boolean = {
    var ret = true
    // check the stop word. if the gram contains any stop word
    if (Nlp.checkStopword(gram,true))
      ret &&= false

    // check if the gram contain at least one noun
    val gramPos = sentence.Pos.slice(start,end).filter(_.length>0).mkString("")
    if(Nlp.checkPosFilter(gramPos))  // if contains noun
      ret &&= false

    traceFilter(INFO,gram,s"${gram} 's check result is ${ret}. pos:${gramPos}, sent:${sentence}")

    ret
  }

  def procTfdf(docNum: Long): Unit = {
    hNgrams.foreach(kv => {
      val gram = kv._2
      gram.procTfdf(docNum)
    })
  }

  /**
   * calculate tfdf, c-value, umls relative
   * @param itr
   * @param docNum
   * @return
   */
  def updateAfterReduce(itr: Iterator[Ngram], docNum: Long, isStage2:Boolean=false) = {
    val s = itr.toSeq
    println(s"grams number after redusce (in this partition, isStage2: ${isStage2}) is  ${s.size}")
    val tagger = new UmlsTagger2()
    s.foreach(gram => {
      gram.procTfdf(docNum)
      gram.getCValue()
      // the following not run at stage 2
      if (!isStage2) {
        val (umlsscore, stysTmp) = tagger.getUmlsScore(gram.text,"fetch")  // do not use 'filter'
        gram.umlsScore = (umlsscore._1,umlsscore._2,if(umlsscore._3!=null)umlsscore._3.cui else "",if (umlsscore._4!=null)umlsscore._4.cui else "")
        if (stysTmp == null)
          gram.stys = Ngram.stysEmpty
        else
          gram.stys = stysTmp
      }
      //println("update " +gram)
      gram
    })

    s.iterator
  }
  def getVectorHead() = {
    "ngram\ttrain\tn\ttfdf\ttf\tdf\tcvalue\tnest\tumls_score\tchv_score\tcui\tcui\tcontain_umls\tcontain_chv\twin_umls\twin_chv\tsent_umls" +
      "\tsent_chv\tumls_dist\tchv_dist\twin_pos\tprefix\tsuffix\tbow_total\tbow_words\tsytax\tnn\tan\tpn\tanpn\tisTrain\tcapt_first\tcapt_term\tcapt_all\tstys\ttext_org\tsentence"
  }
  def main (argv: Array[String]): Unit = {
  }
}