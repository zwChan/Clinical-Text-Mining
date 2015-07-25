package com.votors.aqi

/**
 * Created by chenzhiwei on 3/2/2015.
 */

import com.votors.common.{InterObject}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkContext}
import org.apache.spark.sql.{SQLContext,DataFrame}
import org.apache.spark.mllib.linalg.{_}
import org.apache.spark.mllib.stat.Statistics
import java.util.Date
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._
import com.votors.aqi.Aqi._

/**
 * A class to show the correlations between two fileds
 *
 * @param x String One of the field
 * @param y String The other field
 * @param cr correlation; [-1,1]
 * @param flag is the correlation valid. 0: valid, othes NOT valid
 * @param xRdd
 * @param yRdd
 */
case class CrTable(offset: Int,x: String, y: String, cr: Double, flag: Int, xRdd: RDD[Double], yRdd: RDD[Double]) extends java.io.Serializable {
  override def toString = f"${x} ${y} ${cr}%.2f ${flag} ${offset}"
}

/**
 * Correlation class is supposed to find the most relative feature to 'aqi'. It will evaluate the Pearson correlation
 * between 'aqi' to other fields.
 *
 * @param sc the sparkContext
 * @param sqlContext
 * @param schemaRdd
 */
class Correlation(@transient sc: SparkContext, @transient sqlContext: SQLContext, schemaRdd: DataFrame)  extends java.io.Serializable {
  // Create ts filed RDD
  val fields = schemaRdd.schema.fieldNames.toArray
  val fieldNames = sc.broadcast(fields)
  val tsIndex = fieldNames.value.indexOf("ts")
  val needCorrFieldDefault = "aqi"::"temp"::"dewpt"::"visby"::"cloudHigh"::"windSpd"::"windDir"::Nil

  val tsRdd = schemaRdd.map(r => {
    val tsIndex = fieldNames.value.indexOf("ts")
    r(tsIndex).toString
  })

  def corrs(mainField: String="aqi", someFieles: Seq[String]=Nil, offset: Int = 0): Seq[CrTable] = {
    val needCorrField = if (someFieles.length>0) someFieles else needCorrFieldDefault
    trace(INFO,"Current schemaRDD schema is: ")
    trace(INFO,schemaRdd.schema.treeString)

    val mainIndex = schemaRdd.schema.fieldNames.indexOf(mainField)
    if (mainIndex == -1) {
      trace(ERROR, "mainFile not found")
      return null
    }
    // Create main filed RDD
    val aqiRdd = schemaRdd.map(r => r(mainIndex).toString.toDouble)

    val corrTables = fieldNames.value.map(f => {
      val index = fieldNames.value.indexOf(f)
      assert(index != -1)
      val corrTable =
        if (needCorrField.contains(f)) {
          // create field rdd
          val fieldRdd = schemaRdd.map(r =>r(index).toString.toDouble)
          //evaluate the correlation
          val cr = Statistics.corr(aqiRdd, fieldRdd)
          CrTable(offset,mainField,f,cr,0,aqiRdd,fieldRdd)
        } else {
          CrTable(offset,mainField, f, 0,1,null,null)
        }
      corrTable
    }).filter(r => r.x != r.y)
    corrTables
  }

  /**
   * Transform the DataFrame to VectorRDD, and evaluate the correlation
   */
  def corrsAll(someFieles: Seq[String]=Nil, offset: Int = 0): Seq[CrTable] = {
    val needCorrField = if (someFieles.length>0) someFieles else needCorrFieldDefault
    trace(INFO,"Current schemaRDD schema is: ")
    trace(INFO,schemaRdd.schema.treeString)

    val veterRdd = schemaRdd.map(r => {
      val subRow = needCorrField.map(f =>{
        val index = fieldNames.value.indexOf(f)
        assert(index != -1, f"The ${f} is not found in ${fieldNames.value}")
        r(index).toString.toDouble
      })
      new DenseVector(subRow.toArray)
    })
    val cr = Statistics.corr(veterRdd.asInstanceOf[RDD[Vector]])
    trace(DEBUG,"All field correlation is:")
    trace(DEBUG,cr.toArray.mkString("\t"))
    val crArray = cr.toArray
    val aqiIndex = needCorrField.indexOf("aqi")
    assert(aqiIndex != -1, f"The 'aqi' is not found in needCorrField")
    val result = Range(0,cr.numCols*cr.numCols).map(index => {
      val row = index / cr.numCols
      val col = index - row * cr.numCols
      CrTable(offset,needCorrField(row),needCorrField(col),crArray(aqiIndex*cr.numCols + col),0,null,null)
    }).filter(r => r.x != r.y)
    result
  }

}
