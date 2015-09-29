package com.votors.umls.graph;

import org.jgrapht.graph.*;

import java.util.Objects;

/**
 * Created by Jason on 2015/9/26 0026.
 */
public class IsaEdge extends DefaultEdge{

    @Override public String toString () {return "";}
    @Override public boolean equals(Object obj) {
        if (obj instanceof IsaEdge && ((IsaEdge)obj).getSource().equals(this.getSource())
                && ((IsaEdge)obj).getTarget().equals(this.getTarget())) {
            return true;
        }
        return false;
    }
    @Override public Object getTarget() {return super.getTarget();}
}
