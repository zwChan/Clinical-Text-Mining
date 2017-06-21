package com.votors.ml

import java.io.FileWriter
import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.votors.common.SqlUtils
import org.apache.log4j.{Level, Logger}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import java.util.{Date, Properties}

import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import com.votors.common.Utils._
import com.votors.common.Utils.Trace._
import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}
import org.apache.spark.mllib.clustering._
import com.votors.common._
import org.apache.spark.storage.StorageLevel


/**
 * Created by Jason on 2015/11/9 0009.
 */

class Clustering(sc: SparkContext) {
  var docsNum = 0L
  var trainNum = 0L
//  var allNum = 0L
  val columnName = new ArrayBuffer[String]()

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
    //docsNum = blogIds.size
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
      //val sqlUtil = new SqlUtils("jdbc:mysql://localhost:3306/ytex?user=root&password=root")
      val texts = for (id <- iter) yield {
        val ret = sqlUtil.execQuery(s"select ${textCol} as blogText from ${tbl} where ${idCol}=${id} limit 1")
        //val ret = sqlUtil.execQuery(s"select chosenanswer as blogText from tmp_org_yahoo limit 1")
        ret.next()
        (id, ret.getString(1))
      }
      sqlUtil.jdbcClose()
      texts
    }).map(text=>Nlp.textPreprocess(text._1,text._2))
  }

  def getTextRddFromDir(dir:String):RDD[(Long,String)] = {
    val word2vec = new Word2vec(sc,dir)
      word2vec.getTextRdd().zipWithUniqueId().map(kv=>(kv._2,kv._1))
  }

  def getSentRdd(textRdd: RDD[(Long, String)])  = {
    val ret = textRdd.mapPartitions(itr => {
      println(s"getSentRdd ***")
      val sents = for (blog <- itr) yield Nlp.generateSentence(blog._1,blog._2.replaceAll("[^\\p{Graph}\\x20\\t\\r\\n]",""),null)
      sents
    })
    ret
  }

  def getNgramRdd(sentRdd: RDD[Array[Sentence]], tfFilterInPartition:Int=3, firstStageNgram: Broadcast[mutable.HashMap[String, Ngram]]=null): RDD[Ngram]= {

    val ret = sentRdd.mapPartitions(itr => {
      // reduce key only apply to first stage. to reduce the rare word.
      def reduceNgramItr(hNgrams: mutable.LinkedHashMap[String,Ngram], threashold: Int) = {
        var removeCnt = 0
        hNgrams.keysIterator.toList.foreach(key=>{
          // don't remove too much ...
          if (hNgrams(key).tfAll <= threashold && removeCnt < Conf.partitionReduceStartStep) {
            hNgrams.remove(key)
            removeCnt += 1
          }
        })
        removeCnt
      }
      def reduceNgram(hNgrams: mutable.LinkedHashMap[String,Ngram], preNgramSize: Int) = {
        var threashold = 0
        var removeCnt = 0
        while(removeCnt < Conf.partitionReduceFraction * Conf.partitionReduceStartStep) {
          threashold += 1
          removeCnt += reduceNgramItr(hNgrams,threashold)
        }
        println(s"reduceNgram: remove ${removeCnt} at threashold ${threashold}, current ngram ${hNgrams.size}")
        hNgrams.size
      }
      println(s"getNgramRdd ***")
      val hNgrams = new mutable.LinkedHashMap[String,Ngram]()
      val gramId = new AtomicInteger()
//      val hPreNgram =  firstStageNgram.value
      var startTime = System.currentTimeMillis()
      var ngramSize = 0
      var wordCnt = 0
      itr.foreach(sents => {
        gramId.set(0)
        wordCnt += sents.map(_.words.size).sum
        if (firstStageNgram == null) {
          Nlp.generateNgram(sents, gramId, hNgrams)
          // if add more than Conf.partitionReduceStartStep ngram, start reduce ngram
          if (hNgrams.size > Conf.partitionReduceStartPoint && hNgrams.size - ngramSize > Conf.partitionReduceStartStep) {
            reduceNgram(hNgrams, ngramSize)
            ngramSize = hNgrams.size
          }
        }else{
          Nlp.generateNgramStage2(sents,gramId,hNgrams,firstStageNgram.value)
        }
        // print some info for tracking
        if ((System.currentTimeMillis - startTime)/1000 > 10) {
          println(s"current gram count is: ${hNgrams.size}, read words: ${wordCnt}")
          startTime = System.currentTimeMillis()
        }
        // reduce vocabulary

      })
      val sNgrams = hNgrams.values.toSeq.filter(_.tfFilter(tfFilterInPartition))
      trace(INFO,s"grams number before reduce (in this partition) > ${tfFilterInPartition} is ${sNgrams.size}")
      sNgrams.foreach(_.getNestInfo(sNgrams))
      sNgrams.iterator
    })
    ret
  }

  def getTrainNgramRdd():RDD[Ngram] = {
    if (Conf.clusteringFromFile == false) {
      val rddText = if (Conf.textFromDirectory) {
        this.getTextRddFromDir(Conf.textDirectory)
      }else{
        val rdd = this.getBlogIdRdd(Conf.partitionNumber)
        this.getBlogTextRdd(rdd)
      }
      this.docsNum = rddText.count()
      println(s"### doc number is ${this.docsNum} ###")
      val rddSent = this.getSentRdd(rddText).persist(StorageLevel.DISK_ONLY)
      val docNumber = this.docsNum
      val rddNgram = this.getNgramRdd(rddSent, Conf.partitionTfFilter)
        .map(gram => (gram.key, gram))
        .reduceByKey(_ + _)
        //.sortByKey()
        .map(_._2)
        .filter(_.tfFilter(Conf.stag1TfFilter))
        .mapPartitions(itr => Ngram.updateAfterReduce(itr, docNumber, false))
        .filter(t=>t.cvalue > Conf.stag1CvalueFilter && t.umlsScore._1 > Conf.stag2UmlsScoreFilter && t.umlsScore._2 > Conf.stag2ChvScoreFilter)
        .persist(StorageLevel.DISK_ONLY)
      val ngramCnt = rddNgram.count()  // only to parallel all task
      println(s"number of ngram for stage 1 is ${ngramCnt}")

      //rddNgram.foreach(gram => println(f"${gram.tfdf}%.2f\t${log2(gram.cvalue+1)}%.2f\t${gram}"))
      //println(s"number of gram is ${rddNgram.count}")

      val firstStageRet = new mutable.HashMap[String, Ngram]()
      if (Conf.topTfNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.tfAll * -1).take(Conf.topTfNgram).map(gram => (gram.key, gram))
      else if (Conf.topTfdfNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.tfdf * -1).take(Conf.topTfdfNgram).map(gram => (gram.key, gram))
      else if (Conf.topCvalueNgram > 0)
        firstStageRet ++= rddNgram.sortBy(_.cvalue * -1).take(Conf.topCvalueNgram).map(gram => (gram.key, gram))
      else
        firstStageRet ++= rddNgram.toLocalIterator.map(gram => (gram.key, gram))
      rddNgram.unpersist()

      //firstStageRet.foreach(println)
      Nlp.wordsInbags = Nlp.sortBowKey(firstStageRet)  // Note that the driver process and the work process are separated
      println(s"\n ### the words in the bags ${Nlp.wordsInbags.size}: ")
      println(Nlp.wordsInbags.toSeq.sortBy(_._2._2 * -1).mkString("\t"))
      val firstStageNgram = sc.broadcast(firstStageRet)

      val rddNgram2 = this.getNgramRdd(rddSent, 0, firstStageNgram)
        .map(gram => (gram.key, gram))
        .reduceByKey(_ + _)
        //.sortByKey()
        .map(_._2)
        .filter(_.tfFilter( Conf.stag2TfFilter))
        .mapPartitions(itr => Ngram.updateAfterReduce(itr, docNumber, true))
        .filter(_.cvalue > Conf.stag2CvalueFilter)
      rddSent.unpersist()
      if(Conf.ngramSaveFile.trim.length>0) {
        if (!Conf.bagsOfWord) {
          Utils.writeObjectToFile(Conf.ngramSaveFile + ".no_bow", rddNgram2.map(g=>{
            g.context.wordsInbags=null
            g
          }).collect())
        }
        Utils.writeObjectToFile(Conf.ngramSaveFile+".bow.index", Nlp.wordsInbags)
        Utils.writeObjectToFile(Conf.ngramSaveFile, rddNgram2.collect())
      }

      if (Conf.trainNgramCnt>0) {
        val tmp = sc.parallelize( rddNgram2.take(Conf.trainNgramCnt), Conf.partitionNumber)
//        trainSampleMark(tmp)
        tmp
      }else{
//        trainSampleMark(rddNgram2)
        rddNgram2
      }

    }else {
      println(s"start load ngram from file:")
      var ngrams:Array[Ngram] = null
      if (Conf.bagsOfWord) {
        Nlp.wordsInbags = Utils.readObjectFromFile(Conf.ngramSaveFile + ".bow.index")
        ngrams = Utils.readObjectFromFile[Array[Ngram]](Conf.ngramSaveFile).filter(v => {
          !v.text.matches(Conf.stopwordRegex) && v.tfFilter(Conf.stag2TfFilter)
        })
        ngrams.foreach(g=>if(Conf.bowDialogSetOne){
           val index = Nlp.wordsInbags.getOrElse(g.key,(-1,0L))
           if (index._1 >= 0) g.context.wordsInbags(index._1) = g.tfAll.toInt
         })
      }else{
        ngrams = Utils.readObjectFromFile[Array[Ngram]](Conf.ngramSaveFile+".no_bow").filter(v => {
          !v.text.matches(Conf.stopwordRegex) && v.tfFilter(Conf.stag2TfFilter)
        })
      }
     /* Utils.writeObjectToFile(Conf.ngramSaveFile + ".no_bow", ngrams.map(g=>{
        g.context.wordsInbags=null;
        g
      }))
      sys.exit(0)*/
       /*Utils.writeObjectToFile(Conf.ngramSaveFile + ".part", ngrams.filter(g=>g.hashCode()%10<5))
      sys.exit(0)*/

      if (Conf.trainNgramCnt>0)ngrams = ngrams.take(Conf.trainNgramCnt)
      val rddNgram2 = sc.parallelize(ngrams, Conf.partitionNumber)
//      trainSampleMark(rddNgram2)
      rddNgram2
    }
  }

  /**
   * Just mark a Ngram as 'trainning' status, do not remove the non-training Ngram.
   * @param ngram
   * @return
   */
  def trainSampleMark(ngram: Ngram):Ngram = {
        if (Conf.testSample>0) {
          // take a random number for each Ngram, if the random number is not in the 'test' percentage, it is a training percentage.
          if (Utils.random.nextInt(100000000) >= Conf.testSample*1000000 && (!Conf.trainOnlyChv || ngram.isUmlsTerm(true)))
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
  }
  /**
   *
   * @param rddNgram
   * @return
   */
  def getVectorRdd(rddNgram: RDD[Ngram], useFeatureWeight: Array[(String,Double)], useUmlsContextFeature: Boolean=false): RDD[(Ngram,Vector)] = {
    // some configuration problem will lead to no ngram in the RDD.
    val currNgramCnt = rddNgram.count()
    if(currNgramCnt==0) {
      println("getVectorRdd: werid! no ngram in the RDD, maybe some configuration is wrong.")
      return null
    }

    // get the weight for the vector !!! the order should be the same as constructing the vector !!!!
    val vectorWeight:ArrayBuffer[Double] = new ArrayBuffer[Double]()
    val useFeature = useFeatureWeight.map(_._1)
    val useWeight = useFeatureWeight.map(_._2)
    val gram = rddNgram.take(1)(0)
    columnName.clear()
    if (!Conf.bagsOfWord) {
      if (useFeature.contains("tfdf")) vectorWeight.append(useWeight(useFeature.indexOf("tfdf"))) else vectorWeight.append(0) // tfdf
      columnName.append("tfdf")
      if (useFeature.contains("tf")) vectorWeight.append(useWeight(useFeature.indexOf("tf"))) else vectorWeight.append(0) // tfdf
      columnName.append("tf")
      if (useFeature.contains("df")) vectorWeight.append(useWeight(useFeature.indexOf("df"))) else vectorWeight.append(0) // tfdf
      columnName.append("df")
      if (useFeature.contains("cvalue")) vectorWeight.append(useWeight(useFeature.indexOf("cvalue"))) else vectorWeight.append(0) // c-value, applied a log function
      columnName.append("cvalue")
      if (useFeature.contains("umls_score")) vectorWeight.append(useWeight(useFeature.indexOf("umls_score"))) else vectorWeight.append(0) // simple similarity to umls
      columnName.append("umls_score")
      if (useFeature.contains("chv_score")) vectorWeight.append(useWeight(useFeature.indexOf("chv_score"))) else vectorWeight.append(0) //simple similarity to chv
      columnName.append("chv_score")
      if (useFeature.contains("contain_umls")) vectorWeight.append(useWeight(useFeature.indexOf("contain_umls"))) else vectorWeight.append(0)
      columnName.append("contain_umls")
      if (useFeature.contains("contain_chv")) vectorWeight.append(useWeight(useFeature.indexOf("contain_chv"))) else vectorWeight.append(0)
      columnName.append("contain_chv")

      if (useFeature.contains("nn")) vectorWeight.append(useWeight(useFeature.indexOf("nn"))) else vectorWeight.append(0)
      columnName.append("nn")
      if (useFeature.contains("an")) vectorWeight.append(useWeight(useFeature.indexOf("an"))) else vectorWeight.append(0)
      columnName.append("an")
      if (useFeature.contains("pn")) vectorWeight.append(useWeight(useFeature.indexOf("pn"))) else vectorWeight.append(0)
      columnName.append("pn")
      if (useFeature.contains("anpn")) vectorWeight.append(useWeight(useFeature.indexOf("anpn"))) else vectorWeight.append(0)
      columnName.append("anpn")

      if (useFeature.contains("stys")) vectorWeight.appendAll(gram.stys.map(_ => useWeight(useFeature.indexOf("stys")))) else vectorWeight.appendAll(gram.stys.map(_ => 0.0))
      columnName.appendAll(gram.stys.map(_=>"stys"))
      if (useFeature.contains("win_pos")) vectorWeight.appendAll(gram.context.win_pos.map(p => useWeight(useFeature.indexOf("win_pos")))) else vectorWeight.appendAll(gram.context.win_pos.map(_ => 0.0))
      columnName.appendAll(gram.context.win_pos.map(_=>"win_pos"))

      if (useFeature.contains("capt_first")) vectorWeight.append(useWeight(useFeature.indexOf("capt_first"))) else vectorWeight.append(0)
      columnName.append("capt_first")
      if (useFeature.contains("capt_all")) vectorWeight.append(useWeight(useFeature.indexOf("capt_all"))) else vectorWeight.append(0)
      columnName.append("capt_all")
      if (useFeature.contains("capt_term")) vectorWeight.append(useWeight(useFeature.indexOf("capt_term"))) else vectorWeight.append(0)
      columnName.append("capt_term")

      if (useFeature.contains("win_umls")) vectorWeight.append(useWeight(useFeature.indexOf("win_umls"))) else vectorWeight.append(0)
      columnName.append("win_umls")
      if (useFeature.contains("win_chv")) vectorWeight.append(useWeight(useFeature.indexOf("win_chv"))) else vectorWeight.append(0)
      columnName.append("win_chv")
      if (useFeature.contains("sent_umls")) vectorWeight.append(useWeight(useFeature.indexOf("sent_umls"))) else vectorWeight.append(0)
      columnName.append("sent_umls")
      if (useFeature.contains("sent_chv")) vectorWeight.append(useWeight(useFeature.indexOf("sent_chv"))) else vectorWeight.append(0)
      columnName.append("sent_chv")
      if (useFeature.contains("umls_dist")) vectorWeight.append(useWeight(useFeature.indexOf("umls_dist"))) else vectorWeight.append(0)
      columnName.append("umls_dist")
      if (useFeature.contains("chv_dist")) vectorWeight.append(useWeight(useFeature.indexOf("chv_dist"))) else vectorWeight.append(0)
      columnName.append("chv_dist")

      if (useFeature.contains("prefix")) {
        vectorWeight.appendAll(Nlp.prefixs.map(_ => useWeight(useFeature.indexOf("prefix"))))
        columnName.appendAll(Nlp.prefixs.map(_=>"prefix"))
      }
      if (useFeature.contains("suffix")) {
        vectorWeight.appendAll(Nlp.suffixs.map(_ => useWeight(useFeature.indexOf("suffix"))))
        columnName.appendAll(Nlp.suffixs.map(_=>"suffix"))
      }
      println(s"*** size weight ${vectorWeight.size} ***")
    } else {
      if (gram.context.wordsInbags == null) {
        println("You configured bagsOfWords enable, but there is no bagsOfWords info in ngram.")
        sys.exit(1)
      }
      if (Conf.bowTopNgram>gram.context.wordsInbags.size) {
        println(s"Warning: You specify Conf.bowTopCvalueNgram=${Conf.bowTopNgram}, but only ${gram.context.wordsInbags.size} available")
        Conf.bowTopNgram = gram.context.wordsInbags.size
      }
      vectorWeight.appendAll(gram.context.wordsInbags.take(Conf.bowTopNgram).map(_=>1.0).take(Conf.bowTopNgram))
      columnName.appendAll(gram.context.wordsInbags.take(Conf.bowTopNgram).zipWithIndex.map(kv => s"bow${kv._2+1}"))
    }
    println(s"* the weight for the feature vecotr is \n${columnName.zip(vectorWeight).mkString(",")} *")
    //println(Nlp.wordsInbags.mkString("\t"))

    val tmp_vecter = rddNgram.map(gram => {
      val feature = new ArrayBuffer[Double]()
      if (!Conf.bagsOfWord) {
        if (useFeature.contains("tfdf")) feature.append(gram.tfdf) else feature.append(0) // tfdf
        if (useFeature.contains("tf")) feature.append(gram.tfAll) else feature.append(0) // tfdf
        if (useFeature.contains("df")) feature.append(gram.df) else feature.append(0) // tfdf
        if (useFeature.contains("cvalue")) feature.append(gram.cvalue) else feature.append(0) // c-value, applied a log function

        if (useFeature.contains("umls_score")) feature.append(gram.umlsScore._1 / 100) else feature.append(0) // simple similarity to umls
        if (useFeature.contains("chv_score")) feature.append(gram.umlsScore._2 / 100) else feature.append(0) //simple similarity to chv
        if (useFeature.contains("contain_umls")) feature.append(bool2Double(gram.isContainInUmls)) else feature.append(0)
        if (useFeature.contains("contain_chv")) feature.append(bool2Double(gram.isContainInChv)) else feature.append(0)

        if (useFeature.contains("nn")) feature.append(bool2Double(gram.isPosNN)) else feature.append(0)
        if (useFeature.contains("an")) feature.append(bool2Double(gram.isPosAN)) else feature.append(0)
        if (useFeature.contains("pn")) feature.append(bool2Double(gram.isPosPN)) else feature.append(0)
        if (useFeature.contains("anpn")) feature.append(bool2Double(gram.isPosANPN)) else feature.append(0)

        if (useFeature.contains("stys")) feature.appendAll(gram.stys.map(bool2Double(_))) else feature.appendAll(gram.stys.map(_ => 0.0))
        if (useFeature.contains("win_pos")) feature.appendAll(gram.context.win_pos.map(p => 1.0*p / gram.tfAll)) else feature.appendAll(gram.context.win_pos.map(p => 0.0))

        if (useFeature.contains("capt_first")) feature.append((1.0*gram.capt_first / gram.tfAll)) else feature.append(0)
        if (useFeature.contains("capt_all")) feature.append((1.0*gram.capt_all / gram.tfAll)) else feature.append(0)
        if (useFeature.contains("capt_term")) feature.append((1.0*gram.capt_term / gram.tfAll)) else feature.append(0)

        if (useFeature.contains("win_umls")) feature.append((gram.context.win_umlsCnt * 1.0 / gram.tfAll)) else feature.append(0)
        if (useFeature.contains("win_chv")) feature.append((gram.context.win_chvCnt * 1.0 / gram.tfAll))  else feature.append(0)
        if (useFeature.contains("sent_umls")) feature.append((gram.context.sent_umlsCnt * 1.0 / gram.tfAll))  else feature.append(0)
        if (useFeature.contains("sent_chv")) feature.append((gram.context.sent_chvCnt * 1.0 / gram.tfAll))  else feature.append(0)
        if (useFeature.contains("umls_dist")) feature.append((gram.context.umlsDist * 1.0 / gram.tfAll))  else feature.append(0)
        if (useFeature.contains("chv_dist")) feature.append((gram.context.chvDist * 1.0 / gram.tfAll))  else feature.append(0)

        if (useFeature.contains("prefix")) feature.appendAll(gram.context.win_prefix.map(p => 1.0*p/gram.tfAll))
        if (useFeature.contains("suffix")) feature.appendAll(gram.context.win_suffix.map(p => 1.0*p/gram.tfAll))
        //println(s"NLP prefix ${Nlp.prefixs.size} suffix ${Nlp.suffixs.size} prefix ${gram.context.win_prefix.size}, suffix ${gram.context.win_suffix.size} f ${feature.size} weight ${vectorWeight.size}")

      }else{
        feature.appendAll(gram.context.wordsInbags.take(Conf.bowTopNgram).map(p => (1.0*p / gram.tfAll)))
      }
      (gram,feature)
    })
    if (Conf.normalizeFeature) {
      val vector = Normalize(tmp_vecter, vectorWeight)
      vector
    }else{
      tmp_vecter.map(kv=>(kv._1,Vectors.dense(kv._2.toArray)))
    }
  }

  def Normalize (vecter_input: RDD[(Ngram, ArrayBuffer[Double])], vectorWeight: ArrayBuffer[Double]) = {
    vecter_input.persist()
    var tmp_vecter = vecter_input
    var sd:ArrayBuffer[Double] = null
    var avg:ArrayBuffer[Double] = null
    if (Conf.normalize_standardize || Conf.normalize_outlier_factor > 0.1) {
      val currNgramCnt = tmp_vecter.count()
      // get sum
      val sum = tmp_vecter.map(_._2).reduce((a1, a2) => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(a1.size)(0.0))
        Range(0, a1.size).foreach(index => {
          f(index) = a1(index) + a2(index)
        })
        f
      })
      // get average
      avg = sum.map(_ / currNgramCnt)
      // get variance
      val sumSquare = tmp_vecter.map(_._2).map(v => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(v.size)(0.0))
        Range(0, v.size).foreach(index => {
          f(index) = Math.pow(v(index) - avg(index), 2)
        })
        f
      }).reduce((a1, a2) => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(a1.size)(0.0))
        Range(0, a1.size).foreach(index => {
          f(index) = a1(index) + a2(index)
        })
        f
      })
      // standard deviation
      sd = sumSquare.map(v => Math.sqrt(v / currNgramCnt))
      println("the standard deviation of the features: " + sd.mkString("\t"))
    }

    if (Conf.normalize_outlier_factor>0.1) {
      // limit the outlier not to be farer from (average+sd*factor).
      tmp_vecter = tmp_vecter.map(kv => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(sd.size)(0.0))
        val a = kv._2
        Range(0, a.size).foreach(index => {
          f(index) = if (sd(index)>0.01 &&  Math.abs(a(index) - avg(index)) > Conf.normalize_outlier_factor * sd(index)) {
            if(a(index) > avg(index))
              avg(index) + Conf.normalize_outlier_factor * sd(index)
            else
              avg(index) - Conf.normalize_outlier_factor * sd(index)
          } else {
            a(index)
          }
        })
        (kv._1, f)
      })

    }


    if (Conf.normalize_standardize){
      // normalization to (0,1), then apply the weight of the features
      tmp_vecter = tmp_vecter.map(kv => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(sd.size)(0.0))
        val a = kv._2
        Range(0, a.size).foreach(index => {
          f(index) = if (sd(index)>0.01) (a(index) - avg(index)) / sd(index) else a(index)
        })
        (kv._1, f)
      })
    }
    if (Conf.normalize_rescale) {
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
      println("the maximum of the features: " + max.mkString("\t"))
      // normalization to (0,1), then apply the weight of the features
      tmp_vecter = tmp_vecter.map(kv => {
        val f = new ArrayBuffer[Double]()
        f.appendAll(Array.fill(min.size)(0.0))
        val a = kv._2
        Range(0, a.size).foreach(index => {
          f(index) = if (max(index) - min(index) > 0.0001)
            (a(index) - min(index)) / (max(index) - min(index))
          else
            a(index)
          // apply the weight to the feature
          f(index) *= vectorWeight(index)
        })
        (kv._1,f)
      })
    }
    if (tmp_vecter == vecter_input) {
      println("you configured a invalid normalization type.")
      null
    }
    //
    val vector = tmp_vecter.map(kv=>(kv._1, Vectors.dense(kv._2.toArray)))

    vecter_input.unpersist()
    vector
  }

  def sampleAvgCost(model: KMeansModel,rddVectorDbl: RDD[Vector]): Double = {
    // find average distance of a point to its center. sample some poit to caculate the cost
    val samplePointCost = rddVectorDbl.takeSample(false,Conf.clusterThresholSample).map(p=>MyKmean.findClosest(model.clusterCenters,p)._2)
    val avgCost = samplePointCost.sum / samplePointCost.size
    return avgCost
  }

  def reviseMode(k:Int,modelOrg: KMeansModel,rddVectorDbl: RDD[Vector]): (KMeansModel) = {
    // if reviseMode is not configured
    if (!Conf.reviseModel || Conf.clusterThresholdPt <= 0) return modelOrg

    val ngramCntTrain = rddVectorDbl.count


    /**
     * Get the cost of every point to its closest center, and rand the points by these cost.
     * In fact, it looks like using clustering to get classification goal.
     **/
    val retPridict = rddVectorDbl.map(v => modelOrg.predict(v))
    /* exclude the centers that contain small number of ngram, compare to the average number of each cluster*/
    val retPredictFiltered = retPridict.map(kk => (kk, 1L)).reduceByKey(_ + _).collect().filter(kv => {
      val filter = k * kv._2 * 100.0 / ngramCntTrain >= Conf.clusterThresholdPt &&  kv._2>=Conf.clusterThresholdLimit
      if (filter == false) println(s"cluster ${kv._1} has ${kv._2} ngram, less than ${Conf.clusterThresholdPt}% of train ${ngramCntTrain}/${k}=${ngramCntTrain / k} or minimum limit ${Conf.clusterThresholdLimit}, so it is considered to be excluded.")
      filter
    }).map(_._1)
    // get new centers
    val newCenter = modelOrg.clusterCenters.filter(v => {
      retPredictFiltered.contains(modelOrg.clusterCenters.indexOf(v))
    })
    // update model
    val model = new KMeansModel(newCenter)

    val avgCost = sampleAvgCost(model,rddVectorDbl)
    println(f"new: sample average cost of model for k=${model.k} is ${avgCost}")

    // check the discarded centers, if the cost of then grater than the average cost * 2, add them as center again
    val finalCenter = new ArrayBuffer[Vector]()
    finalCenter.appendAll(newCenter)
    modelOrg.clusterCenters.filter(retPredictFiltered.indexOf(_) < 0).foreach(p=>{
      val cost = MyKmean.findClosest(model.clusterCenters,p)._2
      if(cost > avgCost * Conf.clusterThresholFactor){
        println(f"center cost is ${cost}, factor ${cost/avgCost}%.1f > ${Conf.clusterThresholFactor}, so it still is a center")
        finalCenter.append(p)
      }
    })

    // update model
    val finalModel = new KMeansModel(finalCenter.toArray)
    return finalModel
  }

  /**
   * Rank the ngam based on a cost value. current, we have 3 types of cost:
   * 1: kmeans: the distance between a ngram and its nearest centre.
   * 2. tfAll: the inverse of the term frequency(1/tf)
   * 3: cvalue: the inverse of the cvalue(1/cvalue)
   * @param k the original k, before the model is revised. Only for display.
   * @param rankType: based on the cost of 'kmeans', 'tfAlll', or 'cvalue'
   * @param model: the model of kmeans
   * @param rddRankVector_all:
   * @param ngramCntChvTest: the chv number in the test data
   *
   */
  def rank (k:Int,rankType: String, model: KMeansModel,rddRankVector_all:RDD[(Ngram, Vector)], ngramCntChvTest:Long,tfStat:Map[Int, (Int, Int, Long, Double)]) = {
    /**
     * Get the cost of every point to its closest center, and rand the points by these cost.
     * In fact, it looks like using clustering to get classification goal.
     **/
    val ret = if (rankType.equals("kmeans")) {
      val bcCenters = sc.broadcast(model)
      /** predict the center of each point. */
      rddRankVector_all.map(kv => (kv._1, MyKmean.findClosest(bcCenters.value.clusterCenters, kv._2))).sortBy(_._2._2).collect
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
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\t${ngram.getTypeName()}\t")
      }else if (ngram.isUmlsTerm(false)) {
        cntUmls += 1
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\t${ngram.getTypeName()}\t")
      }else {
        if (Conf.showDetailRankPt>=topPercent)print(f"${kk}\t${cost}%.3f\t${ngram.getTypeName()}\t")
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
      val tfst = if (tfStat!=null)tfStat.get(kk).get else null
      val kNum = if (tfst != null) tfst._2 else 0
      val kTfAvg = if (tfst != null) tfst._3 else 0
      val kTfSd = if (tfst != null) tfst._4 else 0
      if (Conf.showDetailRankPt>=topPercent)print(f"${topPercent}%.1f\t${recall*100}%.2f\t${precision*100}%.2f\t${fscore}%.2f\t${precision_umls*100}%.2f\t${kNum}\t${kTfAvg}%.1f\t${kTfSd}%.1f\t")
      if (Conf.showDetailRankPt>=topPercent)println(ngram.toStringVector())
    })
    println(f"type ${rankType},${k}\t${ngramCntChvTest}\t${recallVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${precisionVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${fscoreVsRank.map(v=>f"${v}%.2f").mkString("\t")}\t${precisionUmlsVsRank.map(v=>f"${v}%.2f").mkString("\t")}")
    println(f"MAX OF recall ${recallVsRank.max}%.2f\tprecision ${precisionVsRank.max}%.2f\tfscore ${fscoreVsRank.max}%.2f\tprecision_umls ${precisionUmlsVsRank.max}%.2f")

    (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank)
  }

  /**
   * Get the score for these K for clustering. this method do not evaluate the score as the Silhouette
   * algorithm describing. For every point, It uses the distance from its current center comparing to the
   * distance frome the second nearest center.
   * It is much faster than the Silhouette algorithm.
   * For detail, see: https://en.wikipedia.org/wiki/Silhouette_(clustering)
   * @param model
   * @param rddVectorDbl
   * @return
   */
  def getSilhouetteScoreFast(model: KMeansModel,rddVectorDbl: RDD[Vector]): Double = {
    val rddK = model.predict(rddVectorDbl)

    val bcCenters = sc.broadcast(model)
    /** predict the center of each point. */
    val rddCostCenter = rddVectorDbl.map(v => MyKmean.findClosest(bcCenters.value.clusterCenters, v)._2)
    //bcCenters.destroy()

    /* To get the "neighbouring cluster(center)" of a point, we modify its center to a 'very far away point',
    and then evaluate the least cost center again. This 'least cost' center is the neighbor center that we need.  */
    val models4Neibhor = model.clusterCenters.map(c =>{
      // !!We have to clone a new clusterCenters to avoid affect the old result.
      val m = new KMeansModel(model.clusterCenters.clone())
      m.clusterCenters(model.clusterCenters.indexOf(c)) = Vectors.dense(Array.fill(c.size)(10.0))
      m
    })

    val bcNeighbors = sc.broadcast(models4Neibhor)
    /** predict the new center of each point. */
    val rddCostNeighbor = rddVectorDbl.zip(rddK).map(vk => MyKmean.findClosest(bcNeighbors.value(vk._2).clusterCenters, vk._1)._2)
    //bcNeighbors.destroy()

    rddCostCenter.zip(rddCostNeighbor).map(ab=>{
      (ab._2-ab._1)/Math.max(ab._2,ab._1)
    }).reduce(_+_) / this.trainNum
  }

  /**
   * Get the score for these K for clustering. this method do exactly evaluate the score as the Silhouette
   * algorithm describing.
   * For detail, see: https://en.wikipedia.org/wiki/Silhouette_(clustering)
   * @param model
   * @param rddVectorDbl
   * @return
   */
  def getSilhouetteScore(model: KMeansModel,rddVectorDbl: RDD[Vector]): Double = {
    val startTime = System.currentTimeMillis()

    // map to (k,vector, norm) for further processing. For performance consideration, we evaluate norm of the vector here.
    val rddKVN = model.predict(rddVectorDbl).zip(rddVectorDbl).zip(rddVectorDbl.map(Vectors.norm(_, 2.0))).map(kvn => (kvn._1._1, kvn._1._2, kvn._2))
    val rddKVector = rddKVN.groupBy(kvn => kvn._1)
    // cartesian combintion, the order may not be preserved, that is why we have to keep the 'vector' in the result.
    // the result (vector, k-of-vector, k-of-cluster, average-distance)
    val rddDist = rddKVN.cartesian(rddKVector).map(kvkv => {
      val kvVecter = kvkv._1
      val kvCluster = kvkv._2
      // get distance from current point to all point in some cluster. this distance could be a(i) or b(i)
      val dist = kvCluster._2.map(kvn => MyKmean.fastSquaredDistance(kvVecter._2, kvVecter._3, kvn._2, kvn._3)).sum
      (kvVecter._2, kvVecter._1, kvCluster._1, dist / kvCluster._2.size)
    }).persist()

    val rddA = rddDist.filter(vkkd => vkkd._2 == vkkd._3).map(vkkd => (vkkd._1, vkkd._4))
    val rddB = rddDist.filter(vkkd => vkkd._2 != vkkd._3).map(vkkd => (vkkd._1, vkkd._4)).reduceByKey((v1, v2) => if (v1 < v2) v1 else v2)
    rddDist.unpersist()

    val numB = rddB.count()
    val numA = rddA.count()
    println(f"getSilhouetteScore: len(a)=${numA}, len(b)=${numB}")
    if (numA != numB){
      println(f"!!!! numbers of A and B is not the same!!!!")
      return -1
    }

    val score = rddA.join(rddB).map(kdd => {
      val ab = kdd._2
      (ab._2 - ab._1) / Math.max(ab._2, ab._1)
    }).reduce(_ + _) / this.trainNum
    println(f"### getSilhouetteScore ${score}, used  time ${System.currentTimeMillis()-startTime} ###")
    score
  }

  /**
    * if nDim < 1, it means keep this proportion of variance
    * @param rddVectorDbl
    * @param nDim
    */
  def pca(rddVectorDbl: RDD[Vector], nDim:Float) = {
    val mat = new RowMatrix(rddVectorDbl)
    var topK = nDim.toInt
    if (topK < 1) {
      val svd = mat.computeSVD(math.min(mat.numRows(),mat.numCols()).toInt)
      val s = svd.s.toArray
      for (i <- 0 to s.size) {
        if (s.slice(0,i).sum / s.sum < nDim) {
          topK = i
        }
      }
      //println(s"** PCA keep ${nDim} variance with dimension ${topK} out of ${mat.numCols()}")
    }

    // Compute the top 10 principal components.
    val pc: Matrix = mat.computePrincipalComponents(topK) // Principal components are stored in a local dense matrix.
    // Project the rows to the linear space spanned by the top 10 principal components.
    val projected: RowMatrix = mat.multiply(pc)
    //println(projected.rows.collect().foreach(v=>println(v.toArray.mkString(" "))))

    projected.rows
  }

}

object Clustering {
  def main (args: Array[String]): Unit = {
    // init spark
    val startTime = new Date()
    val conf = new SparkConf()
      .setAppName("Ngram")
    if (Conf.sparkMaster.length>3)
      conf .setMaster(Conf.sparkMaster)
    val sc = new SparkContext(conf)

    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.WARN)

    // printf more debug info of the gram that match the filter
    Trace.filter = Conf.debugFilterNgram

    val clustering = new Clustering(sc)

    // get all the ngram as a RDD
    val rddNgram4Train = if (Conf.trainedNgramFilterPosRegex.length>0)
      clustering.getTrainNgramRdd().filter(gram=>Ngram.TrainedNgramFilterPosRegex.matcher(gram.posString).matches()==false)
    else
      clustering.getTrainNgramRdd()

    rddNgram4Train.persist()
    var rddGramVectorAll_org = clustering.getVectorRdd(rddNgram4Train, Conf.useFeatures4Train).persist()
    rddNgram4Train.unpersist()
    val rddVectorAll = if (Conf.pcaDimension>0) {
      clustering.pca(rddGramVectorAll_org.map(_._2), Conf.pcaDimension)
    }else{
      rddGramVectorAll_org.map(_._2)
    }
    // we used the vector after pca dimension reduction
    val rddGramVectorAll = if (Conf.pcaDimension>0) {
      val tmpRdd = rddGramVectorAll_org.zip(rddVectorAll).map(kv => (kv._1._1, kv._2)).persist()
      rddGramVectorAll_org.unpersist()
      tmpRdd
    }else{
      rddGramVectorAll_org
    }

    val Seed = new Date().getTime
    val ngramShown = if (Conf.showOrgNgramNum>0){
      rddGramVectorAll.filter(kv => {
        Conf.showOrgNgramOfN.contains(kv._1.n) && Ngram.ShowOrgNgramOfPosRegex.matcher(kv._1.posString).matches() && Ngram.ShowOrgNgramOfTextRegex.matcher(kv._1.text).matches()
      }).takeSample(false,Conf.showOrgNgramNum,Seed).sortBy(kv=>Ngram.sortNgramByTfAndKey(kv._1.tfAll,kv._1.key))
    } else null
    if (Conf.showOrgNgramNum>0)println(s"original ngram: ngramShown count is ${ngramShown.size}")
    val fw = if (Conf.saveNgram2file.length > 0) new FileWriter(Conf.saveNgram2file,false) else null
    if (fw != null) fw.write(f"${Ngram.getVectorHead()}\n")
    if (ngramShown!=null) ngramShown.foreach(v => {
      if (fw != null)
        fw.write(f"${v._1.toStringVector()}\n")
      else
        println(f"${v._1.toStringVector()}")
    })

    if (Conf.bagsOfWord && Conf.bowOutputMatrix) {
      println(Nlp.wordsInbags.toArray.sortBy(kv => kv._2._1).mkString("\t") + "\tkeyOfTerm")
      rddGramVectorAll.sortBy(_._1.tfAll * -1).collect().foreach(kv => {
        if (fw!=null)
          fw.write(s"${kv._1.isUmlsTerm(true)}\t"+kv._1.context.wordsInbags.mkString("\t") + s"\t${kv._1.key}\n")
        else
          print(s"${kv._1.isUmlsTerm(true)}\t"+kv._1.context.wordsInbags.mkString("\t") + s"\t${kv._1.key}\n")
      })
    }

    //  output the vectors
    if (fw != null){
      rddGramVectorAll.map(kv=>(kv._1.isUmlsTerm(true),kv._2)).collect().foreach(kv =>{fw.write(s"${kv._1}\t" + kv._2.toArray.mkString("\t") + "\n")})
    }
    if (Conf.outputVectorOnly) {
      rddGramVectorAll.map(kv=>(kv._1.isUmlsTerm(true),kv._2)).collect().foreach(kv =>{print(s"${kv._1}\t" + kv._2.toArray.mkString("\t") + "\n")})
      sys.exit(0)
    }
    if (fw != null)fw.close()

    Range(1,Conf.sampleRuns+1).foreach(i =>{
      println(s"## iteration ${i} ##")
      val sampleRdd = rddGramVectorAll_org.map(kv=>(kv._1.trainSampleMark(), kv._2)).persist()
      main_do(sampleRdd,clustering)
      sampleRdd.unpersist()
    })

    sc.stop()
    MyCache.close()
    println("*******result is ******************")
    System.out.println("### used time: "+(new Date().getTime()-startTime.getTime())/1000+" ###")
  }
  def main_do (rddGramVectorAll:RDD[(Ngram,Vector)],clustering:Clustering): Unit = {

    val rddNgram4Train = rddGramVectorAll.map(_._1)
    val ngramCntAll = rddNgram4Train.count()
    val ngramCntChv = rddNgram4Train.filter(_.isUmlsTerm(true)).count()
    val ngramCntUmls = rddNgram4Train.filter(_.isUmlsTerm(false)).count()
    val ngramCntTest = rddNgram4Train.filter(g=> !g.isTrain).count()
    val ngramCntUmlsTest = rddNgram4Train.filter(g=>g.isUmlsTerm(false)&& !g.isTrain).count()
    val ngramCntChvTest = rddNgram4Train.filter(g=>g.isUmlsTerm(true)&& !g.isTrain).count()
    clustering.trainNum = rddNgram4Train.filter(g=>g.isTrain).count()

    //val rddVector = clustering.getVectorRdd(rddNgram4Train.filter(g=>g.isTrain), Conf.useFeatures4Train).persist()
    val rddVector = rddGramVectorAll.filter(_._1.isTrain).persist()
    // if (!Conf.trainOnlyChv&&Conf.testSample<=0), there is no test ngram for rank.
    //val rddRankVector_all = clustering.getVectorRdd(if (Conf.rankWithTrainData || (!Conf.trainOnlyChv&&Conf.testSample<=0)) rddNgram4Train else {rddNgram4Train.filter(_.isTrain==false)}, Conf.useFeatures4Test).persist()
    val rddRankVector_all = if (Conf.rankWithTrainData || (!Conf.trainOnlyChv&&Conf.testSample<=0)) rddGramVectorAll else {rddGramVectorAll.filter(_._1.isTrain==false)}
    rddRankVector_all.persist()
    //rddNgram4Train.unpersist()  // don't used it again

    println(s"** ngramCntAll ${ngramCntAll} ngramCntUmls ${ngramCntUmls} ngramCntChv ${ngramCntChv}  ngramOther ${ngramCntAll-ngramCntUmls} **")
    println(s"** ngramCntTrain ${ngramCntAll-ngramCntTest} ngramCntUmlsTrain ${ngramCntUmls-ngramCntUmlsTest} ngramCntChvTrain ${ngramCntChv-ngramCntChvTest}  **")
    println(s"** ngramCntTest ${ngramCntTest} ngramCntUmlsTest ${ngramCntUmlsTest} ngramCntChvTest ${ngramCntChvTest}  ngramOther ${ngramCntTest-ngramCntUmlsTest} **")
    if  (clustering.trainNum <= 0) {
      println("Number of training terms is 0, there much be something configure wrong!")
      sys.exit(1)
    }

    val rddVectorDbl = rddVector.map(_._2).persist()
    //if (Conf.showNgramInCluster<=0) rddVector.unpersist()

    //print the name of the features in vetors
    println("The feature name is:\n" + clustering.columnName.zipWithIndex.map(kv=>s"${kv._1}").mkString("\t"))

    if (Conf.runKmeans) {
      val kCost = for (k <- Range(Conf.k_start, Conf.k_end+1, Conf.k_step)) yield {
        val startTimeTmp = new Date()
        val modelOrg = KMeans.train(rddVectorDbl, k, Conf.maxIterations, Conf.runs)
        val costOrg = modelOrg.computeCost(rddVectorDbl)
        val model = clustering.reviseMode(k,modelOrg,rddVectorDbl)
        val cost = model.computeCost(rddVectorDbl)
        val predictOrg = modelOrg.predict(rddVectorDbl).map((_,1L)).reduceByKey(_+_).collect()
        val predict = model.predict(rddVectorDbl).map((_,1L)).reduceByKey(_+_).collect()
        println(f"cluster result number of point in K: \nold:${predictOrg.mkString("\t")}, \nnew:${predict.mkString("\t")}")

        val avgCost = clustering.sampleAvgCost(model,rddVectorDbl)
        println(f"final: sample average cost of model for k=${model.k} is ${avgCost}")

        val tfStatMap = if(Conf.showTfAvgSdInCluster){
          println("tf average and standard deviation in new clusters:")
          val avgSd = model.predict(rddVectorDbl).zip(rddVector).map(kv=>(kv._1,kv._2._1.tfAll)).groupByKey().map(kv=>(kv._1,kv._2.toArray)).collect().map(kv=>{
            val avg = kv._2.sum/kv._2.size
            val sd = Math.sqrt(kv._2.map(v=>(v-avg)*(v-avg)).sum/kv._2.size)
            (kv._1, kv._2.size,avg, sd)
          }).sortBy(_._3 * -1)
          avgSd.foreach(kv=>println(f"${kv._1}\t${kv._2}\t${kv._3}\t${kv._4}%.2f"))

          if(Conf.showNgramInCluster>0){
            println("gram in new clusters with tf order:")
            //modelOrg.predict(rddVectorDbl).zip(rddVector).map(kv=>(kv._1,kv._2._1)).groupByKey().map(kv=>(kv._1,kv._2.take(Conf.showNgramInCluster))).collect().foreach(kv=>{kv._2.foreach(ngram=>println(f"${kv._1}\t${ngram.toStringVector()}"))})
            val kNgram = model.predict(rddVectorDbl).zip(rddVector).map(kv=>(kv._1,(MyKmean.findClosest(model.clusterCenters, kv._2._2)._2,kv._2._1))).groupByKey().map(kv=>(kv._1,kv._2.take(Conf.showNgramInCluster))).collect().toMap
            avgSd.foreach(t=>kNgram.get(t._1).get.foreach(kv=>{
              val cost = kv._1
              val ngram = kv._2
              // k	cost	type	topPercent	recall	precision	fscore	umls-precision	kNum	kTfAvg	kTfSd + gram, the same as show gram in rank()
              println(f"${t._1}\t${cost}%.4f\t${ngram.getTypeName()}\t0\t0\t0\t0\t0\t${t._2}\t${t._3}\t${t._4}%.2f\t${ngram.toStringVector()}")
            }))
          }

          avgSd.map(kv=>(kv._1,(kv))).toMap
        } else {null}
        val clusterScore = if (Conf.clusterScore) {
          clustering.getSilhouetteScore(model,rddVectorDbl)
        }else{
          0.0
        }
        println(s"###kcost#### $k, newK:${model.k} costOrg:$costOrg, costNew:$cost, costDelta:${cost-costOrg}}" )

        val (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank) = clustering.rank(k,"kmeans",model,rddRankVector_all, ngramCntChvTest,tfStatMap)
        val kc = (k, model.k, costOrg,cost, avgCost,clusterScore,ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank, precisionVsRank,fscoreVsRank,precisionUmlsVsRank)
        println(s"###single kMeans used time: " + (new Date().getTime() - startTimeTmp.getTime())/1000 + " ###")
        kc
      }

      if (Conf.bagsOfWord)
        println(s"#### result for all k: k(${Conf.k_start},${Conf.k_end},${Conf.k_step}, rankGranular is ${Conf.rankGranular}, feature: ${Conf.useFeatures4Train.mkString(",")} ####")
      else
        println(s"#### result for all k: k(${Conf.k_start},${Conf.k_end},${Conf.k_step}, trainOnlyChv ${Conf.trainOnlyChv}, tf ${Conf.stag2TfFilter}, cluster (${Conf.clusterThresholdLimit},${Conf.clusterThresholdPt},${Conf.clusterThresholFactor},${Conf.clusterThresholSample}}), rankGranular (${Conf.rankGranular},base ${Conf.rankLevelBase}}), sample ${Conf.testSample} feature: ${Conf.useFeatures4Train.mkString(",")} ####")
      kCost.foreach(kc =>     println(f"${kc._1}\t${kc._2}\t${kc._3}%.1f\t${kc._4}%.1f\t${kc._5}%.4f\t${kc._6}%.4f\t${kc._7}\t${kc._8}\t${kc._9}\t${kc._10.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._11.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._12.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._10.max}%.2f\t${kc._11.max}%.2f\t${kc._12.max}%.2f\t${kc._13.max}%.2f"))
    }

    if (Conf.baseLineRank) {
      val (recallVsRank, precisionVsRank, fscoreVsRank, precisionUmlsVsRank) = clustering.rank(-1,"tfAll", null, rddRankVector_all, ngramCntChvTest,null)
      val kc = ("-2",0,0,0,0,0, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank, precisionVsRank,fscoreVsRank,precisionUmlsVsRank)
      val (recallVsRank2, precisionVsRank2, fscoreVsRank2, precisionUmlsVsRank2) = clustering.rank(-2,"cvalue", null, rddRankVector_all, ngramCntChvTest,null)
      val kCostBase = kc :: ("-1",0,0,0,0,0, ngramCntAll, ngramCntChv, ngramCntChvTest, recallVsRank2, precisionVsRank2,fscoreVsRank2,precisionUmlsVsRank2) :: Nil
      println(s"#### result for base line: base type(tfall,cvalue), rankGranular is ${Conf.rankGranular} ####")
      kCostBase.foreach(kc => println(f"${kc._1}\t${kc._2}\t${kc._3}%.1f\t${kc._4}%.1f\t${kc._5}%.4f\t${kc._6}%.4f\t${kc._7}\t${kc._8}\t${kc._9}\t${kc._10.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._11.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._12.map(v=>f"${v}%.2f").mkString("\t")}\t${kc._10.max}%.2f\t${kc._11.max}%.2f\t${kc._12.max}%.2f\t${kc._13.max}%.2f"))
    }

  }
}