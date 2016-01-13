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

  def getBlogIdRdd(parallelism: Int): RDD[Long] = {
  val limit = Conf.blogLimit
  val sqlUtil = new SqlUtils(Conf.dbUrl.toString)
    val ret = sqlUtil.execQuery(s"select distinct ${Conf.blogIdCol.toString} as blogId from ${Conf.blogTbl.toString} limit ${limit}")
    val blogIds = new ArrayBuffer[Long]()
    while (ret.next()) blogIds.append(ret.getLong(1))
    sqlUtil.jdbcClose()
    docsNum = blogIds.size
    sc.parallelize(blogIds, parallelism)
  }

  def getBlogTextRdd(rdd: RDD[Long]): RDD[(Long, String)] = {
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

  def getSentRdd(textRdd: RDD[(Long, String)])  = {
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
        if (Conf.bagsOfWord) {
          Utils.writeObjectToFile(Conf.ngramSaveFile, rddNgram2.collect())
          Utils.writeObjectToFile(Conf.ngramSaveFile + ".no_bow", rddNgram2.map(g=>{
            g.context.wordsInbags=null;
            g
          }).collect())
        }else{
          Utils.writeObjectToFile(Conf.ngramSaveFile, rddNgram2.collect())
        }
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
     /* Utils.writeObjectToFile(Conf.ngramSaveFile + ".no_bow", ngrams.map(g=>{
        g.context.wordsInbags=null;
        g
      }))
      sys.exit(0)*/

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
  def getVectorRdd(rddNgram: RDD[Ngram], useFeatureWeight: Array[(String,Double)], useUmlsContextFeature: Boolean=false): RDD[(Ngram,Vector)] = {



    // get the weight for the vector !!! the order should be the same as constructing the vector !!!!
    val vectorWeight:ArrayBuffer[Double] = new ArrayBuffer[Double]()
    val useFeature = useFeatureWeight.map(_._1)
    val useWeight = useFeatureWeight.map(_._2)
    val gram = rddNgram.take(1)(0)
    if (!Conf.bagsOfWord) {
      if (useFeature.contains("tfdf")) vectorWeight.append(useWeight(useFeature.indexOf("tfdf"))) else vectorWeight.append(0) // tfdf
      if (useFeature.contains("cvalue")) vectorWeight.append(useWeight(useFeature.indexOf("cvalue"))) else vectorWeight.append(0) // c-value, applied a log function

      if (useFeature.contains("umls_score")) vectorWeight.append(useWeight(useFeature.indexOf("umls_score"))) else vectorWeight.append(0) // simple similarity to umls
      if (useFeature.contains("contain_umls")) vectorWeight.append(useWeight(useFeature.indexOf("contain_umls"))) else vectorWeight.append(0)
      if (useFeature.contains("contain_chv")) vectorWeight.append(useWeight(useFeature.indexOf("contain_chv"))) else vectorWeight.append(0)
      if (useFeature.contains("chv_score")) vectorWeight.append(useWeight(useFeature.indexOf("chv_score"))) else vectorWeight.append(0) //simple similarity to chv

      if (useFeature.contains("nn")) vectorWeight.append(useWeight(useFeature.indexOf("nn"))) else vectorWeight.append(0)
      if (useFeature.contains("an")) vectorWeight.append(useWeight(useFeature.indexOf("an"))) else vectorWeight.append(0)
      if (useFeature.contains("pn")) vectorWeight.append(useWeight(useFeature.indexOf("pn"))) else vectorWeight.append(0)
      if (useFeature.contains("anpn")) vectorWeight.append(useWeight(useFeature.indexOf("anpn"))) else vectorWeight.append(0)

      if (useFeature.contains("stys")) vectorWeight.appendAll(gram.stys.map(_ => useWeight(useFeature.indexOf("stys")))) else vectorWeight.appendAll(gram.stys.map(_ => 0.0))
      if (useFeature.contains("win_pos")) vectorWeight.appendAll(gram.context.win_pos.map(p => useWeight(useFeature.indexOf("win_pos")))) else vectorWeight.appendAll(gram.stys.map(_ => 0.0))

      if (useFeature.contains("capt_first")) vectorWeight.append(useWeight(useFeature.indexOf("capt_first"))) else vectorWeight.append(0)
      if (useFeature.contains("capt_all")) vectorWeight.append(useWeight(useFeature.indexOf("capt_all"))) else vectorWeight.append(0)
      if (useFeature.contains("capt_term")) vectorWeight.append(useWeight(useFeature.indexOf("capt_term"))) else vectorWeight.append(0)

      if (useFeature.contains("win_umls")) vectorWeight.append(useWeight(useFeature.indexOf("win_umls"))) else if (useUmlsContextFeature) vectorWeight.append(0)
      if (useFeature.contains("win_chv")) vectorWeight.append(useWeight(useFeature.indexOf("win_chv"))) else if (useUmlsContextFeature) vectorWeight.append(0)
      if (useFeature.contains("sent_umls")) vectorWeight.append(useWeight(useFeature.indexOf("sent_umls"))) else if (useUmlsContextFeature) vectorWeight.append(0)
      if (useFeature.contains("sent_chv")) vectorWeight.append(useWeight(useFeature.indexOf("sent_chv"))) else if (useUmlsContextFeature) vectorWeight.append(0)
      if (useFeature.contains("umls_dist")) vectorWeight.append(useWeight(useFeature.indexOf("umls_dist"))) else if (useUmlsContextFeature) vectorWeight.append(0)
      if (useFeature.contains("chv_dist")) vectorWeight.append(useWeight(useFeature.indexOf("chv_dist"))) else if (useUmlsContextFeature) vectorWeight.append(0)

      if (useFeature.contains("prefix")) vectorWeight.appendAll(Nlp.prefixs.map(_ => useWeight(useFeature.indexOf("prefix"))))
      if (useFeature.contains("suffix")) vectorWeight.appendAll(Nlp.suffixs.map(_ => useWeight(useFeature.indexOf("suffix"))))
      println(s"size weight ${vectorWeight.size}")
    } else {
      if (gram.context.wordsInbags == null) {
        println("You configured bagsOfWords enable, but there is no bagsOfWords info in ngram.")
        sys.exit(1)
      };
      if (Conf.bowTopCvalueNgram>gram.context.wordsInbags.size) {
        println(s"Warning: You specify Conf.bowTopCvalueNgram=${Conf.bowTopCvalueNgram}, but only ${gram.context.wordsInbags.size} available")
        Conf.bowTopCvalueNgram = gram.context.wordsInbags.size
      }
      vectorWeight.appendAll(gram.context.wordsInbags.map(_=>1.0).take(Conf.bowTopCvalueNgram))
    }
    println(s"* the weight for the feature vecotr is ${vectorWeight.mkString(",")} *")
    //println(Nlp.wordsInbags.mkString("\t"))

    val tmp_vecter = rddNgram.map(gram => {
      val feature = new ArrayBuffer[Double]()
      if (!Conf.bagsOfWord) {
        if (useFeature.contains("tfdf")) feature.append(gram.tfdf) else feature.append(0) // tfdf
        if (useFeature.contains("cvalue")) feature.append(log2p1(gram.cvalue)) else feature.append(0) // c-value, applied a log function

        if (useFeature.contains("umls_score")) feature.append(gram.umlsScore._1 / 100) else feature.append(0) // simple similarity to umls
        if (useFeature.contains("contain_umls")) feature.append(bool2Double(gram.isContainInUmls)) else feature.append(0)
        if (useFeature.contains("contain_chv")) feature.append(bool2Double(gram.isContainInChv)) else feature.append(0)
        if (useFeature.contains("chv_score")) feature.append(gram.umlsScore._2 / 100) else feature.append(0) //simple similarity to chv

        if (useFeature.contains("nn")) feature.append(bool2Double(gram.isPosNN)) else feature.append(0)
        if (useFeature.contains("an")) feature.append(bool2Double(gram.isPosAN)) else feature.append(0)
        if (useFeature.contains("pn")) feature.append(bool2Double(gram.isPosPN)) else feature.append(0)
        if (useFeature.contains("anpn")) feature.append(bool2Double(gram.isPosANPN)) else feature.append(0)

        if (useFeature.contains("stys")) feature.appendAll(gram.stys.map(bool2Double(_))) else feature.appendAll(gram.stys.map(_ => 0.0))
        if (useFeature.contains("win_pos")) feature.appendAll(gram.context.win_pos.map(p => log2p1(p))) else feature.appendAll(gram.context.win_pos.map(p => 0.0))

        if (useFeature.contains("capt_first")) feature.append(log2p1(1.0*gram.capt_first / gram.tfAll)) else feature.append(0)
        if (useFeature.contains("capt_all")) feature.append(log2p1(1.0*gram.capt_all / gram.tfAll)) else feature.append(0)
        if (useFeature.contains("capt_term")) feature.append(log2p1(1.0*gram.capt_term / gram.tfAll)) else feature.append(0)

        if (useFeature.contains("win_umls")) feature.append(log2p1(gram.context.win_umlsCnt)) else if (useUmlsContextFeature) feature.append(0)
        if (useFeature.contains("win_chv")) feature.append(log2p1(gram.context.win_chvCnt)) else if (useUmlsContextFeature) feature.append(0)
        if (useFeature.contains("sent_umls")) feature.append(log2p1(gram.context.sent_umlsCnt)) else if (useUmlsContextFeature) feature.append(0)
        if (useFeature.contains("sent_chv")) feature.append(log2p1(gram.context.sent_chvCnt)) else if (useUmlsContextFeature) feature.append(0)
        if (useFeature.contains("umls_dist")) feature.append(log2p1(gram.context.umlsDist)) else if (useUmlsContextFeature) feature.append(0)
        if (useFeature.contains("chv_dist")) feature.append(log2p1(gram.context.chvDist)) else if (useUmlsContextFeature) feature.append(0)

        if (useFeature.contains("prefix")) feature.appendAll(gram.context.win_prefix.map(p => log2p1(1.0*p / gram.tfAll)))
        if (useFeature.contains("suffix")) feature.appendAll(gram.context.win_suffix.map(p => log2p1(1.0*p / gram.tfAll)))
        //println(s"NLP prefix ${Nlp.prefixs.size} suffix ${Nlp.suffixs.size} prefix ${gram.context.win_prefix.size}, suffix ${gram.context.win_suffix.size} f ${feature.size} weight ${vectorWeight.size}")

      }else{
        feature.appendAll(gram.context.wordsInbags.take(Conf.bowTopCvalueNgram).map(p => log2p1(1.0*p / gram.tfAll)))
      }
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
      println("the maximum of the features: "+max.mkString("\t"))
      // normalization to (0,1), then apply the weight of the features
      val vecter = tmp_vecter.map(kv => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(min.size)(0.0))
        val a = kv._2
        Range(0, a.size).foreach(index => {
          f(index) = if (max(index) != min(index))
            (a(index) - min(index)) / (max(index) - min(index))
          else
            a(index)
          // apply the weight to the feature
          f(index) *= vectorWeight(index)
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

  /**
   * Rank the ngam based on a cost value. current, we have 3 types of cost:
   * 1: kmeans: the distance between a ngram and its nearest centre.
   * 2. tfAll: the inverse of the term frequency(1/tf)
   * 3: cvalue: the inverse of the cvalue(1/cvalue)
   * @param rankType: based on the cost of 'kmeans', 'tfAlll', or 'cvalue'
   * @param modelOrg: the model of kmeans
   * @param rddVectorDbl: the double value vector
   * @param rddRankVector_all:
   * @param ngramCntChvTest: the chv number in the test data
   * 
   */
  def rank (k:Int,rankType: String, modelOrg: KMeansModel,rddVectorDbl: RDD[Vector], rddRankVector_all:RDD[(Ngram, Vector)], ngramCntChvTest:Long) = {

    val ngramCntTrain = rddVectorDbl.count

    /**
     * Get the cost of every point to its closest center, and rand the points by these cost.
     * In fact, it looks like using clustering to get classification goal.
     **/
    val ret = if (rankType.equals("kmeans")) {
      val retPridict = rddVectorDbl.map(v => modelOrg.predict(v))
      /* exclude the centers that contain small number of ngram, compare to the average number of each cluster*/
      val retPredictFiltered = retPridict.map(k => (k, 1L)).reduceByKey(_ + _).collect.filter(kv => {
        val filter = k * kv._2 * 100.0 / ngramCntTrain > Conf.clusterThresholdPt
        if (filter == false) println(s"cluster ${kv._1} has ${kv._2} ngram, less than ${Conf.clusterThresholdPt}% of train ${ngramCntTrain}/${k}=${ngramCntTrain / k}, so it is excluded.")
        filter
      }).map(_._1)
      // get new centers
      val newCenter = modelOrg.clusterCenters.filter(v => {
        retPredictFiltered.contains(modelOrg.clusterCenters.indexOf(v))
      })
      // update model
      val model = new KMeansModel(newCenter)

      val bcCenters = MyKmean.broadcastMode(rddRankVector_all.context, model)

      /** predict the center of each point. */
      rddRankVector_all.map(kv => (kv._1, MyKmean.computeCost(bcCenters.value, kv._2))).sortBy(_._2._2).collect
    } else if (rankType.equals("tfAll") || rankType.equals("tfall")) {
      rddRankVector_all.map(kv => (kv._1, (-1, 1.0/kv._1.tfAll))).sortBy(_._2._2).collect
    }else { //"cvalue"
      rddRankVector_all.map(kv => (kv._1, (-2, 1.0/kv._1.cvalue))).sortBy(_._2._2).collect
    }
    
    
    var cnt = 0
    var cntUmls = 0
    var cntChvAll = 0
    var cntChvTest= 0
    val rankBase =
      if (Conf.rankLevelBase>0){
        Conf.rankLevelBase
      } else if (ngramCntChvTest>0)
        ngramCntChvTest
      else
        rddRankVector_all.count()

    val recallVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
    val precisionVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
    val precisionUmlsVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
    val fscoreVsRank = Array.fill(Conf.rankLevelNumber)(-1.0)
    ret.foreach(kkvv => {
      val kk = kkvv._2._1
      val cost = kkvv._2._2
      val ngram = kkvv._1
      cnt += 1
      val topPercent = 1.0*cnt/rankBase*100

      if (ngram.isUmlsTerm(true)) {
        cntChvAll += 1
        if (!ngram.isTrain)cntChvTest += 1
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\tchv\t")
      }else if (ngram.isUmlsTerm(false)) {
        cntUmls += 1
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\tumls\t")
      }else {
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\tother\t")
      }
      val rankLevel = topPercent.floor.toInt/Conf.rankGranular
      val recall = if (ngramCntChvTest>0)1.0*cntChvTest/ngramCntChvTest else -1
      val precision = 1.0*cntChvTest/cnt
      val precision_umls = 1.0*cntUmls/cnt
      val fscore = if(precision+recall==0) 0 else (1+Conf.fscoreBeta*Conf.fscoreBeta)*(precision*recall)/(Conf.fscoreBeta*Conf.fscoreBeta*precision+recall)
      if(rankLevel>0 && rankLevel<=Conf.rankLevelNumber && recallVsRank(rankLevel-1)<0) {
        recallVsRank(rankLevel - 1) = recall*100
        precisionVsRank(rankLevel - 1) = precision*100
        precisionUmlsVsRank(rankLevel - 1) = precision_umls*100
        fscoreVsRank(rankLevel - 1) = fscore
      }
      if (Conf.showDetailRankPt>=topPercent)print(f"${topPercent}%.1f\t${recall*100}%.2f\t${precision*100}%.2f\t${fscore}%.2f\t${precision_umls*100}%.2f\t")
      if (Conf.showDetailRankPt>=topPercent)println(ngram.toStringVector())
    })
    println(f"type ${rankType},${k}\t${ngramCntChvTest}\t${recallVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${precisionVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${fscoreVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${precisionUmlsVsRank.map(v=>f"${v}%.2f").mkString("\t")}")
    println(f"MAX OF recall ${recallVsRank.max}%.2f\tprecision ${precisionVsRank.max}%.2f\tfscore ${fscoreVsRank.max}%.2f\tprecision_umls ${precisionUmlsVsRank.max}%.2f")

    (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank)
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
    val Seed = new Date().getTime

    val clustering = new Clustering(sc)

    val rddNgram4Train = if (Conf.trainedNgramFilterPosRegex.length>0)
        clustering.getTrainNgramRdd().filter(gram=>Ngram.TrainedNgramFilterPosRegex.matcher(gram.posString).matches()==false).persist()
      else
        clustering.getTrainNgramRdd().persist()
    val ngramCntAll = rddNgram4Train.count()
    val ngramCntChv = rddNgram4Train.filter(_.isUmlsTerm(true)).count()
    val ngramCntUmls = rddNgram4Train.filter(_.isUmlsTerm(false)).count()
    val ngramCntChvTest = rddNgram4Train.filter(g=>g.isUmlsTerm(true)&& !g.isTrain).count()

    println(s"** ngramCntAll ${ngramCntAll} ngramCntUmls ${ngramCntUmls} ngramCntChv ${ngramCntChv}  ngramCntChvTest ${ngramCntChvTest} **")
    if (Conf.showOrgNgramNum>0)rddNgram4Train.filter(gram => {
      Conf.showOrgNgramOfN.contains(gram.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(gram.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(gram.text).matches()
    }).takeSample(false,Conf.showOrgNgramNum,Seed).foreach(gram => println(f"${gram.toStringVector()}"))

    if (Conf.runKmeans) {
      val rddVector = clustering.getVectorRdd(rddNgram4Train.filter(g=>g.isTrain), Conf.useFeatures4Train,Conf.useUmlsContextFeature).persist()
      val rddRankVector_all = clustering.getVectorRdd(if (Conf.rankWithTrainData) rddNgram4Train else {rddNgram4Train.filter(_.isTrain==false)}, Conf.useFeatures4Test, Conf.useUmlsContextFeature).persist()
      rddNgram4Train.unpersist()

      if (Conf.showOrgNgramNum>0)rddVector.filter(kv => {
        Conf.showOrgNgramOfN.contains(kv._1.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(kv._1.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(kv._1.text).matches()
      }).takeSample(false,Conf.showOrgNgramNum,Seed).foreach(v => println(f"${v._1.text}%-15s\t${v._2.toArray.map(f => f"${f}%.2f").mkString("\t")}"))

      val rddVectorDbl = rddVector.map(_._2).persist()
      rddVector.unpersist()

      var model: KMeansModel = null

      val kCost = for (k <- Range(Conf.k_start, Conf.k_end+1, Conf.k_step)) yield {
        val startTimeTmp = new Date();
        model = KMeans.train(rddVectorDbl, k, Conf.maxIterations, Conf.runs)
        val cost = model.computeCost(rddVectorDbl)
        System.out.println(s"###single kMeans used time: " + (new Date().getTime() - startTimeTmp.getTime()) + " ###")
        println(s"###kcost#### $k $cost" )

        val (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank) = clustering.rank(k,"kmeans",model,rddVectorDbl,rddRankVector_all, ngramCntChvTest)
        val kc = (k, cost, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank, precisionVsRank,fscoreVsRank,precisionUmlsVsRank)
        kc
      }

      if (Conf.bagsOfWord)
        println(s"#### result for all k: k(${Conf.k_start},${Conf.k_end},${Conf.k_step}, rankGranular is ${Conf.rankGranular}, feature: ${Conf.useFeatures4Train.mkString(",")} ####")
      else
        println(s"#### result for all k: k(${Conf.k_start},${Conf.k_end},${Conf.k_step}, rankGranular is ${Conf.rankGranular}, feature: ${Conf.useFeatures4Train.mkString(",")} ####")
      kCost.foreach(kc => println(f"${kc._1}\t${kc._2}%.1f\t${kc._3}\t${kc._4}\t${kc._5}\t${kc._6.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._7.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._8.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._6.max}%.2f\t${kc._7.max}%.2f\t${kc._8.max}%.2f\t${kc._9.max}%.2f"))

      if (Conf.baseLineRank) {
        val (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank) = clustering.rank(-1,"tfAll", model, rddVectorDbl, rddRankVector_all, ngramCntChvTest)
        val kc = ("tfAll",0, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank, precisionVsRank,fscoreVsRank,precisionUmlsVsRank)
        val (recallVsRank2, precisionVsRank2, fscoreVsRank2, precisionUmlsVsRank2) = clustering.rank(-2,"cvalue", model, rddVectorDbl, rddRankVector_all, ngramCntChvTest)
        val kCostBase = kc :: ("cvalue",0, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank2, precisionVsRank2,fscoreVsRank2,precisionUmlsVsRank2) :: Nil
        println(s"#### result for base line: base type(tfall,cvalue), rankGranular is ${Conf.rankGranular} ####")
        kCostBase.foreach(kc => println(f"${kc._1}\t${kc._2}%.1f\t${kc._3}\t${kc._4}\t${kc._5}\t${kc._6.map(v => f"${v}%.2f").mkString("\t")}\t${kc._7.map(v => f"${v}%.2f").mkString("\t")}\t${kc._8.map(v => f"${v}%.2f").mkString("\t")}\t${kc._6.max}%.2f\t${kc._7.max}%.2f\t${kc._8.max}%.2f\t${kc._9.max}%.2f"))
      }
    }

    println("*******result is ******************")
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())+" ###")
  }
}