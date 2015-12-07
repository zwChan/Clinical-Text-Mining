package com.votors.common

import java.io.{FileOutputStream, ObjectOutputStream, FileInputStream}
import java.lang.Exception
import java.sql.{ResultSet, DriverManager, Statement, Connection}
import java.util.{Random, Properties, Date}


/**
 * Created by chenzhiwei on 2/28/2015.
 */
object Utils extends java.io.Serializable{
  val random = new Random()
  def log2(x:Double)=Math.log(x)/Math.log(2)
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
  // Load properties
  val rootDir = sys.props.get("CTM_ROOT_PATH").getOrElse(sys.env.getOrElse("CTM_ROOT_PATH",println("!!!!!!!!you need to set env CTM_ROOT_PATH!!!!!!!!"))).toString
  val prop = new Properties()
  prop.load(new FileInputStream(s"${rootDir}/conf/default.properties"))
  println("Current properties:\n" + prop.toString)

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
  val stopwordRegex = prop.get("stopwordRegex").toString.trim
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
  val useFeatures=prop.get("useFeatures").toString.trim.split(",")

  val runPredict=prop.get("runPredict").toString.toBoolean
  val trainOnlyChv=prop.get("trainOnlyChv").toString.toBoolean
  val runRank=prop.get("runRank").toString.toBoolean
  val rankGranular=prop.get("rankGranular").toString.toInt
  val fscoreBeta=prop.get("fscoreBeta").toString.toDouble
  val showDetailRankPt=prop.get("showDetailRankPt").toString.toInt
  val rankWithTrainData=prop.get("rankWithTrainData").toString.toBoolean

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