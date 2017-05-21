package com.votors.ml

import java.io.FileWriter
import java.util.Date
import java.util.regex.{Matcher, Pattern}

import com.votors.common.Conf
import com.votors.common.Utils.Trace
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

import scala.io.Source
import scala.reflect.io.File

/**
  * Created by Jason on 2017/5/3 0003.
  */
class Word2vec(@transient sc: SparkContext, dir: String) extends Serializable{
  /**
    * get text from files of directory (fileName -> (text of doc in wikipedia)
    */
  def getTextRdd():RDD[(String)] = {
    sc.wholeTextFiles(dir, Conf.partitionNumber).flatMap(kv=>{
      val filename = kv._1
      val text = kv._2
      println(filename)
      // 1. split by </doc>; 2. remove first line <doc ... />;
      //val docList = Word2vec.docStart.split(text).map(t => Word2vec.newLine.split(t.trim, 2)).filter(_.size>=2).map(t=>Word2vec.illegalChar.matcher(t(1)).replaceAll(""))
      val docList = getDocFromFile(text.lines).map(_._3.toString)
      docList
    })
  }
  //string => iterator (id, title, doc)
  def getDocFromFile(lines: Iterator[String]) = getDocFromFileAll(lines).filter(_ != null)
  def getDocFromFileAll(lines: Iterator[String]) = {
    var doc: StringBuilder = null
    var startMatcher: Matcher = null
    var id = ""
    var title = ""
    for (l <- lines if l.trim.size > 0) yield {
      val line = l.trim
      if (line.startsWith(Word2vec.docStartStr) && (startMatcher = Word2vec.docStart.matcher(line)) != null && startMatcher.matches()) {
        doc = new StringBuilder
        id = startMatcher.group(1)
        title = startMatcher.group(2)
        null
      }else if (line == Word2vec.docEndStr && doc != null) {
        val doc_ret = doc
        doc = null
        (id, title, doc_ret)
      }else{
        doc.append(line + "\n")
        null
      }
    }
  }


  /**
    * (text) => (word, pso, lemma)
    * @param textRdd
    * @return
    */
  def getPosLemmaRdd(textRdd: RDD[String]) = {
    textRdd.flatMap(text=>getPosLemma(text))
  }
  def getPosLemma(text: String) = {
      // print the title of the article
    println(if (text.lines.hasNext) text.lines.next + s" ${System.currentTimeMillis()/1000%10000}")
//    ("a","b")::Nil
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
  }
}

object Word2vec {
  val illegalChar = Pattern.compile("[^\\p{Graph}\\x20\\t\\r\\n]")
  val docStartStr = "<doc id="
  val docEndStr = "</doc>"
  val docStart = Pattern.compile("^<doc id=\"(\\d+)\".+ title=\"(.+)\">$")
  val docEnd = Pattern.compile("</doc>")
  val newLine = Pattern.compile("\\n")
  def main(args:Array[String]): Unit = {
    println(args.mkString("\t"))
    if (args.size < 4) {
      println(s"Input parameters: [spark|no] [path of input file] [path of output token file] [output norm file")
      sys.exit(1)
    }
    val startTime = new Date()

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    if (args(0).toLowerCase == "spark"){
      mainSpark(args)
    }else{
      mainSingle(args)
    }
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }
  def mainSingle(args: Array[String]): Unit = {
    val word2vec = new Word2vec(null, dir=args(1))
    val tokenFile = new FileWriter(args(2))
    val normFile = new FileWriter(args(3))

    val lines = Source.fromFile(args(1)).getLines()
    var docNum = 0
    val doc = word2vec.getDocFromFile(lines)
    doc.foreach(d => {
      docNum += 1
      word2vec.getPosLemma(d._3.toString).foreach(t=>{
        tokenFile.append(t._1 + " ")
        normFile.append(t._2 + " ")
      })
    })
    println(s"### doc number is $docNum ###")

    tokenFile.close()
    normFile.close()
  }
  def mainSpark(args: Array[String]): Unit = {
    //System.setProperty("hadoop.home.dir", "C:\\fsu\\ra\\UmlsTagger\\libs")
    // init spark
    val conf = new SparkConf()
      .setAppName("Word2vec")
    if (Conf.sparkMaster.length > 3)
      conf.setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    val word2vec = new Word2vec(sc, dir=args(1))
    val tokenFile = new FileWriter(args(2))
    val normFile = new FileWriter(args(3))

    val docRdd = word2vec.getTextRdd().persist()
    val docNum = docRdd.count()
    println(s"### doc number is $docNum, partition number is ${docRdd.getNumPartitions} ###")
    word2vec.getPosLemmaRdd(docRdd).collect.foreach(t=>{
      tokenFile.append(t._1 + " ")
      normFile.append(t._2 + " ")
    })
    tokenFile.append("\n")
    normFile.append("\n")
    tokenFile.close()
    normFile.close()
    sc.stop()
  }

}