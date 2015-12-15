package org.apache.spark.mllib.clustering

import org.apache.spark.broadcast.Broadcast

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.{SparkContext, Logging}
import org.apache.spark.annotation.Experimental
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.linalg.BLAS.{axpy, scal}
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.Utils
import org.apache.spark.util.random.XORShiftRandom

/**
 * Created by Jason on 2015/12/4 0004.
 */
object MyKmean extends KMeans{
  def broadcastMode(sc: SparkContext,model:KMeansModel) = {
    sc.broadcast(model.clusterCenters.map(new VectorWithNorm(_)))
  }
  def pointCost2(centers: TraversableOnce[VectorWithNorm],
                 point: VectorWithNorm) = KMeans.pointCost(centers,point)

  def computeCost(centers: TraversableOnce[VectorWithNorm],p: Vector): (Int,Double) = {
    KMeans.findClosest(centers, new VectorWithNorm(p))
  }

}
