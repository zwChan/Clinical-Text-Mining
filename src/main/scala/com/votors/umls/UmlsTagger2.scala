package com.votors.umls

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringReader
import java.util.regex.Pattern

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

import org.apache.commons.lang3.StringUtils
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.util.Version
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.impl.HttpSolrServer
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.common.params.CommonParams
import org.apache.solr.common.params.ModifiableSolrParams
import org.apache.solr.common.util.ContentStreamBase

class UmlsTagger2(val solrServerUrl: String) {

  val punctPattern = Pattern.compile("\\p{Punct}")
  val spacePattern = Pattern.compile("\\s+")

  case class Suggestion(val score: Float,
                        val descr: String, val cui: String, val aui: String)

  val solrServer = new HttpSolrServer(solrServerUrl)

  def buildIndexJson(inputFile: File,
                     outputFile: File): Unit = {
    val writer = new PrintWriter(new FileWriter(outputFile))
    writer.println("[")
    var i = 0
    Source.fromFile(inputFile)
      .getLines()
      .foreach(line => {
      val Array(cui, aui, str) = line
        .replace("\",\"", "\t")
        .replaceAll("\"", "")
        .replaceAll("\\\\", "")
        .split("\t")
      val strNorm = normalizeCasePunct(str)
      val strSorted = sortWords(strNorm)
      val strStemmed = stemWords(strNorm)
      val obuf = new StringBuilder()
      if (i > 0) obuf.append(",")
      obuf.append("{")
        .append("\"id\":").append(i).append(",")
        .append("\"cui\":\"").append(cui).append("\",")
        .append("\"aui\":\"").append(aui).append("\",")
        .append("\"descr\":\"").append(str).append("\",")
        .append("\"descr_norm\":\"").append(strNorm).append("\",")
        .append("\"descr_sorted\":\"").append(strSorted).append("\",")
        .append("\"desc_stemmed\":\"").append(strStemmed).append("\"")
        .append("}")
      writer.println(obuf.toString)
      i += 1
    })
    writer.println("]")
    writer.flush()
    writer.close()
  }

  def annotateConcepts(phrase: String):
  List[Suggestion] = {
    // check for full match
    val suggestions = ArrayBuffer[Suggestion]()
    select(phrase) match {
      case Some(suggestion) => suggestions += suggestion
      case None => tag(phrase) match {
        case Some(subSuggs) => suggestions ++= subSuggs
        case None => {}
      }
    }
    suggestions.toList
  }

  ///////////// phrase munging methods //////////////

  def normalizeCasePunct(str: String): String = {
    val str_lps = punctPattern
      .matcher(str.toLowerCase())
      .replaceAll(" ")
    spacePattern.matcher(str_lps).replaceAll(" ")
  }

  def sortWords(str: String): String = {
    val words = str.split(" ")
    words.sortWith(_ < _).mkString(" ")
  }

  def stemWords(str: String): String = {
    val stemmedWords = ArrayBuffer[String]()
    val tokenStream = getAnalyzer().tokenStream(
      "str_stemmed", new StringReader(str))
    val ctattr = tokenStream.addAttribute(
      classOf[CharTermAttribute])
    tokenStream.reset()
    while (tokenStream.incrementToken()) {
      stemmedWords += ctattr.toString()
    }
    stemmedWords.mkString(" ")
  }

  def getAnalyzer(): Analyzer = {
    new StandardAnalyzer(Version.LUCENE_46)
  }

  ///////////////// solr search methods //////////////

  def select(phrase: String): Option[Suggestion] = {
    val phraseNorm = normalizeCasePunct(phrase)
    val phraseSorted = sortWords(phraseNorm)
    val phraseStemmed = stemWords(phraseNorm)
    // construct query
    val query = """descr:"%s" descr_norm:"%s" descr_sorted:"%s" descr_stemmed:"%s""""
      .format(phrase, phraseNorm, phraseSorted, phraseStemmed)
    val params = new ModifiableSolrParams()
    params.add(CommonParams.Q, query)
    params.add(CommonParams.ROWS, String.valueOf(1))
    params.add(CommonParams.FL, "*,score")
    val rsp = solrServer.query(params)
    val results = rsp.getResults()
    if (results.getNumFound() > 0L) {
      val sdoc = results.get(0)
      val descr = sdoc.getFieldValue("descr").asInstanceOf[String]
      val cui = sdoc.getFieldValue("cui").asInstanceOf[String]
      val aui = sdoc.getFieldValue("aui").asInstanceOf[String]
      val score = computeScore(descr,
        List(phrase, phraseNorm, phraseSorted, phraseStemmed))
      Some(Suggestion(score, descr, cui, aui))
    } else None
  }

  def tag(phrase: String): Option[List[Suggestion]] = {
    val phraseNorm = normalizeCasePunct(phrase)
    val params = new ModifiableSolrParams()
    params.add("overlaps", "LONGEST_DOMINANT_RIGHT")
    val req = new ContentStreamUpdateRequest("")
    req.addContentStream(new ContentStreamBase.StringStream(phrase))
    req.setMethod(SolrRequest.METHOD.POST)
    req.setPath("/tag")
    req.setParams(params)
    val rsp = req.process(solrServer)
    val results = rsp.getResponse()
      .get("matchingDocs")
      .asInstanceOf[SolrDocumentList]
    val nwordsInPhrase = phraseNorm.split(" ").length.toFloat
    val suggestions = results.iterator().map(sdoc => {
      val descr = sdoc.getFieldValue("descr").asInstanceOf[String]
      val cui = sdoc.getFieldValue("cui").asInstanceOf[String]
      val aui = sdoc.getFieldValue("aui").asInstanceOf[String]
      val nWordsInDescr = descr.split(" ").length.toFloat
      val descrNorm = normalizeCasePunct(descr)
      val descrSorted = sortWords(descrNorm)
      val descrStemmed = stemWords(descrNorm)
      val nwords = descrNorm.split(" ").length.toFloat
      val score = (nwords / nwordsInPhrase) *
        computeScore(descr,
          List(descr, descrNorm, descrSorted, descrStemmed))
      Suggestion(score, descr, cui, aui)
    })
      .toList
      .groupBy(_.cui) // dedup by cui
      .map(_._2.toList.head)
      .toList
      .sortWith((a,b) => a.score > b.score) // sort by score
    Some(suggestions)
  }

  def computeScore(s: String,
                   candidates: List[String]): Float = {
    val levels = List(100.0F, 75.0F, 50.0F, 25.0F)
    val candLevels = candidates.zip(levels).toMap
    val topscore = candidates.map(candidate => {
      val maxlen = Math.max(candidate.length(), s.length()).toFloat
      val dist = StringUtils.getLevenshteinDistance(candidate, s).toFloat
      (candidate, 1.0F - (dist / maxlen))
    })
      .sortWith((a, b) => a._2 > b._2)
      .head
    val level = candLevels.getOrElse(topscore._1, 0.0F)
    level * topscore._2
  }

  //////////////// misc methods ////////////////

  def formatSuggestion(sugg: Suggestion): String = {
    "[%6.2f%%] (%s) (%s) %s"
      .format(sugg.score, sugg.cui, sugg.aui, sugg.descr)
  }
}