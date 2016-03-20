package com.votors

/**
 * Created by Jason on 2015/11/19 0019.
 */

import java.io.{FileWriter, File}
import java.util
import scala.collection.immutable.HashMap
import scala.io.Source

object Test {

  def reduceAcc(dir: String, dest: String, n: Int) = {
    val set = new util.HashSet[Int]()
    val destFiles = Range(0,n).map(i=>new FileWriter(s"${dest}\\acc-${i}.txt", true)).toArray
    for (file <- new File(dir).listFiles) {
      println(s"process file ${file}")
      for(line <- Source.fromFile(file).getLines()) {
        val tokens = line.split("\\|",3)
        if (tokens.size>2) {
          set.add(tokens(1).toInt)
          destFiles(tokens(1).toInt % n).write(line.replaceAll(",","#")+"\n")
        }
      }
      println(s"acc num ${set.size}")
    }
  }

  def sortAcc(dir: String, dest: String, fraudFile:String): Unit = {
    val fraud = getFraudAcc(fraudFile)
    val ff = new FileWriter(dest + "\\" + "fraud_acc.csv", true)

    for (file <- new File(dir).listFiles) {
      println(s"process file ${file}")
      val of = new FileWriter(dest + "\\" + file.getName, true)
      val lines = Source.fromFile(file).getLines().toArray.map(line => {
        val tokens = line.split("\\|", 3)
        (if(tokens.size>2)tokens(1).toInt else 0, line)
      }).sortBy(_._1).foreach(line => {
        if (fraud.contains(line._1)){
          ff.write(line._2.replaceAll("\\|", ",") + "\n")
        }else{
          of.write(line._2.replaceAll("\\|", ",") + "\n")
        }
      })
      of.close()
    }
    ff.close()
  }

  def getFraudAcc(file:String): util.HashSet[Int] = {
    val set = new util.HashSet[Int]()
    for(line <-  Source.fromFile(file).getLines()){
      val tokens = line.split(",", 3)
      if (tokens.size>2) set.add(tokens(1).toInt)
    }
    println(s"fraud acc num ${set.size}")
    set
  }

  def main(argv: Array[String]) = {

    //reduceAcc("C:\\fsu\\class\\captilone\\data\\training","F:\\data\\tmp",250)
    sortAcc("F:\\data\\tmp", "F:\\data\\ret", "C:\\fsu\\class\\captilone\\data\\fraud.csv")


  }






}



