package com.votors.umls

import java.io.{FileWriter, PrintWriter, FileReader}
import com.votors.common.Utils.Trace._
import org.apache.commons.csv._
import scala.collection.mutable.ListBuffer

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.io.Source
import scala.io.Codec

/**
 * Created by Jason on 2016/1/13 0013.
 */


case class CTRow(val tid: String, criteriaType:String, sentence:String){
  override def toString(): String = {
    val str = f""""${tid.trim}","${criteriaType.trim}","${sentence.trim}"\n"""
    trace(INFO, "Get CTRow parsing result: " + str)
    str
  }
  def getTitle(): String = {
    f""""tid","type","sentence"""" + "\n"
  }
}






class AnalyzeCT(csvFile: String, outputFile:String) {
  val STAG_HEAD=0;
  val STAG_INCLUDE=1
  val STAG_EXCLUDE=2
  val STAG_BOTH=3

  val TYPE_INCLUDE = "Include"
  val TYPE_EXCLUDE = "Exclude"
  val TYPE_BOTH = "Both"
  val TYPE_HEAD = "Head"
  val TYPE_INCLUDE_HEAD = "Include head"
  val TYPE_EXCLUDE_HEAD = "Exclude head"
  val TYPE_BOTH_HEAD = "Both head"

  def analyzeFile(): Unit = {
    var writer = new PrintWriter(new FileWriter(outputFile))
    writer.print(CTRow("","","").getTitle())
    val in = new FileReader(csvFile)
    val records = CSVFormat.DEFAULT
      .withRecordSeparator("\"")
      .withDelimiter(',')
      .withSkipHeaderRecord(true)
      .withEscape('\\')
      .parse(in)
      .iterator()

    // for each row of csv file
    records.drop(1).foreach(row => {
      println(row)
      val tid = row.get(0)
      val criteria = row.get(1)

      var stagFlag = STAG_HEAD
      criteria.split("#").foreach(sent_org =>{
        val sent = sent_org.trim.replaceAll("^\\p{Punct}*","")
        val ctRow =
          if (stagFlag == STAG_HEAD && sent.toUpperCase.contains("INCLUSION CRITERIA")){
            stagFlag = STAG_INCLUDE
            CTRow(tid,TYPE_INCLUDE_HEAD,sent)
          }else if (stagFlag == STAG_HEAD && sent.toUpperCase.contains("INCLUSION AND EXCLUSION CRITERIA")) {
            stagFlag = STAG_BOTH
            CTRow(tid,TYPE_BOTH_HEAD,sent)
          }else if ((stagFlag == STAG_HEAD || stagFlag == STAG_INCLUDE) && sent.toUpperCase.contains("EXCLUSION CRITERIA")) {
            stagFlag = STAG_EXCLUDE
            CTRow(tid,TYPE_EXCLUDE_HEAD,sent)
          }else {
            stagFlag match {
              case STAG_HEAD => {
                CTRow(tid, TYPE_HEAD, sent)
              }
              case STAG_BOTH => {
                CTRow(tid, TYPE_BOTH, sent)
              }   
              case STAG_INCLUDE => {
                CTRow(tid, TYPE_INCLUDE, sent)
              }
              case STAG_EXCLUDE => {
                CTRow(tid, TYPE_EXCLUDE, sent)
              }
            }
          }
        writer.print(ctRow)
      })

    })

    writer.close()
    in.close()

  }


}

object AnalyzeCT {

  def main(avgs: Array[String]): Unit = {
    val ct = new AnalyzeCT("C:\\fsu\\ra\\data\\201601\\criteria_dementia_studies.csv", "C:\\fsu\\ra\\data\\201601\\criteria_dementia_studies_ret.csv")
    ct.analyzeFile()

  }
}