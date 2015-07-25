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
import org.apache.spark.{SparkConf, SparkContext}
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._
import com.votors.aqi.Aqi._

/*
  Origin data for SQLSchema
 */
case class OriginData(stationId: String, ts: Long,
                      windDir: Int, windSpd: Int,
                      cloudHigh: Int, visby: Int,
                      temp: Int, dewpt: Int,
                      remarks: String
                       ) extends java.io.Serializable {
  def normalize() = {
    val windDirTemp = if (windDir == 999)   INVALID_NUM else windDir
    val windSpdTemp = if (windSpd == 999.9 || windSpd == 999)   INVALID_NUM else windSpd  //not 999
    val cloudHighTemp = if (cloudHigh == 99999)   INVALID_NUM else cloudHigh
    val visbyTemp = if (visby == 9999)   INVALID_NUM else visby //not 999999
    val tempTemp = if (temp == 9999)   INVALID_NUM else temp
    val dewptTemp = if (dewpt == 9999)   INVALID_NUM else dewpt

    OriginData(stationId,ts,windDirTemp,windSpdTemp,cloudHighTemp,visbyTemp,tempTemp,dewptTemp,remarks)
  }
  override def toString(): String = {
    f"\n${stationId}, ${ts2Str(ts)}, ${windDir}%.1f, ${windSpd}%.1f, ${cloudHigh}%.1f, ${visby}%.1f, ${temp}%.1f, ${dewpt}%.1f, ${remarks}"
  }

  def toList(): List[Double] = {
    temp.toDouble::dewpt.toDouble::windSpd.toDouble::windDir.toDouble::cloudHigh.toDouble::visby.toDouble::Nil
  }
}

case class AqiData(cityName: String, ts: Long, aqi: Int) extends java.io.Serializable {
  def normalize() = {
    val aqiTemp = if (aqi == -999) INVALID_NUM else aqi

    AqiData(cityName,ts,aqiTemp)
  }
  override def toString: String = {
    f"\n${cityName}, ${ts2Str(ts)}, ${aqi}%.1f"
  }

  def toList: List[Double] = {
    ts::aqi.toDouble::Nil
  }
}
