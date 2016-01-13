package com.votors.common

import java.io.{File, FileOutputStream, ObjectOutputStream, FileInputStream}
import java.lang.Exception
import java.sql.{ResultSet, DriverManager, Statement, Connection}
import java.util.{Random, Properties, Date}


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
  /**
   * if a input item is invalid, return a good enough item instead.
   * @param factor
   */
  def fixInvalid(item:Double, INVALID_NUM: Double, interObj: InterObject, default: Double, factor: Double): Double = {
    if (item == INVALID_NUM) {
      if (! interObj.empty)
        interObj.mean
      else
        default
    } else {
      interObj add item
      item
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


}
/**
  This Class is designed for some "global" variance when we walk items of the RDD. It may not be the best idea for such function.
  e.g. when we run map on RDD[Row], and we want a value of last Row:
  {{{
    var o = new InterObject{value = 1}
    RDD.map{r =>
      val result = if (r._1>o.mean) true else false
      o.add(r._1)
      result
  }}}
 */
class InterObject(factor: Double=0.5, capacity: Int=3) extends java.io.Serializable {
  val valueList = new Array[Double](capacity)
  var counter = 0
  def pos = counter%capacity
  def empty = counter == 0
  def mean = {
    if (empty)
      0.0
    else if (counter <= pos)
      valueList.sum / counter
    else
      valueList.sum / valueList.length
  }
  def add(elm: Double) {
    valueList(pos) = elm
    counter += 1
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
  // read current dir configuration first.
  if (new File("default.properties").exists()) {
    println(s"********* default.propertiest is found in current working directory ************")
    prop.load(new FileInputStream("default.properties"))
  }else {
    println(s"********* default.propertiest is NOT found in current working directory, try ${rootDir}/conf/default.properties ************")
    prop.load(new FileInputStream(s"${rootDir}/conf/default.properties"))
  }
  println("Current properties:\n" + prop.toString)

  val dbUrl = Conf.prop.get("blogDbUrl").toString
  val blogTbl = Conf.prop.get("blogTbl").toString
  val blogIdCol = Conf.prop.get("blogIdCol").toString
  val blogTextCol = Conf.prop.get("blogTextCol").toString
  val blogLimit = Conf.prop.get("blogLimit").toString.toInt

  val caseFactor = prop.get("caseFactor").toString.toFloat
  val ignoreNewLine = prop.get("ignoreNewLine").toString.toInt
  val partitionTfFilter = prop.get("partitionTfFilter").toString.toInt
  val stag1TfFilter = prop.get("stag1TfFilter").toString.toInt
  val stag1CvalueFilter = prop.get("stag1CvalueFilter").toString.toDouble
  val stag2TfFilter = prop.get("stag2TfFilter").toString.toInt
  val stag2CvalueFilter = prop.get("stag2CvalueFilter").toString.toDouble

  val topTfNgram = prop.get("topTfNgram").toString.toInt
  val topCvalueNgram = prop.get("topCvalueNgram").toString.toInt
  val topTfdfNgram = prop.get("topTfdfNgram").toString.toInt

  val sparkMaster = prop.get("sparkMaster").toString.trim
  val partitionNumber = prop.get("partitionNumber").toString.toInt

  val solrServerUrl = prop.get("solrServerUrl").toString
  val lvgdir = prop.get("lvgdir").toString
  val posInclusive = prop.get("posInclusive").toString.split(" ").filter(_.trim.length>0).mkString(" ")
  val jdbcDriver = prop.get("jdbcDriver").toString
  val umlsLikehoodLimit = prop.get("umlsLikehoodLimit").toString.toDouble
  val WinLen = prop.get("WinLen").toString.toInt
  val delimiter = prop.get("delimiter").toString.trim
  val stopwordMatchType = prop.get("stopwordMatchType").toString.toInt
  val stopwordRegex = prop.get("stopwordRegex").toString.trim
  val posFilterRegex = prop.get("posFilterRegex").toString.split(" ").filter(_.trim.length>0)
  val debugFilterNgram = prop.get("debugFilterNgram").toString

  val ngramSaveFile = prop.get("ngramSaveFile").toString.trim
  val clusteringFromFile = prop.get("clusteringFromFile").toString.toBoolean

  val runKmeans=prop.get("runKmeans").toString.toBoolean
  val k_start=prop.get("k_start").toString.toInt
  val k_end=prop.get("k_end").toString.toInt
  val k_step=prop.get("k_step").toString.toInt
  val maxIterations=prop.get("maxIterations").toString.toInt
  val runs=prop.get("runs").toString.toInt
  val clusterThresholdPt=prop.get("clusterThresholdPt").toString.toInt
  val trainNgramCnt=prop.get("trainNgramCnt").toString.toInt
  val testSample=prop.get("testSample").toString.toInt
  val useFeatures4Train=prop.get("useFeatures4Train").toString.trim.split(",").map(fw=>{
    val afw = fw.split(":").filter(_.trim.length>0)
    (afw(0), if(afw.size<2)1.0 else afw(1).toDouble)
  })
  val useFeatures4Test=prop.get("useFeatures4Test").toString.trim.split(",").map(fw=>{
    val afw = fw.split(":").filter(_.trim.length>0)
    (afw(0), if(afw.size<2)1.0 else afw(1).toDouble)
  })
  val useUmlsContextFeature=prop.get("useUmlsContextFeature").toString.toBoolean
  val semanticType=prop.get("semanticType").toString.trim.split(",")
  val posInWindown=prop.get("posInWindown").toString.trim
  val normalizeFeature=prop.get("normalizeFeature").toString.toBoolean

  val baseLineRank=prop.get("baseLineRank").toString.toBoolean
  val trainOnlyChv=prop.get("trainOnlyChv").toString.toBoolean
  val runRank=prop.get("runRank").toString.toBoolean
  val rankGranular=prop.get("rankGranular").toString.toInt
  val rankLevelNumber=prop.get("rankLevelNumber").toString.toInt
  val rankLevelBase=prop.get("rankLevelBase").toString.toInt
  val fscoreBeta=prop.get("fscoreBeta").toString.toDouble
  val showDetailRankPt=prop.get("showDetailRankPt").toString.toInt
  val rankWithTrainData=prop.get("rankWithTrainData").toString.toBoolean
  val showOrgNgramNum=prop.get("showOrgNgramNum").toString.toInt
  val showOrgNgramOfN=prop.get("showOrgNgramOfN").toString.split(",").map(_.toInt)
  val showOrgNgramOfPosRegex=prop.get("showOrgNgramOfPosRegex").toString.trim
  val showOrgNgramOfTextRegex=prop.get("showOrgNgramOfTextRegex").toString.trim
  val trainedNgramFilterPosRegex=prop.get("trainedNgramFilterPosRegex").toString.trim
  val prefixSuffixUseWindow=prop.get("prefixSuffixUseWindow").toString.toBoolean
  val bagsOfWord=prop.get("bagsOfWord").toString.toBoolean
  var bowTopCvalueNgram=prop.get("bowTopCvalueNgram").toString.toInt

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
}