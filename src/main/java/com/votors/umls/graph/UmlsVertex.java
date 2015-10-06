package com.votors.umls.graph;

import org.jgrapht.*;
import org.jgrapht.graph.ListenableDirectedGraph;

import java.io.Serializable;

/**
 * Created by Jason on 2015/9/26 0026.
 */
public class UmlsVertex implements Serializable {
    private String aui = null;
    private String auiStr = null;
    /*status of the vertex: "root" or "child" */
    public static final String ROOT = "root";
    public static final String ROOT_NEW = "new-root";
    public static final String CHILD = "child";
    //public static final String RELAY = "relay";
    public static final String COPY = "copy";
    public String status = ROOT;
    public UmlsVertex root = this;   // who is the root of this vertex
    public int groupId = 0;     // which group this vertex belong to; 0 is no group yet.
    public int layer = 0;       // which layer do the vertex locate in? for method SctGraph.fix()
    public boolean fix = false;
    transient private ListenableDirectedGraph g = null;
    private static int copyCnt = 0;
    private static UmlsVertex NULL = null;

    public UmlsVertex(String aui) {
        this.aui = aui;
    }
    public UmlsVertex(UmlsVertex cp) {
        copyCnt++;
        aui = cp.aui + "-copy-"+copyCnt;
        root = cp.root;
        groupId = cp.groupId;
        status = UmlsVertex.COPY;
        layer = cp.layer;
        auiStr = cp.auiStr;
        g = cp.g;
    }
    public String getAui() { return aui;}
    public void setGraph(ListenableDirectedGraph graph) {g = graph;}
    public int getOutDegree() { if (g == null) return 0; else return g.outDegreeOf(this);}
    public int getInDegree() { if (g == null) return 0; else return g.inDegreeOf(this);}
    public void setAuiStr(String str) { auiStr = str;}
    public String getAuiStr() { return auiStr;}

    @Override public String toString () {
        if (auiStr == null) {
            return groupId + ":" + aui;
        } else {
            return groupId + ":" + aui + "\n" + auiStr;
        }
    }
    @Override public int hashCode() {return aui.hashCode();}
    @Override public boolean equals(Object obj) {
        if ((obj instanceof UmlsVertex) && aui.equals(((UmlsVertex)obj).aui)) {
            return true;
        }
        return false;
    }

    public static UmlsVertex getNULL () {
        if (NULL == null) {
            NULL = new UmlsVertex("null");
            NULL.status = ROOT;
        }
        return NULL;
    }

    public String toString2() {
        return "Aui:" + aui + ",\tstatus: " + status + ",\tgroupId: " + groupId + ",\troot: "
                + root.getAui() + ",\tlayer: " + layer + ",\tout: " + getOutDegree() + ",\tin: " + getInDegree();
    }
}
