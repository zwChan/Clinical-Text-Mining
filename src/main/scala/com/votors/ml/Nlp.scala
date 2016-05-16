package com.votors.ml

import java.io._
import java.lang.Exception
import java.util
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

import edu.stanford.nlp.parser.lexparser.LexicalizedParser
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory
import edu.stanford.nlp.tagger.maxent.{MaxentTagger, TaggerConfig}

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.collection.JavaConverters._

import breeze.numerics.abs
import com.votors.common.Utils.Trace._
import com.votors.common.Utils._
import com.votors.common._
import com.votors.umls.UmlsTagger2
import edu.stanford.nlp.io.EncodingPrintWriter.out
import gov.nih.nlm.nls.lvg.Api.LvgCmdApi
import opennlp.tools.chunker._
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.parser.{ParserFactory, ParserModel}
import opennlp.tools.postag.{POSModel, POSTaggerME}
import opennlp.tools.sentdetect.{SentenceDetectorME, SentenceModel}
import opennlp.tools.stemmer.PorterStemmer
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel}
import org.apache.spark.broadcast.Broadcast

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.io.File
import scala.util.control.Breaks._

import edu.stanford.nlp.ling.{Word, CoreLabel, HasWord}
;
import edu.stanford.nlp.process.{WordTokenFactory, CoreLabelTokenFactory, DocumentPreprocessor, PTBTokenizer}
;

import scala.util.control.Exception
;


/**
 * Created by Jason on 2015/11/9 0009.
 */
class Lvg(lvgdir: String=Conf.lvgdir) {
  val properties = new util.Hashtable[String, String]()
  properties.put("LVG_DIR", lvgdir)
  println("lvgdir: " + Conf.lvgdir)
  //option see: http://lexsrv3.nlm.nih.gov/LexSysGroup/Projects/lvg/2015/docs/designDoc/UDF/flow/index.html
  val lvgApi = if (File(Conf.lvgdir + "data/config/lvg.properties").exists) {
    new LvgCmdApi("-f:g:o:t:l:B", Conf.lvgdir + "data/config/lvg.properties", properties)
  }else{
    println("!!!!!lvg config not find, maybe something wrong, use PorterStemmer!!!!!!")
    null
  }

  def getNormTerm(term: String) = {
    var ret = ""
    if (lvgApi != null) {
      val outputFromLvg = lvgApi.MutateToString(term)
      val arrrayRet = outputFromLvg.split("\\|")
      //println(outputFromLvg)
      if (arrrayRet.size >= 2)
        ret = arrrayRet(1)
    }
    ret
  }
}

object Nlp {
  final val NotPos = "*"      // the char using indicating this is not a POS tagger, may be a punctuation
  //final val TokenEnd = "$$"
  //\p{Punct}	Punctuation: One of !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
  val punctPattern = Pattern.compile("\\p{Punct}")
  val spacePattern = Pattern.compile("\\s+")
  final val StopwordRegex = Pattern.compile(Conf.stopwordRegex)
  final val PosFilterRegex = Conf.posFilterRegex.map(Pattern.compile(_))

  var wordsInbags: Map[String,Int] = null

  //val solrServer = new HttpSolrServer(Conf.solrServerUrl)

  //opennlp models path
  val modelRoot = Conf.rootDir + "/data"
  val posModlePath = s"${modelRoot}/en-pos-maxent.bin"
  val sentModlePath = s"${modelRoot}/en-sent.bin"

  // get a new tokenizorFactor based on the new options
  val tokenizerFactory = PTBTokenizerFactory.newPTBTokenizerFactory(new WordTokenFactory(), Conf.stanfordTokenizerOption)

  //get pos after case/punctuation delete(input)?  // XXX: This may be not a corret approach!
  var sentDetector:SentenceDetectorME = null
  def getSent(phrase: String) = {
    val retSent = if (Conf.useStanfordNLP) {
      val reader = new StringReader(phrase)
      val dp = new DocumentPreprocessor(reader)
      dp.setTokenizerFactory(tokenizerFactory)
      dp.iterator().map(_.toArray.mkString(" ")).toArray
    }else{
      if (sentDetector == null) {
        val sentmodelIn = new FileInputStream(sentModlePath)
        val sentmodel = new SentenceModel(sentmodelIn)
        sentDetector = new SentenceDetectorME(sentmodel)
      }
      sentDetector.sentDetect(phrase)
    }
    trace(DEBUG, retSent.mkString(","))
    retSent
  }

  var tokenizer:TokenizerME = null
  def getToken(str: String) = {
    if (Conf.useStanfordNLP) {
      val reader = new StringReader(str)
      val dp = new DocumentPreprocessor(reader)
      dp.setTokenizerFactory(tokenizerFactory)
      dp.iterator().map(_.toArray().map(_.toString)).flatMap(_.toSeq).toArray
    }else{
      if (tokenizer == null) {
        val tokenModeIn = new FileInputStream(s"${modelRoot}/en-token.bin");
        val model = new TokenizerModel(tokenModeIn);
        tokenizer = new TokenizerME(model);
      }
      tokenizer.tokenize(str).filter(_.trim.length > 0)
    }
  }

  //get pos after case/punctuation delete(input has done this work)?  // XXX: This may be not a correct approach!
  var postagger:POSTaggerME = null
  var tagger:MaxentTagger = null
  /**
   * If a token have no pos tag, use '*' instead.
   * see: http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html for the meaning of tags.
   * @param phraseNorm
   * @return
   */
  def getPos(phraseNorm: Array[String], transfer: Boolean=false) = {
    if (Conf.useStanfordNLP) {
      if (tagger == null) {
        val options = Conf.stanfordTaggerOption.split(" ").map(_.split("=")).filter(_.length>=2).map(t=>(t(0),t(1))).toMap
        val properties = new Properties()
        options.foreach(p=>properties.setProperty(p._1,p._2))
        println(properties)
        val config: TaggerConfig = new TaggerConfig(properties)
        tagger = new MaxentTagger(config.getModel, config)
      }
      val orgPos = tagger.tagSentence(phraseNorm.map(w=>new Word(w)).toList.asJava).iterator().map(t=>t.tag).toArray
      if (transfer) orgPos.map(Nlp.posTransform(_)) else orgPos
    }else {
      if (postagger ==null) {
        val posmodelIn = new FileInputStream(posModlePath)
        val posmodel = new POSModel(posmodelIn)
        postagger = new POSTaggerME(posmodel)
        println("all openNLP POS TAGS: " + postagger.getAllPosTags)
      }
      val orgPos = postagger.tag(phraseNorm)
      if (transfer) orgPos.map(Nlp.posTransform(_)) else orgPos
    }
  }

  // never used for now
  val chunkerModeIn = new FileInputStream(s"${modelRoot}/en-chunker.bin")
  val chunkerModel = new ChunkerModel(chunkerModeIn)
  val chunker = new ChunkerME(chunkerModel)

  def getChunk(words: Array[String], tags: Array[String]) = {
    //chunker.chunk(words,tags)
    chunker.chunkAsSpans(words,tags)
  }

  var parser: opennlp.tools.parser.Parser = null
  def getParser(words: Array[String]) = {
    if (Conf.useStanfordNLP) {
      println("you used the wrong parser api. Current configuration is stanford.")
      null
    }else {
      if (parser == null) {
        val parserModeIn = new FileInputStream(s"${modelRoot}/en-parser-chunking.bin")
        val parserModel = new ParserModel(parserModeIn);
        parser = ParserFactory.create(parserModel)
      }
      val topParses = ParserTool.parseLine(words.mkString(" "), parser, 1)
      if (topParses != null) topParses(0) else null
    }
  }

  var lexicalParser: LexicalizedParser = null
  def getStanfordParse(words: Array[String]) = {
    if (lexicalParser==null) {
      lexicalParser = LexicalizedParser.loadModel()
    }
    val tree = lexicalParser.parse(words.map(new Word(_)).toList.asJava)
    tree
  }

  ///////////// phrase munging methods //////////////

  /**
   * 1. replace all punctuation to space
   * 2. replace all continuous space to one space
   * 3. trim the space at the beginning and the end
   * @param str
   * @return
   */
  def normalizeCasePunct(str: String, tokenSt: TokenState=null): String = {
    var ret = Ngram.Delimiter.matcher(str.toLowerCase()).replaceAll(" ").trim()
    if (ret.length > 0) {
      val str_lps = punctPattern.matcher(ret).replaceAll(" ")
       ret = spacePattern.matcher(str_lps).replaceAll(" ").trim()
    } else {
      // 'str' only contains delimiter, use a special string to indicate it.
      if (tokenSt != null)
        tokenSt.delimiter = true
    }
    ret
  }

  def sortWords(str: Array[String], tokenSt: TokenState=null)  = {
    str.sortWith(_ < _)
  }
  val lvg = new Lvg()
  def stemWords(str: String,tokenSt: TokenState=null): String = {
    val ret = lvg.getNormTerm(str)
    if (ret.length > 0) {
      ret
    }else{
      stemWordsPorter(str,null)
    }

  }
  val stemmer = new PorterStemmer()
  def stemWordsPorter(str: String,tokenSt: TokenState=null): String = {
    stemmer.stem(str)
  }
  def normalizeAll(str: String, tokenSt: TokenState=null, isStem: Boolean=true): String = {
    var ret = normalizeCasePunct(str,tokenSt)
    if (isStem)ret = stemWords(ret,tokenSt)
    //println(s"norm: $str\t$ret")
    ret
  }

  var prefixs = new ArrayBuffer[String]()
  for (line <- scala.io.Source.fromFile(s"${modelRoot}/prefix.txt").getLines()) {
    if (line.trim.length > 0 && !line.trim.startsWith("#"))
      prefixs.append(line.trim)
  }
  prefixs = prefixs.sortBy(_.length * -1)  // sort by length of the string.
  println(s"prefix: ${prefixs.mkString("\t")}")

  var suffixs = new ArrayBuffer[String]()
  for (line <- scala.io.Source.fromFile(s"${modelRoot}/suffix.txt").getLines()) {
    if (line.trim.length > 0 && !line.trim.startsWith("#"))
      suffixs.append(line.trim)
  }
  suffixs = suffixs.sortBy(_.length * -1)  // sort by length of the string.
  println(s"suffix: ${suffixs.mkString("\t")}")


  val stopwords = new mutable.TreeSet[String]()
  for (line <- scala.io.Source.fromFile(s"${modelRoot}/stopwords.txt").getLines()) {
    if (line.trim.length > 0 && !line.trim.startsWith("#"))
      stopwords.add(Nlp.getToken(line).map(t =>{Nlp.normalizeAll(t)}).mkString(" ").trim)
  }

  /**
   * return true if it is a stop word.
   * how does ngram  match the stop words list?
   * 0:exactly matching;
   * 1: ngram contains any stop word;
   * 2. ngram start or end with any stop word
   * @param str
   * @param withRegex check the stop word regex in addition the the stop word file.
   * @return
   */
  def checkStopword(str: String, withRegex: Boolean=true):Boolean ={
    var ret = false
    if (Conf.stopwordMatchType == 0)
      ret ||= stopwords.contains(str)
    else if (Conf.stopwordMatchType == 1) {
      //ret ||= stopwords.exists(s=> str.matches(s".*\\b${s}\\b.*"))
      ret ||= stopwords.exists(s => s == str || str.startsWith(s + " ") || str.endsWith(" " + s) || str.contains(" " + s + " "))
    }else if (Conf.stopwordMatchType == 2)
      ret ||= stopwords.exists(s=> s==str || str.startsWith(s+" ") || str.endsWith(" "+s))

    if (withRegex) ret ||= StopwordRegex.matcher(str).matches()
    ret
  }

  def checkPosFilter(posString: String): Boolean = {
    PosFilterRegex.foreach(p => {
      if (p.matcher(posString).matches())
        return true
    })
    return false
  }

  /**
   *
   * @param blogId blogId
   * @param text the content of the blog
   */
  def generateSentence(blogId: Long, text: String, hSents: mutable.LinkedHashMap[(Long,Int),Sentence] = null): Array[Sentence] = {
    // process the newline as configuration. 1: replace with space; 2: replace with '.'; 0: do nothing
    //    val text_tmp = if (Conf.ignoreNewLine == 1) {
    //      target.replace("\r\n", " ").replace("\r", " ").replace("\n", ". ").replace("\"", "\'")
    //    } else if (Conf.ignoreNewLine == 2) {
    //      target.replace("\r\n", ". ").replace("\r", ". ").replace("\n", ". ").replace("\"", "\'")
    //    } else {
    //      target.replace("\"", "\'")
    //    }
    val text_tmp = text
    //segment the target into sentences
    var sentId = 0
    val sents = Nlp.getSent(text_tmp)
    sents.filter(_.length > 0).map(sent => {
      val sent_tmp = new Sentence()
      sent_tmp.sentId = sentId
      sent_tmp.blogId = blogId
      sent_tmp.words = Nlp.getToken(sent)
      sent_tmp.tokenSt = Array.fill(sent_tmp.words.length)(new TokenState())
      var tokenIdx = -1
      sent_tmp.tokens = sent_tmp.words.map(t => {
        tokenIdx += 1; Nlp.normalizeAll(t, sent_tmp.tokenSt(tokenIdx))
      })
      sent_tmp.Pos = Nlp.getPos(sent_tmp.words,true)
      //sent_tmp.chunk = Nlp.getChunk(sent_tmp.words, sent_tmp.pos)
      //sent_tmp.parser = Nlp.getParser(sent_tmp.words)

      if (hSents != null) hSents.put((blogId, sentId), sent_tmp)
      sentId += 1
      sent_tmp
    })
  }
  def generateNgram(sentence: Seq[Sentence], gramId: AtomicInteger, hNgrams: mutable.LinkedHashMap[String,Ngram], maxN: Int = Ngram.N): Unit = {
    sentence.foreach(sent => {
      // process ngram
      var pos = 0
      //val grams = new ArrayBuffer[Ngram]()
      // for each sentence
      while (pos < sent.tokens.length) {
        if (sent.tokens(pos).length > 0) {
          // the start token should not be blank
          // for each stat position, get the ngram
          var hitDelimiter = false  // the ngram should stop at delimiter, e.g. comma.
          Range(0, maxN).foreach(n => {
            if (pos + n < sent.tokens.length && !hitDelimiter) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                val key = Ngram.getKey(gram_text,sent.Pos.slice(pos, pos+n+1).mkString(""))
                if (sent.tokens(pos + n).length > 0 && Ngram.checkNgram(gram_text, sent,pos,pos+n+1)) {
                  // check if the gram is valid. e.g. stop words
                  val gram = Ngram.getNgram(key,gram_text, hNgrams)
                  // some info have to update when the gram created
                  if (gram.id < 0) {
                    gram.id = gramId.getAndAdd(1)
                    gram.n = n + 1
                    gram.updateOnCreated(sent, pos, pos + n + 1)
                  }
                  // on create or found it again.
                  gram.updateOnHit(sent,pos,pos+n+1)
                  if (gram.tfAll == 1) {
                    traceFilter(INFO, gram.text, s"Creating gram ${gram}, sent:${sent}")
                  } else {
                    traceFilter(INFO, gram.text, s"Updating gram ${gram}, sent:${sent}")
                  }
                }
              } else {
                hitDelimiter = true
              }
            }
          })

        }
        pos += 1
      }
    })
  }

  def generateNgramStage2(sentence: Seq[Sentence],
                    gramId: AtomicInteger,
                    hNgrams: mutable.LinkedHashMap[String,Ngram],
                    hPreNgram: mutable.HashMap[String, Ngram]=null,
                    ngram: Int = Ngram.N
                    ): Unit = {
    if (Conf.bagsOfWord && Nlp.wordsInbags == null) {
      Nlp.wordsInbags = if (Conf.bowTopNgram>0) {
        (if(Conf.bagsOfWordFilter) hPreNgram.filter(kv=>kv._2.isUmlsTerm(Conf.trainOnlyChv)) else {hPreNgram}).map(kv=>(kv._1,kv._2.tfAll)).toSeq.sortBy(_._2 * -1).take(Conf.bowTopNgram).map(_._1).zipWithIndex.toMap
      }else{
        (if(Conf.bagsOfWordFilter) hPreNgram.filter(kv=>kv._2.isUmlsTerm(Conf.trainOnlyChv)) else {hPreNgram}).map(kv=>(kv._1,kv._2.tfAll)).toSeq.sortBy(_._2 * -1).map(_._1).zipWithIndex.toMap
      }
      println(Nlp.wordsInbags.toSeq.sortBy(_._2).mkString("\t"))
    }

    //println(s"generateNgramStage2, pre gram # ${hPreNgram.size}")
    sentence.foreach(sent => {
      // process ngram
      var pos = 0
      val gramInSent = new ArrayBuffer[(Int,Ngram,Ngram)]()
      //val grams = new ArrayBuffer[Ngram]()
      // for each sentence
      while (pos < sent.tokens.length) {
        if (sent.tokens(pos).length > 0) {
          // the start token should not be blank
          // for each stat position, get the ngram
          var hitDelimiter = false  // the ngram should stop at delimiter, e.g. comma.
          Range(0, ngram).foreach(n => {
            if (pos + n < sent.tokens.length && !hitDelimiter) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                val key = Ngram.getKey(gram_text,sent.Pos.slice(pos, pos+n+1).mkString(""))
                val pre_gram = hPreNgram.getOrElse(key,null)
                if ( (sent.tokens(pos + n).length > 0 && pre_gram != null) ) {
                  //in stage 2, we can directly use result of stage 1
                  val gram = Ngram.getNgram(key,gram_text, hNgrams)
                  // some info have to update when the gram created
                  if (gram.id < 0) {
                    gram.id = gramId.getAndAdd(1)
                    gram.n = n + 1
                    // first we obtain the information of this occurring sentence, but it may be overlap by first occurring info.
                    gram.updateOnCreated(sent, pos, pos + n + 1)
                    gram.getInfoFromPrevious(pre_gram)
                  }
                  // on create or found it again.
                  gram.updateOnHit(sent,pos,pos+n+1,hPreNgram)
                  traceFilter(INFO, gram.text, s"Updating gram ${gram}, sent:${sent}")
                  gramInSent.append((pos,gram,pre_gram))
                }
              } else {
                hitDelimiter = true
              }
            }
          })

        }
        pos += 1
      }
      updateContex(sent,gramInSent)
    })
  }

  def updateContex(sent:Sentence, gramInSent: ArrayBuffer[(Int,Ngram,Ngram)]): Unit = {
    // the 'for' is roughly search for a window,
    // and the (e._1>s._1 && (e._1-s._1+s._2.n)>Nlp.WinLen) will finally determin the actually window.
    //for (win_center <- 0 to gramInSent.size; win_walker <-(win_center-Nlp.WinLen) to (win_center+Nlp.WinLen) if (win_walker>=0 && win_walker < gramInSent.size) ) {
    for (start <- Range(0,gramInSent.size)) {
      val c = gramInSent(start) // center gram in the windows
      val cGram = c._2 // the center gram, which is to be updated
      var nearestUmls = Ngram.WinLen
      var nearestChv  = Ngram.WinLen
      for (end <- Range(0, gramInSent.size) if (start != end)) {
        val w = gramInSent(end) // grams walking around the center-gram in the range of window
        val wPreGram = w._3 // result of stage 1 for walker gram

        //exclude the term that is nested by cGram
        if (w._1<c._1 || w._1+wPreGram.n>c._1+cGram.n) {
          // update in the sentence
          if (wPreGram.isUmlsTerm(true)) {
            // score of chv
            cGram.context.sent_chvCnt += 1
            cGram.context.sent_umlsCnt += 1
          } else if (wPreGram.isUmlsTerm(false)) {
            //score of umls
            cGram.context.sent_umlsCnt += 1
          }
        }

        // update bags of words counter by sentence
        if (Conf.bagsOfWord && cGram.context.wordsInbags != null) {
          val index = Nlp.wordsInbags.get(wPreGram.key).getOrElse(-1)
          traceFilter(INFO, cGram.text, s"${cGram.key} found ${wPreGram.key},index ${index}, blog ${sent.blogId},sent ${sent.sentId}, ${sent.tokens.mkString(",")}, ${gramInSent.map(v=>(v._1,v._2.key)).mkString(",")}")
          if (index>=0){
            cGram.context.wordsInbags(index) += 1
          }
        }

        // update the nearest umls term, exclude the term that is nested by cGram
        if (w._1<c._1 || w._1+wPreGram.n>c._1+cGram.n) {
          if (wPreGram.isUmlsTerm() && Math.abs(c._1 - w._1) < nearestUmls) {
            traceFilter(INFO, cGram.text, s"umls/chv dist is ${c._1} & ${w._1}, ${cGram.key}, ${wPreGram.key}")
            nearestUmls = Math.abs(c._1 - w._1)
          }
          if (wPreGram.isUmlsTerm(true) && Math.abs(c._1 - w._1) < nearestChv) {
            traceFilter(INFO, cGram.text, s"chv dist is ${c._1}-${w._1}, ${cGram.key}, ${wPreGram.key}")
            nearestChv = Math.abs(c._1 - w._1)
          }
        }
        // update in the window,  exclude the term that is nested by cGram
        if (((w._1 + w._2.n + Ngram.WinLen > c._1) && (c._1 + c._2.n + Ngram.WinLen) < w._1) && (w._1<c._1 || w._1+wPreGram.n>c._1+cGram.n)) {
          if (wPreGram.isUmlsTerm(true)) {
            // score of chv
            cGram.context.win_chvCnt += 1
            cGram.context.win_umlsCnt += 1
          } else if (wPreGram.isUmlsTerm()) {
            //score of umls
            cGram.context.win_umlsCnt += 1
          }
        }
      }
      cGram.context.umlsDist += nearestUmls
      cGram.context.chvDist += nearestChv

      var winStart = if (c._1-Conf.WinLen>0) c._1-Conf.WinLen else 0
      var winEnd = if (c._1+Conf.WinLen>sent.Pos.size) sent.Pos.size else c._1+Conf.WinLen
      sent.Pos.slice(winStart,winEnd).foreach(p => {
        val index = Conf.posInWindown.indexOf(p)
        if (index>=0) cGram.context.win_pos(index) += 1
      })

      // if only count the prefix/suffix of ngram itself, change the window to itself
      if (!Conf.prefixSuffixUseWindow) {
        winStart = c._1
        winEnd = c._1 + c._2.n
      }
      updatePrefixSuffix(sent.tokens.slice(winStart, winEnd),cGram)

    }

  }

  /**
   *
   * @param words: the window of words that we have to check if contain pre/suf-fix
   * @param ngram: the ngram that we are going to update the context
   */
  def updatePrefixSuffix(words: Seq[String], ngram: Ngram) = {
    // if only count the prefix/suffix of ngram itself, change the window to itself
    words.foreach(w => {
      breakable {
        Nlp.prefixs.zipWithIndex.foreach(kv => {
          if (w.startsWith(kv._1)) {
            ngram.context.win_prefix(kv._2) += 1
            break
          }
        })
      }
      breakable {
        Nlp.suffixs.zipWithIndex.foreach(kv => {
          if (w.endsWith(kv._1)) {
            ngram.context.win_suffix(kv._2) += 1
            break
          }
        })
      }
    })
  }

  /**
   * When the gram is create, we get some useful info from the sentence.
   * gram is sentence.tokens[start, end)
   * pos tag see: http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
   * 6.	  IN	Preposition or subordinating conjunction
   * 7.	  JJ	Adjective
   * 8.	  JJR	Adjective, comparative
   * 9.	  JJS	Adjective, superlative
   * 12.	NN	Noun, singular or mass
   * 13.	NNS	Noun, plural
   * 14.	NNP	Proper noun, singular
   * 15.	NNPS	Proper noun, plural
   */
  def posTransform(pos: String) = {
    /**
    0. Noun, basic pattern, name N
    1. Noun+Noun, named NN
    2. (Adj|Noun)+Noun, named ANN
    3. ((Adj|Noun)+|((Adj|Noun)?(NounPrep)?)(Adj|Noun)?)Noun, named ANAN
      */
    val nounPos = "NN NNS NNP NNPS".split(" ")
    if (nounPos.contains(pos))
      "N" // noun
    else if (pos == "JJ" || pos == "JJR" || pos == "JJS")
      "A" // adjective
    else if (pos == "IN")
      "P" // preposition or a conjunction that introduces a subordinate clause, e.g., although, because.
    else if (pos == "RB"|pos == "RBR"|pos == "RBS"| pos=="WRB")
      "R" // Adverb
    else if (pos == "VB"|pos == "VBD"|pos == "VBG"|pos=="VBN"|pos=="VBP"|pos=="VBZ")
      "V" // verb
    else if (pos == "TO")
      "T" // to
    else if ("DT" ==pos|"PDT"==pos|"WDT"==pos)
      "D" // determiner
    else if (pos == "EX")
      "E" // existential
    else if (pos == "FW")
      "F" // foreign word
    else if (pos == "CD")
      "M" // cardinal number
    else if (pos == "RPR"|pos == "RPR$"|pos == "WP"|pos == "WP$")
      "U" // pronoun
    else if (pos == "CC")
      "C" // a conjunction placed between words, phrases, clauses, or sentences of equal rank, e.g., and, but, or.
    else
      "O" // others
  }

  /**
   * preprocessing the blog text. e.g. punctuation,
   * @param blogId
   * @param text
   * @return
   */
  def textPreprocess(blogId: Long, text: String) = {
    val ret = text.replaceAll("([~`=+<>,:|;/\"\\[\\]\\(\\)\\{\\}\\.!\\?\\|\\\\])"," $1 ").replaceAll("\\s+"," ")
    (blogId, ret)
  }
  /**
   * for test
   * @param argv
   */
  def main(argv: Array[String]): Unit = {

    Trace.currLevel = DEBUG

//    val lvg =  new Lvg()
//    val ret = lvg.getNormTerm("glasses")
//    println(s"lvg out put ${ret}")
//
//    val tagger = new UmlsTagger2(Conf.solrServerUrl, Conf.rootDir)
//    def textPreprocess(blogId: Int, text: String) = {
//      val ret = text.replaceAll("([:|;\"\\[\\]\\(\\)\\{\\}\\.!\\?/\\\\])"," $1 ").replaceAll("\\s+"," ")
//      (blogId, ret)
//    }
    val text = "The girl you love has more than 2 dozens of boy friends in the last 3 years before you met her."
    val tokens = Nlp.getToken(text)
//    val orgPos = postagger.tag(tokens)
    val orgPos = Nlp.getPos(tokens)
    val pos = orgPos.map(Nlp.posTransform(_))
    println(tokens.mkString("\t"))
    println(orgPos.mkString("\t"))
    println(pos.mkString("\t"))
    val tree = Nlp.getStanfordParse(tokens)
    tree.pennPrint()
    //println(tree.flatten())
    tree.iterator().foreach(t=>{
      println(s"label: ${t.label()}, tags: ${t.taggedYield()}, span: ${t.getSpan}, size: ${t.size}, value: ${t.value}")
    })

//    val ret = Nlp.getToken(text)
//    val ret2 = ret.map(Nlp.normalizeAll(_))
//
//    println(s"Nlp result: ${ret2.mkString(" ")}")
//    println("nlp then old tool result: " + tagger.normalizeAll(text,false))
//    println("select result: " + tagger.select(text).mkString(","))
//    println("select result: " + tagger.getUmlsScore(text))



//    val s1 = Nlp.generateSentence(1,"""Hi, how are you going? My name is Jason, an (international student). Jason! jason? jason;jason:jason.""",Ngram.hSents)
//    val s2 = Nlp.generateSentence(2,"""jason is study in fsu for more then 3 month. His Chinese name is zc..""",Ngram.hSents)
//    val s3 = Nlp.generateSentence(3,"""As a international student, Jason have to study English hard..""",Ngram.hSents)
//    val gramId = new AtomicInteger()
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    gramId.set(0)
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    gramId.set(0)
//    Nlp.generateNgram(s1,gramId,Ngram.hNgrams)
//    Ngram.hSents.foreach(sent =>{
//      println("#sentence# " + sent._2.toString())
//    })
//
//    Ngram.hNgrams.foreach(gram =>{
//      println("[gram]:" + gram._2.toString())
//    })
    //println(allPosTag.mkString("*"))
  }
}


