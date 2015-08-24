package com.votors.umls

import java.io.File
import com.votors.common.Utils.Trace
import com.votors.common.Utils.Trace._

import scala.sys

import org.junit.Assert
import org.junit.Test

class UmlsTagger2Test {

  // The root config dir of the opennlp models files and and
  val dataDir = "C:\\fsu\\ra\\UmlsTagger\\data\\"
  Trace.currLevel = INFO


  if (! new File(dataDir).exists()) {
    println("Error! You have to config a valid dataDir in class UmlsTagger2Test first")
    sys.exit(1)
  }

  @Test
  def testBuildIndex(): Unit = {
    val tagger = new UmlsTagger2("",dataDir)
    tagger.buildIndexJson(
      new File("C:\\fsu\\cuistr2.csv"),
      new File("C:\\fsu\\cuistr2.json"))
  }

  @Test
  def testGetFull(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr",dataDir)
    val phrases = List("Sepsis", "Biliary tract disease", "Progressive systemic sclerosis")
    phrases.foreach(phrase => {
      Console.println()
      Console.println("Query: %s".format(phrase))
      val suggestions = tagger.select(phrase)
      suggestions match {
        case suggestion: Array[Suggestion] => {
          suggestion.foreach(s => Console.println(tagger.formatSuggestion(s)))
          //Assert.assertNotNull(suggestion.cui)
        }
        case _ =>
          Assert.fail("No results for [%s]".format(phrase))
      }
    })
  }

  @Test
  def testGetPartial(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr",dataDir)
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
            Console.println(tagger.formatSuggestion(psugg))
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
    val tagger = new UmlsTagger2("http://localhost:8983/solr",dataDir)
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
        Console.println(tagger.formatSuggestion(suggestion))
      })
    })
  }

  @Test
  def testStermWord(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr",dataDir)

    val phrases = List("Sepsis (to) < a ,Man and another. man", "Biliary of disease", "Australia Antigen")
    phrases.foreach(phrase => {
      //Console.println(s"$phrase,${tagger.stemWords(phrase)}")
      Console.println(s"$phrase,${tagger.normalizeCasePunct(phrase)}")

    })

  }

  @Test
  def testAnnotateFile(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr")
    tagger.annotateFile("C:\\fsu\\ra\\UmlsTagger\\data\\umls_output\\clinical_text.txt")
  }

  @Test
  def testPosFilter():Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr")

    val input = "I am a big boy"

    println("input: " + input)
    val ret = tagger.posFilter(input.split(" "))
    println("pos output: " + ret.mkString(","))
  }

}