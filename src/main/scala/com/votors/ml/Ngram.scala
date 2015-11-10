package com.votors.ml

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
 */
case class Ngram () {
  val WinLen = 10

  var id = 0                // identify of this gram
  var normText = ""         // final format of this gram. after stemmed/variant...
  var N: Int = 0            // number of word in this gram
  var blogId: Int = 0       // blog id
  var sentenceId :Int = 0   // sentence id
  var context = null        // context of this gram

  /**
   * context of a Ngram. such as window, syntax tree
   */
  class Context {
    var window = null
    var syntex = null
  }
}

class Sentence {
  var blogId = 0
  var sentId = 0
  var words: Array[String] = null      // original words in the sentence. nothing is filtered.
  var tokens: Array[String] = null     // Token from openNlp
  var pos :Array[String] = null        // see http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
  var chunk:Array[opennlp.tools.util.Span] = null
  var parser: Parse = null

  override def toString (): String = {
    val parseStr = new StringBuffer()
    if (parser != null)parser.show(parseStr)
    s"${blogId}, ${sentId}\t" +
    words.mkString(" $ ") + "\n" +
      tokens.mkString(" $ ") + "\n" +
      pos.mkString(" $ ") + "\n" +
      chunk.mkString(" $ ") + "\n" +
      parseStr
  }

}
