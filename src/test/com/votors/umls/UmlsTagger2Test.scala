package com.votors.umls

import java.io.{FileWriter, PrintWriter, File}
import com.votors.common.Utils.Trace
import com.votors.common.Utils.Trace._
import org.junit.{AfterClass, Assert, Test}

import scala.io.Source

class UmlsTagger2Test   {

    // The root config dir of the opennlp models files and and
    val rootDir = "C:\\fsu\\ra\\UmlsTagger"
    Trace.currLevel = ERROR

    if (! new File(rootDir).exists()) {
      println("Error! You have to config a valid dataDir in class UmlsTagger2Test first")
      sys.exit(1)
    }

    @Test
    def testBuildIndex(): Unit = {
      val tagger = new UmlsTagger2("",rootDir)
      tagger.buildIndexJson(
        new File("C:\\fsu\\all_0912.csv"),
        new File("C:\\fsu\\all_0912.json"))
    }

    @Test
    def testGetFull(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr",rootDir)
      val phrases = List("Sepsis", "sEPSIS", "Progressive systemic sclerosis")
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
    def testGetPartial(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr",rootDir)
      val phrases = List(
        "Heart Attack and diabetes",
        "carcinoma (small-cell) of lung",
        "side effects of Australia Antigen")
      phrases.foreach(phrase => {
        Console.println()
        Console.println("Query: %s".format(phrase))
        val suggestions = tagger.tag(phrase)
        suggestions match {
          case Some(psuggs) => {
            psuggs.foreach(psugg => {
              Console.println(psugg)
              Console.println(psugg.toString())
            })
            Assert.assertNotNull(psuggs)
          }
          case None =>
            Assert.fail("No results for [%s]".format(phrase))
        }
      })
    }

    @Test
    def testAnnotateConcepts(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr",rootDir)
      val phrases = List("Lung Cancer",
        "Heart Attack",
        "Diabetes",
        "Heart Attack and diabetes",
        "carcinoma (small-cell) of lung",
        "asthma side effects"
      )
      phrases.foreach(phrase => {
        Console.println()
        Console.println("Query: %s".format(phrase))
        val suggestions = tagger.annotateConcepts(phrase)
        suggestions.foreach(suggestion => {
          Console.println(suggestion.toString())
        })
      })
    }

    @Test
    def testStermWord(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr",rootDir)

      val phrases = List("the pets", "the men", "two dishes", "three Babies", "holidays"  ,"the datum",
        "politics", "news ")
      phrases.foreach(phrase => {
        Console.println(s"$phrase,${tagger.stemWords(phrase)}")
       // Console.println(s"$phrase,${tagger.normalizeCasePunct(phrase)}")

      })

    }

    @Test
    def testAnnotateFile(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
      tagger.annotateFile(s"${rootDir}/data/raw_data_CHV_study2_format.csv",
        s"${rootDir}/data/raw_data_CHV_study2_format_ret.csv",
        4,
        5,
        ',','\n')
    }

    // find terms from dictionary for a string
    @Test
    def testAnnotateTag(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
      //tagger.annotateTag(s"${rootDir}/data/taglist-zhiwei.txt",s"${rootDir}/data/taglist-zhiwei.csv")
      tagger.annotateTag(s"${rootDir}/data/tags_0920_final_ST.csv",
        s"${rootDir}/data/tags_0920_final_ST_ret.csv")

      tagger.jdbcClose()
    }

    @Test
    def testPosFilter():Unit = {

    }

    @Test
    def testSql():Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
      val rs = tagger.execQuery("select count(*) as cnt from mrsty")

      while (rs.next) {
        println(rs.getString("cnt"))
      }

      tagger.jdbcClose()
    }


  @Test
  def testtransferExcelCvs(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
    tagger.transferExcelCvs(s"${rootDir}/data/raw_data_CHV_study2.csv.txt", "")
  }

    //  @AfterClass
//  def cleanup():Unit = {
//
//  }
}