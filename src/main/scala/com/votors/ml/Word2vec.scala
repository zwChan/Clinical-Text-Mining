package com.votors.ml

import java.io.FileWriter
import java.lang.Thread
import java.util.Date
import java.util.regex.{Matcher, Pattern}

import com.votors.common.{Conf, MyCache}
import com.votors.common.Utils.Trace
import org.apache.log4j.{Level, Logger}
import org.apache.spark.broadcast.Broadcast
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

  def getDepContextRdd(textRdd: RDD[String], vocab: Broadcast[mutable.HashSet[String]]) = {
    textRdd.flatMap(text=>getDepContext(text,vocab.value))
  }
  def getDepContext(text: String, vocab: mutable.HashSet[String]) = {
    val startTime = System.currentTimeMillis()
    val edges = StanfordNLP.getDependencyContext(text)
    val ret = edges.filter(edge => {
      val sWord = edge._1
      val tWord = edge._2
      val rel = edge._3
      if (rel.contains("punct") || rel.contains("adpmod"))
        false
      else if (vocab!= null && vocab.size > 0 && (!vocab.contains(sWord) || !vocab.contains(tWord))){
        false
      }else{
        true
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
      println(s"Input parameters: [context|token] [path of input file] [path of output file] [input vocab file or norm file] [stat file]")
      sys.exit(1)
    }
    val startTime = new Date()

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    if (args(0).toLowerCase == "context"){
      mainContext(args)
    }else{
      mainToken(args)
    }
    MyCache.close()
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }
/*  def mainSingle(args: Array[String]): Unit = {
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
  }*/
  def mainToken(args: Array[String]): Unit = {
    //System.setProperty("hadoop.home.dir", "C:\\fsu\\ra\\UmlsTagger\\libs")
    // init spark
    val conf = new SparkConf()
      .setAppName("TokenAndLemma")
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
    val cntPos = mutable.HashMap[String,Int]()
    val cntToken = mutable.HashMap[String,Int]()
    val cntLemma = mutable.HashMap[String,Int]()
    tokenRdd.toLocalIterator.foreach(t=>{
      val temp = t._2.split("\\|")

      val (pos,lemma) = if (temp.size > 1) (temp(0),temp(1)) else ("X",t._1)
      if (pos == "N" || pos == "A")
        tokenFile.append(lemma + " ") // for adjactive and noun, use lemma
      else
        tokenFile.append(t._1 + " ")
      normFile.append(t._2 + " ")
      val poskey   = if (t._1 == "\n") "</s>" else if (t._2.size == 0) "</d>" else t._2.trim.substring(0,1)
      val token = if (t._1 == "\n") "</s>" else if (t._1.size == 0) "</d>" else t._1.trim
      val norm  = if (t._2 == "\n") "</s>" else if (t._2.size == 0) "</d>" else t._2.trim
      cntPos.update(poskey, cntPos.getOrElse(poskey, 0) + 1)
      cntToken.update(token, cntToken.getOrElse(token, 0) + 1)
      cntLemma.update(norm, cntLemma.getOrElse(norm, 0) + 1)
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

  def mainContext(args: Array[String]): Unit = {
    val conf = new SparkConf()
      .setAppName("Context")
    if (Conf.sparkMaster.length > 3)
      conf.setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)
    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    val word2vec = new Word2vec(sc, dir=args(1))
    val tokenFile = new FileWriter(args(2))
    val vocabFile = args(3)
    val outFile = new FileWriter(args(4))
    val thrshhold = if (args.size > 5) Integer.parseInt(args(5)) else 0

    val vocab = new mutable.HashSet[String]()
    if (vocabFile.toLowerCase != "null") {
      Source.fromFile(vocabFile).getLines.foreach(line=>{
        val words = line.trim.split("\\s")
        if (words.size >0) {
          val word = words(0)
          val freq = if (words.size > 1 ) try {Integer.parseInt(words(1))} catch {case _:Throwable => 0 }else 0
          if (freq >= thrshhold) vocab.add(word)
        }
      })
    }

    val docRdd = word2vec.getTextRdd().persist(StorageLevel.DISK_ONLY)
    val docNum = docRdd.count()
    println(s"### doc number is $docNum, partition number is ${docRdd.getNumPartitions} ###")

    val vocab_bc = sc.broadcast(vocab)
    val depRdd = word2vec.getDepContextRdd(docRdd,vocab_bc).persist(StorageLevel.DISK_ONLY)
    docRdd.unpersist()
    val tokenCnt = depRdd.count()
    val cntRel = mutable.HashMap[String,Int]()
    depRdd.toLocalIterator.foreach(t=>{
      val sWord = t._1
      val tWord = t._2
      val rel = t._3
      tokenFile.append(s"${sWord} ${tWord}@${rel}\n")
      tokenFile.append(s"${tWord} ${sWord}@${rel}I\n")
      cntRel.update(rel, cntRel.getOrElse(rel, 0) + 1)
    })
    outFile.append(s"context number is ${tokenCnt}\n")
    outFile.append(s"vocab number is ${vocab.size}\n")
    outFile.append(s"doc number is ${docNum}\n")
    outFile.append("# stat of relation:\n")
    cntRel.toArray.sortBy(_._2 * -1).foreach(kv=>{
      outFile.append(s"${kv._1}\t${kv._2}\n")
    })
    tokenFile.append("\n")
    tokenFile.close()
    outFile.close()
    sc.stop()
  }
}