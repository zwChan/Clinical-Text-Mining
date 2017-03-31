package com.votors.ml

import java.io.FileWriter
import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.votors.common.SqlUtils
import org.apache.log4j.{Level, Logger}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import java.util.{Date, Properties}

import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}

import com.votors.common.Utils._
import com.votors.common.Utils.Trace._

import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}

import org.apache.spark.mllib.clustering._

import com.votors.common._

/**
  * Created by Jason on 2017/3/30 0030.
  */
class KNN {

}


object KNN {
  def main (args: Array[String]): Unit = {
    // init spark
    val startTime = new Date()
    val conf = new SparkConf()
      .setAppName("NLP")
    if (Conf.sparkMaster.length>3)
      conf .setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    val clustering = new Clustering(sc)

    // get all the ngram as a RDD
    val rddNgram4Train = if (Conf.trainedNgramFilterPosRegex.length>0)
      clustering.getTrainNgramRdd().filter(gram=>Ngram.TrainedNgramFilterPosRegex.matcher(gram.posString).matches()==false)
    else
      clustering.getTrainNgramRdd()

    rddNgram4Train.persist()
    var rddGramVectorAll_org = clustering.getVectorRdd(rddNgram4Train, Conf.useFeatures4Train).persist()
    rddNgram4Train.unpersist()
    val rddVectorAll = if (Conf.pcaDimension>0) {
      clustering.pca(rddGramVectorAll_org.map(_._2), Conf.pcaDimension)
    }else{
      rddGramVectorAll_org.map(_._2)
    }
    // we used the vector after pca dimension reduction
    val rddGramVectorAll = if (Conf.pcaDimension>0) {
      val tmpRdd = rddGramVectorAll_org.zip(rddVectorAll).map(kv => (kv._1._1, kv._2)).persist()
      rddGramVectorAll_org.unpersist()
      tmpRdd
    }else{
      rddGramVectorAll_org
    }

    val Seed = new Date().getTime
    val ngramShown = if (Conf.showOrgNgramNum>0){
      rddGramVectorAll.filter(kv => {
        Conf.showOrgNgramOfN.contains(kv._1.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(kv._1.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(kv._1.text).matches()
      }).takeSample(false,Conf.showOrgNgramNum,Seed).sortBy(kv=>Ngram.sortNgramByTfAndKey(kv._1.tfAll,kv._1.key))
    } else null
    if (Conf.showOrgNgramNum>0)println(s"original ngram: ngramShown count is ${ngramShown.size}")
    val fw = if (Conf.saveNgram2file.length > 0) new FileWriter(Conf.saveNgram2file,false) else null
    if (fw != null) fw.write(f"${Ngram.getVectorHead()}\n")
    if (ngramShown!=null) ngramShown.foreach(v => {
      if (fw != null)
        fw.write(f"${v._1.toStringVector()}\n")
      else
        println(f"${v._1.toStringVector()}")
    })

    if (Conf.bagsOfWord && Conf.bowOutputMatrix) {
      println(Nlp.wordsInbags.toArray.sortBy(kv => kv._2._1).mkString("\t") + "\tkeyOfTerm")
      rddGramVectorAll.sortBy(_._1.tfAll * -1).collect().foreach(kv => {
        if (fw!=null)
          fw.write(s"${kv._1.isUmlsTerm(true)}\t"+kv._1.context.wordsInbags.mkString("\t") + s"\t${kv._1.key}\n")
        else
          print(s"${kv._1.isUmlsTerm(true)}\t"+kv._1.context.wordsInbags.mkString("\t") + s"\t${kv._1.key}\n")
      })
    }

    //  output the vectors
    if (fw != null){
      rddGramVectorAll.map(kv=>(kv._1.isUmlsTerm(true),kv._2)).collect().foreach(kv =>{fw.write(s"${kv._1}\t" + kv._2.toArray.mkString("\t") + "\n")})
    }
    if (Conf.outputVectorOnly) {
      rddGramVectorAll.map(kv=>(kv._1.isUmlsTerm(true),kv._2)).collect().foreach(kv =>{print(s"${kv._1}\t" + kv._2.toArray.mkString("\t") + "\n")})
      sys.exit(0)
    }
    if (fw != null)fw.close()

    Range(1,Conf.sampleRuns+1).foreach(i =>{
      println(s"## iteration ${i} ##")
      val sampleRdd = rddGramVectorAll_org.map(kv=>(kv._1.trainSampleMark(), kv._2)).persist()
      main_do(sampleRdd,clustering)
      sampleRdd.unpersist()
    })

    sc.stop()
    println("*******result is ******************")
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }
  def main_do (rddGramVectorAll:RDD[(Ngram,Vector)],clustering:Clustering): Unit = {

    val rddNgram4Train = rddGramVectorAll.map(_._1)
    val ngramCntAll = rddNgram4Train.count()
    val ngramCntChv = rddNgram4Train.filter(_.isUmlsTerm(true)).count()
    val ngramCntUmls = rddNgram4Train.filter(_.isUmlsTerm(false)).count()
    val ngramCntTest = rddNgram4Train.filter(g=> !g.isTrain).count()
    val ngramCntUmlsTest = rddNgram4Train.filter(g=>g.isUmlsTerm(false)&& !g.isTrain).count()
    val ngramCntChvTest = rddNgram4Train.filter(g=>g.isUmlsTerm(true)&& !g.isTrain).count()
    clustering.trainNum = rddNgram4Train.filter(g=>g.isTrain).count()

    //val rddVector = clustering.getVectorRdd(rddNgram4Train.filter(g=>g.isTrain), Conf.useFeatures4Train).persist()
    val rddVector = rddGramVectorAll.filter(_._1.isTrain).persist()
    // if (!Conf.trainOnlyChv&&Conf.testSample<=0), there is no test ngram for rank.
    //val rddRankVector_all = clustering.getVectorRdd(if (Conf.rankWithTrainData || (!Conf.trainOnlyChv&&Conf.testSample<=0)) rddNgram4Train else {rddNgram4Train.filter(_.isTrain==false)}, Conf.useFeatures4Test).persist()
    val rddRankVector_all = if (Conf.rankWithTrainData || (!Conf.trainOnlyChv&&Conf.testSample<=0)) rddGramVectorAll else {rddGramVectorAll.filter(_._1.isTrain==false)}.persist()
    //rddNgram4Train.unpersist()  // don't used it again

    println(s"** ngramCntAll ${ngramCntAll} ngramCntUmls ${ngramCntUmls} ngramCntChv ${ngramCntChv}  ngramOther ${ngramCntAll-ngramCntUmls} **")
    println(s"** ngramCntTrain ${ngramCntAll-ngramCntTest} ngramCntUmlsTrain ${ngramCntUmls-ngramCntUmlsTest} ngramCntChvTrain ${ngramCntChv-ngramCntChvTest}  **")
    println(s"** ngramCntTest ${ngramCntTest} ngramCntUmlsTest ${ngramCntUmlsTest} ngramCntChvTest ${ngramCntChvTest}  ngramOther ${ngramCntTest-ngramCntUmlsTest} **")
    if  (clustering.trainNum <= 0) {
      println("Number of training terms is 0, there much be something configure wrong!")
      sys.exit(1)
    }

    val rddVectorDbl = rddVector.map(_._2).persist()
    //if (Conf.showNgramInCluster<=0) rddVector.unpersist()

    //print the name of the features in vetors
    println("The feature name is:\n" + clustering.columnName.zipWithIndex.map(kv=>s"${kv._1}").mkString("\t"))

    val rddGramVectorAll_norm = rddGramVectorAll.map(kv=>(kv._1,kv._2, Vectors.norm(kv._2, 2.0)))
    val topKneighbors = rddGramVectorAll_norm.cartesian(rddGramVectorAll_norm).map(kv=>{
      val distance = MyKmean.fastSquaredDistance(kv._1._2,kv._1._3,kv._2._2,kv._2._3)
      ((kv._1._1.key,kv._1._1.tfAll,kv._1._1.isUmlsTerm(false),kv._1._1.isUmlsTerm(true)),(kv._2._1.key,distance))
    }).groupByKey().map(kv=>{
      val neighbors = kv._2
      val topK = neighbors.toArray.sortBy(_._2).take(10+1).drop(1)
      (kv._1, topK)
    })

    println("\n### KNN result:")
    println("key\ttf\tisUMLS\tisCHV\tknn")
    topKneighbors.collect().foreach(kv=>{
//      println(s"${kv._1.key}\t${kv._2.map(t=>(t._1.key,t._2)).mkString("\t")}")
      println(s"${kv._1.productIterator.mkString("\t")}\t${kv._2.map(t=>(t._1/*,t._2*/)).mkString("\t")}")
    })

  }
}