package com.votors.umls

import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.{Date, Properties}

import com.votors.common.{SqlUtils, Conf}
import com.votors.ml.{Clustering, Nlp}
import edu.stanford.nlp.util.IntPair
import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkContext, SparkConf}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.io.Source
import scala.io.Codec
/**
  * Created by Jason on 2016/12/2 0002.
  */
case class SemanticType(sty:String,var abbr:String="", var fullName:String="",var groupAbbr:String="", var groupName:String="")

object SemanticType {
  val mapSty = new mutable.HashMap[String, SemanticType]()
  val mapAbbr2sty = new mutable.HashMap[String, String]()
  def init() = {
    val ftype=Source.fromFile(Conf.rootDir + "/data/SemanticTypes_2013AA.txt")
    for (line <- ftype.getLines() if line.trim.size > 5) {
      val tokens = line.split("\\|")
      val sty = mapSty.getOrElseUpdate(tokens(1),SemanticType(tokens(1)))
      mapAbbr2sty.getOrElseUpdate(tokens(0),tokens(1))
      sty.abbr = tokens(0)
      sty.fullName = tokens(2)
    }
    val fgroup=Source.fromFile(Conf.rootDir + "/data/SemGroups.txt")
    for (line <- fgroup.getLines() if line.trim.size > 5) {
      val tokens = line.split("\\|")
      val sty = mapSty.getOrElseUpdate(tokens(2),SemanticType(tokens(2)))
      sty.groupAbbr = tokens(0)
      sty.groupName = tokens(1)
      sty.fullName = tokens(3)
    }
  }
init()

def main(args:Array[String]) = {
  init()
  println(mapSty)
  println(mapAbbr2sty)
}

}
