package com.votors.ml

import java.io.FileWriter
import java.lang.Thread
import java.util.Date
import java.util.regex.{Matcher, Pattern}

import com.votors.common.{Conf, MyCache}
import com.votors.common.Utils.Trace
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

import scala.collection.mutable
import scala.io.Source
import scala.reflect.io.File

/**
  * Created by Jason on 2017/5/3 0003.
  */
class Word2vec(@transient sc: SparkContext, val dir: String) extends Serializable{
  /**
    * get text from files of directory (fileName -> (text of doc in wikipedia)
    */
  final val ignoreTag = Pattern.compile("<.{0,30}?>")
  final val urlReg = Pattern.compile("(http|ftp|https)://([\\w_-]+(?:(?:\\.[\\w_-]+)+))([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?")
  def getTextRdd():RDD[(String)] = {
    val docRdd = sc.wholeTextFiles(dir, Conf.partitionNumber).flatMap(kv=>{
      val filename = kv._1
      val text = kv._2
      println(filename)
      // 1. split by </doc>; 2. remove first line <doc ... />;
      //val docList = Word2vec.docStart.split(text).map(t => Word2vec.newLine.split(t.trim, 2)).filter(_.size>=2).map(t=>Word2vec.illegalChar.matcher(t(1)).replaceAll(""))
      val docList = getDocFromFile(text.lines).map(_._3.toString)
      docList
    })
    if (Conf.repartitionForce){
      docRdd.repartition(Conf.partitionNumber)
    }else{
      docRdd
    }
  }
  //string => iterator (id, title, doc)
  def getDocFromFile(lines: Iterator[String]) = getDocFromFileAll(lines).filter(_ != null)
  def getDocFromFileAll(lines: Iterator[String]) = {
    var doc: StringBuilder = null
    var startMatcher: Matcher = null
    var id = ""
    var title = ""
    for (l <- lines if l.trim.size > 0) yield {
      val line = Nlp.illegalChar.matcher(l.trim).replaceAll(" ")
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
        val line_doc = this.ignoreTag.matcher(line).replaceAll("")
        //val line_doc = this.urlReg.matcher(line).replaceAll("U#R#L")
        doc.append(line_doc + "\n")
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
    val startTime = System.currentTimeMillis()
    //Thread.sleep(1000)
    val ret = StanfordNLP.getPosLemma(text).map(t=>{
      if (t._1 == "\n")
        ("\n", "\n")
      else if (Nlp.isTokenDelimiter(t._1))
        ("", "")
      else {
        val word = t._1.toLowerCase()
        val pos  = Nlp.posTransform(t._2)
        val norm = s"${pos}|${t._3.toLowerCase}"
        (word, norm)
      }
    })
    if (text.lines.hasNext)
      println(s" ${System.currentTimeMillis()/1000%100000}\t${System.currentTimeMillis()-startTime}\t${text.lines.next}")
    ret
  }
}

object Word2vec {
  val docStartStr = "<doc id="
  val docEndStr = "</doc>"
  val docStart = Pattern.compile("^<doc id=\"(\\d+)\".+ title=\"(.+)\">$")
  val docEnd = Pattern.compile("</doc>")
  val newLine = Pattern.compile("\\n")
  def main(args:Array[String]): Unit = {
    println(args.mkString("\t"))
    if (args.size < 5) {
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
    MyCache.close()
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }
  def mainSingle(args: Array[String]): Unit = {
    val word2vec = new Word2vec(null, dir=args(1))
    val tokenFile = new FileWriter(args(2))
    val normFile = new FileWriter(args(3))
    val outFile = new FileWriter(args(4))

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
    val outFile = new FileWriter(args(4))

    val docRdd = word2vec.getTextRdd().persist(StorageLevel.DISK_ONLY)
    val docNum = docRdd.count()
    println(s"### doc number is $docNum, partition number is ${docRdd.getNumPartitions} ###")
    val tokenRdd = word2vec.getPosLemmaRdd(docRdd).persist(StorageLevel.DISK_ONLY)
    docRdd.unpersist()
    val tokenCnt = tokenRdd.count()
    val cntPos = mutable.HashMap[Char,Int]()
    val cntToken = mutable.HashMap[String,Int]()
    val cntLemma = mutable.HashMap[String,Int]()
    tokenRdd.toLocalIterator.foreach(t=>{
      val temp = t._2.split("\\|")

      val pos,lemma = if (temp.size > 1) (temp(0),temp(1)) else ("X",t._1)
      if (pos == 'N' || pos == "A")
        tokenFile.append(lemma + " ") // for adjactive and noun, use lemma
      else
        tokenFile.append(t._1 + " ")
      normFile.append(t._2 + " ")
      if (t._1.length > 2) {
        val key = t._2.charAt(0)
        cntPos.update(key, cntPos.getOrElse(key, 0) + 1)
        cntToken.update(t._1, cntToken.getOrElse(t._1, 0) + 1)
        cntLemma.update(t._2, cntLemma.getOrElse(t._2, 0) + 1)
      }
    })
    outFile.append(s"token number is ${tokenCnt}\n")
    outFile.append(s"doc number is ${docNum}\n")
    outFile.append("# stat of pos:\n")
    cntPos.toArray.sortBy(_._2 * -1).foreach(kv=>{
      outFile.append(s"${kv._1}\t${kv._2}\n")
    })
    outFile.append("# stat of token:\n")
    cntToken.toArray.sortBy(_._2 * -1).foreach(kv=>{
      outFile.append(s"${kv._1}\t${kv._2}\n")
    })
    outFile.append("# stat of lemma:\n")
    cntLemma.toArray.sortBy(_._2 * -1).foreach(kv=>{
      outFile.append(s"${kv._1}\t${kv._2}\n")
    })
    tokenFile.append("\n")
    normFile.append("\n")
    tokenFile.close()
    normFile.close()
    outFile.close()
    sc.stop()
  }

}