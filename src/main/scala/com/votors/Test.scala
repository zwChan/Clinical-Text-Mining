package com.votors

/**
 * Created by Jason on 2015/11/19 0019.
 */

import java.util.regex.Pattern

import scala.collection.mutable.HashMap

object Test {
  var cnt = 0
  val N = 32
  val a = Array.fill(N)({cnt+=1;cnt})
  var F = 0
  def main(argv: Array[String]) = {

//    val captTerm = Pattern.compile("^([A-Z][\\S]*\\b\\s*)+$")
//    val ret = captTerm.matcher("Aaa AA AAzaAA Zz").matches()
//    println(ret)



/*

    def exch(pos:Int,v:Int, s:Int):Unit= {
      if (F==1 && pos<=s){
        if(pos==s)a(pos)=v
        println("")
        return
      }
      print(s"${pos}->")
      F = 1
      if (pos%2==0) {
        exch((N)/2+(pos)/2, a((N)/2+(pos)/2),s)
        a((N)/2+(pos)/2)=v
      }else{
        exch((pos)/2,a((pos)/2),s)
        a(pos/2)=v
      }
    }
*/

    def exch2(pos:Int,v:Int, s:Int):Unit= {
      if (F==1 && pos>=s && s>2){
        F = 0
        println(s"")
        exch2(s-2,a(s-1-2),s-2)
        return
      }
      print(s"${pos}->")

      if (pos==1){
        println("")
        return
      }

      F = 1
      if (pos%2==0) {
        exch2((N)/2+(pos)/2, a((N)/2+(pos)/2-1),s)
        a((N)/2+(pos)/2-1)=v
      }else{
        exch2((pos+1)/2,a((pos+1)/2-1),s)
        a((pos+1)/2-1)=v
      }
    }


    exch2(N-1,a(N-1-1),N-1)
    println(s"${a.mkString("\t")}")




  }






}



