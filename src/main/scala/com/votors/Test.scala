package com.votors

/**
 * Created by Jason on 2015/11/19 0019.
 */

import java.util.regex.Pattern

import scala.collection.mutable.HashMap

object Test {
  def main(argv: Array[String]) = {

    val captTerm = Pattern.compile("^([A-Z][\\S]*\\b\\s*)+$")
    val ret = captTerm.matcher("Aaa AA AAzaAA Zz").matches()
    println(ret)
  }

}



