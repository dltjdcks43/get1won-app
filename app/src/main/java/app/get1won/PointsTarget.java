package app.get1won;

import java.util.*;

/** POINTS-only native resolution. Geometry associates evidence, never invents a parent. */
final class PointsTarget {
    record Result(Semantic.Node target,String context) {
        String selectedSummary(Semantic.Node selected) {
            return "POINTS selected source="+(selected==null?"none":selected.id()<0?"synthetic":selected.source());
        }
        String targetSummary() {
            return "POINTS target "+(target==null?"source=none":"source="+target.source()+" bounds="+target.box()+" clickable="+target.clickable())+" context="+context;
        }
    }
    private static boolean excluded(Semantic.Node n) {
        String s=Semantic.normalize(n.text()+" "+n.description());
        return s.contains("알림") || s.contains("포인트받기") || s.contains("내근처혜택")
            || s.contains("이벤트") || s.contains("동의") || s.contains("출석");
    }
    private static boolean descendant(Semantic.Node child,Semantic.Node target,Map<Integer,Semantic.Node> nodes) {
        Set<Integer> seen=new HashSet<>();
        for(int i=0;child!=null && i<32 && seen.add(child.id());i++,child=nodes.get(child.parent()))
            if(child.id()==target.id() || child.box().equals(target.box()) && child.text().equals(target.text())
                && child.description().equals(target.description()) && child.resourceId().equals(target.resourceId()))return true;
        return false;
    }
    private static boolean unsafe(Semantic.Node target,Semantic.Node label,Map<Integer,Semantic.Node> nodes) {
        // Inspect only this native subtree and its direct, local parent. Unrelated overlapping
        // siblings and page-wide promotion descriptions must not veto a real points row.
        for(var n:nodes.values())if(n.enabled() && descendant(n,target,nodes) && excluded(n))return true;
        var parent=nodes.get(target.parent());
        return target.id()==label.id() && parent!=null && parent.box().height()<=label.box().height()*4 && excluded(parent);
    }
    static Result resolve(Semantic.Scene fresh,Semantic.Node evidence) {
        if(evidence==null)return new Result(null,"unknown");
        var nativeNodes=fresh.nodes().stream().filter(n->n.id()>=0 && n.source().equals("Accessibility")).toList();
        // Re-run the existing points semantics using only fresh native observations. In
        // particular an old OCR/synthetic id can never be mistaken for a current handle.
        var label=Semantic.pointsEvidence(new Semantic.Scene(nativeNodes,fresh.screen())).node();
        if(label==null || !Semantic.samePosition(label.box(),evidence.box()))return new Result(null,"unknown");
        Map<Integer,Semantic.Node> nodes=new HashMap<>();for(var n:nativeNodes)nodes.put(n.id(),n);
        // Prefer a tree-connected copy over the same native label returned by priority lookup.
        if(label.id()>=0 && label.parent()<0) {
            var original=label;
            label=nativeNodes.stream().filter(n->n.parent()>=0 && n.box().equals(original.box())
                && n.text().equals(original.text()) && n.description().equals(original.description()))
                .findFirst().orElse(label);
        }
        Set<Integer> seen=new HashSet<>();Semantic.Node cursor=label;
        for(int depth=0;cursor!=null && depth<12 && seen.add(cursor.id());depth++,cursor=nodes.get(cursor.parent())) {
            if(cursor.id()<0)continue; // Native split-label evidence: follow its actual shared parent.
            if(!cursor.enabled() || !fresh.screen().contains(cursor.box()) || !cursor.box().contains(label.box())
                || cursor.box().height()>label.box().height()*4 || cursor.box().height()>fresh.screen().height()/3)break;
            if(unsafe(cursor,label,nodes))return new Result(null,"notification/event");
            if(cursor.clickable())return new Result(cursor,"points");
        }
        return new Result(null,"unknown");
    }
}
