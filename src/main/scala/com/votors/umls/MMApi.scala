package com.votors.umls

import scala.collection.JavaConversions._
import java.io.{FileReader, FileWriter, PrintStream, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger
import java.io._
import java.util

import com.votors.common.{Conf, TimeX}
import com.votors.common.Utils.Trace._
import com.votors.common.Utils._
import com.votors.ml.{Nlp, StanfordNLP}
import edu.stanford.nlp.util.IntPair
import gov.nih.nlm.nls.metamap.AcronymsAbbrevs
import gov.nih.nlm.nls.metamap.MetaMapApi
import gov.nih.nlm.nls.metamap.MetaMapApiImpl
import gov.nih.nlm.nls.metamap.Result
import gov.nih.nlm.nls.metamap.Utterance
import gov.nih.nlm.nls.metamap.PCM
import gov.nih.nlm.nls.metamap.Mapping
import gov.nih.nlm.nls.metamap.Ev

case class MMResult(cui:String, score:Int,orgStr:String,cuiStr:String,pfName:String, sent:String) {
  val sourceSet = new util.HashSet[String]
  val stySet = new util.HashSet[String]
  val span = new IntPair(-1,-1)
  var neg = -1
  var sentId = 0
  var matchType = 0; //match with our result: 1=same cui; 2= same orgStr; 3=1+2
  val sb: StringBuilder = new StringBuilder
  override def toString = sb.toString()
}

/**
  * Created by Jason on 2016/11/30 0030.
  */
object MMApi {
  var api: MetaMapApi = null

  /**
    * given a string (sentence), return the result from Metamap.
  */
  def process(terms: String): Seq[MMResult] = {
    if (!Conf.MMenable) return Seq()
    init()
    val resultList: util.List[Result] = api.processCitationsFromString(terms)
    val mmRets = new util.ArrayList[MMResult]()
    for (result <- resultList) {
      /** write result as: cui|score|semtypes|sources|utterance */
      for (utterance <- result.getUtteranceList) {
        for (pcm <- utterance.getPCMList) {
          for (map <- pcm.getMappingList) {
            for (mapEv <- map.getEvList) {
              val mmRet = MMResult(mapEv.getConceptId, math.abs(mapEv.getScore), mapEv.getMatchedWords.mkString(" "), mapEv.getConceptName, mapEv.getPreferredName, terms)
              val sb: StringBuilder = new StringBuilder
              mmRet.sourceSet.addAll(mapEv.getSources.filter(sab => sab.matches(Conf.sabFilter)))
              mmRet.stySet.addAll(mapEv.getSemanticTypes.filter(sty => Conf.semanticType.indexOf(SemanticType.mapAbbr2sty(sty)) >= 0))
              if (mmRet.sourceSet.size > 0 && mmRet.stySet.size > 0 && mmRet.score >= Conf.MMscoreThreshold) {
                mmRets.add(mmRet)
                for (p <- mapEv.getPositionalInfo) {
                  if (mmRet.span.get(0) == -1 || p.getX < mmRet.span.get(0)) mmRet.span.set(0, p.getX)
                  if (mmRet.span.get(1) == -1 || p.getX + p.getY > mmRet.span.get(1)) mmRet.span.set(1, p.getX + p.getY)
                }
                mmRet.neg = mapEv.getNegationStatus
                mmRet.sb.append(mapEv.getConceptId + "|"
                  + mmRet.orgStr + "|"
                  + mmRet.cuiStr + "|"
                  + mapEv.getPositionalInfo + "|"
                  + mmRet.stySet.mkString(" ") + "|"
                  + mmRet.span + "|"
                  + mmRet.sourceSet.mkString(" ") + "|"
                  + mmRet.stySet.mkString(" ") + "|"
                  + utterance.getString)
                println(mmRet.sb)
              } else {
                println(s"filter by sty:${mmRet.stySet.size}, sab:${mmRet.sourceSet.size}, ${mmRet.score}, ${mmRet.cui}, ${mmRet.orgStr}")
              }
            }
          }
        }
      }
    }
    return mmRets.to[Seq]
  }

  private def init():Unit = {
    if (api != null) return
    api = new MetaMapApiImpl
    if (Conf.MMhost.trim.size > 0)api.setHost(Conf.MMhost)
    if (Conf.MMport.trim.size > 0)api.setPort(Conf.MMport.toInt)
    val options: String = Conf.MMoptions
    api.setOptions(options)
  }

  def main(args: Array[String]) {
    init()
    process("No Clinical diagnosis of acne vulgaris. You suffer from diabetes.")
  }
}
