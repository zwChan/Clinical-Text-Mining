
package com.votors.umls.graph;

import java.io.*;
import java.util.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;

public class TestJava {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String time = in.next();

        boolean pm = false;
        if (time.contains("PM"))pm=true;
        String[] t = time.substring(0,time.length()-2).split(":");
        int h = Integer.parseInt(t[0]);
        int m = Integer.parseInt(t[1]);
        int s = Integer.parseInt(t[2]);
        if (pm && h < 12) h += 12;
        if (!pm && h == 12) h=0;
        System.out.println(String.format("%02d:%02d:%02d", h,m,s));




    }
}
