package com.votors.common

import java.io._
import java.sql.{Connection, DriverManager, ResultSet, Statement}
import java.util.{Date, Properties, Random}

import edu.stanford.nlp.util
import edu.stanford.nlp.util.IntPair
import org.apache.commons.csv.{CSVFormat, CSVRecord}
import org.apache.commons.lang3.StringUtils
import org.joda.time.{DateTime, Duration, Period}

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.io.Source
import scala.io.Codec
import org.joda.time.format.ISOPeriodFormat

/**
 * Created by chenzhiwei on 2/28/2015.
 */
object Utils extends java.io.Serializable{
  val random = new Random()
  // log2(x+1)
  def log2p1(x:Double)=Math.log(x+1)/Math.log(2)
  def string2Int(s: String, default: Int=0): Int = {
    try{
      s.toFloat.toInt
    }catch{
      case e: Exception => default
    }
  }


  def str2Date(s: String): java.util.Date = {
    val format = new java.text.SimpleDateFormat("yyyyMMddHHmm")
    format.parse(s)
  }
  def str2Ts(s: String): Long = {
    (str2Date(s).getTime / 1000)
  }
  def ts2Str(ts: Long): String = {
    val format = new java.text.SimpleDateFormat("yyyyMMddHHmm")
    format.format(new Date(ts*1000))
  }
  def arrayAddInt(a:Array[Int],b:Array[Int],ret:Array[Int]) = {
    Range(0,a.size).foreach(index =>{
      ret(index) = a(index)+(b(index))
    })
  }
  //class TraceLevel extends Enumeration {}
  object Trace extends Enumeration {
    type TraceLevel = Value
    val DEBUG,INFO,WARN,ERROR,NEVER=Value
    var currLevel = INFO
    //regrex format
    var filter = ""
    def trace(level: TraceLevel, x: Any) = traceFilter(level,null,x)
    def traceFilter(level: TraceLevel, toFilter: String, x: Any) = {
      if (level >= currLevel && (toFilter == null || toFilter.matches(filter)))println(x)
    }
  }
  def bool2Str(b: Boolean) = {
    if (b) "T" else "F"
  }
  def bool2Double(b: Boolean): Double = {
    if (b) 1.0 else 0.0
  }
  def bool2Int(b: Boolean): Int = {
    if (b) 1 else 0
  }
  def writeObjectToFile(filename: String, obj: AnyRef) = {
    // obj must have serialiazable trait
    import java.io._
    val fos = new FileOutputStream(filename)
    val oos = new ObjectOutputStream(fos)
    oos.writeObject(obj)
    oos.close()
  }
  def readObjectFromFile[T](filename: String): T = {
    import java.io._
    val fis = new FileInputStream(filename)
    val ois = new ObjectInputStream(fis)
    val obj = ois.readObject()
    ois.close()
    obj.asInstanceOf[T]
  }

  def readCsvFile(csvFile: String, delimiter:Char=',',separator:Char='\n'):Iterator[CSVRecord] = {
    //get text content from csv file
    val in = new FileReader(csvFile)
    val records = CSVFormat.DEFAULT
      .withRecordSeparator(separator)
      .withDelimiter(delimiter)
      .withSkipHeaderRecord(false)
      .withEscape('\\')
      .parse(in)
      .iterator()
    records
//    val tagList = ListBuffer[String]()
//    // for each row of csv file
//    records.map(rec => {
//      // if current record is the last record, we have to process it now.
//      rec
//    })
  }

  def spanMerge(span:IntPair, v1:Int, v2:Int) = {
    span.set(0,math.min(span.getSource,math.min(v1,v2)))
    span.set(1,math.max(span.getTarget,math.max(v1,v2)))
    span
  }
  def span1based(span: IntPair) = {
    new util.IntPair(span.get(0)+1, span.get(1)+1)
  }

  def strSimilarity(s1:String,s2:String, caseSensitive:Boolean=false):Float = {
    val diff = if (caseSensitive)
      StringUtils.getLevenshteinDistance(s1, s2)
    else
      StringUtils.getLevenshteinDistance(s1.toLowerCase, s2.toLowerCase)
    return 1 - diff.toFloat/math.max(s1.size, s2.size)
  }

}

object Conf extends java.io.Serializable{
  // Load properties. root dir is importance. the configuration and resource files are under the root dir.
  val rootDir = sys.props.get("CTM_ROOT_PATH").getOrElse(sys.env.getOrElse("CTM_ROOT_PATH",println("!!!!!!!!you need to set java property/env CTM_ROOT_PATH!!!!!!!!"))).toString
  if (rootDir.trim.length == 0) {
    println("!!!!!! root dir not found, check java option or env to make sure  CTM_ROOT_PATH is defined.!!!!!!!!!!!!")
    sys.exit(1)
  }
  val prop = new Properties()
  val propDef = new Properties()
  // read current dir configuration first.
  if (new File("default.properties").exists()) {
    println(s"********* default.propertiest is found in current working directory ************")
    prop.load(new FileInputStream("current.properties"))
    propDef.load(new FileInputStream("default.properties"))
  }else {
    println(s"********* default.propertiest is NOT found in current working directory, try ${rootDir}/conf/default.properties ************")
    prop.load(new FileInputStream(s"${rootDir}/conf/current.properties"))
    propDef.load(new FileInputStream(s"${rootDir}/conf/default.properties"))
  }

  val dbUrl = Conf.prop.getOrDefault("blogDbUrl", propDef.get("blogDbUrl")).toString
  val blogTbl = Conf.prop.getOrDefault("blogTbl", propDef.get("blogTbl")).toString
  val blogIdCol = Conf.prop.getOrDefault("blogIdCol", propDef.get("blogIdCol")).toString
  val blogTextCol = Conf.prop.getOrDefault("blogTextCol", propDef.get("blogTextCol")).toString
  val blogLimit = Conf.prop.getOrDefault("blogLimit", propDef.get("blogLimit")).toString.toInt
  val targetTermTbl = Conf.prop.getOrDefault("targetTermTbl", propDef.get("targetTermTbl")).toString.toString
  val targetTermTblDropAndCreate = Conf.prop.getOrDefault("targetTermTblDropAndCreate", propDef.get("targetTermTblDropAndCreate")).toString.toBoolean
  val targetTermUsingSolr = Conf.prop.getOrDefault("targetTermUsingSolr", propDef.get("targetTermUsingSolr")).toString.toBoolean

  val caseFactor = prop.getOrDefault("caseFactor", propDef.get("caseFactor")).toString.toFloat
  val ignoreNewLine = prop.getOrDefault("ignoreNewLine", propDef.get("ignoreNewLine")).toString.toInt
  val partitionTfFilter = prop.getOrDefault("partitionTfFilter", propDef.get("partitionTfFilter")).toString.toInt
  val stag1TfFilter = prop.getOrDefault("stag1TfFilter", propDef.get("stag1TfFilter")).toString.toInt
  val stag1CvalueFilter = prop.getOrDefault("stag1CvalueFilter", propDef.get("stag1CvalueFilter")).toString.toDouble
  val stag2TfFilter = prop.getOrDefault("stag2TfFilter", propDef.get("stag2TfFilter")).toString.toInt
  val stag2CvalueFilter = prop.getOrDefault("stag2CvalueFilter", propDef.get("stag2CvalueFilter")).toString.toDouble
  val stag2UmlsScoreFilter = prop.getOrDefault("stag2UmlsScoreFilter", propDef.get("stag2UmlsScoreFilter")).toString.toDouble
  val stag2ChvScoreFilter = prop.getOrDefault("stag2ChvScoreFilter", propDef.get("stag2ChvScoreFilter")).toString.toDouble

  val topTfNgram = prop.getOrDefault("topTfNgram", propDef.get("topTfNgram")).toString.toInt
  val topCvalueNgram = prop.getOrDefault("topCvalueNgram", propDef.get("topCvalueNgram")).toString.toInt
  val topTfdfNgram = prop.getOrDefault("topTfdfNgram", propDef.get("topTfdfNgram")).toString.toInt

  val sparkMaster = prop.getOrDefault("sparkMaster", propDef.get("sparkMaster")).toString.trim
  val partitionNumber = prop.getOrDefault("partitionNumber", propDef.get("partitionNumber")).toString.toInt

  val solrServerUrl = prop.getOrDefault("solrServerUrl", propDef.get("solrServerUrl")).toString
  val lvgdir = prop.getOrDefault("lvgdir", propDef.get("lvgdir")).toString
  val posInclusive = prop.getOrDefault("posInclusive", propDef.get("posInclusive")).toString.split(" ").filter(_.trim.length>0).mkString(" ")
  val jdbcDriver = prop.getOrDefault("jdbcDriver", propDef.get("jdbcDriver")).toString
  val umlsLikehoodLimit = prop.getOrDefault("umlsLikehoodLimit", propDef.get("umlsLikehoodLimit")).toString.toDouble
  val WinLen = prop.getOrDefault("WinLen", propDef.get("WinLen")).toString.toInt
  val delimiter = prop.getOrDefault("delimiter", propDef.get("delimiter")).toString.trim
  val stopwordMatchType = prop.getOrDefault("stopwordMatchType", propDef.get("stopwordMatchType")).toString.toInt
  val stopwordRegex = prop.getOrDefault("stopwordRegex", propDef.get("stopwordRegex")).toString.trim
  val cuiStringFilterRegex = prop.getOrDefault("cuiStringFilterRegex", propDef.get("cuiStringFilterRegex")).toString.trim
  val posFilterRegex = prop.getOrDefault("posFilterRegex", propDef.get("posFilterRegex")).toString.split(" ").filter(_.trim.length>0)
  val debugFilterNgram = prop.getOrDefault("debugFilterNgram", propDef.get("debugFilterNgram")).toString
  val saveNgram2file = prop.getOrDefault("saveNgram2file", propDef.get("saveNgram2file")).toString.trim

  val ngramSaveFile = prop.getOrDefault("ngramSaveFile", propDef.get("ngramSaveFile")).toString.trim
  val clusteringFromFile = prop.getOrDefault("clusteringFromFile", propDef.get("clusteringFromFile")).toString.toBoolean

  val runKmeans=prop.getOrDefault("runKmeans", propDef.get("runKmeans")).toString.toBoolean
  val k_start=prop.getOrDefault("k_start", propDef.get("k_start")).toString.toInt
  val k_end=prop.getOrDefault("k_end", propDef.get("k_end")).toString.toInt
  val k_step=prop.getOrDefault("k_step", propDef.get("k_step")).toString.toInt
  val maxIterations=prop.getOrDefault("maxIterations", propDef.get("maxIterations")).toString.toInt
  val runs=prop.getOrDefault("runs", propDef.get("runs")).toString.toInt
  val clusterThresholdPt=prop.getOrDefault("clusterThresholdPt", propDef.get("clusterThresholdPt")).toString.toInt
  val clusterThresholSample=prop.getOrDefault("clusterThresholSample", propDef.get("clusterThresholSample")).toString.toInt
  val clusterThresholFactor=prop.getOrDefault("clusterThresholFactor", propDef.get("clusterThresholFactor")).toString.toDouble
  val clusterThresholdLimit=prop.getOrDefault("clusterThresholdLimit", propDef.get("clusterThresholdLimit")).toString.toInt
  val trainNgramCnt=prop.getOrDefault("trainNgramCnt", propDef.get("trainNgramCnt")).toString.toInt
  val testSample=prop.getOrDefault("testSample", propDef.get("testSample")).toString.toInt
  val useFeatures4Train=prop.getOrDefault("useFeatures4Train", propDef.get("useFeatures4Train")).toString.trim.split(",").map(fw=>{
    val afw = fw.split(":").filter(_.trim.length>0)
    (afw(0), if(afw.size<2)1.0 else afw(1).toDouble)
  })
  val useFeatures4Test=prop.getOrDefault("useFeatures4Test", propDef.get("useFeatures4Test")).toString.trim.split(",").map(fw=>{
    val afw = fw.split(":").filter(_.trim.length>0)
    (afw(0), if(afw.size<2)1.0 else afw(1).toDouble)
  })
  //  val useUmlsContextFeature=prop.getOrDefault("useUmlsContextFeature", propDef.get("useUmlsContextFeature")).toString.toBoolean
  val semanticType=prop.getOrDefault("semanticType", propDef.get("semanticType")).toString.trim.split(",")
  val sabFilter=prop.getOrDefault("sabFilter", propDef.get("sabFilter")).toString.trim
  val posInWindown=prop.getOrDefault("posInWindown", propDef.get("posInWindown")).toString.trim
  val normalizeFeature=prop.getOrDefault("normalizeFeature", propDef.get("normalizeFeature")).toString.toBoolean
  val normalize_rescale=prop.getOrDefault("normalize_rescale", propDef.get("normalize_rescale")).toString.toBoolean
  val normalize_standardize=prop.getOrDefault("normalize_standardize", propDef.get("normalize_standardize")).toString.toBoolean
  val normalize_outlier_factor=prop.getOrDefault("normalize_outlier_factor", propDef.get("normalize_outlier_factor")).toString.toDouble
  val outputVectorOnly=prop.getOrDefault("outputVectorOnly", propDef.get("outputVectorOnly")).toString.toBoolean

  val baseLineRank=prop.getOrDefault("baseLineRank", propDef.get("baseLineRank")).toString.toBoolean
  val trainOnlyChv=prop.getOrDefault("trainOnlyChv", propDef.get("trainOnlyChv")).toString.toBoolean
  val runRank=prop.getOrDefault("runRank", propDef.get("runRank")).toString.toBoolean
  val rankGranular=prop.getOrDefault("rankGranular", propDef.get("rankGranular")).toString.toInt
  val rankLevelNumber=prop.getOrDefault("rankLevelNumber", propDef.get("rankLevelNumber")).toString.toInt
  val rankLevelBase=prop.getOrDefault("rankLevelBase", propDef.get("rankLevelBase")).toString.toInt
  val fscoreBeta=prop.getOrDefault("fscoreBeta", propDef.get("fscoreBeta")).toString.toDouble
  val showDetailRankPt=prop.getOrDefault("showDetailRankPt", propDef.get("showDetailRankPt")).toString.toInt
  val rankWithTrainData=prop.getOrDefault("rankWithTrainData", propDef.get("rankWithTrainData")).toString.toBoolean
  val showOrgNgramNum=prop.getOrDefault("showOrgNgramNum", propDef.get("showOrgNgramNum")).toString.toInt
  val showOrgNgramOfN=prop.getOrDefault("showOrgNgramOfN", propDef.get("showOrgNgramOfN")).toString.split(",").map(_.toInt)
  val showOrgNgramOfPosRegex=prop.getOrDefault("showOrgNgramOfPosRegex", propDef.get("showOrgNgramOfPosRegex")).toString.trim
  val showOrgNgramOfTextRegex=prop.getOrDefault("showOrgNgramOfTextRegex", propDef.get("showOrgNgramOfTextRegex")).toString.trim
  val trainedNgramFilterPosRegex=prop.getOrDefault("trainedNgramFilterPosRegex", propDef.get("trainedNgramFilterPosRegex")).toString.trim
  val prefixSuffixUseWindow=prop.getOrDefault("prefixSuffixUseWindow", propDef.get("prefixSuffixUseWindow")).toString.toBoolean
  val bagsOfWord=prop.getOrDefault("bagsOfWord", propDef.get("bagsOfWord")).toString.toBoolean
  val bowUmlsOnly=prop.getOrDefault("bowUmlsOnly", propDef.get("bowUmlsOnly")).toString.toBoolean
  val bowTfFilter=prop.getOrDefault("bowTfFilter", propDef.get("bowTfFilter")).toString.toInt
  val bowDialogSetOne=prop.getOrDefault("bowDialogSetOne", propDef.get("bowDialogSetOne")).toString.toBoolean
  val tfdfLessLog=prop.getOrDefault("tfdfLessLog", propDef.get("tfdfLessLog")).toString.toBoolean
  var bowTopNgram=prop.getOrDefault("bowTopNgram", propDef.get("bowTopNgram")).toString.toInt
  val reviseModel=prop.getOrDefault("reviseModel", propDef.get("reviseModel")).toString.toBoolean
  val clusterScore=prop.getOrDefault("clusterScore", propDef.get("clusterScore")).toString.toBoolean
  var showNgramInCluster=prop.getOrDefault("showNgramInCluster", propDef.get("showNgramInCluster")).toString.toInt
  var pcaDimension=prop.getOrDefault("pcaDimension", propDef.get("pcaDimension")).toString.toFloat
  val sampleRuns=prop.getOrDefault("sampleRuns", propDef.get("sampleRuns")).toString.toInt
  val showSentence=prop.getOrDefault("showSentence", propDef.get("showSentence")).toString.toBoolean
  //val showTfAvgSdInCluster=prop.getOrDefault("showTfAvgSdInCluster", propDef.get("showTfAvgSdInCluster")).toString.toBoolean
  val showTfAvgSdInCluster=true
  val useStanfordNLP=prop.getOrDefault("useStanfordNLP", propDef.get("useStanfordNLP")).toString.toBoolean
  val stanfordTokenizerOption=prop.getOrDefault("stanfordTokenizerOption", propDef.get("stanfordTokenizerOption")).toString
  val stanfordTaggerOption=prop.getOrDefault("stanfordTaggerOption", propDef.get("stanfordTaggerOption")).toString
  val stanfordPatternFile=prop.getOrDefault("stanfordPatternFile", propDef.get("stanfordPatternFile")).toString
  val useDependencyTree=prop.getOrDefault("useDependencyTree", propDef.get("useDependencyTree")).toString.toBoolean
  val analyzNonUmlsTerm=prop.getOrDefault("analyzNonUmlsTerm", propDef.get("analyzNonUmlsTerm")).toString.toBoolean
  var sentenceLenMax=prop.getOrDefault("sentenceLenMax", propDef.get("sentenceLenMax")).toString.toInt
  val partUmlsTermMatch=prop.getOrDefault("partUmlsTermMatch", propDef.get("partUmlsTermMatch")).toString.toBoolean
  val outputNormalizedText=prop.getOrDefault("outputNormalizedText", propDef.get("outputNormalizedText")).toString.toBoolean
  val outputNoCuiSentence=prop.getOrDefault("outputNoCuiSentence", propDef.get("outputNoCuiSentence")).toString.toBoolean
  val MMoptions = prop.getOrDefault("MMoptions", propDef.get("MMoptions")).toString.trim
  val MMhost = prop.getOrDefault("MMhost", propDef.get("MMhost")).toString.trim
  val MMport = prop.getOrDefault("MMport", propDef.get("MMport")).toString.trim
  val MMenable=prop.getOrDefault("MMenable", propDef.get("MMenable")).toString.toBoolean
  val MMscoreThreshold=prop.getOrDefault("MMscoreThreshold", propDef.get("MMscoreThreshold")).toString.toInt

  println("\n\ndefault properties:")
  propDef.keySet().iterator().toArray.map(_.toString).sorted.foreach(key=>println(s"${key}: ${propDef.get(key).toString}"))
  println("\n\nCurrent properties:")
  prop.keySet().iterator().toArray.map(_.toString).sorted.foreach(key=>println(s"${key}: ${prop.get(key).toString}"))
  propDef.keySet().iterator().toArray.map(_.toString).sorted.foreach(key=>{
    if (prop.get(key) != null && !propDef.get(key).equals(prop.get(key))) {
      println(s"!!! You change the configuration: key= [${key.toString}], from default value [${propDef.get(key).toString}] to [${prop.get(key).toString}}] !!!")
    }
  })


}

/**
 * Process on data base, test on Mysql only
 */
class SqlUtils(driverUrl: String) extends java.io.Serializable{
  private var isInitJdbc = false
  private var jdbcConnect: Connection = null
  private var sqlStatement: Statement = null
  def initJdbc() = {
    if (isInitJdbc == false) {
      // Load the driver
      val dirver = classOf[com.mysql.jdbc.Driver]
      // Setup the connection
      jdbcConnect = DriverManager.getConnection(driverUrl)
      // Configure to be Read Only
      sqlStatement = jdbcConnect.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      isInitJdbc = true
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
  def execUpdate (sql: String):Int = {
    if (isInitJdbc == false){
      initJdbc()
    }
    // Execute Query
    val rs = sqlStatement.executeUpdate(sql)
    rs
  }
}

/**
 * Some methods that wrap the method of joda-time (http://www.joda.org/joda-time/)
 * For now, it is use to parse the duration string this is output of Stanford NLP.
 * For duration, we use "2000-01-01T00:00:00" as the start point.
 */
object TimeX {
  val timeParser = ISOPeriodFormat.standard()
  val periodStart = new DateTime("2000-01-01T00:00:00")

  /**
   * Parse a 'Duration' string of stanfordNLP to a standard comparable 'Duration' using joda-time.
   * @param timeStr Duration string from StanfordNLP 'Duration'
   * @return
   */
  def parse(timeStr: String):Duration = {
    try {
      val p = timeParser.parsePeriod(timeStr).toDurationFrom(periodStart)
      p
    }catch {
      case e:Exception => {
        println(s"*** TimeX: parse duration fail. error: ${e}")
        new Duration(-1)
      }
    }
  }
}