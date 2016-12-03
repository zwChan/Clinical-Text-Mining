package com.votors

import java.time.Duration

import com.votors.common.TimeX

/**
 * Created by Jason on 2016/6/15 0015.
 */
object Test {
  def main(args:Array[String]) = {

    var splitType = "#"
    val criteria = "Fertile patients must use effective contraception during and for 3 months after study No other malignancy within the past 3 years No serious concurrent medical illness or active infection that would preclude study chemotherapy No allergy or sensitivity to imidazole antifungal medications (e.g., fluconazole, ketoconazole, miconazole, itraconazole, and clotrimazole)"
    criteria.split("#|\\n").flatMap(s=> {
      // if there is more than tow ':' in a sentence, we should split it using ':', cuz some clinical trails use ':' as separate symbol.
      if (s.count(_ == ':') >= 3) {
        splitType = ":"
        s.split(":")
      } else if (s.count(_ == '-') >= 3) {
        splitType = "-"
        s.split(" - ")
      } else if (s.split("\\s").count(_=="No") >= 3) {
        // some sentences without any punctuation to separate
        splitType = "No"
        s.split("(?=\\sNo\\s)")
      }  else if (s.split("\\s").count(s=> s.equals("OR") || s.equals("Or")) >= 3) {
        // some sentences without any punctuation to separate
        splitType = "or"
        s.split("Or|OR")
      } else {
        s :: Nil
      }
    }).filter(_.trim.size > 2).foreach(sent_org => {
      val sent = sent_org.trim.replaceAll("^[\\p{Punct}\\s]*", "") // the punctuation at the beginning of a sentence
      println(sent)
    })

  }


}
