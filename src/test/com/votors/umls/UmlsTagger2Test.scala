package com.votors.umls

import java.io.File

import org.junit.Assert
import org.junit.Test

class UmlsTagger2Test {

  @Test
  def testBuildIndex(): Unit = {
    val tagger = new UmlsTagger2("")
    tagger.buildIndexJson(
      new File("C:\\fsu\\cuistr2.csv"),
      new File("C:\\fsu\\cuistr2.json"))
  }

  @Test
  def testGetFull(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr")
    val phrases = List("Sepsis", "Biliary tract disease", "Australia Antigen")
    phrases.foreach(phrase => {
      Console.println()
      Console.println("Query: %s".format(phrase))
      val suggestions = tagger.select(phrase)
      suggestions match {
        case Some(suggestion) => {
          Console.println(tagger.formatSuggestion(suggestion))
          Assert.assertNotNull(suggestion.cui)
        }
        case None =>
          Assert.fail("No results for [%s]".format(phrase))
      }
    })
  }

  @Test
  def testGetPartial(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr")
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
    val tagger = new UmlsTagger2("http://localhost:8983/solr")
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
}