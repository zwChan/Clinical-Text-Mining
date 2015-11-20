package com.votors.common

import java.io.{FileOutputStream, ObjectOutputStream, FileInputStream}
import java.lang.Exception
import java.sql.{ResultSet, DriverManager, Statement, Connection}
import java.util.{Properties, Date}


/**
 * Created by chenzhiwei on 2/28/2015.
 */
object Utils extends java.io.Serializable{
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
  val rootDir = sys.env.get("CTM_ROOT_PATH").get
  val prop = new Properties()
  prop.load(new FileInputStream(s"${rootDir}/conf/default.properties"))
  println("Current properties:\n" + prop.toString)

  val caseFactor = prop.get("caseFactor").toString.toFloat
  val ignoreNewLine = prop.get("ignoreNewLine").toString.toInt

  val solrServerUrl = prop.get("solrServerUrl")
  val includePosTagger = prop.get("includePosTagger")
  val lvgdir = prop.get("lvgdir").toString
  val posInclusive = prop.get("posInclusive").toString
  val jdbcDriver = prop.get("jdbcDriver").toString
  val fistStagResultFile = prop.get("fistStagResultFile").toString
  val umlsLikehoodLimit = prop.get("umlsLikehoodLimit").toString.toDouble
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