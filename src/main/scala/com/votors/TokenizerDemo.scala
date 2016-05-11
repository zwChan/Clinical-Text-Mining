package com.votors

/**
 * Created by Jason on 2016/5/2 0002.
 */
import java.io.FileReader
import java.io.IOException
import java.util
import java.util.List

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}

import edu.stanford.nlp.parser.lexparser.LexicalizedParser

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.{List, Range}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import scala.io.Source
import scala.io.Codec

import edu.stanford.nlp.ling.{TaggedWord, CoreLabel, HasWord}
import edu.stanford.nlp.process.CoreLabelTokenFactory
import edu.stanford.nlp.process.DocumentPreprocessor
import edu.stanford.nlp.process.PTBTokenizer

object TokenizerDemo {

  def main(args: Array[String]) {
    for (arg <- args) {
      // option #1: By sentence.
      val dp = new DocumentPreprocessor(arg).iterator()
      val tokens = dp.map(_.toArray().map(_.toString)).flatMap(_.toSeq).toArray
      println(tokens.mkString(" "))
      // option #2: By token
      val ptbt = new PTBTokenizer(new FileReader(arg),
        new CoreLabelTokenFactory(), "");
      while (ptbt.hasNext()) {
        val label = ptbt.next();
        System.out.println(label);
      }
    }
//
    // set up grammar and options as appropriate
//    val lp = LexicalizedParser.loadModel();
//    val sent3 = Array("I", "can", "do", "it", "." )
//    // Parser gets tag of second "can" wrong without help
//    val tag3 = Array( "PRP", "MD", "VB", "PRP", "." )
//    val sentence3 = new util.ArrayList[TaggedWord]()
//    for (i <- 0 to (sent3.length-1)) {
//      sentence3.add(new TaggedWord(sent3(i), tag3(i)));
//    }
//    val sents = Array("I go to school at 9:00 tomorrow.")
//    val parse = lp.parse(sentence3);
//    //val parse = lp.parseStrings(sents);
//    parse.pennPrint();

  }
}