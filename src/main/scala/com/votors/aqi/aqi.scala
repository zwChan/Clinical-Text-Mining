/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.votors.aqi

import java.util.Date
import com.votors.common.InterObject
import org.apache.spark.{SparkConf, SparkContext}
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._
import org.apache.spark.sql.{SQLContext,DataFrame}

//import org.apache.spark.sql.SQLContext


/*
  The main class of AQI-Seeker.
 */
case class Aqi(sc: SparkContext, sqlContext: SQLContext, DataFilesOrPath: String, aqiFilesOrPath: String) extends java.io.Serializable {
  private def loader = new LoadData()
  val originalRdd = loader.loadOrigina.cache()
  private var originalRddTemp = originalRdd  //Backup the original data RDD for shiftDataTime(offset)
  val aqiRdd = loader.loadAqi.cache()
  var originDataTableName = "origin"
  var aqiDataTableName    = "aqi"
  import sqlContext.implicits._

  private var isTableCreated = false
  def createTable() {
    if (!isTableCreated) {
      isTableCreated = true
      trace(DEBUG, "originalRddTemp table take 10 items: " + originalRddTemp.take(10).mkString(","))
      originalRddTemp.toDF.registerTempTable(originDataTableName)
      trace(INFO,originalRddTemp.toDF.schema.treeString)

      trace(DEBUG,"aqiRdd table take 10 items: " + aqiRdd.take(10).mkString(","))
      aqiRdd.toDF.registerTempTable(aqiDataTableName)
      aqiRdd.toDF.schema.fieldNames.foreach(trace(INFO,_))
    }
  }

  /**
   * Shift the value of 'ts' in 'origin' table, so that we can evaluate the correlation
   * between 'aqi' and history 'origin' data.
   * @param offsetHours
   */
  def shiftDataTime(offsetHours: Int): Unit = if (offsetHours != 0) {
    trace(INFO,f"Shift the data timeStamp to and offset ${offsetHours} hour(s)")
    originalRddTemp = originalRdd.map(r => {
      OriginData(r.stationId,r.ts + 3600 * offsetHours,r.windDir,r.windSpd,r.cloudHigh,r.visby,r.temp,r.dewpt,r.remarks)
    })
    isTableCreated = false
    createTable()
  }
  /**
   * Execute a sql statement, using spark SQL
   *
   * @param sql
   * @param limit default limit number of output if no 'limit' in @sql
   * @return
   */
  def sql(sql: String, limit: Int=1000) = {
    createTable()
    val sqlTemp = if (! sql.contains("limit") && limit > 0) sql + " limit " + limit else sql
    sqlContext.sql(sqlTemp)
  }

  /**
   * Load weather information, and fix the invalid field
   */
  class LoadData extends java.io.Serializable {
    def loadOrigina = {
      val DATA_POS_MAX=1007
      val DATA_POS_ID = 0
      val DATA_POS_DATE = 2
      val DATA_POS_HRMN = 3
      val DATA_POS_WINDDIR = 7
      val DATA_POS_WINDSPD = 10
      val DATA_POS_CLDHIGH = 12
      val DATA_POS_VISBY = 16
      val DATA_POS_TEMP = 20
      val DATA_POS_DEWPT = 22
      val DATA_POS_MARKS = DATA_POS_MAX-1

      val dataItems = sc.textFile(DataFilesOrPath, 3).flatMap(_.split("\n")).map(_.split(",")).filter(i => i.length >= DATA_POS_MAX && i(2).length == 8)
      //trace(DEBUG,"dataItems: " + dataItems.take(10).mkString(", "))
      val dataRdd = dataItems.map(i => {
        if (i.length > DATA_POS_MAX)
          OriginData(i(DATA_POS_ID), str2Ts(i(DATA_POS_DATE) + i(DATA_POS_HRMN)),
            string2Int(i(DATA_POS_WINDDIR),Aqi.INVALID_NUM), string2Int(i(DATA_POS_WINDSPD),Aqi.INVALID_NUM),
            string2Int(i(DATA_POS_CLDHIGH),Aqi.INVALID_NUM), string2Int(i(DATA_POS_VISBY),Aqi.INVALID_NUM),
            string2Int(i(DATA_POS_TEMP),Aqi.INVALID_NUM), string2Int(i(DATA_POS_DEWPT),Aqi.INVALID_NUM),
            i(DATA_POS_MARKS).split(";")(0)).normalize()
        else
          OriginData(Aqi.INVALID_STR,0,Aqi.INVALID_NUM,Aqi.INVALID_NUM,Aqi.INVALID_NUM,Aqi.INVALID_NUM,Aqi.INVALID_NUM,Aqi.INVALID_NUM,Aqi.INVALID_STR)
        }).filter(_.ts != 0)

      // fix the invalid field value
      val dataRddNew = dataRdd.groupBy(_.stationId).map(g =>{
        val interObjWindDir = new InterObject()
        val interObjWindSpd = new InterObject()
        val interObjcloudHigh = new InterObject()
        val interObjvisby = new InterObject()
        val interObjtemp = new InterObject()
        val interObjdewpt = new InterObject()
        //XXX: need sort first?  maybe.
        /** get the data from the same station, then short by time, and fix the invalid value using the mean of values before the invalid value*/
        g._2.toSeq.sortBy(_.ts).map(r => {
          OriginData(r.stationId, r.ts,
            fixInvalid(r.windDir,Aqi.INVALID_NUM,interObjWindDir,0,0).toInt,
            fixInvalid(r.windSpd,Aqi.INVALID_NUM,interObjWindSpd,0,0).toInt,
            fixInvalid(r.cloudHigh,Aqi.INVALID_NUM,interObjcloudHigh,0,0).toInt,
            fixInvalid(r.visby,Aqi.INVALID_NUM,interObjvisby,0,0).toInt,
            fixInvalid(r.temp,Aqi.INVALID_NUM,interObjtemp,0,0).toInt,
            fixInvalid(r.dewpt,Aqi.INVALID_NUM,interObjdewpt,0,0).toInt,
            r.remarks
          )
        })
      }).flatMap(x=>x)
      dataRddNew
    }

    /**
     * Load aqi data from hdfs, and fix the field with invalid value
     *
     * @return
     */
    def loadAqi = {
      val aqiItems = sc.textFile(aqiFilesOrPath, 3).flatMap(_.split("\n")).map(_.split(",")).filter(_.length >= 10)
      //trace(DEBUG,"aqiItems: " + aqiItems.take(10).mkString(","))
      val aqiRdd = aqiItems.filter(i => i.length>7 && i(2).length>13).map(i => {
        AqiData(i(0),
          str2Ts(i(2).substring(0, 4) + i(2).substring(5, 7) + i(2).substring(8, 10) + i(2).substring(11, 13) + i(2).substring(14, 16)),
          string2Int(i(7),Aqi.INVALID_NUM)).normalize()
      })

      val interObjMain = new InterObject()
      val aqiRddNew = aqiRdd.map(r => {
        AqiData(r.cityName,r.ts,fixInvalid(r.aqi, Aqi.INVALID_NUM,interObjMain,0,0).toInt)
      })
      aqiRddNew
    }
  }

  /**
   * A wrap method that support to pick data using spark-sql, then evaluate the correlations with adding a time offset
   * to the data's timestamp field. Finally, it show the result with the %table format on Zeppelin.
   * @param aqi
   * @param sql
   * @param start the start of time offset
   * @param end the end of time offset
   * @param step the step between the start and end of time offset
   */
  def aqiCorrs(aqi: Aqi, sql: String, start: Int=0, end: Int=1, step: Int=1): Unit = {
    val res = Range(start, end, step).map(off => {
      aqi.shiftDataTime(off)
      val combindRdd = aqi.sql(sql, -1)
      if (combindRdd.count() > 0) {
        trace(DEBUG, "combindRdd: " + combindRdd.take(10).mkString("\n"))
        val cr = new Correlation(sc, sqlContext, combindRdd)
        val result = cr.corrs(offset = off)
        result
      } else {
        trace(WARN,f"No data is found at sql=${sql}, offset=${off}")
        Nil
      }
    }).flatMap(r => r)
    showCorrsFields(res)
  }

  /**show the result with the %table format on Zeppelin.*/
  def showCorrsFields(crTables: Seq[CrTable], mainField: String="aqi", factor: Int=100): Unit = {
    // get offset number
    val buffTitle = "%table offset\t" + crTables.filter(r => r.x == mainField && r.flag == 0).map(_.y).distinct.mkString("\t")
    println(buffTitle)
    var buffValue = ""
    crTables.filter(r => r.x == mainField && r.flag == 0).groupBy(_.offset).toSeq.sortBy(_._1)
      .foreach(g => {
      buffValue += g._1 + "\t"
      buffValue += g._2.map(r => (r.cr * factor).toInt).toSeq.mkString("\t")
      println(buffValue)
      buffValue = ""
    })
  }


}

object Aqi {
  val INVALID_NUM = -999
  val INVALID_STR = INVALID_NUM.toString()
  def main(args: Array[String]) {
    if (args.length <= 2){
      println("You should input option: original-file aqi-file!")
      return
    }

    // init spark
    val startTime = new Date()
    val sc = new SparkContext(new SparkConf().setAppName("AQI seeker"))
    val sqlContext = new SQLContext(sc)

    //init aqi
    val aqi = new Aqi(sc, sqlContext,args(0), args(1))
    aqi.originDataTableName ="origin"
    aqi.aqiDataTableName    ="aqi"

    aqi.aqiCorrs(aqi,"select origin.ts,aqi,temp,dewpt,visby,windSpd,cloudHigh,windDir from origin, aqi where origin.ts=aqi.ts order by origin.ts")

    val train = new Train(aqi)

    return
    /**
    // shift the time of data.
    aqi.shiftDataTime(string2Int(args(2)))
    //got the data we interested in
    val combindRdd = aqi.sql("select origin.ts,aqi,temp,dewpt,visby,windSpd,cloudHigh,windDir from origin, aqi where origin.ts=aqi.ts order by origin.ts",-1)
    trace(DEBUG, "Show 10 item of the sql result item in the schemaRDD:")
    trace(DEBUG, combindRdd.take(10).mkString("\n"))

    //evaluate the correlation
    val cr = new Correlation(sc,sqlContext,combindRdd)
    val result = cr.corrs()
    println(result.mkString("\n"))
    println(result(2).xRdd.count())
    println(result(2))
    aqi.showCorrsFields(result)
    //println(result(2).xRdd.collect().mkString("\t"))
    //println(result(2).yRdd.collect().mkString("\t"))
    //println(result(3).yRdd.collect().mkString("\t"))
    //println(result(4).yRdd.collect().mkString("\t"))
    //println(result(5).yRdd.collect().mkString("\t"))

    /*val retOffset = cr.corrsOfOffset(result,10)
    println(result.mkString("\n"))
    println(result(2).xRdd.count())
    println(result(2))
    println(result(2).xRdd.collect().mkString("\t"))
    println(result(2).yRdd.collect().mkString("\t"))
    */
    val res = cr.corrsAll("aqi"::"temp"::"dewpt"::"visby"::"cloudHigh"::"windSpd"::"windDir"::Nil)
    println(res.mkString("\n"))
    aqi.showCorrsFields(res)

    return
    trace(INFO,"select count(*) from origin limit 10")
    trace(INFO,aqi.sql("select * from origin").collect().mkString(","))
    trace(INFO,"select * from aqi limit 10")
    trace(INFO,aqi.sql("select count(*) from aqi").collect().mkString(","))

    println("*******result is ******************")
    //System.out.println("unsorted: " + dataItems.take(10).mkString(", "))
    val endTime = new Date()
    System.out.println("### used time: "+(endTime.getTime()-startTime.getTime())+" ###")
      */
  }
}
