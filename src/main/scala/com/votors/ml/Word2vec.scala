package com.votors.ml

import java.io.{File, FileWriter}
import java.util.Date
import java.util.regex.Pattern

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
      // 1. split by </doc>; 2. remove first line <doc ... />;
      val docList = Word2vec.docSplit.split(text).map(t => Word2vec.newLine.split(t.trim, 2)).filter(_.size>=2).map(t=>Word2vec.illegalChar.matcher(t(1)).replaceAll(""))
      docList
    })
  }

  /**
    * (text) => (word, pso, lemma)
    * @param textRdd
    * @return
    */
  def getPosLemmaRdd(textRdd: RDD[String]) = {
    textRdd.flatMap(text=>{
      StanfordNLP.getPosLemma(text).map(t=>{
        if (t._1 == "\n")
          ("\n", "\n")
        else if (Nlp.isTokenDelimiter(t._1))
          ("", "")
        else {
          val word = t._1.toLowerCase()
          val pos  = Nlp.posTransform(t._2)
          val norm = s"${t._3.toLowerCase}|${pos}"
          (word, norm)
        }
      })
    })
  }


}

object Word2vec {
  val illegalChar = Pattern.compile("[^\\p{Graph}\\x20\\t\\r\\n]")
  val docSplit = Pattern.compile("</doc>")
  val newLine = Pattern.compile("\\n")
  def main(args: Array[String]): Unit = {
    println(args.mkString("\t"))
    if (args.size < 3) {
      println(s"Input parameters: [path of files, for API 'wholeTextFiles'] [token_file] [norm_token_file]")
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
    val tokenFile = new FileWriter(args(1))
    val normFile = new FileWriter(args(2))

    val docRdd = word2vec.getTextRdd()
    val docNum = docRdd.count()
    println(s"### doc number is $docNum ###")
    word2vec.getPosLemmaRdd(docRdd).toLocalIterator.foreach(t=>{
      tokenFile.append(t._1 + " ")
      normFile.append(t._2 + " ")
    })
    tokenFile.close()
    normFile.close()
    sc.stop()
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }

}