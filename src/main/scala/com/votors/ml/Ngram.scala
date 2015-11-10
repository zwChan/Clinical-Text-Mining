package com.votors.ml

import java.util.concurrent.atomic.AtomicInteger

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
class Ngram (var text: String) {
  var id = Ngram.idCnt.getAndAdd(1)                // identify of this gram
  //var text = ""         // final format of this gram. after stemmed/variant...
  var n: Int = 0            // number of word in this gram
  var hBlogId = new mutable.HashMap[Int, Stat]()       // save the blog id and one of its sentence id.
  var context = new Context()   // context of this gram
  var tfAll = 0     // term frequency of all document
  var df = 0        // document frequency

  def updateBlog(blogId: Int, sentId: Int): Unit = {
    val stat = hBlogId.getOrElseUpdate((blogId),{df += 1; new Stat(blogId)})
    stat.tf += 1
    tfAll += 1

  }
  /**
   * context of a Ngram. such as window, syntax tree
   */
  class Context {
    var window = null
    var syntex = null
  }

  class Stat(var blogId: Int = 0, var sentId:Int = 0) {
     //sentId: Only one sentenid will keep, for debug
    var tf = 0    // term frequency

    override def toString(): String = {
      s"tf:${tf}"
    }
  }

  override def toString(): String = {
    s"[${n}]${text}|id:${id},tfAll:${tfAll},df:${df},blogs:${hBlogId.toString()}"
  }
}

class TokenState {
  var delimiter = false
  override def toString(): String = {
    s"${if(delimiter)"d" else ""}"
  }
}
class Sentence {
  var blogId = 0
  var sentId = 0
  var words: Array[String] = null      // original words in the sentence. nothing is filtered.
  var tokens: Array[String] = null     // Token from openNlp
  var tokenSt: Array[TokenState] = null // the special status of the token. e.g. if it is a delimiter
  var pos :Array[String] = null        // see http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
  var chunk:Array[opennlp.tools.util.Span] = null
  var parser: Parse = null


  override def toString (): String = {
    val parseStr = new StringBuffer()
    if (parser != null)parser.show(parseStr)
    s"${blogId}, ${sentId}\t" + "\n" +
    "words: " + words.mkString(" $ ") + "\n" +
      "tokens: " +   tokens.mkString(" $ ") + "\n" +
      "token-State: " + tokenSt.mkString(" $ ") + "\n" +
      "POS: " + pos.mkString(" $ ") + "\n" +
      "chunk: " + chunk.mkString(" $ ") + "\n" +
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

  val hSents = new mutable.LinkedHashMap[(Int,Int),Sentence]()  //(blogId, sentId)
  val hNgrams = new mutable.LinkedHashMap[String,Ngram]()       // Ngram.text

  /**
   * get a Ngram from the hashtable, add a new one if not exists
   * @param gram
   * @return
   */
  def getNgram (gram: String): Ngram = {
    hNgrams.getOrElseUpdate(gram, new Ngram(gram))
  }

  def checkNgram(gram: String): Boolean = {
    // check the stop word
    return true
  }

  def main (argv: Array[String]): Unit = {
  }
}