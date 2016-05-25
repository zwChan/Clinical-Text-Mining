package com.votors.umls

import java.io.{FileWriter, PrintWriter, FileReader}
import java.util.concurrent.atomic.AtomicInteger
import com.votors.common.Conf
import com.votors.common.Utils.Trace._
import com.votors.ml.StanfordNLP
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.RelationMentionsAnnotation
import edu.stanford.nlp.ling.CoreAnnotations._
import edu.stanford.nlp.ling.Label
import edu.stanford.nlp.ling.tokensregex.{NodePattern, MatchedExpression, CoreMapExpressionExtractor, TokenSequencePattern}
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.semgraph.SemanticGraph
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation
import edu.stanford.nlp.trees.{LeftHeadFinder, TypedDependency, Tree}
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation
import edu.stanford.nlp.util.{IntPair, CoreMap}
import org.apache.commons.csv._
import scala.collection.mutable.{ListBuffer, ArrayBuffer}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.io.Source
import scala.io.Codec

/**
 * Created by Jason on 2016/1/13 0013.
 */

/* The tokens that is matched in a group of the regular expression.
* First we mark it as a special ner-type, than extracted them from the ner-annotation*/
class RegexGroup(var name:String) {
  private val tokens = new ListBuffer[CoreMap]
  val span = new IntPair(-1,-1)
  val cuis = new ListBuffer[Suggestion]
  var duration: String = null //the string after within or without, describe how long of the history

  def addToken(t:CoreMap) = {
    tokens.append(t)
    // set the span of this group. Using index start of 0, caz the span of tree is start with 0
    val index = t.get(classOf[IndexAnnotation]) - 1
    if(span.getSource < 0 || span.getSource > index) {
      span.set(0,index)
    }
    if(span.getTarget < 0 || span.getTarget < index) {
      span.set(1,index)
    }
  }
  def getTokens = tokens
  def isOverlap(spanOther: IntPair) = {
    !(spanOther.get(1)<span.get(0) || spanOther.get(0)>span.get(1))
  }
  def isContains(spantarget: IntPair) = {
    (spantarget.get(0)>=span.get(0) && spantarget.get(1)<=span.get(1))
  }

  def update() = {
    getDuration()
  }

  def getDuration() = {
    if (name.contains("DURATION")){
      duration = getTokens(0).get(classOf[NormalizedNamedEntityTagAnnotation])
    }
  }
  override def toString = {
    if (name.contains("CUI_")) {
      val cuiBuff = if (cuis.size > 0){
        s"[${cuis(0).orgStr}]=(${cuis(0).cui}:${cuis(0).descr});"
      }else{
        s"${tokens.map(_.get(classOf[TextAnnotation])).mkString(" ")}"
      }
      s"${name}\t[${span}]:${cuiBuff}"
    }else if (name.contains("DURATION")) {
      s"${name}\t[${span}]:${duration}"
    }else{
      s"${name}\t[${span}]:${tokens.map(_.get(classOf[TextAnnotation])).mkString(" ")}"
    }
  }
  def getTitle = "Name\tValue"
}


/* The basic pattern result for each pattern that is mathched. */
class CTPattern (name:String, matched: MatchedExpression, sentence:CoreMap){
  var negation = false // if it is negation (

  /* *******************************************************
   * Init the information that is used in all Pattern
   ********************************************************/
  println(s"Creating pattern: ${name}")
  val ann = matched.getAnnotation()
  //println(matched.getAnnotation().keySet())
  // it is the matched tokens, not all tokens in the sentence
  val tokens = ann.get(classOf[TokensAnnotation])
  // syntactic tree (constituency parse)
  val tree = sentence.get(classOf[TreeAnnotation])
  tree.setSpans()
  //println("test tree:")
  tree.iterator().foreach(t=>{
    //println(t.getSpan) // span start with 0
    //println(t.constituents()) // ranges
    //if(!t.isLeaf)println(t.headPreTerminal(new LeftHeadFinder()))
    //println(t.dependencies) //  null exp
    //println(t.`yield`()) // all words
    //println(t.value()) // word or POS
    //println(t.getLeaves()) // word or POS
  })


  // dependency parse
  val dep = sentence.get(classOf[CollapsedCCProcessedDependenciesAnnotation])

  // ner2tokens aggregate the congruous tokens with the same ner
  val ner2groups = new ListBuffer[RegexGroup]()
  var lastNer = ""
  // aggregate the congruous tokens with the same ner
  tokens.iterator().foreach(t=>{
    val ner = t.get(classOf[NamedEntityTagAnnotation])
    //println(s"ner ${t} -> ${ner}")
    if (!ner.equals("O")) {
      if (ner != lastNer) {
        val rg = new RegexGroup(ner)
        rg.addToken(t)
        ner2groups.append(rg)
      } else {
        ner2groups.last.addToken(t)
      }
      lastNer = ner
    }
  })
  // update information of regular groups
  ner2groups.foreach(_.update)
  println(s"ner2groups: ${ner2groups}")

  val norm = ann.get(classOf[NormalizedNamedEntityTagAnnotation])
  //println(s"TokensAnnotation: ${ann.get(classOf[TokensAnnotation])}")

  /* Get cui if necessary*/
  def getCui(n:Int=5, tree: Tree=tree):Boolean = {
    if (tree.isLeaf) {
      // do not find a cui
      return false
    }
    // the tree has to contain some token in some group
    if (false == ner2groups.map(_.isOverlap(tree.getSpan)).reduce(_ || _)) {
      return false
    }

    ner2groups.filter(_.name.startsWith("CUI_")).foreach(g=>{
      //println(s"[${tree.getLeaves.size}]${tree.getSpan},${ner2groups.map(_.isOverlap(tree.getSpan)).reduce(_ || _)}, ${StanfordNLP.isNoun(tree.value()) && g.isContains(tree.getSpan)},${tree.value()}\t${tree.getLeaves.iterator().mkString(" ")}")
      if (!tree.isLeaf && StanfordNLP.isNoun(tree.value()) && g.isContains(tree.getSpan) && tree.getLeaves.size <= n) {
        // if cui is found, return, else continue to search in the subtree.
        val cuis = UmlsTagger2.tagger.select(tree.getLeaves.iterator().mkString(" "))
        if (cuis.size>0) {
          g.cuis.appendAll(cuis)
          //println(s"get cuis ${cuis.mkString("\t")}")
          return true
        }
      }
    })

    val kids = tree.children
    for (kid <- kids) {
      getCui(n,kid)
    }
    // should not reach here
    return false
  }
  /* Get the negation status of the sentence. */
  def getNegation() = {
    //dep.typedDependencies.iterator.map(_.toString()).foreach(println)
    negation = dep.typedDependencies.iterator.map(_.toString()).filter(_.startsWith("neg")).size > 0
  }

  def getSentence() = {
    tokens.iterator().map(_.get(classOf[TextAnnotation])).mkString(" ")
  }

  def update() = {
    getCui()
    getNegation()
  }
  private def value = {
    ner2groups.map(g=>s"${g.toString}").mkString("\t")
  }
  override def toString = s"${name}\t${negation}\t${value}"
}
object CTPattern {
  def getTitle = "PatternName\tNegation\tGroup1Name\tGroup1VAlue\tGroup2Name\tGroup2VAlue\tGroup3Name\tGroup3VAlue..."
}

case class CTRow(val tid: String, val criteriaType:String, var sentence:String, var markedType:String="", var depth:String="-1", var cui:String="None", var cuiStr:String="None"){
  var hitNumType = false
  val sentId = AnalyzeCT.sentIdIncr.getAndIncrement()
  val patternList = new ArrayBuffer[CTPattern]()
  override def toString(): String = {
    val str = f""""${tid.trim}","${criteriaType.trim}","${markedType}","${depth}","${cui}","${cuiStr}","${sentId}","${sentence.trim.replaceAll("\\\"","'")}""""
    if(markedType.size > 1 && markedType != "None")trace(INFO, "Get CTRow parsing result: " + str)
    str
  }
  def patternOutput(pattern: CTPattern) = {
    s"${tid.trim}\t${criteriaType}\t${sentId}\t${pattern.getSentence()}\t${pattern.toString}"
  }
  /**
   *
   * @param jobType
   * @return
   */
  def getTitle(jobType: String="parse"): String = {
    if (jobType == "parse")
    """"tid","type","Numerical type","depth" ,"cui" ,"cuiStr","sentence_id","sentence""""
    else if (jobType == "pattern")
      s"tid\ttype\tsentenceId\tsentence\t${CTPattern.getTitle}"
    else
      ""
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

  def analyzeFile(jobType:String="parse"): Unit = {
    var writer = new PrintWriter(new FileWriter(outputFile))
    writer.print(CTRow("","","").getTitle(jobType))
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
        if (jobType == "parse")
          detectKeyword(ctRow,writer)
        else if (jobType == "quantity")
          detectQuantity(ctRow,writer)
        else if (jobType == "pattern")
          detectPattern(ctRow,writer)
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
        writer.println(ctRow)
      }else{
        //ctRow.numericalType = reg._1
        //ctRow.hitNumType = false
      }
    })
    if (ctRow.markedType.size==0) {
      ctRow.markedType="None"
      ctRow.hitNumType=false
      writer.println(ctRow)
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
        writer.println(ctRow)
      }else{
        //ctRow.numericalType = reg._1
        //ctRow.hitNumType = false
      }
    })
    if (ctRow.markedType.size==0) {
      ctRow.markedType="None"
      ctRow.hitNumType=false
      writer.println(ctRow)
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

  def detectPattern (ctRow: CTRow, writer:PrintWriter) = {
    //writer.println(s"Tid\tType\tsentenId}")
    ctRow.patternList ++= StanfordNLP.findPattern(ctRow.sentence)
    ctRow.patternList.foreach(p=>{
      writer.println(s"${ctRow.patternOutput(p)}")
    })
  }


}


case class ParseText(val text: String) {
  // create an empty Annotation just with the given text
  val document: Annotation = new Annotation(text);

  // run all Annotators on this text
  StanfordNLP.pipeline.annotate(document)
  val sentences = document.get(classOf[SentencesAnnotation])
  for( sentence <- sentences.iterator()) {
    println("### sentence\n" + sentence)
    val sent = ParseSentence(sentence)
    sent.getPattern()
  }

}

/**
 *  Parse a sentence.
 *  */
case class ParseSentence(val sentence: CoreMap) {
  /**
   * The sentence is annotated before this method is call.
   * @return
   */
  def getPattern() = {
    val matched = ParseSentence.extractor.extractExpressions(sentence)

    // this is the parse tree of the current sentence
    val tree: Tree = sentence.get(classOf[TreeAnnotation])
    //println("### tree\n" + tree)
    // this is the Stanford dependency graph of the current sentence
    val dependencies: SemanticGraph = sentence.get(classOf[CollapsedCCProcessedDependenciesAnnotation])
    //println("### dependencies\n" + dependencies)

    val tokens = sentence.get(classOf[TokensAnnotation])
    println("### tokens\n" + tokens)

    val retList = new ArrayBuffer[CTPattern]()

/*
    var lastNer = ""
    var what = ""
    var pattern: HistoryPatter = null
    for (token <- tokens.iterator()) {
      // this is the text of the token
      val word = token.get(classOf[TextAnnotation])
      // this is the POS tag of the token
      val pos = token.get(classOf[PartOfSpeechAnnotation])
      // this is the NER label of the token
      val lemma = token.get(classOf[LemmaAnnotation])
      //println(s"${token} lemma is " + lemma)
      val ner = token.get(classOf[NamedEntityTagAnnotation])
      if (ner == "DISEASE") {
        if (lastNer != "DISEASE") {
          retList += pattern
        }
        pattern.what += word + " "

      } else if (ner == "DURATION") {
        val nerNorm = token.get(classOf[NormalizedNamedEntityTagAnnotation])
        if (pattern != null) {
          pattern.duration = nerNorm
          pattern.cuis ++= ParseSentence.cuiTagger.annotateSentence(pattern.what)
          pattern = null // duration is the end of the pattern
        }
      } else {
      }
      lastNer = ner
      println(s"### ${word} ner ${ner}")
    }
*/


    /* for every mached expressed, we collect the result information. */
    matched.iterator.foreach(m => {
      println(s"keys of annotation: ${m.getValue}")
      val pattern = m.getValue.toString match {
      case _ => {
          new CTPattern(m.getValue.get.toString, m, sentence)
        }
      }
      if (pattern != null) {
        pattern.update()
        retList.append(pattern)
      }
      val ann = m.getAnnotation()
      println(m.getAnnotation().keySet())
      val tokens = ann.get(classOf[TokensAnnotation])
      val ner = ann.get(classOf[NamedEntityTagAnnotation])
      val norm = ann.get(classOf[NormalizedNamedEntityTagAnnotation])
      //println(s"TokensAnnotation: ${ann.get(classOf[TokensAnnotation])}")
    })
    retList
  }
}

object ParseSentence {
  val env = TokenSequencePattern.getNewEnv()
  env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE)
  val extractor = CoreMapExpressionExtractor.createExtractorFromFiles(env, Conf.stanfordPatternFile)
  val cuiTagger = new UmlsTagger2(Conf.solrServerUrl, Conf.rootDir)

}

object AnalyzeCT {
  val sentIdIncr = new AtomicInteger()
  var jobType = "parse"
  
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
    ct.analyzeFile(AnalyzeCT.jobType)
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
    jobType = avgs(0)
    val dir = avgs(1)
    val iFiles = avgs(2).split(",")
    val extFile = avgs(3)

    if (jobType == "prepare")doGetKeywords(dir, extFile, extFile)

    iFiles.foreach(f =>{
      println(s"processing: ${f}")
      if (jobType != "prepare")doAnaly(dir, f, extFile)
    })


  }
}