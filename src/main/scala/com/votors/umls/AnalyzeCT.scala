package com.votors.umls

import java.io.{FileWriter, PrintWriter, FileReader}
import com.votors.common.Conf
import com.votors.common.Utils.Trace._
import org.apache.commons.csv._
import scala.actors.threadpool.AtomicInteger
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


case class CTRow(val tid: String, val criteriaType:String, var sentence:String, var markedType:String="", var depth:String="-1", var cui:String="None", var cuiStr:String="None"){
  var hitNumType = false
  val sentId = AnalyzeCT.sentIdIncr.getAndIncrement()
  override def toString(): String = {
    val str = f""""${tid.trim}","${criteriaType.trim}","${markedType}","${depth}","${cui}","${cuiStr}","${sentId}","${sentence.trim.replaceAll("\\\"","'")}"""" + "\n"
    if(markedType.size > 1 && markedType != "None")trace(INFO, "Get CTRow parsing result: " + str)
    str
  }
  def getTitle(): String = {
    """"tid","type","Numerical type","depth" ,"cui" ,"cuiStr","sentence_id","sentence"""" + "\n"
  }
}






class AnalyzeCT(csvFile: String, outputFile:String, externFile:String, externRetFile:String) {
  val STAG_HEAD=0;
  val STAG_INCLUDE=1
  val STAG_EXCLUDE=2
  val STAG_BOTH=3
  val STAG_DISEASE_CH=4
  val STAG_PATIENT_CH=5
  val STAG_PRIOR_CH  =6

  val TYPE_INCLUDE = "Include"
  val TYPE_EXCLUDE = "Exclude"
  val TYPE_BOTH = "Both"
  val TYPE_HEAD = "Head"
  val TYPE_DISEASE_CH = "DISEASE CHARACTERISTICS"
  val TYPE_PATIENT_CH = "PATIENT CHARACTERISTICS"
  val TYPE_PRIOR_CH = "PRIOR CONCURRENT THERAPY"
  val TYPE_INCLUDE_HEAD = "Include head"
  val TYPE_EXCLUDE_HEAD = "Exclude head"
  val TYPE_BOTH_HEAD = "Both head"

  val numericReg = new ArrayBuffer[(String,String)]()

  var isNumericInit = false
  def initNumericReg() = {
    if (!isNumericInit){
      isNumericInit = true
      val in = new FileReader(externFile)
      CSVFormat.DEFAULT
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
    }
  }

    /**
     *   input: (depth, cui, keyword)
     *   output:(root,depth,cui,keyword)
     */
  val keywords = new ArrayBuffer[(String,String,String,String)]()
  var isKeywordInit = false
  def initKeyword() = {
    if (!isKeywordInit){
      isKeywordInit = true
      var root = ""
      Source.fromFile(externRetFile).getLines()
        .foreach(line => {
          val tokens = line.split(",", 3)
          if (tokens.size >= 3 && tokens(0) == "#") {
            root = tokens(2)
          } else if (tokens.size >= 3) {
            val kw = tokens(2).toLowerCase()
            val cui = tokens(1)
            val depth = tokens(0)
            keywords.append((root,depth,cui,kw))
          }
      })
    }
  }


  val tagger = new UmlsTagger2(Conf.solrServerUrl,Conf.rootDir)


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
      //println(row)
      val tid = row.get(0)
      val criteria = row.get(1)

      var stagFlag = STAG_HEAD
      criteria.split("#|\\n").foreach(sent_org =>{
        val sent = sent_org.trim.replaceAll("^\\p{Punct}*","")
        val ctRow =
          if (stagFlag != STAG_INCLUDE && sent.toUpperCase.startsWith("INCLUSION CRITERIA")){
            stagFlag = STAG_INCLUDE
            CTRow(tid,TYPE_INCLUDE_HEAD,sent)
          }else if (stagFlag != STAG_EXCLUDE && sent.toUpperCase.startsWith("EXCLUSION CRITERIA")) {
            stagFlag = STAG_EXCLUDE
            CTRow(tid,TYPE_EXCLUDE_HEAD,sent)
          }else if (stagFlag != STAG_INCLUDE && stagFlag != STAG_EXCLUDE && stagFlag != STAG_DISEASE_CH && sent.toUpperCase.startsWith("DISEASE CHARACTERISTICS")) {
            stagFlag = STAG_DISEASE_CH
            CTRow(tid,TYPE_DISEASE_CH,sent)
          }else if (stagFlag != STAG_INCLUDE && stagFlag != STAG_EXCLUDE  && stagFlag != STAG_PATIENT_CH && sent.toUpperCase.startsWith("PATIENT CHARACTERISTICS")) {
            stagFlag = STAG_PATIENT_CH
            CTRow(tid,TYPE_PATIENT_CH,sent)
          }else if (stagFlag != STAG_INCLUDE && stagFlag != STAG_EXCLUDE && stagFlag != STAG_PRIOR_CH && sent.toUpperCase.startsWith("PRIOR CONCURRENT THERAPY")) {
            stagFlag = STAG_PRIOR_CH
            CTRow(tid,TYPE_PRIOR_CH,sent)
          }else if (stagFlag == STAG_HEAD && sent.toUpperCase.startsWith("INCLUSION AND EXCLUSION CRITERIA")) {
            stagFlag = STAG_BOTH
            CTRow(tid,TYPE_BOTH_HEAD,sent)
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
              case STAG_DISEASE_CH => {
                CTRow(tid, TYPE_DISEASE_CH, sent)
              }
              case STAG_PATIENT_CH => {
                CTRow(tid, TYPE_PATIENT_CH, sent)
              }
              case STAG_PRIOR_CH => {
                CTRow(tid, TYPE_PRIOR_CH, sent)
              }
              case _ =>
                CTRow(tid, "None", sent)

            }
          }
        //detectQuantity(ctRow,writer)
        detectKeyword(ctRow,writer)
      })

    })

    writer.close()
    in.close()

  }

  // detect normal key words
  def detectQuantity(ctRow: CTRow, writer:PrintWriter) = {
    initNumericReg()
    //replace all table,  reduce to only one space between words
    ctRow.sentence = ctRow.sentence.replaceAll("\\s+"," ")
    numericReg.foreach(reg=>{
      if(ctRow.markedType.size ==0 && ctRow.sentence.toLowerCase.matches(reg._2)){
        println("********" + reg._1)
        ctRow.markedType = reg._1
        ctRow.hitNumType = true
        writer.print(ctRow)
      }else{
        //ctRow.numericalType = reg._1
        //ctRow.hitNumType = false
      }
    })
    if (ctRow.markedType.size==0) {
      ctRow.markedType="None"
      ctRow.hitNumType=false
      writer.print(ctRow)
    }

  }

  // detect special symbol, such as >=, !=
  def detectQuantity2(sentence:String) = {
    initNumericReg()
    //replace all table,  reduce to only one space between words
    val ctRow = CTRow("test","",sentence,"")
    numericReg.foreach(reg=>{
      if(ctRow.sentence.toLowerCase.matches(".*"+reg._2+".*")){
        println("********" + reg._1)
        ctRow.markedType += {if (ctRow.markedType.size==0) "" else "|"} + reg._1
      }
    })
    if (ctRow.markedType.size==0) {
      ctRow.markedType="None"
      false
    }else{
      true
    }
  }

  // detect normal key words
  var multiHit = true
  def detectKeyword(ctRow: CTRow, writer:PrintWriter) = {
    //replace all table,  reduce to only one space between words
    initKeyword()
    ctRow.sentence = ctRow.sentence.replaceAll("\\s+"," ")
    keywords.foreach(kw=>{
      if((multiHit || ctRow.markedType.size ==0) && ctRow.sentence.toLowerCase.matches(s".*\\b${kw._4}\\b.*")){
        //println(s"$kw, $ctRow")
        ctRow.markedType = kw._1
        ctRow.depth = kw._2
        ctRow.cui = kw._3
        ctRow.cuiStr = kw._4
        ctRow.hitNumType = true
        writer.print(ctRow)
      }else{
        //ctRow.numericalType = reg._1
        //ctRow.hitNumType = false
      }
    })
    if (ctRow.markedType.size==0) {
      ctRow.markedType="None"
      ctRow.hitNumType=false
      writer.print(ctRow)
    }

  }

  // (cui,str) -> (dept)
  val hCriteria = new mutable.LinkedHashMap[(String,String),(Int)]()
  /**
   * lookup the the child term in UMLS recursively.
   * for the root node, do not search its brother node, just all the child node.
   * @param term
   */
  def findChildTerm(cui: String, term: String, dept: Int): Unit = {
    val ret1 = tagger.execUpdate("drop table if exists prtbl;")
    val ret2 = if (cui.size < 3)tagger.execUpdate("create /*temporary*/ table prtbl as (" +
      "  select distinct rel.cui1 from umls.mrconso as conso, umls.mrrel as rel" +
      s"  where str='${term}' and conso.cui = rel.cui2  and (REL='PAR')      " +
      " );" )
    else
      tagger.execUpdate("create /*temporary*/ table prtbl as (" +
        "  select distinct rel.cui1 from umls.mrconso as conso, umls.mrrel as rel" +
        s"  where conso.cui='${cui}' and conso.cui = rel.cui2  and (REL='RB' OR REL='PAR')      " +
        " );" )
    val ret = tagger.execQuery("select distinct conso.cui, conso.str from umls.mrconso as conso, prtbl as pr where conso.cui=pr.cui1;")
    val buff = new ArrayBuffer[(String,String)]()
    while(ret.next()) {
      if (hCriteria.contains((ret.getString(1), ret.getString(2)))) {
        println(s"${ret.toString} already exists.")
      }else {
        buff.append((ret.getString(1), ret.getString(2)))
      }
    }
    println(s"dept: ${dept}, ${cui}, ${term}: get child number ${buff.size}")
    if (buff.size <= 0) {
      hCriteria.getOrElseUpdate((cui,term),(dept))
      return
    }

    if (dept >= 5) {
      println("Too much recursive, there may be a loop!")
      hCriteria.getOrElseUpdate((cui,term),(dept))
      return
    }
    ret.close()
    buff.foreach(kv =>{
      hCriteria.getOrElseUpdate((cui,term),(dept))
      findChildTerm(kv._1, kv._2, dept+1)
    })
  }

  /**
   * only find the synonym of the current keyword
   * @param cui
   * @param term
   * @param dept
   */
  def findChildTerm_simple(cui: String, term: String, dept: Int): Unit = {
    val ret1 = tagger.execUpdate("drop table if exists prtbl;")
    val ret2 = tagger.execUpdate("create /*temporary*/ table prtbl as (" +
        "  select distinct cui from umls.mrconso " +
        s"  where str='${term}' and LAT='ENG' " +
        " );" )

    val ret = tagger.execQuery("select distinct conso.cui, conso.str from umls.mrconso as conso, prtbl as pr where conso.cui=pr.cui;")
    val buff = new ArrayBuffer[(String,String)]()
    while(ret.next()) {
      if (hCriteria.contains((ret.getString(1), ret.getString(2)))) {
        println(s"${ret.toString} already exists.")
      }else {
        buff.append((ret.getString(1), ret.getString(2)))
      }
    }
    println(s"dept: ${dept}, ${cui}, ${term}: get child number ${buff.size}")

    ret.close()
    buff.foreach(kv =>{
      hCriteria.getOrElseUpdate((kv._1,kv._2),(dept))
      //findChildTerm(kv._1, kv._2, dept+1)
    })
  }


  def findTerm(): Unit = {
    var writer = new PrintWriter(new FileWriter(externRetFile))
    Source.fromFile(externFile).getLines().foreach(line => {
      val tmp = line.split(",",2)
      if (tmp.size>1) {
        //findChildTerm(tmp(0),tmp(1),0)
        findChildTerm_simple(tmp(0),tmp(1),1)
        // if find nothing in umls, just search itself
        if (hCriteria.size == 0) hCriteria.getOrElseUpdate((tmp(0),tmp(1)),0)
        writer.append(s"#,${tmp(0)},${tmp(1)}\n")
        hCriteria.foreach(term => {
          writer.append(s"${term._2},${term._1._1},${term._1._2}\n")
        })
        hCriteria.clear()
      }
    })
    writer.close()
  }
}

object AnalyzeCT {
  val sentIdIncr = new AtomicInteger()

  def doGetKeywords(dir:String, f: String, externFile:String) = {
    val ct = new AnalyzeCT(s"${dir}${f}.csv",
      s"${dir}${f}_ret.csv",
      s"${dir}${externFile}.txt",
      s"${dir}${externFile}_ret.txt")
    //ct.analyzeFile()
    ct.findTerm()
  }
  def doAnaly(dir:String, f: String, externFile:String): Unit = {
    val ct = new AnalyzeCT(s"${dir}${f}.csv",
      s"${dir}${f}_ret.csv",
      s"${dir}${externFile}.txt",
      s"${dir}${externFile}_ret.txt")
    ct.analyzeFile()
  }

  /**
   * arg 1: dir
   * arg 2: file to be parsed, no ext; separated with ',', end with '/' or '\'; (ext is csv)
   * arg 3: extern input fil, no ext;  (ext is txt)
   * @param avgs
   */
  def main(avgs: Array[String]): Unit = {
//    doAnaly("Obesity_05_14_random300")
//    doAnaly("Congective_Heart_Failure_05_14_all233")
//    doAnaly("Dementia_05_14_all197")
//    doAnaly("Hypertension_05_14_random300")
//    doAnaly("T2DM_05_14_random300")

//    doAnaly("Obesity_05_14_random300","criteriaWords")
//    doAnaly("Congective_Heart_Failure_05_14_all233","criteriaWords")
//    doAnaly("Dementia_05_14_all197","criteriaWords")
//    doAnaly("Hypertension_05_14_random300","criteriaWords")
//    doAnaly("T2DM_05_14_random300","criteriaWords")
      //doAnaly("criteria_cancer_trials_2004_2014","criteriaWords")
//    doGetKeywords("T2DM_05_14_random300","criteriaWords")
    println("the input args are:\n" + avgs.mkString("\n"))
    if (avgs.size < 4) {
      println(s"invalid inputs, should be: prepare|parse dir file1,file2... extern-file")
      sys.exit(1)
    }
    val jobType = avgs(0)
    val dir = avgs(1)
    val iFiles = avgs(2).split(",")
    val extFile = avgs(3)

    if (jobType == "prepare")doGetKeywords(dir, extFile, extFile)

    iFiles.foreach(f =>{
      println(s"processing: ${f}")
      if (jobType == "parse")doAnaly(dir, f, extFile)
    })


  }
}