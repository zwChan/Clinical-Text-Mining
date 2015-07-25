package com.votors.common

import java.util.Date


/**
 * Created by chenzhiwei on 2/28/2015.
 */
object Utils extends java.io.Serializable{
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
    def trace(level: TraceLevel, x: Any) = if (level >= currLevel)println(x)

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