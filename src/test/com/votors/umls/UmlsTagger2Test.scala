package com.votors.umls

import java.io.{FileWriter, PrintWriter, File}
import com.votors.common.Utils.Trace
import com.votors.common.Utils.Trace._
import org.junit.{AfterClass, Assert, Test}

import scala.io.Source

class UmlsTagger2Test   {

    // The root config dir of the opennlp models files and and
    val rootDir = "C:\\fsu\\ra\\UmlsTagger"
    Trace.currLevel = INFO

    if (! new File(rootDir).exists()) {
      println("Error! You have to config a valid dataDir in class UmlsTagger2Test first")
      sys.exit(1)
    }

    @Test
    def testBuildIndex(): Unit = {
      val tagger = new UmlsTagger2("",rootDir)
      tagger.buildIndexJson(
        new File("C:\\fsu\\all.csv"),
        new File("C:\\fsu\\all.json"))
    }

    @Test
    def testGetFull(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr",rootDir)
      val phrases = List("Sepsis", "Biliary tract disease", "Progressive systemic sclerosis")
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

      val phrases = List("Sepsis (to) < a ,Man and another. man", "Biliary of disease", "Australia Antigen")
      phrases.foreach(phrase => {
        //Console.println(s"$phrase,${tagger.stemWords(phrase)}")
        Console.println(s"$phrase,${tagger.normalizeCasePunct(phrase)}")

      })

    }

    @Test
    def testAnnotateFile(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
      tagger.annotateFile(s"${rootDir}/data/text_from_min_sook.txt",3)
    }

    // find terms from dictionary for a string
    @Test
    def testAnnotateTag(): Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)
      tagger.annotateTag(s"${rootDir}/data/text_tag_from_min_sook.txt",s"${rootDir}/data/text_tag_from_min_sook_ret_part_umls.csv")
      //tagger.annotateTag(s"${rootDir}/data/text_tag_from_min_sook.txt",s"${rootDir}/data/text_tag_from_min_sook_ret_all_umls.csv")

      tagger.jdbcClose()
    }

    @Test
    def testPosFilter():Unit = {
      val tagger = new UmlsTagger2("http://localhost:8983/solr", rootDir)

      val input = "I am a big boy"

      println("input: " + input)
      val ret = tagger.posFilter(input.split(" "))
      println("pos output: " + ret.mkString(","))
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




    //  @AfterClass
//  def cleanup():Unit = {
//
//  }
}