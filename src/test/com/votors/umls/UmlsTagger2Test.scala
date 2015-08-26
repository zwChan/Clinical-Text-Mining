package com.votors.umls

import java.io.{FileWriter, PrintWriter, File}
import com.votors.common.Utils.Trace
import com.votors.common.Utils.Trace._
import org.junit.Assert
import org.junit.Test

import scala.io.Source

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
      new File("C:\\fsu\\all.csv"),
      new File("C:\\fsu\\all.json"))
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
    tagger.annotateFile(s"${dataDir}text_from_min_sook.txt",3)
  }

  // find terms from dictionary for a string
  @Test
  def annotateTag(): Unit = {
    val tagger = new UmlsTagger2("http://localhost:8983/solr")
    val source = Source.fromFile(s"${dataDir}text_tag_from_min_sook.txt", "UTF-8")
    var writer = new PrintWriter(new FileWriter(s"${dataDir}text_tag_from_min_sook_ret_part_umls.csv"))
    val lineIterator = source.getLines
    lineIterator.foreach(line =>{
      if (line.trim().length>0) {
        val tokens = line.split(" ",2)
        if (tokens.length>1) {
          tagger.select(tokens(1).trim) match {
            case suggestion: Array[Suggestion] => {
              val str = "\"" + tokens(0) + "\",\"" + tokens(1).trim() + "\",\"" + suggestion.map(tagger.formatSuggestion(_)).mkString(";") + "\""
              writer.print(str)
              //println(str)
            }
            case _ => writer.print("\"" + tokens(0) + "\",\"" + tokens(1).trim() + "\",\"not found\"")
          }
          writer.print("\n")
        }
        }
      })
    writer.flush()
    writer.close()
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