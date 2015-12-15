package com.votors.ml

import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.votors.aqi.Train
import com.votors.common.SqlUtils
import org.apache.log4j.{Level, Logger}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

import scala.collection.mutable.ArrayBuffer
import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.{Date, Properties}

import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.io.Source
import scala.io.Codec

import org.apache.commons.lang3.StringUtils
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.snowball.SnowballFilter
import org.apache.lucene.util.Version
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.impl.HttpSolrServer
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.common.params.CommonParams
import org.apache.solr.common.params.ModifiableSolrParams
import org.apache.solr.common.util.ContentStreamBase
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._

import opennlp.tools.cmdline.BasicCmdLineTool
import opennlp.tools.cmdline.CLI
import opennlp.tools.cmdline.PerformanceMonitor
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSSample
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.stemmer.PorterStemmer
import opennlp.tools.stemmer.snowball.englishStemmer
import opennlp.tools.tokenize.WhitespaceTokenizer
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream
import java.sql.{Statement, Connection, DriverManager, ResultSet}
import org.apache.spark.mllib.linalg.{Vector, Vectors}

import org.apache.spark.mllib.clustering._

import com.votors.common._


/**
 * Created by Jason on 2015/11/9 0009.
 */

class Clustering (sc: SparkContext) {
  var docsNum = 0L

  if (Conf.dbUrl.length==0 || Conf.blogTbl.length==0 || Conf.blogIdCol.length==0 || Conf.blogTextCol.length==0) {
    trace(ERROR, "Some database config not exist.")
    sys.exit(1)
  }

  def getBlogIdRdd(parallelism: Int): RDD[Int] = {
  val limit = Conf.blogLimit
  val sqlUtil = new SqlUtils(Conf.dbUrl.toString)
    val ret = sqlUtil.execQuery(s"select ${Conf.blogIdCol.toString} as blogId from ${Conf.blogTbl.toString} limit ${limit}")
    val blogIds = new ArrayBuffer[Int]()
    while (ret.next()) blogIds.append(ret.getInt(1))
    sqlUtil.jdbcClose()
    docsNum = blogIds.size
    sc.parallelize(blogIds, parallelism)
  }

  def getBlogTextRdd(rdd: RDD[Int]): RDD[(Int, String)] = {
    val url = Conf.dbUrl
    val textCol = Conf.blogTextCol
    val tbl = Conf.blogTbl
    val idCol = Conf.blogIdCol
    rdd.mapPartitions(iter=> {
      println(s"getBlogTextRdd ***")
      val sqlUtil = new SqlUtils(url)
//      val sqlUtil = new SqlUtils("jdbc:mysql://localhost:3306/ytex?user=root&password=root")
      val texts = for (id <- iter) yield {
        val ret = sqlUtil.execQuery(s"select ${textCol} as blogText from ${tbl} where ${idCol}=${id} limit 1")
//        val ret = sqlUtil.execQuery(s"select chosenanswer as blogText from tmp_org_yahoo limit 1")
        ret.next()
        (id, ret.getString(1))
      }
      sqlUtil.jdbcClose()
      texts
    }).map(text=>Nlp.textPreprocess(text._1,text._2))
  }

  def getSentRdd(textRdd: RDD[(Int, String)])  = {
    val ret = textRdd.mapPartitions(itr => {
      println(s"getSentRdd ***")
      val sents = for (blog <- itr) yield Nlp.generateSentence(blog._1,blog._2,null)
      sents
    })
    ret
  }

  def getNgramRdd(sentRdd: RDD[Array[Sentence]], tfFilterInPartition:Int=3, firstStageNgram: Broadcast[mutable.HashMap[String, Ngram]]=null): RDD[Ngram]= {
    val ret = sentRdd.mapPartitions(itr => {
      println(s"getNgramRdd ***")
      val hNgrams = new mutable.LinkedHashMap[String,Ngram]()
      val gramId = new AtomicInteger()
      itr.foreach(sents => {
        gramId.set(0)
        if (firstStageNgram == null) {
          Nlp.generateNgram(sents, gramId, hNgrams)
        }else{
          Nlp.generateNgramStage2(sents,gramId,hNgrams,firstStageNgram)
        }
      })
      val sNgrams = hNgrams.values.toSeq.filter(_.tfAll>tfFilterInPartition)
      trace(INFO,s"grams number before reduce (in this partition) > ${tfFilterInPartition} is ${sNgrams.size}")
      sNgrams.foreach(_.getNestInfo(sNgrams))
      sNgrams.iterator
    })
    ret
  }

  def getTrainNgramRdd():RDD[Ngram] = {
    if (Conf.clusteringFromFile == false) {
      val rdd = this.getBlogIdRdd(Conf.partitionNumber)
      val rddText = this.getBlogTextRdd(rdd)
      val rddSent = this.getSentRdd(rddText).persist()
      val docNumber = this.docsNum
      val rddNgram = this.getNgramRdd(rddSent, Conf.partitionTfFilter)
        .map(gram => (gram.text, gram))
        .reduceByKey(_ + _)
        //.sortByKey()
        .map(_._2)
        .filter(_.tfAll > Conf.stag1TfFilter)
        .mapPartitions(itr => Ngram.updateAfterReduce(itr, docNumber, false))
        .filter(_.cvalue > Conf.stag1CvalueFilter)
      //.persist()

      //rddNgram.foreach(gram => println(f"${gram.tfdf}%.2f\t${log2(gram.cvalue+1)}%.2f\t${gram}"))
      //println(s"number of gram is ${rddNgram.count}")

      val firstStageRet = new mutable.HashMap[String, Ngram]()
      if (Conf.topTfNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.tfAll * -1).take(Conf.topTfNgram).map(gram => (gram.text, gram))
      else if (Conf.topTfdfNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.tfdf * -1).take(Conf.topTfdfNgram).map(gram => (gram.text, gram))
      else if (Conf.topCvalueNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.cvalue * -1).take(Conf.topCvalueNgram).map(gram => (gram.text, gram))
      else
        firstStageRet ++= rddNgram.collect().map(gram => (gram.text, gram))

      //firstStageRet.foreach(println)
      val firstStageNgram = sc.broadcast(firstStageRet)

      val rddNgram2 = this.getNgramRdd(rddSent, 0, firstStageNgram)
        .map(gram => (gram.text, gram))
        .reduceByKey(_ + _)
        //.sortByKey()
        .map(_._2)
        .filter(_.tfAll > Conf.stag2TfFilter)
        .mapPartitions(itr => Ngram.updateAfterReduce(itr, docNumber, true))
        .filter(_.cvalue > Conf.stag2CvalueFilter)
      rddSent.unpersist()
      if(Conf.ngramSaveFile.trim.length>0) {
        Utils.writeObjectToFile(Conf.ngramSaveFile, rddNgram2.collect())
      }

      if (Conf.trainNgramCnt>0) {
        val tmp = sc.parallelize( rddNgram2.take(Conf.trainNgramCnt), Conf.partitionNumber)
        trainSampleMark(tmp)
      }else{
        trainSampleMark(rddNgram2)
      }

    }else {
      println(s"start load ngram from file:")
      var ngrams = Utils.readObjectFromFile[Array[Ngram]](Conf.ngramSaveFile)
      if (Conf.trainNgramCnt>0)ngrams = ngrams.take(Conf.trainNgramCnt)
      val rddNgram2 = sc.parallelize(ngrams, Conf.partitionNumber)
      trainSampleMark(rddNgram2)
    }
  }

  /**
   * Just mark a Ngram as 'trainning' status, do not remove the non-training Ngram.
   * @param ngramRdd
   * @return
   */
  def trainSampleMark(ngramRdd: RDD[Ngram]):RDD[Ngram] = {
      ngramRdd.map(ngram=>{
        if (Conf.testSample>0) {
          if (Utils.random.nextInt(100) >= Conf.testSample && (!Conf.trainOnlyChv || ngram.isUmlsTerm(true)))
            ngram.isTrain = true
          else
            ngram.isTrain = false
        }else{
          if (!Conf.trainOnlyChv || ngram.isUmlsTerm(true)) {
            ngram.isTrain = true
          }else{
            ngram.isTrain = false
          }
        }
        ngram
      })
  }
  /**
   *
   * @param rddNgram
   * @return
   */
  def getVectorRdd(rddNgram: RDD[Ngram]): RDD[(Ngram,Vector)] = {
    val tmp_vecter = rddNgram.map(gram => {
      val feature = new ArrayBuffer[Double]()
      if(Conf.useFeatures.contains("tfdf"))feature.append(gram.tfdf)     // tfdf
      if(Conf.useFeatures.contains("cvalue"))feature.append(log2(gram.cvalue+1)) // c-value, applied a log function

      if(Conf.useFeatures.contains("umls_score"))feature.append(gram.umlsScore._1/100) // simple similarity to umls
      if(Conf.useFeatures.contains("contain_umls"))feature.append(bool2Double(gram.isContainInUmls))
      if(Conf.useFeatures.contains("contain_chv"))feature.append(bool2Double(gram.isContainInChv))
      if(Conf.useFeatures.contains("chv_score"))feature.append(gram.umlsScore._2/100) //simple similarity to chv

      if(Conf.useFeatures.contains("nn"))feature.append(bool2Double(gram.isPosNN))
      if(Conf.useFeatures.contains("an"))feature.append(bool2Double(gram.isPosAN))
      if(Conf.useFeatures.contains("pn"))feature.append(bool2Double(gram.isPosPN))
      if(Conf.useFeatures.contains("anpn"))feature.append(bool2Double(gram.isPosANPN))

      if(Conf.useFeatures.contains("stys"))feature.appendAll(gram.stys.map(bool2Double(_)))
      if(Conf.useFeatures.contains("win_pos"))feature.appendAll(gram.context.win_pos.map(p=>log2(p+1)))

      if(Conf.useFeatures.contains("capt_first"))feature.append(log2(gram.capt_first/gram.tfAll+1))
      if(Conf.useFeatures.contains("capt_all"))feature.append(log2(gram.capt_all/gram.tfAll+1))
      if(Conf.useFeatures.contains("capt_term"))feature.append(log2(gram.capt_term/gram.tfAll+1))

      if(Conf.useFeatures.contains("win_umls"))feature.append(log2(gram.context.win_umlsCnt+1))
      if(Conf.useFeatures.contains("win_chv"))feature.append(log2(gram.context.win_chvCnt+1))
      if(Conf.useFeatures.contains("sent_umls"))feature.append(log2(gram.context.sent_umlsCnt+1))
      if(Conf.useFeatures.contains("sent_chv"))feature.append(log2(gram.context.sent_chvCnt+1))
      if(Conf.useFeatures.contains("umls_dist"))feature.append(log2(gram.context.umlsDist+1))
      if(Conf.useFeatures.contains("chv_dist"))feature.append(log2(gram.context.chvDist+1))

      //(gram,Vectors.dense(feature.toArray))
      (gram,feature)
    })
    if (Conf.normalizeFeature) {
      tmp_vecter.persist()
      // get minimum
      val min = tmp_vecter.map(_._2).reduce((a1, a2) => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(a1.size)(0.0))
        Range(0, a1.size).foreach(index => {
          f(index) = if (a1(index) < a2(index)) a1(index) else a2(index)
        })
        f
      })
      // get maximum
      val max = tmp_vecter.map(_._2).reduce((a1, a2) => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(a1.size)(0.0))
        Range(0, a1.size).foreach(index => {
          f(index) = if (a1(index) > a2(index)) a1(index) else a2(index)
        })
        f
      })
      // normalization to (0,1)
      val vecter = tmp_vecter.map(kv => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(min.size)(0.0))
        val a = kv._2
        Range(0, a.size).foreach(index => {
          f(index) = if (max(index) != min(index))
            (a(index) - min(index)) / (max(index) - min(index))
          else
            a(index)
        })
        (kv._1, Vectors.dense(f.toArray))
      })

      //
      tmp_vecter.unpersist()
      vecter
    }else{
      tmp_vecter.map(kv=>(kv._1,Vectors.dense(kv._2.toArray)))
    }
  }
}

object Clustering {
  def main (args: Array[String]): Unit = {
    // init spark
    val startTime = new Date()
    val conf = new SparkConf()
      .setAppName("NLP")
    if (Conf.sparkMaster.length>0)
      conf .setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    val clustering = new Clustering(sc)

    val rddNgram4Train = if (Conf.trainedNgramFilterPosRegex.length>0)
        clustering.getTrainNgramRdd().filter(gram=>Ngram.TrainedNgramFilterPosRegex.matcher(gram.posString).matches()).persist()
      else
        clustering.getTrainNgramRdd().persist()
    val ngramCntAll = rddNgram4Train.count()
    val ngramCntChv = rddNgram4Train.filter(_.isUmlsTerm(true)).count()
    val ngramCntChvTest = rddNgram4Train.filter(g=>g.isUmlsTerm(true)&& !g.isTrain).count()

    println(s"** ngramCntAll ${ngramCntAll} ngramCntChv ${ngramCntChv}  ngramCntChvTest ${ngramCntChvTest} **")
    if (Conf.showOrgNgramNum>0)rddNgram4Train.filter(gram => {
      Conf.showOrgNgramOfN.contains(gram.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(gram.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(gram.text).matches()
    }).take(Conf.showOrgNgramNum).foreach(gram => println(f"${gram.toStringVector()}"))

    if (Conf.runKmeans) {
      val rddVector = clustering.getVectorRdd(rddNgram4Train.filter(g=>g.isTrain)).persist()

      if (Conf.showOrgNgramNum>0)rddVector.filter(kv => {
        Conf.showOrgNgramOfN.contains(kv._1.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(kv._1.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(kv._1.text).matches()
      }).take(Conf.showOrgNgramNum).foreach(v => println(f"${v._1.text}%-15s\t${v._2.toArray.map(f => f"${f}%.2f").mkString("\t")}"))

      val rddVectorDbl = rddVector.map(_._2).persist()
      rddVector.unpersist()
      val ngramCntTrain = rddVectorDbl.count

      var model: KMeansModel = null

      val kCost = for (k <- Range(Conf.k_start, Conf.k_end+1, Conf.k_step)) yield {
        val startTimeTmp = new Date();
        model = KMeans.train(rddVectorDbl, k, Conf.maxIterations, Conf.runs)
        val cost = model.computeCost(rddVectorDbl)
        System.out.println(s"###single kMeans used time: " + (new Date().getTime() - startTimeTmp.getTime()) + " ###")
        println(s"###kcost#### $k $cost" )

        /**
         * predict the center of each point.
         */
        var recallVsRank: Array[Double]=null
        var precisionVsRank: Array[Double]=null
        var fscoreVsRank: Array[Double]=null
        if (Conf.runPredict) {
          // TODO choose a "best" k?

          val retPridict = rddVectorDbl.map(v => model.predict(v))
          /**
           * Get the cost of every point to its closest center, and rand the points by these cost.
           * In fact, it looks like using clustering to get classification goal.
           **/
          if (Conf.runRank) {
            val rddVector_all = clustering.getVectorRdd(if (Conf.rankWithTrainData) rddNgram4Train else rddNgram4Train.filter(_.isTrain==false)).persist()
            /* exclude the centers that contain small number of ngram, compare to the average number of each cluster*/
            val retPredictFiltered = retPridict.map(k => (k, 1L)).reduceByKey(_ + _).collect.filter(kv => {
              val filter = k * kv._2 * 100.0 / ngramCntTrain > Conf.clusterThresholdPt
              if (filter == false) println(s"cluster ${kv._1} has ${kv._2} ngram, less than ${Conf.clusterThresholdPt}% of train ${ngramCntTrain}/${k}=${ngramCntTrain/k}, so it is excluded.")
              filter
            }).map(_._1)
            // get new centers
            val newCenter = model.clusterCenters.filter(v => {
              retPredictFiltered.contains(model.clusterCenters.indexOf(v))
            })
            // update model
            model = new KMeansModel(newCenter)

            val ngramCntRank = rddVector_all.count()
            val bcCenters = MyKmean.broadcastMode(rddVector_all.context, model)
            val ret = rddVector_all.map(kv => (kv._1, MyKmean.computeCost(bcCenters.value, kv._2))).sortBy(_._2._2).collect
            var cnt = 0
            var cntUmls = 0
            var cntChvAll = 0
            var cntChvTest= 0
            recallVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
            precisionVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
            fscoreVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
            ret.foreach(kkvv => {
              val kk = kkvv._2._1
              val cost = kkvv._2._2
              val ngram = kkvv._1
              cnt += 1
              val rankBase =
                if (Conf.rankLevelBase>0){
                  Conf.rankLevelBase
                } else if (ngramCntChvTest>0)
                  ngramCntChvTest
                else
                  ngramCntRank
              val topPercent = 1.0*cnt/rankBase*100

              if (ngram.isUmlsTerm(true)) {
                cntChvAll += 1
                if (!ngram.isTrain)cntChvTest += 1
                if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.1f\tchv\t")
              }else if (ngram.isUmlsTerm(false)) {
                cntUmls += 1
                if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.1f\tumls\t")
              }else {
                if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.1f\tother\t")
              }
              val rankLevel = topPercent.floor.toInt/Conf.rankGranular
              val recall = if (ngramCntChvTest>0)1.0*cntChvTest/ngramCntChvTest else -1
              val precision = 1.0*cntChvAll/cnt
              val precision_umls = 1.0*cntChvAll/cnt
              val fscore = (1+Conf.fscoreBeta*Conf.fscoreBeta)*(precision*recall)/(Conf.fscoreBeta*Conf.fscoreBeta*precision+recall)
              if(rankLevel>0 && rankLevel<=Conf.rankLevelNumber && recallVsRank(rankLevel-1)<0) {
                recallVsRank(rankLevel - 1) = recall*100
                precisionVsRank(rankLevel - 1) = precision*100
                fscoreVsRank(rankLevel - 1) = fscore
              }
              if (Conf.showDetailRankPt>=topPercent)print(f"${topPercent}%.1f\t${recall*100}%.2f\t${precision*100}%.2f\t${fscore}%.2f\t${precision_umls*100}%.2f\t")
              if (Conf.showDetailRankPt>=topPercent)println(ngram.toStringVector())

            })
          }

        }
        (k, cost, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank, precisionVsRank,fscoreVsRank)
      }

      println(s"#### result for all k: k(${Conf.k_start},${Conf.k_end},${Conf.k_step}, rankGranular is ${Conf.rankGranular} ####")
      kCost.foreach(kc => println(f"${kc._1}\t${kc._2}%.1f\t${kc._3}\t${kc._4}\t${kc._5}\t${kc._6.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._7.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._8.map(v=>f"${v}%.2f").mkString("\t")}"))
    }

    println("*******result is ******************")
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())+" ###")
  }
}