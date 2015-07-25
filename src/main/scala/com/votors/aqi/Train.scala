package com.votors.aqi

import com.votors.common.{InterObject}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.sql.{SQLContext,SchemaRDD}
import org.apache.spark.mllib.linalg.{_}
import org.apache.spark.mllib.stat.Statistics
import java.util.Date
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._
import com.votors.aqi.Aqi._
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.linalg.{Vectors, Vector}

/**
 * Created by chenzhiwei on 3/9/2015.
 */

/**
 * Tran the mode using randomforest, the training data format is :
 *  lable: aqi, field: distance:Int, temp, windSpd
 * @param aqi
 */
class Train(@transient aqi: Aqi) extends java.io.Serializable{

  val aqiDataTemp =aqi.aqiRdd.map(r => (r.ts, r))
  val data = aqi.originalRdd.map(r => (r.ts, r)).join(aqiDataTemp).
    filter( r => r._2._1 != null && r._2._2 != null).map(r => {
    val feature = r._2._1.toList()
    val aqi = r._2._2.aqi.toDouble
    LabeledPoint(aqi, Vectors.dense(feature.toArray))
  })

  trace(INFO,"the join result os aqi and original data:")
  data.take(100).foreach(trace(INFO,_))

  // Split the data into training and test sets (30% held out for testing)
  val splits = data.randomSplit(Array(0.7, 0.3))
  val (trainingData, testData) = (splits(0), splits(1))

  // Train a RandomForest model.
  //  Empty categoricalFeaturesInfo indicates all features are continuous.
  val numClasses = 2
  val categoricalFeaturesInfo = Map[Int, Int]()
  val numTrees = 30 // Use more in practice.
  val featureSubsetStrategy = "auto" // Let the algorithm choose.
  val impurity = "variance"
  val maxDepth = 10
  val maxBins = 32

  val model = RandomForest.trainRegressor(trainingData, categoricalFeaturesInfo,
    numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins)

  // Evaluate model on test instances and compute test error
  val labelsAndPredictions = testData.map { point =>
    val prediction = model.predict(point.features)
    (point.label, prediction)
  }
  println(labelsAndPredictions.take(200).mkString("\t"))
  val testMSE = labelsAndPredictions.map{ case(v, p) => math.pow((v - p), 2)}.mean()
  println("Test Mean Squared Error = " + testMSE)
  //println("Learned regression forest model:\n" + model.toDebugString)

  /**
   * Train the data using random forest
   */
  /*
  def randomForest(someFieles: Seq[String]=Nil, offset: Int = 0): Seq[CrTable] = {
    val needCorrField = if (someFieles.length>0) someFieles else needCorrFieldDefault
    trace(INFO,"Current schemaRDD schema is: ")
    trace(INFO,schemaRdd.schemaString)

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
*/

}
