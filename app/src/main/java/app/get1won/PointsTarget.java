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
            || s.contains("이벤트") || s.contains("동의") || s.contains("출석") || s.contains("페이스페이혜택");
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
    private static boolean belowOrBeside(Semantic.Box child,Semantic.Box evidence) {
        int h=evidence.height();
        return child.width()>0 && child.height()>0 && child.top()>=evidence.top()-h/2
            && child.top()<=evidence.bottom()+h*2 && child.bottom()<=evidence.bottom()+h*3;
    }
    /** Strict parent IDs for structural proof: overlapping sibling boxes never create ancestry. */
    private static boolean inSubtree(Semantic.Node child,Semantic.Node target,Map<Integer,Semantic.Node> nodes) {
        Set<Integer> seen=new HashSet<>();
        for(int i=0;child!=null && i<32 && seen.add(child.id());i++,child=nodes.get(child.parent()))
            if(child.id()==target.id())return true;
        return false;
    }
    private static Result structure(Semantic.Scene fresh,Semantic.Node evidence,Map<Integer,Semantic.Node> nodes) {
        if(!evidence.source().contains("OCR") || !Semantic.normalize(Semantic.label(evidence)).equals("내포인트"))
            return new Result(null,"no_native_label");
        var box=evidence.box();
        if(box.height()==0 || box.width()==0 || !fresh.screen().contains(box))return new Result(null,"no_points_structure");
        List<Semantic.Node> candidates=new ArrayList<>();boolean promotion=false;
        for(var region:nodes.values()) {
            if(!region.enabled() || !region.clickable() || !fresh.screen().contains(region.box())
                || !region.box().contains(box) || region.box().height()>box.height()*6
                || region.box().height()>fresh.screen().height()/4)continue;
            boolean balance=false,withdrawal=false,bad=excluded(region);
            for(var child:nodes.values())if(child.enabled() && inSubtree(child,region,nodes)) {
                bad|=excluded(child);
                if(!region.box().contains(child.box()) || !belowOrBeside(child.box(),box))continue;
                String text=Semantic.normalize(Semantic.label(child));
                balance|=text.matches("(?:잔액)?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:원|[Pp]|포인트)");
                withdrawal|=text.equals("출금");
            }
            var parent=nodes.get(region.parent());
            if(parent!=null && parent.box().equals(region.box()))bad|=excluded(parent);
            if(bad){promotion=true;continue;}
            if(balance && withdrawal)candidates.add(region);
        }
        // Remove only real ancestors of a narrower proven region, not arbitrary larger boxes.
        var all=List.copyOf(candidates);
        candidates.removeIf(parent->all.stream().anyMatch(child->child.id()!=parent.id() && inSubtree(child,parent,nodes)));
        if(candidates.size()>1)return new Result(null,"ambiguous_points_regions");
        if(candidates.isEmpty())return new Result(null,promotion?"promotion_rejected":"no_points_structure");
        return new Result(candidates.get(0),"points_structure");
    }
    static Result resolve(Semantic.Scene fresh,Semantic.Node evidence) {
        if(evidence==null)return new Result(null,"unknown");
        var nativeNodes=fresh.nodes().stream().filter(n->n.id()>=0 && n.source().equals("Accessibility")).toList();
        // Re-run the existing points semantics using only fresh native observations. In
        // particular an old OCR/synthetic id can never be mistaken for a current handle.
        var points=Semantic.pointsEvidence(new Semantic.Scene(nativeNodes,fresh.screen()));
        var label=points.node();
        Map<Integer,Semantic.Node> nodes=new HashMap<>();for(var n:nativeNodes)nodes.put(n.id(),n);
        if(label==null) {
            // Missing labels alone permit structural fallback; ambiguous/rejected native labels do not.
            if(!points.reason().equals("label_missing"))return new Result(null,"no_native_label");
            return structure(fresh,evidence,nodes);
        }
        if(!Semantic.samePosition(label.box(),evidence.box()))return new Result(null,"no_native_label");
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
            if(unsafe(cursor,label,nodes))return new Result(null,"promotion_rejected");
            if(cursor.clickable())return new Result(cursor,"points");
        }
        return new Result(null,"unknown");
    }
}
