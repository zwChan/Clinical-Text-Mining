package com.votors.umls.graph;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.ListenableDirectedGraph;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

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
    int cntRelay = 0;
    UmlsVertex nullVertex = null;
    public String layoutType = "hierarchy";  //default is hierarchy; others are organic; circle
    HashSet<LinkedList<UmlsVertex>> fixEdgeSet = new HashSet<LinkedList<UmlsVertex>>();

    /*for jGraphX begin*/
    private static final long serialVersionUID = 2202072534703043194L;
    private static final Dimension DEFAULT_SIZE = new Dimension(530, 320);
    transient JFrame frame = null;
    transient JApplet applet = null;
    transient JGraphXAdapter<UmlsVertex, IsaEdge> jgxAdapter;

    /*for jGraphX end*/

    public SctGraph(){};
    public SctGraph (String inputPairs) {
        if (inputPairs == null) {
            // for test
            inputPairs ="664	939`664	244`244	165`649	165`548	244`369	262`369	775`775	374`374	649`119	120`548	120";
        }
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
    public void clean() {
        cleanFrame();
        cleanGraph();
    }

    /* use a new ListenableDirectedGraph will significantly speed up the processing.*/
    public void cleanGraph() {
        //g.removeAllVertices(new HashSet(g.vertexSet()));
        g =  new ListenableDirectedGraph<UmlsVertex, IsaEdge>(IsaEdge.class);
        cntSingle = 0;
        cntRootNew = 0;
        cntRoot = 0;
        cntChild = 0;

    }
    public void cleanSingleVertex() {
        HashSet<UmlsVertex> vSet = new HashSet(g.vertexSet());
        for (UmlsVertex v: vSet) {
            if (v.getInDegree() ==0 && v.getOutDegree() == 0 && v.status == UmlsVertex.ROOT) {
                if (v.status == UmlsVertex.ROOT_NEW) {
//                    g.addVertex(UmlsVertex.getNULL());
//                    g.addEdge(v, UmlsVertex.getNULL());
                } else {
                    g.removeVertex(v);
                }
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
        //System.out.println(g);
        //System.out.println(g.vertexSet().toString());
        // second: allocate groupId to other vertex
        //UmlsVertex r = null;
        for (UmlsVertex v: g.vertexSet()) {
            if (g.outDegreeOf(v) == 1) {
                v.root = getRoot(v);
                v.groupId = v.root.groupId;
                //if (r!=null)System.out.println("compare:" + r.getAui() + " " + v.root.getAui() + " " + (r == v.root));
                //r = v.root;
            }
            //System.out.println(v.toString2());
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
            UmlsVertex t = realVertex(e.getTarget());
            if (t.status == UmlsVertex.ROOT || t.status == UmlsVertex.ROOT_NEW) {
                //System.out.println("root " + t.toString2());
                // !!! t is not the same reference in vertex set. It is weird!!!
                return t;
            } else {
                return getRoot(t);
            }
        }

        System.out.println("Warning: child more than one parent: " + child.getAui());
        return child;  // Should never reach here
    }

    /*Get the vertex in vertexSet of the graph. some time v is a copy of vertex and has no complete info.*/
    public UmlsVertex realVertex(UmlsVertex v) {
        for (UmlsVertex rv: g.vertexSet()) {
            if (v.getAui().equals(rv.getAui()))
                return rv;
        }

        System.out.println("!!Error, a vertex not found in vertexSet.!!");
        return v;
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

    // for test only
    private void removeSpecialVertex() {
        Set<UmlsVertex> vSet = new HashSet<UmlsVertex>(g.vertexSet());

        int b = 11;
        int e = 11;
        int cnt = 0;
        for (UmlsVertex v : vSet) {
            if (v.status == UmlsVertex.ROOT_NEW) {
                cnt++;
                if ((cnt > e || cnt < b) && cnt != 4 && cnt != 9) continue;
                //System.out.println(cnt + " delete: " + v.toString2());
                g.removeVertex(v);
            }
        }

        return;
    }

    /*If there are new root that can be locate at different hierarchy, add temp vertex to fix this problem*/
    /* It is fix by updating new jgraphx version!
    * not used now. but this code may be useful*/
    public void fix() {
        Set<UmlsVertex> vSet = g.vertexSet();
        // annotate all root and child vertex a layer
        int layer = 1;
        for (UmlsVertex v: vSet) {
            if (v.status == UmlsVertex.ROOT) {
                v.layer = layer;
                setLayer(v, layer+1);
            }
        }
        printVertic();

        // if the parents of a new root vertex are in different layers, add temp vertex to solve it.
        boolean modify = false;
        while (true) {
            //pick the topest new root vertex
            UmlsVertex currV = null;
            if (modify) vSet = g.vertexSet();
            for (UmlsVertex v: vSet) {
                if (v.status == UmlsVertex.ROOT_NEW && v.fix == false) {
                    if(currV == null || currV.layer > v.layer) {
                        currV = v;
                    }
                }
            }
            if (currV == null ) break;

            // get all the parent
            Set<IsaEdge> eSet = new HashSet<IsaEdge>(g.outgoingEdgesOf(currV));
            modify = false;
            for (IsaEdge e: eSet) {
                UmlsVertex p = realVertex(e.getTarget());
                int tempLayer = currV.layer - p.layer;
                int relayLayer = p.layer + 1;
                System.out.println("target "+p.toString2());
                if (tempLayer>1) {
                    // fix the layer differ to 1 by adding new vertic.
                    while (tempLayer>1) {
                        g.removeEdge(currV,p);  // remove old edge
                        cntRelay++;
                        UmlsVertex x = new UmlsVertex("x"+cntRelay);
                        x.layer = relayLayer++;
                        x.root = p.root;
                        x.status = UmlsVertex.COPY;
                        x.groupId = p.groupId;

                        g.addVertex(x); //add a relay vertex
                        g.addEdge(x,p); //add a edge between relay and parent
                        p = x; // set teh relay vertex as the parent for nex relay vertex
                        tempLayer--;
                        modify = true;
                        System.out.println("Add a relay vertex for " + currV);
                    }
                    g.addEdge(currV,p); // connect the current vertex to the relay vertex.
                }
            }
            currV.fix = true;
        }
    }


    /**
     * remove the connection of the new root to its parents, and add a copy of the new root to the parents;
     * */
    public void splitNewRoot (boolean isCopy) {
        Set<UmlsVertex> vSet = new HashSet<UmlsVertex>(g.vertexSet());
        for (UmlsVertex v_tmp: vSet) {
            UmlsVertex v = realVertex(v_tmp);
            if (v.status == UmlsVertex.ROOT_NEW) {
                Set<IsaEdge> eSet = new HashSet<IsaEdge>(g.outgoingEdgesOf(v));
                for (IsaEdge e : eSet) {
                    if (isCopy) {
                        UmlsVertex cp = new UmlsVertex(v);
                        g.addVertex(cp);
                        g.addEdge(cp, realVertex(e.getTarget()));
                    }
                    //System.out.println("remove edg from source: " + v.toString2());
                    g.removeEdge(e);
                }
            }
        }
    }

    /*set the layer for the vertex of the graph. Not used now.*/
    private void setLayer (UmlsVertex root, int layer) {
        Set<IsaEdge> eSet = g.incomingEdgesOf(root);
        for (IsaEdge e: eSet ) {
            UmlsVertex rv = realVertex(e.getSource());
            if (rv.layer < layer) {
                rv.layer = layer;   // set it to be more deep layer.
                setLayer(rv, layer+1);
            }
        }
    }
    /**
     * Init graph display.
     * !! Note: It will delete the single vertex of the graph first.
     */
    public void initJGraphX()
    {
        if (frame != null) {
            frame.setVisible(false);
            frame.removeAll();
        }
        if (applet != null) applet.removeAll();

        // delete all vertex that has no edge.
        this.cleanSingleVertex();

        // create a visualization using JGraph, via an adapter
        jgxAdapter = new JGraphXAdapter<UmlsVertex, IsaEdge>(this.getGraph());

        // positioning via jgraphx layouts
        mxGraphLayout layout = null;
        if (layoutType.equals("hierarchy")) {
            layout = new mxHierarchicalLayout(jgxAdapter,SwingConstants.NORTH);  // hierarchy
        } else if (layoutType.equals("organic")) {
            layout = new mxFastOrganicLayout(jgxAdapter); //has center
        } else {
            layout = new mxHierarchicalLayout(jgxAdapter);  // as a circle
        }
        layout.execute(jgxAdapter.getDefaultParent());

        applet = new JApplet();
        mxGraphComponent graphComponent = new mxGraphComponent(jgxAdapter);
        applet.getContentPane().add(graphComponent);
        applet.resize(DEFAULT_SIZE);

        frame = new JFrame();
        frame.getContentPane().add(applet);
        frame.setTitle("Graph: single="+cntSingle+",root="+cntRoot+",rootNew="+cntRootNew+",child="+cntChild);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        this.setGraphColor();
        frame.setVisible(true);
    }

    /*Save the current graph to the file system.*/
    private void saveGraph2File (String file, String format) {
        if (jgxAdapter == null) return;
        BufferedImage image = mxCellRenderer.createBufferedImage(jgxAdapter, null, 1, Color.WHITE, true, null);
        try {
            ImageIO.write(image, format.toUpperCase(), new File(file));
        } catch (Exception e) {
            System.out.println("write graph file fail." + e.toString());
        }
    }

    public void cleanFrame() {
        if (frame != null) {
            frame.setVisible(false);
            frame.removeAll();
        }
        if (applet != null) applet.removeAll();
    }

    public void setGraphColor() {
        if (jgxAdapter == null) return;

        HashMap<UmlsVertex, mxICell> cells = jgxAdapter.getVertexToCellMap();
        for (Map.Entry<UmlsVertex,mxICell> e: cells.entrySet()) {
            //System.out.println(e.getValue().getStyle()); //null
            if (e.getKey().status == UmlsVertex.ROOT) {
                jgxAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "green", new Object[]{e.getValue()});
            } else if (e.getKey().status == UmlsVertex.ROOT_NEW) {
                jgxAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "red", new Object[]{e.getValue()});
            } else if (e.getKey().status == UmlsVertex.COPY) {
                jgxAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "blue", new Object[]{e.getValue()});
            }
        }

        HashMap<IsaEdge, mxICell> edgeCells = jgxAdapter.getEdgeToCellMap();
        Set<IsaEdge> eSet = g.edgeSet();
        for (Map.Entry<IsaEdge,mxICell> e: edgeCells.entrySet()) {
            //System.out.println(realVertex(g.getEdgeSource(e.getKey())).toString2());
            if (realVertex(g.getEdgeSource(e.getKey())).status == UmlsVertex.ROOT_NEW) {
                //System.out.println(g.getEdgeSource(e.getKey()).toString2());
                jgxAdapter.setCellStyles(mxConstants.STYLE_STROKECOLOR, "red", new Object[]{e.getValue()});
            } else if (realVertex(g.getEdgeSource(e.getKey())).status == UmlsVertex.COPY) {
                //System.out.println(g.getEdgeSource(e.getKey()).toString2());
                jgxAdapter.setCellStyles(mxConstants.STYLE_STROKECOLOR, "blue", new Object[]{e.getValue()});
            }
        }

    }

    @Override public String toString() {
        return "SctGraph: layout: " +layoutType+ ",single="+cntSingle+",root="+cntRoot+",rootNew="+cntRootNew+",child="+cntChild;
    }
    public void printVertic() {
        Set<UmlsVertex> vSet = new HashSet<UmlsVertex>(g.vertexSet());
        System.out.println("\n***************start*****************");
        for (UmlsVertex v: vSet) {
            System.out.println(v.toString2());
        }
        System.out.println("\n##################end##############");

    }
    public static void main(String [] args)
    {
        for (String str: args)
            System.out.println("your input is: " + str);
        if (args.length < 2) {
           System.out.println("Input invalid: {inputFile} {outputFile} [result-type] [layout-type] [no-split|split|split-copy] [graph-format] [output-graph path]");
           System.exit(1);
        }

        String csvFile = args[0];
        String outputFile = args[1];
        String resultType = "all";
        String layout = "hierarchy";
        String splitType = "";
        String format = null;
        String graphDir = null;
        if (args.length>2)  resultType = args[2];
        if (args.length>3)  layout = args[3];
        if (args.length>4) splitType = args[4];
        if (args.length>5) format = args[5];
        if (args.length>6) graphDir = args[6];

       try {
           Thread.setDefaultUncaughtExceptionHandler(new MyThreadExceptionHandler());

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

           if (!resultType.equals("graph")) {
               PrintWriter writer = new PrintWriter(new FileWriter(outputFile));
               writer.append("\"stt\",\"sty\",\"cntTotal\",\"cntRoot\",\"cntSingle\",\"cntChild\",\"cntRootNew\",\"newRootFlag\",\"groupId\",\"aui\",\"auiStr\"\n");

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

           /*write the graph to the file system.*/
           if (format != null && graphDir != null) {
               in = new FileReader(csvFile);
               records = CSVFormat.DEFAULT
                       .withRecordSeparator('\n')
                       .withDelimiter(',')
                       .withSkipHeaderRecord(true)
                       .withEscape('\\')
                       .parse(in)
                       .iterator();
               while (records.hasNext()) {
                   CSVRecord r = records.next();
                   String stt = r.get(0);
                   String sty = r.get(1);
                   //System.out.print("?:" + line);
                   int cntTotal = Integer.parseInt(r.get(2));
                   int cntParent = Integer.parseInt(r.get(3));
                   String pairs = r.get(4);
                   String pair_str1 = r.get(5);
                   String pair_str2 = r.get(6);
                   sg.clean();
                   sg.loadGraph(pairs, pair_str1, pair_str2);
                   sg.group();
                   if (splitType.equals("split")) {
                       sg.splitNewRoot(false);
                   } else if (splitType.equals("split-copy")) {
                       sg.splitNewRoot(true);
                   }
                   sg.initJGraphX();
                   String file = (stt.replaceAll("\\W"," ") + "-" + sty.replaceAll("\\W"," ") + "."+format.toLowerCase());
                   sg.saveGraph2File(graphDir+file,format);
                   System.out.println("Save graph: " + sg.toString() + " to " + graphDir+file);
               }
           } else if (!resultType.equals("text")) {
               // Enter the show graph shell.
               BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
               System.out.println("Input semantic tag and semantic type:");
               while (true) {
                   try {
                       System.out.print(">");
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
                           sg.clean();
                           sg.loadGraph(pairs, pair_str1, pair_str2);
                           sg.group();
                           if (splitType.equals("split")) {
                               sg.splitNewRoot(false);
                           } else if (splitType.equals("split-copy")) {
                               sg.splitNewRoot(true);
                           }
                           sg.initJGraphX();
                           System.out.println(sg.toString());
                           hit = true;
                       }
                       if (hit != true) {
                           System.out.println("the input semantic tag and semantic type cannot found in the input csv file.");
                       }
                   } catch (java.lang.IllegalArgumentException ex) {
                       System.out.println("Exception: " + ex.toString() + ". There may be a cycle. You can try layout: organic.");
                       ex.printStackTrace();

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
        System.out.println("done!");
        System.exit(0);
    }

    public static class MyThreadExceptionHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            System.out.println("Thread exception:" + e.toString());
        }
    }
}
