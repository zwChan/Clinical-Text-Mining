package org.apache.spark.mllib.clustering

import org.apache.spark.broadcast.Broadcast

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.{SparkContext}
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
//  def pointCost2(centers: TraversableOnce[VectorWithNorm],
//                 point: Vector) = KMeans.pointCost(centers, new VectorWithNorm(point))

  /**
   * Returns the index of the closest center to the given point, as well as the squared distance.
   */
  def findClosest(centers: TraversableOnce[Vector],p: Vector): (Int,Double) = {
    KMeans.findClosest(clusterCentersWithNorm(centers), new VectorWithNorm(p))
  }
  def clusterCentersWithNorm(clusterCenters: TraversableOnce[Vector]): TraversableOnce[VectorWithNorm] =
    clusterCenters.map(new VectorWithNorm(_))

  def fastSquaredDistance( v1: Vector, norm1:Double, v2: Vector, norm2:Double): Double = {
    KMeans.fastSquaredDistance(new VectorWithNorm(v1,norm1),new VectorWithNorm(v2,norm2))
  }
}
