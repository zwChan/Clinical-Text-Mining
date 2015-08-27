package com.votors.umls

import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.Properties

import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.Range
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.io.Codec

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
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._

import opennlp.tools.cmdline.BasicCmdLineTool
import opennlp.tools.cmdline.CLI
import opennlp.tools.cmdline.PerformanceMonitor
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSSample
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.tokenize.WhitespaceTokenizer
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream

import java.sql.{Statement, Connection, DriverManager, ResultSet}

/**
 * Suggestion is the result of a match.
 *
 * @param score
 * @param descr
 * @param cui
 * @param aui
 */
case class Suggestion(val score: Float,
                      val descr: String, val cui: String, val aui: String, val sab: String) {
  override
  def toString(): String = {
      "[%6.2f%%] (%s) (%s) (%s) %s".format(score, cui, aui, sab, descr)
  }
}

/**
 * Main entry of the project.
 * Currently, the @select method is the most important function.
 *
 * @param solrServerUrl: the solr server url.
 * @param rootDir the dir of model files for opennlp
 */
class UmlsTagger2(val solrServerUrl: String, rootDir:String) {

  val punctPattern = Pattern.compile("\\p{Punct}")
  val spacePattern = Pattern.compile("\\s+")
  val solrServer = new HttpSolrServer(solrServerUrl)

  //opennlp models path
  val modelRoot = rootDir + "/data"
  val posModlePath = s"${modelRoot}/en-pos-maxent.bin"
  val sentModlePath = s"${modelRoot}/en-sent.bin"

  // Load properties
  val prop = new Properties()
  prop.load(new FileInputStream(s"${rootDir}/conf/default.properties"))
  println("Current properties:\n" + prop.toString)

  /**
   *  load SemGroups.txt. The format of the file is "Semantic Group Abbrev|Semantic Group Name|TUI|Full Semantic Type Name"
   *  see: http://metamap.nlm.nih.gov/SemanticTypesAndGroups.shtml
  */
  val tuiMap = new mutable.HashMap[String, String]()
  Source.fromFile(s"${rootDir}/data/SemGroups.txt")
    .getLines()
    .foreach(line => {
      val tokens = line.split('|')
      if (tokens.length>=4) {
        tuiMap.put(tokens(2), tokens(1))
      }
  })
  //println(tuiMap.mkString(";"))

  // debug level, default is INFO
  //Trace.currLevel = WARN

  /**
   * Normalization:
   * - step 1: case and punctuation delete
   * - step 2: stem
   * - step 3: sort
   *
   * @param inputFile
   * @param outputFile
   */
  def buildIndexJson(inputFile: File,
                     outputFile: File): Unit = {

    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    var writer = new PrintWriter(new FileWriter(outputFile))
    writer.println("[")
    var i = 0
    var cntByte = 0
    var cntFile = 1
    var newFile = true
    Source.fromFile(inputFile)
      .getLines()
      .foreach(line => {
      val Array(cui, aui, sab, str) = line
        .replace("\",\"", "\t")
        .replaceAll("\"", "")
        .replaceAll("\\\\", "")
        .split("\t")
      // the string in a Glossary is always considered as a sentence. No sentence detecting step.
      val strNorm = normalizeCasePunct(str)
      //val strPos = getPos(strNorm.split("")).sorted.mkString("+")
      val strStemmed = stemWords(strNorm)
      val strSorted = sortWords(strNorm)
      val obuf = new StringBuilder()
      if (newFile == false) obuf.append(",")
      newFile = false
      obuf.append("{")
        .append("\"id\":").append(i).append(",")
        .append("\"cui\":\"").append(cui).append("\",")
        .append("\"aui\":\"").append(aui).append("\",")
        .append("\"sab\":\"").append(sab).append("\",")
        .append("\"descr\":\"").append(str).append("\",")
        .append("\"descr_norm\":\"").append(strNorm).append("\",")
        .append("\"descr_sorted\":\"").append(strSorted).append("\",")
        .append("\"desc_stemmed\":\"").append(strStemmed).append("\"")
        .append("}")
      writer.println(obuf.toString)
      i += 1
      cntByte += obuf.toString().length

      //Avoid the file is too big. it will fail to import to solr if the file bigger than 2.5G
      if (cntByte > 1*1024*1024*1024) {
        // close the old writer
        println(cntByte)
        writer.println("]")
        writer.flush()
        writer.close()
        // create a new writer
        cntByte = 0
        cntFile += 1
        newFile = true
        writer = new PrintWriter(new FileWriter(outputFile + s"($cntFile)"))
        writer.println("[")
      }

    })
    writer.println("]")
    writer.flush()
    writer.close()
  }

  /**
   * Not for now.
   *
   * @param phrase
   * @return
   */
  def annotateConcepts(phrase: String):
  List[Suggestion] = {
    // check for full match
    val suggestions = ArrayBuffer[Suggestion]()
    select(phrase) match {
      case suggestion: Array[Suggestion] => suggestions ++= suggestion
      case _ => tag(phrase) match {
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
    spacePattern.matcher(str_lps).replaceAll(" ").trim()
  }

  def sortWords(str: String): String = {
    val words = str.split(" ")
    words.sortWith(_ < _).mkString(" ")
  }

  def stemWords(str: String): String = {
    val stemmedWords = ArrayBuffer[String]()
    val tokenStream = getAnalyzer().tokenStream(
      "str_stemmed", new StringReader(str))

    trace(DEBUG,s"before stem:${str}")

    val ctattr = tokenStream.addAttribute(
      classOf[CharTermAttribute])
    tokenStream.reset()
    while (tokenStream.incrementToken()) {
      stemmedWords += ctattr.toString()
    }

    trace(DEBUG,s"after stem :${stemmedWords.mkString(" ")}")
    stemmedWords.mkString(" ")
  }

  //get pos after case/punctuation delete(input has done this work)?  // XXX: This may be not a correct approach!
  val posmodelIn = new FileInputStream(posModlePath)
  val posmodel = new POSModel(posmodelIn)
  val postagger = new POSTaggerME(posmodel)
  def getPos(phraseNorm: Array[String]) = {
    val retPos = postagger.tag(phraseNorm)
    //trace(DEBUG,phraseNorm + " pos is: " + retPos.mkString(","))
    retPos
  }
  //get pos after case/punctuation delete(input)?  // XXX: This may be not a corret approach!
  val sentmodelIn = new FileInputStream(sentModlePath)
  val sentmodel = new SentenceModel(sentmodelIn)
  val sentDetector = new SentenceDetectorME(sentmodel)
  def getSent(phrase: String) = {
    val retSent = sentDetector.sentDetect(phrase)
    trace(DEBUG,retSent.mkString(","))
    retSent
  }

  def getAnalyzer(): Analyzer = {
    new StandardAnalyzer(Version.LUCENE_46)
  }

  ///////////////// solr search methods //////////////
  /**
   * Select all the result in solr.
   * The input string has to be normalized(case/puntuation delete, stemed, sorted), then search in
   * solr. All the result from solr will be evaluated a score. The higher the score, the closer the
   * result relative to the input.
   *
   * @param phrase the words to be search in solr
   * @return all the suggestion result in an array, sorted by score.
   */
  def select(phrase: String): Array[Suggestion] = {
    val phraseNorm = normalizeCasePunct(phrase)
    val queryPos = getPos(phraseNorm.split(" ")).sorted.mkString("+")
    val phraseStemmed = stemWords(phraseNorm)
    val phraseSorted = sortWords(phraseNorm)

    // construct query. boost different score to stress fields.
    val query = """descr:"%s"^10 descr_norm:"%s"^5 descr_sorted:"%s" descr_stemmed:"%s"^2"""
      .format(phrase, phraseNorm, phraseSorted, phraseStemmed)
    val params = new ModifiableSolrParams()
    params.add(CommonParams.Q, query)
    params.add(CommonParams.ROWS, String.valueOf(10000))
    params.add(CommonParams.FL, "*,score")
    val rsp = solrServer.query(params)
    val results = rsp.getResults()
    if (results.getNumFound() > 0L) {
      trace(INFO,s"select get ${results.getNumFound()} result for [${phrase}].")
      val ret = results.iterator().map(sdoc =>{
        val descr = sdoc.getFieldValue("descr").asInstanceOf[String]
        val cui = sdoc.getFieldValue("cui").asInstanceOf[String]
        val aui = sdoc.getFieldValue("aui").asInstanceOf[String]
        val sab = sdoc.getFieldValue("sab").asInstanceOf[String]
        val descrNorm = normalizeCasePunct(descr)
        val resultPos = getPos(descrNorm.split(" ")).sorted.mkString("+")
        val score = computeScore(descr,
          List(phrase, phraseNorm, phraseStemmed, phraseSorted, queryPos,resultPos))
        Suggestion(score, descr, cui, aui,sab)
      }).toArray.sortBy(s => 1 - s.score) // Decrease
      ret
    } else Array()
  }

  /**
   * Not for now.
   *
   * @param phrase the input string to be queried in solr
   * @return An array of Suggestion sorted by its score.
   */
  def tag(phrase: String): Option[List[Suggestion]] = {
    val phraseNorm = normalizeCasePunct(phrase)
    val inputPos = getPos(phraseNorm.split(" ")).sorted.mkString("+")
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
      val sab = sdoc.getFieldValue("sab").asInstanceOf[String]
      val nWordsInDescr = descr.split(" ").length.toFloat
      val descrNorm = normalizeCasePunct(descr)
      val resultPos = getPos(descrNorm.split(" ")).sorted.mkString("+")
      val descrSorted = sortWords(descrNorm)
      val descrStemmed = stemWords(descrNorm)
      val nwords = descrNorm.split(" ").length.toFloat
      val score = (nwords / nwordsInPhrase) *
        computeScore(descr,
          List(descr, descrNorm, descrStemmed, descrSorted, inputPos, resultPos))
      Suggestion(score, descr, cui, aui, sab)
    })
      .toList
      .groupBy(_.cui) // dedup by cui
      .map(_._2.toList.head)
      .toList
      .sortWith((a,b) => a.score > b.score) // sort by score
    Some(suggestions)
  }

  /**
   * The score is affect by 3 facts:
   * 1. a level or a base score: the more steps of transforming the string, the lower base score.
   * 2. a distance: for now it is getLevenshteinDistance.
   * 3. pos discount: if the pos is not the same, make a discount to the score directly.
   *
   * @param s
   * @param candidates
   * @return
   */
  def computeScore(s: String, candidates: List[String]): Float = {
    trace(DEBUG, s"computeScore(): ${s}, " + candidates.mkString("[",",","]"))
    val levels = List(100.0F, 90.0F, 70.0F, 50.0F, 0f, 0f)
    var candLevels = mutable.HashMap[String,  Float]()
    val posFlag = candidates(5) == candidates(4) //pos

    val candidatesLevel = Array(
      (candidates(0),levels(0)),
      (candidates(1),levels(1)),
      (candidates(2),levels(2)),
      (candidates(3),levels(3))
    )

    val topscore = candidatesLevel.map(cl => {
      val candidate = cl._1
      val level = cl._2
      if (candidate != null) {
        val maxlen = Math.max(candidate.length(), s.length()).toFloat
        val dist = StringUtils.getLevenshteinDistance(candidate, s).toFloat
        (candidate, (1.0F - (dist / maxlen)) * level)
      } else {
        ("", 0.0f)
      }
    })
      .sortWith((a, b) => a._2 > b._2)
      .head
    if (posFlag != true)
      topscore._2 * 0.7f // pos are different, make a 30% discount
    else
      topscore._2
  }

  /////////////////// select for a text file ////////////////////
  /**
   * find terms for dictionary for a text file
   *
   * @param file
   * @param ngram the ngram limited
   */
  def annotateFile(file: String, ngram:Int=5): Unit = {
    val source = Source.fromFile(file, "UTF-8")
    val lineIterator = source.getLines
    lineIterator.foreach(line =>{
      if (line.length>0) {
        val sents = getSent(line)
        sents.foreach(sent => {
          if (sent.length>0)
            annotateSentence(sent,ngram)
        })
      }
    })
  }

  /**
   * select for a sentence.
   *
   * @param sentence
   * @param ngram
   */
  def annotateSentence(sentence: String, ngram:Int=5): Unit = {
    trace(INFO,"\nsentence:" + sentence)
    val sentenceNorm = normalizeCasePunct(sentence)
    //val sentencePosFilter = posFilter(sentenceNorm)
    val tokens = sentenceNorm.split(" ")
    for (n <- Range(ngram,0,-1)) {
      trace(INFO,"  gram:" + n)
      if (tokens.length >= n) {
        for (idx <- 0 to (tokens.length - n)) {
          val gram = tokens.slice(idx,idx+n)
          val pos = getPos(gram)
          if (posContains(gram," NN NNS NNP NNPS ")) {
            select(gram.mkString(" ")) match {
              case suggestion: Array[Suggestion] => {
                suggestion.foreach(s => println("    " + s.toString()))
              }
              case _ => ""
            }
          }
          else {
          ""
          }
        }
      }
    }
  }

  /*the output format for tag parsing*/
  case class TagRow(val blogid: String, val hashTag: String,val umlsFlag: Boolean,
                    val score: Float, val cui: String, val sab: String, val aui: String, val desc: String,
                    val tui: String, val semName: String){
    override def toString(): String = {
      val str = s""""${blogid}","${hashTag}","${if(umlsFlag)'Y' else 'N'}","${score}","${cui}","${sab}","${aui}","${desc}","${tui}","${semName}"\n"""
      trace(INFO, "Get Tag parsing result: " + str)
      str
    }
    def getTitle(): String = {
      """"blogId","hashTage","umlsFlag","score","cui","sab","aui","umlsStr","tui","semName"""" + "\n"
    }
  }
  /**
   * Match some 'tags' to dictionary(e.g. UMLS), and get their semantic type.
   *
   * @param tagFile
   */
  def annotateTag(tagFile: String, outputFile: String): Unit = {
    val source = Source.fromFile(tagFile, "UTF-8")
    var writer = new PrintWriter(new FileWriter(outputFile))
    writer.print(TagRow("","",true,0,"","","","","","").getTitle())
    val lineIterator = source.getLines
    // for each tag, get the UMLS terms
    lineIterator.foreach(line =>{
      if (line.trim().length>0) {
        val tokens = line.split(" ",2)
        if (tokens.length>1) {
          //get all terms from solr
          select(tokens(1).trim) match {
            case suggestions: Array[Suggestion] => {
              // for each UMLS terms, get their TUI from MRSTY table
              if (suggestions.length > 0) {
                suggestions.foreach(suggestion => {
                  //get all tui from mrsty table.
                  val mrsty = getMrsty(suggestion.cui)
                  while (mrsty.next) {
                    //for each TUI, get their semantic type from SemGroups.txt
                    val tui = mrsty.getString("TUI")
                    val sty = tuiMap.get(tui)
                    writer.print(TagRow(tokens(0), tokens(1).trim, true,
                      suggestion.score, suggestion.cui, suggestion.sab, suggestion.aui, suggestion.descr,
                      tui, sty.getOrElse("")))
                  }
                })
              } else {
                writer.print(TagRow(tokens(0), tokens(1).trim, false, 0, "", "", "", "", "", ""))
              }
            }
            case _ => writer.print(TagRow(tokens(0), tokens(1).trim, false,0,"","","","","",""))
          }
        }
      }
    })
    writer.flush()
    writer.close()
  }

  /**
   *
   * @param cui
   * @return
   */
  def getMrsty(cui: String): ResultSet = {
    execQuery(s"select * from mrsty where CUI='${cui}';")
  }

  /**
   * Filer the words in a sentence by POS. Currently only noun is processed.
   * see:
   * http://blog.pengyifan.com/how-to-use-opennlp-to-do-part-of-speech-tagging/
   * http://paula.petcu.tm.ro/init/default/post/opennlp-part-of-speech-tags
   *
   * @param phraseNorm
   */
  def posFilter(phraseNorm: Array[String], filter:String=" NN ") = {
    val retPos = postagger.tag(phraseNorm)
    val retFilter = if (filter.length>0) {
      phraseNorm.filter(item => {
        val flag = retPos(phraseNorm.indexOf(item))
        filter.contains(s" ${flag} ")
      } )
    } else {
      phraseNorm
    }

    trace(DEBUG, phraseNorm.mkString(",") + " pos is: " + retPos.mkString(",") + ", result: " + retFilter.mkString(","))

    retFilter
  }

  /**
   * If the sentence contains all the pos in $filter, return true, else false
   * @param phraseNorm the sentence to evaluate
   * @param filter the pos as a eligible criteria
   * @return true if eligible, else false
   */
  def posContains(phraseNorm: Array[String], filter:String="NN"):Boolean = {
    val retPos = postagger.tag(phraseNorm)
    trace(DEBUG, phraseNorm.mkString(",") + " pos is: " + retPos.mkString(","))

    var hit = false
    filter.split(" ").filter(_.length>0).foreach(p =>{
      if (retPos.indexOf(p) >= 0)
        hit = true
    })

    hit
  }

  private var isInitJdbc = false
  private var jdbcConnect: Connection = null
  private var sqlStatement: Statement = null
  def initJdbc() = {
    if (isInitJdbc == false) {
      isInitJdbc = true
      // Database Config
      val conn_str = prop.get("jdbcDriver").toString
      println("jdbcDrive is: " + conn_str)
      // Load the driver
      val dirver = classOf[com.mysql.jdbc.Driver]
      // Setup the connection
      jdbcConnect = DriverManager.getConnection(conn_str)
      // Configure to be Read Only
      sqlStatement = jdbcConnect.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    }
  }

  def jdbcClose() = {
    if (isInitJdbc) {
      isInitJdbc = false
      jdbcConnect.close()
    }
  }
  def execQuery (sql: String):ResultSet = {
    if (isInitJdbc == false){
      initJdbc()
    }
    // Execute Query
    val rs = sqlStatement.executeQuery(sql)
    rs
  }
}