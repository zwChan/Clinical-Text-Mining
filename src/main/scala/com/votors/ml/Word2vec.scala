package com.votors.ml

import java.io.File
import java.util.Date

import com.votors.common.Conf
import com.votors.common.Utils.Trace
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

/**
  * Created by Jason on 2017/5/3 0003.
  */
class Word2vec(sc: SparkContext, dir: String) {
  /**
    * get text from files of directory (fileName -> (text of doc in wikipedia)
    */
  def getTextRdd():RDD[(String)] = {
    sc.wholeTextFiles(dir, Conf.partitionNumber).flatMap(kv=>{
      val filename = kv._1
      val text = kv._2
      val docList = text.split("</doc>").map(_.split("\n",2)).filter(_.size>=2).map(text=>text(1))
      docList
    })
  }


}

object Word2vec {
  def main(args: Array[String]): Unit = {
    println(args.mkString("\t"))
    if (args.size < 1) {
      println(s"Input parameters: [path of files, for API 'wholeTextFiles']")
      sys.exit(1)
    }
    //System.setProperty("hadoop.home.dir", "C:\\fsu\\ra\\UmlsTagger\\libs")

    // init spark
    val startTime = new Date()
    val conf = new SparkConf()
      .setAppName("Word2vec")
    if (Conf.sparkMaster.length > 3)
      conf.setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    val word2vec = new Word2vec(sc, dir=args(0))
    word2vec.getTextRdd().collect.foreach(println(_))

    sc.stop()
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }

}