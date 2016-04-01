package com.votors.umls

import java.io.{FileWriter, PrintWriter, File}
import com.votors.common.Conf
import com.votors.common.Utils.Trace
import com.votors.common.Utils.Trace._
import org.junit.{AfterClass, Assert, Test}

import scala.io.Source

class UmlsTagger2Test   {

    // The root config dir of the opennlp models files and and
    val rootDir = Conf.rootDir
    Trace.currLevel = ERROR

    if (! new File(rootDir).exists()) {
      println("Error! You have to config a valid dataDir in class UmlsTagger2Test first")
      sys.exit(1)
    }

    @Test
    def testBuildIndexJson(): Unit = {
      val tagger = new UmlsTagger2("",rootDir)
      tagger.buildIndexJson(
        new File("C:\\fsu\\target.terms.csv"),
        new File("C:\\fsu\\target.terms.txt"))
    }

  @Test
  def testBuildIndexXml(): Unit = {
    val tagger = new UmlsTagger2("",rootDir)
    tagger.buildIndexCsv(
      new File("C:\\fsu\\target.terms.csv"),
      new File("C:\\fsu\\target.terms.ret.csv"))
  }
  @Test
  def testBuildIndex2db(): Unit = {
    val tagger = new UmlsTagger2("",rootDir)
    tagger.buildIndex2db(
      new File("C:\\fsu\\target.terms.csv"))
  }

    @Test
    def testGetFull(): Unit = {
      val tagger = new UmlsTagger2(Conf.solrServerUrl,rootDir)
      val phrases = List("age")
      phrases.foreach(phrase => {
        Console.println()
        Console.println("Query: %s".format(phrase))
        val suggestions = tagger.select(phrase)
        suggestions match {
          case suggestion: Array[Suggestion] => {
            suggestion.foreach(s => Console.println(s.toString()))
            //Assert.assertNotNull(suggestion.cui)
          }
          case _ =>
            Assert.fail("No results for [%s]".format(phrase))
        }
      })
    }


    @Test
    def testStermWord(): Unit = {
      val tagger = new UmlsTagger2(Conf.solrServerUrl,rootDir)

      val phrases = List("green tea")
      phrases.foreach(phrase => {
        Console.println(s"$phrase,${tagger.normalizeAll(phrase)}")
       // Console.println(s"$phrase,${tagger.normalizeCasePunct(phrase)}")

      })

    }

  @Test
  def testAnnotateSentence() = {
    val tagger = new UmlsTagger2(Conf.solrServerUrl, rootDir)
    val sent = "I lost a tone of weight i was alway 130 or above , i eat fine but i cant have really big meals ."
    val sugg=tagger.annotateSentence(sent,5)
    sugg.filter(_._2.size>0).foreach(s=>{
      println(s"${s._1}\t${s._2.mkString(",")}")
    })
  }


    @Test
    def testAnnotateFile(): Unit = {
      val tagger = new UmlsTagger2(Conf.solrServerUrl, rootDir)
//      tagger.annotateFile(s"C:/fsu/ra/data/201603/nsrr-canonical-data-dictionary.txt",
//        s"C:/fsu/ra/data/201603/ret-nsrr-canonical-data-dictionary.txt",
        tagger.annotateFile(s"C:/fsu/ra/data/201603/nsrr-canonical-data-dictionary.txt",
          s"C:/fsu/ra/data/201603/ret-nsrr-canonical-data-dictionary.txt",
        2,
        5,
        '\t','\n')
    }

    // find terms from dictionary for a string
    @Test
    def testAnnotateTag(): Unit = {
      val tagger = new UmlsTagger2(Conf.solrServerUrl, rootDir)
      //tagger.annotateTag(s"${rootDir}/data/taglist-zhiwei.txt",s"${rootDir}/data/taglist-zhiwei.csv")
      tagger.annotateTagAppend(s"C:/fsu/ra/data/201603/nsrr-canonical-data-dictionary.txt",
        s"C:/fsu/ra/data/201603/ret-nsrr-canonical-data-dictionary.txt",1)

      tagger.jdbcClose()
    }

    @Test
    def testPosFilter():Unit = {

    }

    @Test
    def testSql():Unit = {
      val tagger = new UmlsTagger2(Conf.solrServerUrl, rootDir)
      val rs = tagger.execQuery("select count(*) as cnt from mrsty")

      while (rs.next) {
        println(rs.getString("cnt"))
      }

      tagger.jdbcClose()
    }


    //  @AfterClass
//  def cleanup():Unit = {
//
//  }
}