package com.votors.umls.graph;


import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.swing.mxGraphComponent;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.ListenableDirectedGraph;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

/**
 * Created by Jason on 2015/9/26 0026.
 */
public class SctGraph {
    private ListenableDirectedGraph<UmlsVertex, IsaEdge> g =
            new ListenableDirectedGraph<UmlsVertex, IsaEdge>(IsaEdge.class);
    public ListenableDirectedGraph getGraph() { return g;}
    private HashMap<String,String> auiStrMap = new HashMap<String, String>();
    int cntSingle = 0;
    int cntRootNew = 0;
    int cntRoot = 0;
    int cntChild = 0;
    public String layoutType = "hierarchy";  //default is hierarchy; others are organic; circle

    /*for jGraphX begin*/
    private static final long serialVersionUID = 2202072534703043194L;
    private static final Dimension DEFAULT_SIZE = new Dimension(530, 320);
    private static final Color DEFAULT_BG_COLOR = Color.decode("#FAFBFF");
    transient JFrame frame = null;
    transient JApplet applet = null;
    /*for jGraphX end*/

    public SctGraph(){};
    public SctGraph (String inputPairs) {
        //String inputPairs = "A18785586 A3558430,A20828628 A3558430,A20828068 A3558430,A3806238 A3558430,A3347309 A3806238,A3896303 A3806238,A3033241 A3347309,A3896813 A3347309";
        this.loadGraph(inputPairs,null,null);
    }

    /**
     * Load the graph vertice and edges from a string.
     * @param inputPairs
     */
    public void loadGraph(String inputPairs, String pairStr1, String pairStr2) {
        // Load aui+str map first.
        if (pairStr1 != null) {loadAuiStr(pairStr1);}
        if (pairStr2 != null) {loadAuiStr(pairStr2);}

        String [] pairs = inputPairs.split("`");
        for (String pair_s :pairs) {
            String [] pair_a = pair_s.split("\t");
            UmlsVertex v1 = new UmlsVertex(pair_a[0]);
            v1.setAuiStr(auiStrMap.get(pair_a[0]));
            v1.setGraph(g);
            g.addVertex(v1);
            if (!pair_a[1].equals("null")) {
                UmlsVertex v2 = new UmlsVertex(pair_a[1]);
                v2.setAuiStr(auiStrMap.get(pair_a[1]));
                v2.setGraph(g);
                g.addVertex(v2);
                g.addEdge(v1, v2);
            }
        }
    }

    /* Load the map of AUI and STR of aui*/
    private void loadAuiStr(String str) {
        if (str.equals("\\N")) return;
        String[] pairs = str.split("`");
        for (String pair_s : pairs) {
            String[] pair_a = pair_s.split("\t");
            auiStrMap.put(pair_a[0], pair_a[1]);
        }
    }

    /**
     * Clean the current graph, so that we can load a new graph, avoiding creating a new instance.
     */
    public void cleanGraph() {
        g.removeAllVertices(new HashSet(g.vertexSet()));
        cntSingle = 0;
        cntRootNew = 0;
        cntRoot = 0;
        cntChild = 0;

    }
    public void cleanSingleVertex() {
        HashSet<UmlsVertex> vSet = new HashSet(g.vertexSet());
        for (UmlsVertex v: vSet) {
            if (v.getInDegree() ==0 && v.getOutDegree() == 0) {
                g.removeVertex(v);
            }
        }
    }
    /** Split the graph into different group. The vertice will be put into a new group if:
     * 1. they have no parent; or
     * 2. they have more than one group
     */
    public void group() {
        int gid = 1;
        // first: find all root to allocate the groupId
        for (UmlsVertex v: g.vertexSet()) {
            if (g.outDegreeOf(v) == 0) {
                v.status = UmlsVertex.ROOT;
                v.groupId = gid++;
                if (g.inDegreeOf(v) == 0){
                    cntSingle++;
                } else {
                    cntRoot++;
                }
            } else if (g.outDegreeOf(v) >= 2) {
                v.status = UmlsVertex.ROOT_NEW;
                v.groupId = gid++;
                cntRootNew++;
            } else {
                v.status = UmlsVertex.CHILD;
                cntChild++;
            }
        }
        System.out.println(g);
        System.out.println(g.vertexSet().toString());
        // second: allocate groupId to other vertex
        //UmlsVertex r = null;
        for (UmlsVertex v: g.vertexSet()) {
            if (g.outDegreeOf(v) == 1) {
                v.root = getRoot(v);
                v.groupId = v.root.groupId;
                //if (r!=null)System.out.println("compare:" + r.getAui() + " " + v.root.getAui() + " " + (r == v.root));
                //r = v.root;
            }
            System.out.println(v.toString2());
        }
    }

    /**
     *Get the root of a vertex. A vertex will be a root if :
     * 1. it has no parent, or
     * 2. it has more than one parent
     *
     * @param child
     * @return
     */
    UmlsVertex getRoot(UmlsVertex child) {
        for (IsaEdge e: g.outgoingEdgesOf(child)) {
            UmlsVertex t = (UmlsVertex)e.getTarget();
            if (t.status == UmlsVertex.ROOT || t.status == UmlsVertex.ROOT_NEW) {
                //System.out.println("root " + t.toString2());
                // !!! t is not the same reference in vertex set. It is weird!!!
                for (UmlsVertex v: g.vertexSet()) {
                    if (v.getAui().equals(t.getAui()))
                        return v;
                }
            } else {
                return getRoot(t);
            }
        }

        System.out.println("Warning: child more than one parent: " + child.getAui());
        return child;  // Should never reach here

    }

    /**
     * Get the result in a string format.
     * @return
     */
    public Map<Integer,Set<UmlsVertex>> getResult() {
        Set<UmlsVertex> s = g.vertexSet();
        Map<Integer, Set<UmlsVertex>> ret = new HashMap<Integer, Set<UmlsVertex>>();

        for (UmlsVertex v: g.vertexSet()) {
            if (ret.get(v.groupId) == null) {
                ret.put(v.groupId, new HashSet<UmlsVertex>());
            }
            ret.get(v.groupId).add(v);
        }

        //System.out.println(ret.toString());
        return ret;
    }

    /**
     * Init graph display.
     * !! Note: It will delete the single vertex of the graph first.
     */
    public void initJGraphX()
    {
        JGraphXAdapter<SctGraph, IsaEdge> jgxAdapter;
        if (frame != null) {
            frame.setVisible(false);
            frame.removeAll();
        }
        if (applet != null) applet.removeAll();

        // delete all vertex that has no edge.
        this.cleanSingleVertex();

        // create a visualization using JGraph, via an adapter
        jgxAdapter = new JGraphXAdapter<SctGraph, IsaEdge>(this.getGraph());

        // positioning via jgraphx layouts
        mxGraphLayout layout = null;
        if (layoutType.equals("hierarchy")) {
            layout = new mxHierarchicalLayout(jgxAdapter);  // hierarchy
        } else if (layoutType.equals("organic")) {
            layout = new mxFastOrganicLayout(jgxAdapter); //has center
        } else {
            layout = new mxCircleLayout(jgxAdapter);  // as a circle
        }
        layout.execute(jgxAdapter.getDefaultParent());
        applet = new JApplet();
        applet.getContentPane().add(new mxGraphComponent(jgxAdapter));
        applet.resize(DEFAULT_SIZE);

        frame = new JFrame();
        frame.getContentPane().add(applet);
        frame.setTitle("Graph: single="+cntSingle+",root="+cntRoot+",rootNew="+cntRootNew+",chile="+cntChild);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public void cleanFrame() {
        if (frame != null) {
            frame.setVisible(false);
            frame.removeAll();
        }
        if (applet != null) applet.removeAll();
    }

    public static void main(String [] args)
    {
        System.out.println("your input is: " + args.toString());
        if (args.length < 2) {
           System.out.println("Input invalid: {inputFile} {outputFile} [result-type] [layout-type]");
           System.exit(1);
        }

        String csvFile = args[0];
        String outputFile = args[1];
        String type = "all";
        String layout = "hierarchy";
        if (args.length>2)  type = args[2];
        if (args.length>3)  layout = args[3];

       try {
            //String input = "A3894737\tnull`A2932549\tnull`A3335714\tnull`A3043947\tnull`A3056048\tnull`A3009263\tnull`A3016398\tnull`A3018098\tnull`A2929870\tnull`A2929888\tnull`A3018901\tnull`A2881577\tnull`A2873252\tnull`A2935052\tnull`A3038774\tnull`A2990536\tnull`A3044324\tnull`A3588508\tnull`A3050220\tnull`A2873253\tnull`A2940329\tnull`A3054121\tnull`A3054171\tnull`A2885457\tnull`A2885469\tnull`A3650843\tnull`A2942956\tnull`A3061228\tnull`A3009264\tA2873252`A3122338\tA2873252`A3049658\tA2873252`A3124096\tnull`A3150157\tnull`A3182538\tnull`A3029410\tnull`A2940529\tnull`A3364346\tnull`A3009313\tnull`A3010999\tnull`A3033440\tnull`A3033442\tnull`A2878204\tnull`A2992312\tnull`A2873467\tnull`A2992457\tnull`A2992458\tnull`A2992462\tnull`A2923492\tnull`A3008523\tA2878204`A3120523\tA2878204`A3055085\tA2878204`A3202601\tA2878204`A2994384\tA2878204`A2873462\tnull`A3364337\tA3364346`A3364341\tA3364346`A3364349\tA3364346`A2992459\tnull`A2992465\tnull`A3061420\tnull`A3895649\tA3894737`A3899541\tA3894737`A3904024\tA3894737`A3905419\tA3894737";
            SctGraph sg = new SctGraph();
           sg.layoutType =layout;

            //get text content from csv file
           FileReader in = new FileReader(csvFile);
           CSVParser parser = CSVFormat.DEFAULT
                .withRecordSeparator('\n')
                .withDelimiter(',')
                .withSkipHeaderRecord(true)
                .withEscape('\\')
                .parse(in);

           Iterator<CSVRecord> records = parser.iterator();

           if (!type.equals("graph")) {
               PrintWriter writer = new PrintWriter(new FileWriter(outputFile));
               writer.append("\"stt\",\"sty\",\"cntTotal\",\"cntRoot\",\"cntSingle\",\"cntChile\",\"cntRootNew\",\"newRootFlag\",\"groupId\",\"aui\",\"auiStr\"\n");

               // for each row of csv file
               while (records.hasNext()) {
                   CSVRecord r = records.next();
                   String stt = r.get(0);
                   String sty = r.get(1);
                   int cntTotal = Integer.parseInt(r.get(2));
                   int cntParent = Integer.parseInt(r.get(3));
                   String pairs = r.get(4);
                   String pair_str1 = r.get(5);
                   String pair_str2 = r.get(6);
                   sg.cleanGraph();
                   sg.loadGraph(pairs, pair_str1, pair_str2);
                   sg.group();
                   Map<Integer, Set<UmlsVertex>> ret = sg.getResult();

                   StringBuilder sb = new StringBuilder();
                   for (Map.Entry<Integer, Set<UmlsVertex>> m : ret.entrySet()) {
                       for (UmlsVertex v : m.getValue()) {
                           boolean newRootFlag = false;
                           if (v.getOutDegree() >= 2) {
                               newRootFlag = true;
                           }
                           sb.append("\"" + stt + "\",\"" + sty + "\",\"" + cntTotal + "\",\""
                                   + sg.cntRoot + "\",\"" + sg.cntSingle + "\",\"" + sg.cntChild
                                   + "\",\"" + sg.cntRootNew + "\",\"" + newRootFlag + "\",\"" + m.getKey() + "\",\"" + v.getAui() + "\",\"" + v.getAuiStr() + "\"\n");
                       }
                   }
                   writer.append(sb);


               }
               writer.flush();
               writer.close();
           }
           // Enter the show graph shell.
           if (!type.equals("text")) {
               BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
               System.out.println("Input semantic tag and semantic type:");
               while (true) {
                   try {
                       System.out.println(">");
                       String line = br.readLine().replaceAll("\\W", "").toLowerCase();
                       //System.out.print(">:" + line);
                       //br.skip(1);
                       if (line.length() <= 0) {
                           continue;
                       }
                       if (line.trim().equals("quit")) {
                           System.exit(0);
                       }

                       in = new FileReader(csvFile);
                       records = CSVFormat.DEFAULT
                               .withRecordSeparator('\n')
                               .withDelimiter(',')
                               .withSkipHeaderRecord(true)
                               .withEscape('\\')
                               .parse(in)
                               .iterator();
                       boolean hit = false;
                       while (records.hasNext()) {
                           CSVRecord r = records.next();
                           String stt = r.get(0);
                           String sty = r.get(1);
                           String sttsty = (stt + sty).replaceAll("\\W", "").toLowerCase();
                           //System.out.print("?:" + line);
                           if (!sttsty.equals(line)) continue;
                           int cntTotal = Integer.parseInt(r.get(2));
                           int cntParent = Integer.parseInt(r.get(3));
                           String pairs = r.get(4);
                           String pair_str1 = r.get(5);
                           String pair_str2 = r.get(6);
                           sg.cleanFrame();
                           sg.cleanGraph();
                           sg.loadGraph(pairs, pair_str1, pair_str2);
                           sg.group();
                           sg.initJGraphX();
                           hit = true;
                       }
                       if (hit != true) {
                           System.out.println("the input semantic tag and semantic type cannot found in the input csv file.");
                       }
                   } catch (java.lang.IllegalArgumentException ex) {
                       System.out.println("Exception: " + ex.toString() + ". There may be a cycle. You can try layout: organic.");
                   }
               }
           }

       } catch (Exception e) {
            System.out.println("Exception: " + e.toString());
            e.printStackTrace();
       }
//        try{Thread.sleep(5000);}catch(InterruptedException e){}
//        sg.cleanGraph();
//        try{Thread.sleep(5000);}catch(InterruptedException e){}
//        sg.loadGraph("A18785586 A3558430,A20828628 A3558430,A20828068 null");
    }
}
