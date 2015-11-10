package com.votors.ml

import opennlp.tools.chunker._
import opennlp.tools.cmdline.parser.ParserTool
import opennlp.tools.parser.{ParserFactory, ParserModel}

import scala.collection.mutable.ArrayBuffer
import java.io._
import java.nio.charset.CodingErrorAction
import java.util.regex.Pattern
import java.util.Properties

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
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel, WhitespaceTokenizer}
import opennlp.tools.util.ObjectStream
import opennlp.tools.util.PlainTextByLineStream
import java.sql.{Statement, Connection, DriverManager, ResultSet}

import org.apache.commons.csv._
import com.votors.ml._

import com.votors.ml
/**
 * Created by Jason on 2015/11/9 0009.
 */
class Nlp {

  /**
   *
   * @param blogId blogId
   * @param target the content of the blog
   * @param ngram the max words in a gram
   */
  def processText(blogId: Int, target: String, ngram: Int = Ngram.N) = {
    // process the newline as configuration. 1: replace with space; 2: replace with '.'; 0: do nothing
    val target_tmp = if (Conf.ignoreNewLine == 1) {
      target.replace("\r\n", " ").replace("\r", " ").replace("\n", ". ").replace("\"", "\'")
    } else if (Conf.ignoreNewLine == 2) {
      target.replace("\r\n", ". ").replace("\r", ". ").replace("\n", ". ").replace("\"", "\'")
    } else {
      target.replace("\"", "\'")
    }

    //segment the target into sentences
    var sentId = 0
    val sents = Nlp.getSent(target_tmp)
    sents.filter(_.length>0).map(sent => {
      val sent_tmp = new Sentence()
      sent_tmp.sentId = sentId
      sent_tmp.blogId = blogId
      sent_tmp.words = Nlp.getToken(sent)
      sent_tmp.tokenSt = Array.fill(sent_tmp.words.length)(new TokenState())
      var tokenIdx = -1
      sent_tmp.tokens = sent_tmp.words.map(t =>{tokenIdx += 1; Nlp.normalizeAll(t,sent_tmp.tokenSt(tokenIdx))})
      sent_tmp.pos = Nlp.getPos(sent_tmp.words)
      sent_tmp.chunk = Nlp.getChunk(sent_tmp.words, sent_tmp.pos)
      sent_tmp.parser = Nlp.getParser(sent_tmp.words)

      Ngram.hSents.put((blogId,sentId), sent_tmp)
      sentId += 1
      sent_tmp
    }).foreach(sent => {
      // process ngram
      var pos = 0
      // for each sentence
      while (pos < sent.tokens.length) {
        if (sent.tokens(pos).length > 0) {
          // the start token should not be blank
          // for each stat position, get the ngram
          var hitDelimiter = false  // the ngram should stop at delimiter, e.g. comma.
          Range(0, ngram).foreach(n => {
            if (pos + n < sent.tokens.length && hitDelimiter == false) {
              if (sent.tokenSt(pos + n).delimiter == false) {
                val gram_text = sent.tokens.slice(pos, pos+n+1).mkString(" ").trim()
                if (sent.tokens(pos + n).length > 0 && Ngram.checkNgram(gram_text)) {
                  // check if the gram is valid. e.g. stop words
                  val gram = Ngram.getNgram(gram_text)
                  gram.updateBlog(blogId, sentId)
                  gram.n = n+1
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
}

object Nlp {
  final val NotPos = "*"      // the char using indicating this is not a POS tagger, may be a punctuation
  final val TokenEnd = "$$"
  val punctPattern = Pattern.compile("\\p{Punct}")
  val spacePattern = Pattern.compile("\\s+")
  //val solrServer = new HttpSolrServer(Conf.solrServerUrl)

  //opennlp models path
  val modelRoot = Conf.rootDir + "/data"
  val posModlePath = s"${modelRoot}/en-pos-maxent.bin"
  val sentModlePath = s"${modelRoot}/en-sent.bin"

  //get pos after case/punctuation delete(input)?  // XXX: This may be not a corret approach!
  val sentmodelIn = new FileInputStream(sentModlePath)
  val sentmodel = new SentenceModel(sentmodelIn)
  val sentDetector = new SentenceDetectorME(sentmodel)

  def getSent(phrase: String) = {
    val retSent = sentDetector.sentDetect(phrase)
    trace(DEBUG, retSent.mkString(","))
    retSent
  }

  val tokenModeIn = new FileInputStream(s"${modelRoot}/en-token.bin");
  val model = new TokenizerModel(tokenModeIn);
  val tokenizer = new TokenizerME(model);

  def getToken(str: String) = {
    tokenizer.tokenize(str).filter(_.trim.length>0)
  }

  //get pos after case/punctuation delete(input has done this work)?  // XXX: This may be not a correct approach!
  val posmodelIn = new FileInputStream(posModlePath)
  val posmodel = new POSModel(posmodelIn)
  val postagger = new POSTaggerME(posmodel)
  val allPosTag = postagger.getAllPosTags

  /**
   * If a token have no pos tag, use '*' instead.
   * see: http://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html for the meaning of tags.
   * @param phraseNorm
   * @return
   */
  def getPos(phraseNorm: Array[String]) = {
    val retPos = postagger.tag(phraseNorm)
    //trace(DEBUG,phraseNorm + " pos is: " + retPos.mkString(","))
    retPos
  }

  val chunkerModeIn = new FileInputStream(s"${modelRoot}/en-chunker.bin")
  val chunkerModel = new ChunkerModel(chunkerModeIn)
  val chunker = new ChunkerME(chunkerModel)

  def getChunk(words: Array[String], tags: Array[String]) = {
    //chunker.chunk(words,tags)
    chunker.chunkAsSpans(words,tags)
  }

  val parserModeIn = new FileInputStream(s"${modelRoot}/en-parser-chunking.bin")
  val parserModel = new ParserModel(parserModeIn);
  val parser = ParserFactory.create(parserModel)

  def getParser(words: Array[String]) = {
    val topParses = ParserTool.parseLine(words.mkString(" "), parser, 1)
    if (topParses != null) topParses(0) else null
  }

  ///////////// phrase munging methods //////////////

  /**
   * 1. replace all punctuation to space
   * 2. replace all continuous space to one space
   * 3. trim the space at the beginning and the end
   * @param str
   * @return
   */
  def normalizeCasePunct(str: String, tokenSt: TokenState): String = {
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

  def sortWords(str: Array[String], tokenSt: TokenState)  = {
    str.sortWith(_ < _)
  }

  val stemmer = new PorterStemmer()
  def stemWords(str: String,tokenSt: TokenState): String = {
    stemmer.stem(str)
  }
  def normalizeAll(str: String, tokenSt: TokenState=null, isStem: Boolean=true): String = {
    var ret = normalizeCasePunct(str,tokenSt)
    if (isStem)ret = stemWords(ret,tokenSt)
    ret
  }

  /**
   * for test
   * @param argv
   */
  def main(argv: Array[String]): Unit = {

    Trace.currLevel = DEBUG

    val nlp = new Nlp()
    nlp.processText(1,"""Hi, how are you going? My name is Jason a b c d e f g, an (international student). Jason! jason? jason;jason:jason.""")
    nlp.processText(2,"""1. Understanding
                        |
                        |A long-debated problem in the philosophy of artificial intelligence concerns whether intelligent machines are able to understand their own tasks. Some intelligent machine-engineers and researchers might say that whether a machine understands the tasks it is performing has no implications as to its pragmatic function, which concerns how efficiently the machine can execute these tasks. But for certain tasks an intelligent machine must surely understand the tasks it is performing.  An ‘understanding machine’ may be the only kind of machine that stands a chance of passing the Turing Test: an understanding machine would be able to consistently compose meaningful sentences and thereby communicate efficiently—the way humans communicate.
                        |
                        |An understanding machine might even be able to produce brilliant works that rival renowned writers like Shakespeare. I say this because language is determined by both semantics and syntax, if the machine does not possess any semantic properties it must rely on syntax and specific software that correlates words. Such a machine would only be able to compose a limited number of intelligent sentences, however, and this would be the equivalent of a human manipulating the words of a language he or she does not understand. Considering this it seems a little far-fetched to expect a syntactic machine to consistently compose meaningful sentences.  An intelligent human who does not have knowledge of a new language would be incapable of so composing consistently intelligent sentences, and it seems that the expectation of syntactic machines to perform such a task is not grounded in a reasonable assumption.
                        |
                        |The assumption is that syntactic machines are able to communicate intelligently without understanding a language but the standard we use for comparing intelligence is that of humans, for there is no further recourse. If an intelligent human cannot compose meaningful sentences without understanding the language at hand then by what logic are we to assume that an intelligent machine could? The assumption completely neglects the important function of understanding and semantics in intelligent communication, which is obviously evident in humans.
                        |
                        |Even though the mechanism behind how semantics and understanding emerges in human cognition is still poorly understood, one cannot say that the mechanism that allows humans to understand language is irrelevant in intelligent communication. If the Turing Test is a measure of artificial intelligence, machines that understand language would surely have the best chance of passing the Turing Test. The problem of engineering such a machine becomes apparent when one delves into the nature of meaning and the emergence of meaning from electrical impulses—though there are certain human attributes that an intelligent machine needs to possess in order to understand language.
                        |
                        |
                        |
                        |2. The Emergence of Meaning
                        |
                        |In the ‘Chinese Room’ thought experiment John Searle proposed that artificially intelligent machines, even if conscious, would be unable to understand language on account of the way they function. Computers manipulate symbols and words based on syntactic instructions pre-written in their programs. The computer is following instructions, and though it does this very well, there is no human learning or understanding involved in the computer’s processing these instructions or its generating outputs. It seems that it is hard to comprehend how an intelligent being begins to understand language; and this is of course expected since the understanding of language relies on the emergence of meaning. One has to understand the meaning of words in order to understand language. The hurdle of engineering a computer that understands language is concerned with the emergence of meaning.
                        |
                        |There is no plausible mechanism or an objectively satisfying explanation that describes how meaning emerges from electrical impulses. This applies whether we are talking about the electrical impulses in a functional computer or the electrical impulses generated by human neurophysiology. As humans we know things mean something to us; we have meaningful experiences but such are not reducible to our neurophysiology.
                        |
                        |John Searle demonstrated that the emergence of meaning is not reducible to the instructions coded in computer software when he created the ‘Chinese Room’ thought experiment. Searle showed that a computer which is efficient at generating language could never apprehend the meaning of the words and sentences it is generating. Searle asks the reader: At what point in this mechanism, which is modelled after typical computer software, does meaning emerge? The conclusion is obvious: if the machine only follows instructions and never grasps the meaning of words, it will never learn what they denote and as a consequence it will never be able to understand language.
                        |
                        |Similarly we can ask where meaning emerges in our neurophysiology. Can we say that a single neuron can have a meaningful experience when it is stimulated by electricity? What about a small network of neurons? We can then further extend the network to include millions or billions of neurons and we can still ask the question of emergent meaning: when are these neurons capable of having a meaningful experience? We can infinitely add more neurons and make the neural network infinitely more complex; but it appears we will never know how meaning emerges from neurons, since their number or complexity of network provides us with nothing by way of a plausible mechanism for the emergence of meaning. We can always code more instructions into the computer software and make it infinitely more complex, as Searle proposed, but it seems semantics will never emerge from this.
                        |
                        |Although how meaning emerges from a physical or a biophysical mechanism is a puzzling and confusing question, we can think about how the meaning of words begins to emerge during development. We can take a simple example from our everyday experience and observe how children learn new languages. Children learn to speak before they learn to read; they do not have the convenience of consulting dictionaries. In truth we learn very few words by consulting dictionaries, and if we think about the emergence of language in general, it must have originated before words were institutionally defined: we learn language by associating words with experiences, phenomena, and concepts.
                        |
                        |The way people understand words is also highly abstract. Understanding does not emerge solely within associations between objects and words; it emerges as a web of associations between a concrete object, its abstract representation (which does not have a substance or form), and the term that describes the object. This abstract representation is what we call conceptual understanding. Our conceptual understanding of a tree, for instance, is not represented by any specific physical example of a tree; it is represented by our abstract understanding of it, understanding as a collection of properties or information that pertain to specific trees.
                        |
                        |This web of associations is crucial. If we associate a word and a concept but fail to associate both word and concept with an actual object, we are stuck with abstract definitions which would seem like a collection of information that does not apply to anything specific or actual. In such a case we could learn about the word ‘tree’ and what the properties of a tree might be, but we would not recognize a tree if we were to come across one. On the other hand if we associate the word and the actual object, but fail to associate the word and the actual object with the concept, we would then be unable to collectively identify several different types of trees, or a picture of a tree, even though we are able to identify a specific. The understanding of what words mean requires a web of associations between concepts, objects, and words; an intelligent machine intelligent enough to understand language must thus possess this ability to create webs of associations.
                        |
                        |
                        |
                        |3. The Problem of Precise Definitions
                        |
                        |The meaning of a word is associated with a the phenomenon it denotes. We can, however, semantically associate a given word with other words that describe or define the word in question. We often attempt to provide a precise definition of a word in terms of others: for instance we can identify the word ‘tree,’ the object, and the concept with which it is associated, but we cannot describe what the word actually means, precisely, unless we use other words to provide an accurate description. We might say the tree is a living organism, it has branches, or it has leaves, and so on.
                        |
                        |Language acquisition through precise definitions is impossible, however—since if we fail to associate words with anything else, other than words, we must be stuck in a cycle of ignorance (which we are not). If we start looking for the precise definition of any given word, we have to understand it in terms of other words, but since we do not understand what these others mean we have to look for their respective definition as well. We look endlessly for definitions of words we do not understand, and if we fail to associate these with anything beyond lingual terms, we fail to understand what any of those words might mean.
                        |
                        |We may think of a given word W, and this word is precisely defined by another set of words, W1. But because each of these words in W1 must also be defined precisely, there has to be yet another set of words, W2, which defines those in W1. And again, since the set of words in W2 require precise definition, there must be a further set of words, W3, that defines those in W2—ad infinitum.
                        |
                        |The problem here is that once we start to define words in terms of other words we end up going into an infinite regress of definitions in order to find the precise definition of any given example. We define endlessly without ever hitting that precise definition. This is why words are naturally vague and ambiguous: they cannot be defined precisely.
                        |
                        |So it is clear that one cannot initially learn the meaning of words in terms of others, since this kind of learning is impossible as demonstrated above. Someone who attempts to learn the meaning of words solely by rifling through dictionaries falls into an infinite regress of definitions which appear ultimately meaningless to the reader. Language has to be learned through associations of words with concepts and actual objects.  The intelligent machine that understands language must possess some kind of sensor that allows it to make associations between words and objects. Computers can easily make these associations for they are simple relations between concrete symbols and objects. It is difficult, however, to know whether the intelligent machine is able to form a concept from this association. The major problem with conceptual formation is that the process requires semantics. It is a mental process that requires a certain understanding in order to make successful associations between concept, word, and object. This understanding relates to how and why a collection of properties, or information, pertains to a specific object.
                        |
                        |
                        |
                        |4. The Learning Machine
                        |
                        |Learning is crucial for language semantics. From what we know about language acquisition, one cannot develop the semantics and the understanding of language without first learning that language. Previously I mentioned why learning is crucial for language acquisition and why language needs to be learned by the association of actual objects with concepts and words. The process of learning a language is also the process which allows us to begin understanding language. Language acquisition is therefore developmental by nature: it is something that must develop through an interaction of organism and environment.
                        |
                        |Learning the meaning of words through association with others is problematic. Such a method of learning does not allow for understanding on account of the reasons stated above. Similarly we can conceive of a Turing machine bearing database of all words, books, and encyclopedias ever written: this machine has an endless supply of literature; it has enough computing power to associate words with other words and words with sentences; and from these correlations it is able to compose intelligible discourse. But such a computer would be unable still to understand the meaning of the words it uses. It can only correlate words with other words, and words with sentences, but under these conditions semantics and understanding cannot emerge. The machine knows how words correlate but it cannot know how they are correlated to the phenomenon they are intended to denote.
                        |
                        |What if we installed a sensory device and we programmed the machine to associate words with objects? In such a case the computer might make intelligible associations between such things, but as stated above the machine must also have a conceptual understanding of the words it uses. Nor can organisms that understand language rely on a static, pre-determined code to learn language because one cannot program the inductive knowledge necessary for language acquisition using a static pre-written code. The machine might have an infinite number of terms and sensory devices installed but if it cannot encounter the actual phenomena the words are meant to denote it cannot have perception and understanding of what those phenomena might mean: it cannot understand how the words are related to existing objects. It lacks the capacity to acquire inductive knowledge.
                        |
                        |If we posit that a machine can understand without acquiring inductive knowledge it would be similar to stating that the machine can somehow know or understand facts about the world without encountering or observing them. It seems a little ridiculous to claim that a machine can acquire such ‘magical’ or ‘psychic’ knowledge, especially when discussing the kind of knowledge necessary to language acquisition.
                        |
                        |A machine that runs on a static pre-written code is incapable of acquiring inductive knowledge because upon observation or encounter of a particular fact the machine must undergo changes in processing or operation. The state of the machine has to transition from ‘not knowing that particular fact’ to ‘knowing that particular fact.’ Learning a language requires some degree of creative self-processing because learning something new entails the altering of one’s processing. Such changes are regularly observed in organisms that have the capacity to learn and acquire inductive knowledge—not machines.
                        |
                        |I will admit that the term ‘creative self-processing’ is somewhat vague. A creative self-processing machine would, for instance, alter its own pre-written processing—and we could always wonder at what point a machine, or its environment, might be altering its own processing. But can we say that a machine that evolves and acquires linguistic skills through association of words and concepts with its environment is learning in a human way? The machine does not have a pre-written program for language, but it picks up language as it interacts with its environment and the speakers it encounters. We could say that such a machine is ‘learning,’ but if it is doing so by association can we assume that the machine understands? We might assume that it understands but that this interpretation of ‘understanding’ is inaccurate for a number of reasons (and I will go into detail about this later).
                        |
                        |Other important aspects in human learning are related to the plasticity of the brain. During development and learning the brain’s connectivity undergoes various changes, new connections, and the formation of new synapses. We know that as new neural synapses are being formed this phenomenon will alter the activity of individual neurons. Within the human brain we are not only concerned with decentralized processing, but also the fact that the processing units so applied tend to change as the human brain learns. There are myriad factors involved in human learning, such as epigenetic factors, which alter gene expression in the neural cells—once again altering the nature of the processing.
                        |
                        |I would like to emphasize that human learning is a dynamic system where constant changes occur in the processing and these changes have an effect on how humans come to learn and understand language. These kinds of changes are the kind of changes that we would expect a creative self-processing machine to undergo.
                        |
                        |
                        |
                        |5. Is the Learning Machine an Understanding Machine?
                        |
                        |Previously I stated that association is necessary for understanding languages, but in this I did not mean it is sufficient: learning is required as well. Now we can ask if learning by association is sufficient for understanding. Once again the answer is probably ‘no.’ We know that both learning and association are important to language acquisition, but we are still a long way from making sense of the understanding machine. The emergence of meaning still remains a mystery. Learning by association does not guarantee that a conceptual understanding (the kind of understanding required for language semantics) will emerge. As a consequence we cannot at this stage know when a machine begins to form concepts. Conceptual formation is the necessary element of artificial intelligence that remains vague and poorly defined in the physical sciences. One cannot define a concept or the emergence of a concept physically: as I mentioned earlier the associations between concepts and words requires understanding. When one understands the concept of the word, then the meanings of that word begin to emerge.
                        |
                        |Other difficulties that pose obstacles for an understanding machine are related to the means of perceiving phenomena. We know, for instance, that there are computers that play chess and possess learning algorithms but is it reasonable to claim these computers understand what chess is? Let us assume for argument’s sake that these machines have a conceptual understanding of it: but we must now think about all the other things the machine must understand in order to grasp chess the way we do. I do not think we can claim that chess-learning computers have an adequate understanding of chess on account of their lack of a wide enough understanding of the cultural context in which chess is played and the context in which chess is defined as a leisure game or a sport. The chess-playing computer cannot therefore understand chess, or rather it cannot understand chess the way we understand it. For us, however, understanding appears to emerge from perceiving an integrated network of phenomena and experiences. A machine that lacks such perception will be unable to understand things the way we do; and it would be ridiculous for us to expect the machine to understand phenomena in such a way if we do not share a common means of perception with it.
                        |
                        |If we want to build a machine that understands language, we have to ask ourselves what else the machine must understand besides language. Like chess, language is probably not an isolated phenomenon; it is integrated with many other aspects of our phenomenal experience which we must ourselves understand before we can start developing our understanding of language. Humans must have a certain mechanism for perceiving language; and in order for an intelligent machine to understand it the way we do, it must share with us a common perception it currently lacks.
                        |
                        |To summarize I have identified two properties which are crucial in order for the machine to understand language. One is the process of creating a web of associations between words, objects, and concepts; the second is the machine’s capacity to learn by association or, in other words, to acquire inductive knowledge. The difficulty in building a machine that understands concepts presents a major hurdle in building engineering something that grasps the meaning of language, and phenomena in general. Building a computer that understands concepts and knows the meaning of words is currently beyond the scope of scientific engineering, since there is no plausible physical mechanism that explains the emergence of meaning. Finally, the understanding of language requires a more integrated phenomenal experience and a human perception of language—none of which intelligent machines currently possess..""")

    Ngram.hSents.foreach(sent =>{
      println("#sentence# " + sent._2.toString())
    })

    Ngram.hNgrams.foreach(gram =>{
      println("[gram]:" + gram._2.toString())
    })
    //println(allPosTag.mkString("*"))
  }
}


