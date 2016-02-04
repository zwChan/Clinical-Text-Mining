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


case class CTRow(val tid: String, val criteriaType:String, var sentence:String, var numericalType:String=""){
  var hitNumType = false
  override def toString(): String = {
    val str = f""""${tid.trim}","${criteriaType.trim}","${sentence.trim.replaceAll("\\\"","'")}","${numericalType}","${if(hitNumType)'Y' else 'N'}"\n"""
    trace(INFO, "Get CTRow parsing result: " + str)
    str
  }
  def getTitle(): String = {
    f""""tid","type","sentence","Numerical type"""" + "\n"
  }
}






class AnalyzeCT(csvFile: String, outputFile:String, numVarFile:String) {
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

  val numericReg = new ArrayBuffer[(String,String)]()
  val in = new FileReader(numVarFile)
  val records = CSVFormat.DEFAULT
    //.withDelimiter(' ')
    .parse(in)
    .iterator()
    .filter(_.size()>=2)
    .foreach(r => {
      r.size()
      val name = r.get(0)
      val reg = r.get(1).toLowerCase()
      // Note: you can not use 'word boundary' to reg that with operation character beginning or ending
      val reg2 =
        if (name.contains("(op)")) {
          ".*(" + reg.replaceAll("xxx","""\\S*\\d+\\S*""") + ").*"
        }else{
          ".*\\b(" + reg.replaceAll("xxx","""\\S*\\d+\\S*""") + ")\\b.*"
        }
      println(s"${name}\t${reg2}")
    numericReg.append((name,reg2))
  })




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
        detectQuantity(ctRow,writer)
      })

    })

    writer.close()
    in.close()

  }

  def detectQuantity(ctRow: CTRow, writer:PrintWriter) = {
    //replace all table,  reduce to only one space between words
    ctRow.sentence = ctRow.sentence.replaceAll("\\s+"," ")
    numericReg.foreach(reg=>{
      if(ctRow.numericalType.size ==0 && ctRow.sentence.toLowerCase.matches(reg._2)){
        println("********" + reg._1)
        ctRow.numericalType = reg._1
        ctRow.hitNumType = true
        writer.print(ctRow)
      }else{
        //ctRow.numericalType = reg._1
        //ctRow.hitNumType = false
      }
    })
    if (ctRow.numericalType.size==0) {
      ctRow.numericalType="None"
      ctRow.hitNumType=false
      writer.print(ctRow)
    }

  }

  def detectQuantity2(sentence:String) = {
    //replace all table,  reduce to only one space between words
    val ctRow = CTRow("test","",sentence,"")
    numericReg.foreach(reg=>{
      if(ctRow.sentence.toLowerCase.matches(".*"+reg._2+".*")){
        println("********" + reg._1)
        ctRow.numericalType += {if (ctRow.numericalType.size==0) "" else "|"} + reg._1
      }
    })
    if (ctRow.numericalType.size==0) {
      ctRow.numericalType="None"
      false
    }else{
      true
    }

  }

}

object AnalyzeCT {

  def doAnaly(f: String): Unit = {
    val ct = new AnalyzeCT(s"C:\\fsu\\ra\\data\\201601\\split_criteria\\${f}.csv",
      s"C:\\fsu\\ra\\data\\201601\\split_criteria\\${f}_ret.csv",
      "C:\\fsu\\ra\\data\\201601\\split_criteria\\numeric_variables.csv")
    ct.analyzeFile()
  }
  def main(avgs: Array[String]): Unit = {
    doAnaly("Obesity_05_14_random300")
    doAnaly("Congective_Heart_Failure_05_14_all233")
    doAnaly("Dementia_05_14_all197")
    doAnaly("Hypertension_05_14_random300")
    doAnaly("T2DM_05_14_random300")
  }
}