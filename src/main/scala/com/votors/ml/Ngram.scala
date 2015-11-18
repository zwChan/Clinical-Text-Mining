package com.votors.ml

import java.util.concurrent.atomic.AtomicInteger

import com.votors.common.Utils._
import opennlp.tools.parser.Parse

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
import opennlp.tools.tokenize.WhitespaceTokenizer
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream
import java.sql.{Statement, Connection, DriverManager, ResultSet}

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
class Ngram (var text: String) extends java.io.Serializable{
  var id = -1                // identify of this gram  - looks like useless. forget it.
  //var text = ""         // final format of this gram. after stemmed/variant...
  var n: Int = 0            // number of word in this gram
  var hBlogId = new mutable.HashMap[Int, Stat]()       // save the blog id and one of its sentence id.
  var context = new Context()   // context of this gram
  var tfAll:Long = 0     // term frequency of all document
  var df = 0        // document frequency
  var tfdf = 0.0     // tfdf of this gram: tf-idf = log(1+ avg(tf)) * log(1+n(t)/N)
  var cvalue = -1.0    // c-value
  var nestedCnt = 0   // the number of term that contains this gram
  var nestedTf: Long =0     // the total frequency of terms that contains this gram
  var nestTerm = new mutable.HashSet[String]()         // nested term
  def updateBlog(blogId: Int, sentId: Int): Unit = {
    val stat = hBlogId.getOrElseUpdate((blogId),{df += 1; new Stat(blogId)})
    stat.tf += 1
    tfAll += 1
  }


  def + (other: Ngram) = merge(other)
  def merge (other: Ngram): Ngram = {
    val newNgram = new Ngram(this.text)
    newNgram.id = this.id
    if (this.n != other.n) {
      trace(ERROR, s"warn: Not the same n in merge Ngram ${this.text}, ${other.text}, ${this.n}, ${other.n}!")
    }

    if (this.tfAll < other.tfAll)
      newNgram.n = other.n
    else
      newNgram.n = this.n

    traceFilter(INFO,this.text,s"${this.text} ${other.text} ${this.id} ${other.id} tfall ${this.tfAll}, ${other.tfAll}")
    other.hBlogId.foreach(kv => newNgram.hBlogId.put(kv._1,kv._2)) //safe, because blogId could not be the same
    this.hBlogId.foreach(kv => newNgram.hBlogId.put(kv._1,kv._2)) //safe, because blogId could not be the same
    //this.context = ?
    newNgram.tfAll = this.tfAll + other.tfAll
    newNgram.df = this.df + other.df  // this is safe
    newNgram.nestedCnt = this.nestedCnt + other.nestedCnt
    newNgram.nestedTf = this.nestedTf + other.nestedTf
    //this.cvalue = ?
    newNgram
  }

  def procTfdf(docNum: Long): Ngram = {
    this.tfdf = log2(1+(log2(1+1.0*tfAll/docNum) * df))  // supress the affect of tfAll
    // gram.tfidf = Math.log(1+gram.tfAll/gram.hBlogId.size) * Math.log(1+docNum/gram.hBlogId.size)
    //gram.tfdf = Math.sqrt(gram.tfdf)
    this
  }

  def updateAfterReduce(docNum: Long): Ngram = {
    this.procTfdf(docNum)
    this.getCValue()
    this
  }

  def getNestInfo(hNgram: Seq[Ngram]): Ngram = {
    val Ta = new ArrayBuffer[Ngram]()
    hNgram.foreach(ngram =>{
      // if it is nested
      if (this != ngram && ngram.text.matches(".*\\b" + this.text + "\\b.*")) {
        Ta.append(ngram)
      }
    })
    this.nestedCnt = Ta.size
    this.nestedTf = if (this.nestedCnt > 0) Ta.map(_.tfAll).reduce(_+_) else 0
    this.nestTerm ++= Ta.map(_.text)
    traceFilter(INFO,text,s" ${text} nested info: ${nestedCnt}, ${nestedTf}, ${nestTerm.mkString(",")}")
    this
  }

  // calculate the cValue. Since log(1)==0, we should add 1 to the value put into log()
  def getCValue() = {
    this.cvalue = if (this.nestedCnt == 0) {
      log2(this.n+1) * this.tfAll
    }else{
      log2(this.n+1) * (this.tfAll - (this nestedTf)/this.nestedCnt)
    }
    this
  }

  override def toString(): String = {
    toString(false)
  }
  def toString(detail: Boolean): String = {
    f"[${n}]${text}%-12s|tfdf(${tfdf}%.2f,${tfAll},${df}),cvalue(${cvalue}%.2f,${nestedCnt},${nestedTf})," +
      {if (detail) f"blogs:${hBlogId.size}:${hBlogId.mkString(",")}" else ""}
  }
}

/**
 * context of a Ngram. such as window, syntax tree
 */
class Context extends java.io.Serializable{
  var window = null
  var syntex = null
}

class Stat(var blogId: Int = 0, var sentId:Int = 0) extends java.io.Serializable {
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
  var blogId = 0
  var sentId = 0
  var words: Array[String] = null      // original words in the sentence. nothing is filtered.
  var tokens: Array[String] = null     // Token from openNlp
  var tokenSt: Array[TokenState] = null // the special status of the token. e.g. if it is a delimiter
  var pos :Array[String] = null        // see http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
  //var chunk:Array[opennlp.tools.util.Span] = null
  //var parser: Parse = null


  override def toString (): String = {
    val parseStr = new StringBuffer()
    //if (parser != null)parser.show(parseStr)
    s"${blogId}, ${sentId}\t" + "\n" +
      "sentence: " + words.mkString(" ") + "\n" +
      "words: " + words.mkString(" $ ") + "\n" +
      "tokens: " +   tokens.mkString(" $ ") + "\n" +
      "token-State: " + tokenSt.mkString(" $ ") + "\n" +
      "POS: " + pos.mkString(" $ ") + "\n" +
      //"chunk: " + chunk.mkString(" $ ") + "\n" +
      "syntax: " + parseStr
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
  final val WinLen = 10
  final val N = 5
  final val Delimiter = Pattern.compile("[,;\\:\\(\\)\\[\\]\\{\\}\"]+")   // the char using as delimiter of a Ngram of token, may be ,//;/:/"/!/?
  val idCnt = new AtomicInteger()

  @transient val  hSents = new mutable.LinkedHashMap[(Int,Int),Sentence]()  //(blogId, sentId)
  @transient val hNgrams = new mutable.LinkedHashMap[String,Ngram]()       // Ngram.text
  /**
   * get a Ngram from the hashtable, add a new one if not exists
   * @param gram
   * @return
   */
  def getNgram (gram: String, hNgrams: mutable.LinkedHashMap[String,Ngram]): Ngram = {
    hNgrams.getOrElseUpdate(gram, {
      if (gram.equals("diabet"))println(s"create Ngram ${gram}")
      new Ngram(gram)
    })
  }

  def checkNgram(gram: String): Boolean = {
    // check the stop word. if the gram contains any stop word
    if (Nlp.checkStopword(gram) || (gram.contains(" ") && gram.split(" ").filter(Nlp.checkStopword(_)).size > 0))
      false
    else
      true
  }

  def procTfdf(docNum: Long): Unit = {
    hNgrams.foreach(kv => {
      val gram = kv._2
      gram.procTfdf(docNum)
    })
  }

  def main (argv: Array[String]): Unit = {
  }
}