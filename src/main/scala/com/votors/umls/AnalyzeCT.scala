package com.votors.umls

import java.io.{FileWriter, PrintWriter, FileReader}
import java.util.concurrent.atomic.AtomicInteger
import com.votors.common.{TimeX, Conf}
import com.votors.common.Utils.Trace._
import com.votors.common.Utils._
import com.votors.ml.{Nlp, StanfordNLP}
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.RelationMentionsAnnotation
import edu.stanford.nlp.ling.CoreAnnotations._
import edu.stanford.nlp.ling.{IndexedWord, Label}
import edu.stanford.nlp.ling.tokensregex.{NodePattern, MatchedExpression, CoreMapExpressionExtractor, TokenSequencePattern}
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.semgraph.SemanticGraph
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations._
import edu.stanford.nlp.trees.{LeftHeadFinder, TypedDependency, Tree}
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation
import edu.stanford.nlp.util.{IntPair, CoreMap}
import org.apache.commons.csv._
import org.joda.time.Duration
import scala.collection.mutable.{ListBuffer, ArrayBuffer}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.io.Source
import scala.io.Codec

/**
 * Created by Jason on 2016/1/13 0013.
 */

case class Term(name:String, head:IndexedWord) {
  private val modifiers = ListBuffer[IndexedWord]()
  val span = new IntPair(head.index,head.index)
  val cuis = new ListBuffer[Suggestion]
  var isInConj = false

  def addModifier(m:IndexedWord, rel:String) = {
    modifiers.append(m)
    spanMerge(span,m.index,m.index)
  }
  def getModifiers = modifiers
  def hashcode():Int = {
    return head.hashCode()
  }
  def equals(t:Term): Boolean = {
    return head.equals(t.head)
  }
  def isOverlap(spanOther: IntPair) = {
    !(spanOther.get(1)<span.get(0) || spanOther.get(0)>span.get(1))
  }
  override def toString() = {
    s"Term: ${name}, ${head}, [${modifiers}]"
  }
}

/* The tokens that is matched in a group of the regular expression.
* First we mark it as a special ner-type, than extracted them from the ner-annotation*/
class RegexGroup(var name:String) {
  private val tokens = new ListBuffer[CoreMap]
  val span = new IntPair(-1,-1)   // start from 1. Be care the span from tree.getSpan() is start from 0;
  val cuis = new ListBuffer[Suggestion]
  var cuiSource = ""  // "fullDep" / "partDep" / "tree".
  var duration: Duration = null //the string after within or without, describe how long of the history
  val terms = new mutable.HashMap[Int,Term] // sub-group of this regex group. Usually is 'or'/'and'.
  var logic = "None"  // logic in the group, for now, 'or' / 'and',
  // keep all the words in conj reletion, so that we can combine them. A, B or J C => AC BC JC
  val conjWords = new mutable.HashSet[IndexedWord]()

  def addToken(t:CoreMap) = {
    tokens.append(t)
    // set the span of this group. Using index start of 0, caz the span of tree is start with 0
    val index = t.get(classOf[IndexAnnotation])
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
  def isHitCuiRange(span: IntPair):Boolean = {
    terms.values.foreach(t=>{
      if (t.cuis.size>0 && t.isOverlap(span))
        return true
    })
    false
  }

  def update() = {
    getDuration()
  }

  def getDuration() {
    if (name.contains("DURATION")){
      getTokens.foreach(d=>{
        if(duration == null || duration.getStandardSeconds <= 0) {
          duration = TimeX.parse(d.get(classOf[NormalizedNamedEntityTagAnnotation]))
        }
      })
    }
  }

  override def toString = {
    val termStr = terms.values.map(t=>t.getModifiers.map(_.value).mkString(" ") + " " + t.head.value).mkString(";")
    if (name.contains("CUI_")) {
      val cuiBuff = if (cuis.size > 0){
        s"[${tokens.map(_.get(classOf[TextAnnotation])).mkString(" ")}(${logic}|${cuiSource})]=(${cuis.map(c=>s"${c.cui}:${c.descr}<${c.stys.mkString(",")}>").mkString(";")});"
      }else{
        s"${tokens.map(_.get(classOf[TextAnnotation])).mkString(" ")}(${logic}|${cuiSource})"
      }
      s"${name}\t[${span}]:${cuiBuff}:(${termStr})"
    }else if (name.contains("DURATION")) {
      s"${name}\t[${span}]:${duration.getStandardDays}"
    }else{
      s"${name}\t[${span}]:${tokens.map(_.get(classOf[TextAnnotation])).mkString(" ")}:(${termStr})"
    }
  }
  def getTitle = "Name\tValue"
}


/**
 * The basic pattern result for each pattern that is mathched.
 * name: if it is 'None', there is no pattern found. but we need to process the sentence.
 * */
class CTPattern (val name:String, val matched: MatchedExpression, val sentence:CoreMap){
  var negation = 0 // if it is negation (
  val span = new IntPair(-1,-1)
  /* *******************************************************
   * Init the information that is used in all Pattern
   ********************************************************/
  //println(s"Creating pattern: ${name}")
  // if there is no pattern found, process the whole sentence.
  val ann = if (name != "None") matched.getAnnotation() else sentence
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
  val rgAll = new RegexGroup("CUI_ALL")
  tokens.iterator().foreach(t=>{
    if (name == "None") {
      if (rgAll.getTokens.size == 0) {
        ner2groups.append(rgAll)
      }
      rgAll.addToken(t)
    }
    val ner = t.get(classOf[NamedEntityTagAnnotation])
    //println(s"ner ${t} -> ${ner}")
    if (ner != null && !ner.equals("O")) {
      //println(s"found group: ${t}->${ner}")
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
  println(s"ner2groups of ${name}: ${ner2groups}")

  //val norm = ann.get(classOf[NormalizedNamedEntityTagAnnotation])
  //println(s"TokensAnnotation: ${ann.get(classOf[TokensAnnotation])}")

  def getSentence() = {
    sentence.get(classOf[TextAnnotation])
  }

    /**
     * General get cui by a string and filter.
     */
  def getCui(str: String, reduceBySty:Boolean=true): Array[Suggestion] = {
      var suggustions = UmlsTagger2.tagger.select(str,true,true)
        .filter(s=>{
        var flag = s.score > Conf.umlsLikehoodLimit  && !Nlp.checkStopword(s.orgStr,true)
        var flag_sty = false
        s.stys.foreach(tui=>{
          flag_sty |= (Conf.semanticType.indexOf(tui) >=0)
        })
        flag && flag_sty
      })
      if (reduceBySty) suggustions = suggustions.flatMap(s=>{
        // map to (tui,suggestion)
        s.stys.map((_,s.copy))
      }).groupBy(_._1).map(kv=>{
        // choose the smallest cui for key (cui,tui)
        val tmp = kv._2.sortBy(_._2.cui)
        val firstKv = tmp(0)
        val sugg = firstKv._2
        sugg.stys.add(firstKv._1)
        sugg
      }).toArray

      suggustions
    }

  /* Get cui if the group have not found cui by dependency.*/
  def getCuiByTree(n:Int=5, tree: Tree=tree):Boolean = {
    if (tree.isLeaf) {
      // do not find a cui
      return false
    }
    // the tree has to contain some token in some group
    if (false == ner2groups.map(_.isOverlap(span1based(tree.getSpan))).reduce(_ || _)) {
      return false
    }

    // filter out these already found cui by dependency.
    ner2groups.filter(g=>g.name.startsWith("CUI_")/* && g.cuis.size == 0*/).foreach(g=>{
      //println(s"[${tree.getLeaves.size}]${span1based(tree.getSpan))},${ner2groups.map(_.isOverlap(span1based(tree.getSpan))).reduce(_ || _)}, ${StanfordNLP.isNoun(tree.value()) && g.isContains(tree.getSpan)},${tree.value()}\t${tree.getLeaves.iterator().mkString(" ")}")
      // 1. the leave have no POS info; 2. is must be noun; 3. it must be in the group; 4. it should not be found CUI by dependency; 5. its length should less than N
      if (!tree.isLeaf
        && StanfordNLP.isNoun(tree.value())
        && g.isContains(span1based(tree.getSpan))
        && !g.isHitCuiRange(span1based(tree.getSpan))
        && tree.getLeaves.size <= n) {
        // if cui is found, return, else continue to search in the subtree.
        val str = tree.getLeaves.iterator().mkString(" ")
        val cuis = getCui(str)
        if (cuis.size>0) {
          g.cuis.appendAll(cuis)
          g.cuiSource += s"tree(${str}),"
          //println(s"get cuis ${cuis.mkString("\t")}")
          return true
        }
      }
    })

    val kids = tree.children
    for (kid <- kids) {
      getCuiByTree(n,kid)
    }
    // should not reach here
    return false
  }

  def getCuiByDep() = {
    ner2groups.filter(g=>g.name.startsWith("CUI_") && g.cuis.size == 0).foreach(g=>{
      g.terms.foreach(kv=>{
        val term = kv._2
        // we have to may a copy to make sure do not change the modifiers.
        val words = term.getModifiers.clone
        words.append(term.head)
        val str = words.sortBy(_.index).map(_.value()).mkString(" ")
        val cuis = getCui(str)
        if (cuis.size>0) {
          term.cuis.appendAll(cuis)
          g.cuis.appendAll(cuis)
          g.cuiSource += s"fullDep(${str}),"
          //println(s"get cuis ${cuis.mkString("\t")}")
        } else if (term.getModifiers.size > 1) {
          // if we can't get any cui using all the modifiers, we try to use each modifier to fetch cui.
          val modNum = term.getModifiers.size
          //first try the (longest suffix modifiers + head), if there is more than 2 modifiers
          var foundCui = false
          if (modNum>=3) Range(1,modNum).foreach(idx=>if(!foundCui){
            val str_longest = term.getModifiers.slice(idx,modNum).map(_.value).mkString(" ") + s" ${term.head.value}"
            val cuis_longest = getCui(str_longest)
            if (cuis_longest.size>0) {
              foundCui = true
              term.cuis.appendAll(cuis_longest)
              g.cuis.appendAll(cuis_longest)
              g.cuiSource += s"partDep*(${str_longest}),"
            }
          })
          // try (every modifier + head) to find cui
          if (!foundCui) term.getModifiers.foreach(m=>{
            val str_part = s"${m.value} ${term.head.value}"
            val cuis_part = getCui(str_part)
            if (cuis_part.size>0) {
              term.cuis.appendAll(cuis_part)
              g.cuis.appendAll(cuis_part)
              g.cuiSource += s"partDep(${str_part}),"
            }
          })
        }
        // if the term is in 'conj' relation, we combine the head word with all the words in 'conj' relation.
        if (term.isInConj) {
          g.conjWords.foreach(m=>if(!m.equals(term.head)){
            val str_conj = s"${m.value} ${term.head.value}"
            val cuis_conj = getCui(str_conj)
            if (cuis_conj.size>0) {
              term.cuis.appendAll(cuis_conj)
              g.cuis.appendAll(cuis_conj)
              g.cuiSource += s"conjDep(${str_conj}),"
              // ! we only add the word in 'conj' relation as modifier when we can find a cui.
              // because it is not sure if the word is supposed to be a modifier or not.
              term.addModifier(m,"conj")
            }
          })

        }

      })
    })
  }

  /**
   * 
   * @param dep the dependency tree
   * @param pos the current node
   * @param accessed the nodes that is accessed
   */
  def walkDep(dep:SemanticGraph, pos: IndexedWord, accessed:mutable.HashSet[IndexedWord]): Unit= {
    if(accessed.contains(pos)){
      return
    }else{
      accessed.add(pos)
    }
    val children = dep.getChildList(pos)
    //if (children.size == 0) return

    println(s"${pos} ***depth:" + dep.getPathToRoot(pos).size)
    val semGraph = dep.getOutEdgesSorted(pos)
    semGraph.iterator.foreach(s=>{
      println(s"### ${s} : ${s.getRelation.toString}")
      val rel = s.getRelation.toString
      // first check if the relation in any group
      var hitGroup = false

      ner2groups.filter(g=>g.isContains(new IntPair(math.min(s.getSource.index,s.getTarget.index), math.max(s.getSource.index,s.getTarget.index()))))
        .foreach(g=>{
          if(name != "CUI_ALL")hitGroup = true  // 'CUI_ALL is the default pattern. excluding.
          if (rel.equals("conj:or") || rel.equals("conj:and")) {
            val start = s.getSource.index()
            val end = s.getTarget.index()
            println(s"${rel}: ${start}, ${end}")
            g.logic = rel
            g.conjWords += s.getSource
            g.conjWords += s.getTarget
          }else if (rel.equals("neg")){

          }else if (rel.equals("amod") || rel.equals("acomp") || rel.equals("vmod") || rel.equals("nn") || rel.equals("compound") || rel.equals("nmod")){
            val tmp = Term(rel,s.getSource)
            val t = g.terms.getOrElseUpdate(tmp.hashcode(),tmp)
            t.addModifier(s.getTarget,rel)
            if (g.conjWords.contains(s.getSource)) t.isInConj = true   // this term is associated with conj relation
          }
      })
      if (hitGroup == false) {
        if (rel.equals("neg")){
          // only the negation before the pattern is counter for negation.
          if (s.getSource.index() < span.getSource) {
            println("negation detect!")
            negation += 1
          }
        }
      }
    })

    children.iterator.foreach(cld=>{
      walkDep(dep,cld,accessed)
    })
  }

  /* Get the negation status of the sentence. */
  def getNegation() = {
    //dep.typedDependencies.iterator.map(_.toString()).foreach(println)
    negation = dep.typedDependencies.iterator.map(_.toString()).filter(_.startsWith("neg")).size
    println(s"###neg:${negation}###")
  }

  def getMatchedTokens() = {
    tokens.iterator().map(_.get(classOf[TextAnnotation])).mkString(" ")
  }

  /**
   * should be called after ner2groups initialed.
   */
  def getSpan() = {
    if (true/*ner2groups.size>0*/){
      val s = ner2groups.map(_.span).reduce((s1,s2)=>new IntPair(math.min(s1.getSource,s2.getSource), math.max(s1.getTarget,s2.getTarget)))
      span.set(0,s.getSource)
      span.set(1,s.getTarget)
      println(s"Update span of pattern is ${span}")
    }
  }

  def update() = {
    getSpan()
    //getNegation()
    walkDep(dep,dep.getFirstRoot,new mutable.HashSet[IndexedWord]())
    getCuiByDep()
    // if there is no cui found by dependency, use syntactic to find cui again
    getCuiByTree()
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
  val criteriaId = AnalyzeCT.criteriaIdIncr.getAndIncrement()
  val patternList = new ArrayBuffer[(CoreMap, CTPattern)]()
  var subTitle = "None"
  override def toString(): String = {
    val str = f""""${tid.trim}","${criteriaType.trim}","${markedType}","${depth}","${cui}","${cuiStr}","${criteriaId}","${sentence.trim.replaceAll("\\\"","'")}""""
    if(markedType.size > 1 && markedType != "None")trace(INFO, "Get CTRow parsing result: " + str)
    str
  }
  def patternOutput(pattern: CTPattern, sent:String=null) = {
      val paternStr = if (pattern == null)
        sent + "\tNone"
      else
        pattern.getSentence() + "\t" + pattern.toString

    s"${tid.trim}\t${criteriaType}\t${criteriaId}\t${subTitle}\t" + paternStr

  }
  def patternCuiDurOutput(writer:PrintWriter,pattern: CTPattern, filter:String) = {
    if (pattern != null ) {
      val durGroup = pattern.ner2groups.find(_.name == "DURATION").getOrElse(null)
      val dur = if (durGroup!=null) {
        durGroup.duration.getStandardDays
      }else{-1}
      pattern.ner2groups.foreach(g => {
        g.cuis.foreach(cui => {
          cui.stys.foreach(sty=> {
            writer.println(s"${AnalyzeCT.taskName}\t${tid.trim}\t${criteriaType}\t${criteriaId}\t${pattern.name}\t${g.name}\t${cui.cui}\t${sty}\t${cui.orgStr}\t${cui.descr}\t${dur}\t${pattern.negation}\t${pattern.getSentence()}")
          })
        })
      })
    }
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
      s"tid\ttype\tcriteriaId\tsubTitle\tsentence\t${CTPattern.getTitle}"
    else if (jobType == "cui")
      s"task\ttid\ttype\tcriteriaId\tpattern\tgroup\tcui\tsty\torg_str\tcui_str\tduration\tneg\tsentence"
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
        //println(s"${name}\t${reg2}")
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

  /**
   * A sentence has a sub-title if:
   *  1. it is a item of a list
   *    a) start with '- ' or '[1-9]. '
   * then the sub-title is the closest sentence before the first item of the list
   * @param sent
   * @return
   */
  def hasSubTitle(sent:String) = {
    sent.trim.matches("^[\\-\\*]\\s.*|[0-9]+\\.?\\s.*")
  }

  def analyzeFile(jobType:String="parse"): Unit = {
    var writer = new PrintWriter(new FileWriter(outputFile))
    var writer_cui = new PrintWriter(new FileWriter(outputFile+".cui"))
    writer.println(CTRow("","","").getTitle(jobType))
    writer_cui.println(CTRow("","","").getTitle("cui"))
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
      var subTitle = ""
      criteria.split("#|\\n").foreach(sent_org =>{
        val sent = sent_org.trim.replaceAll("^[\\p{Punct}\\s]*","")

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

        // cache the sentence, it will be use as the sub-title of the next sentence
        if (hasSubTitle(sent_org)){
          ctRow.subTitle = subTitle
        }else{
          subTitle = sent
        }

        if (jobType == "parse")
          detectKeyword(ctRow,writer)
        else if (jobType == "quantity")
          detectQuantity(ctRow,writer)
        else if (jobType == "pattern")
          detectPattern(ctRow,writer,writer_cui)
      })
      writer.flush()

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

  def detectPattern (ctRow: CTRow, writer:PrintWriter, writer_cui:PrintWriter) = {
    //writer.println(s"Tid\tType\tsentenId}")
    ctRow.patternList ++= StanfordNLP.findPattern(ctRow.sentence)
    if (ctRow.patternList.size>0) {
      ctRow.patternList.foreach(p => {
        if (p._2 != null) {
          writer.println(s"${ctRow.patternOutput(p._2)}")
          ctRow.patternCuiDurOutput(writer_cui, p._2, "")
        }else {
          writer.println(s"${ctRow.patternOutput(null, p._1.get(classOf[TextAnnotation]))}")
        }
      })
    }else{
      writer.println(s"${ctRow.patternOutput(null,ctRow.sentence)}")
    }
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
    println("### tree\n" + tree.pennPrint())
    // this is the Stanford dependency graph of the current sentence
    //val dependencies: SemanticGraph = sentence.get(classOf[BasicDependenciesAnnotation])
    //println("### basic dependencies 1\n" + dependencies)
    //val dependencies2: SemanticGraph = sentence.get(classOf[edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation])
    //println("### enhanced dependencies 2\n" + dependencies2)
    //val dependencies3: SemanticGraph = sentence.get(classOf[edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation])
    //println("### ++ dependencies 3\n" + dependencies3)

    val tokens = sentence.get(classOf[TokensAnnotation])
    println("### tokens\n" + tokens)
    for (t <- tokens.iterator()) {
      val ner = t.get(classOf[NamedEntityTagAnnotation])
      print(s"${t}:${ner}\n")
    }

    val retList = new ArrayBuffer[CTPattern]()
    /* for every mached expressed, we collect the result information. */
    matched.iterator.foreach(m => {
      //println(s"keys of annotation: ${m.getValue}")
      val pattern = m.getValue.toString match {
      case _ => {
          new CTPattern(m.getValue.get.toString, m, sentence)
        }
      }
      if (pattern != null) {
        pattern.update()
        retList.append(pattern)
      }
//      val ann = m.getAnnotation()
//      //println(m.getAnnotation().keySet())
//      val tokens = ann.get(classOf[TokensAnnotation])
//      val ner = ann.get(classOf[NamedEntityTagAnnotation])
//      val norm = ann.get(classOf[NormalizedNamedEntityTagAnnotation])
      //println(s"TokensAnnotation: ${ann.get(classOf[TokensAnnotation])}")
    })

    // if there is no pattern matched, add a 'None' pattern.
    if (matched.size == 0) {
      val pattern = new CTPattern("None", null, sentence)
      if (pattern != null) {
        pattern.update()
        retList.append(pattern)
      }
    }

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
  val criteriaIdIncr = new AtomicInteger()
  var jobType = "parse"
  var taskName = ""
  
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
    taskName = avgs(4)

    if (jobType == "prepare")doGetKeywords(dir, extFile, extFile)

    iFiles.foreach(f =>{
      println(s"processing: ${f}")
      if (jobType != "prepare")doAnaly(dir, f, extFile)
    })


  }
}