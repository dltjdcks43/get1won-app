package app.get1won;
// Historical beta9-12 policy fixture only; not shipped or used by beta13 dispatch.

import java.util.*;

/** POINTS-only native resolution. Geometry associates evidence, never invents a parent. */
final class PointsTarget {
    record Result(Semantic.Node target,String context,String nativeReason,String structure) {
        Result(Semantic.Node target,String context){this(target,context,"none","not_attempted");}
        Result diagnosed(String reason,String outcome){return new Result(target,context,reason,outcome);}
        String selectedSummary(Semantic.Node selected) {
            return "POINTS selected source="+(selected==null?"none":selected.id()<0?"synthetic":selected.source())+" native="+nativeReason+" structure="+structure;
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
    private static boolean localSection(Semantic.Box region,Semantic.Box label) {
        if(region.contains(label))return true;
        int overlap=Math.min(region.right(),label.right())-Math.max(region.left(),label.left());
        int h=label.height();
        // A balance/withdrawal click row may begin below the OCR heading. Geometry
        // only admits a candidate; both native descendants are still mandatory.
        return h>0 && overlap>=label.width()*.8 && region.top()>=label.top()-h/2
            && region.top()<=label.bottom()+h && region.bottom()<=label.bottom()+h*3;
    }
    private static Result structure(Semantic.Scene fresh,Semantic.Node evidence,Map<Integer,Semantic.Node> nodes) {
        if(!evidence.source().contains("OCR") || !Semantic.normalize(Semantic.label(evidence)).equals("내포인트"))
            return new Result(null,"no_native_label");
        var box=evidence.box();
        if(box.height()==0 || box.width()==0 || !fresh.screen().contains(box))return new Result(null,"no_points_structure");
        List<Semantic.Node> candidates=new ArrayList<>();boolean promotion=false;
        for(var region:nodes.values()) {
            if(!region.enabled() || !region.clickable() || !fresh.screen().contains(region.box())
                || !localSection(region.box(),box) || region.box().height()>box.height()*6
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
    private static boolean mentionsPoints(Semantic.Node n) {
        return Semantic.normalize(n.text()+" "+n.description()).contains("내포인트");
    }
    static Result resolve(Semantic.Scene fresh,Semantic.Node evidence) {
        if(evidence==null)return new Result(null,"unknown").diagnosed("label_missing","not_attempted");
        var nativeNodes=fresh.nodes().stream().filter(n->n.id()>=0 && n.source().equals("Accessibility")).toList();
        var points=Semantic.pointsEvidence(new Semantic.Scene(nativeNodes,fresh.screen()));
        Map<Integer,Semantic.Node> nodes=new HashMap<>();for(var n:nativeNodes)nodes.put(n.id(),n);
        String reason=points.reason();
        if(points.node()!=null && !Semantic.samePosition(points.node().box(),evidence.box()))reason="position_mismatch";
        boolean ocr=evidence.source().contains("OCR") && Semantic.normalize(Semantic.label(evidence)).equals("내포인트");
        if(!ocr) {
            if(points.node()==null || reason.equals("position_mismatch"))return new Result(null,"no_native_label").diagnosed(reason,"not_attempted");
            return nativeTarget(fresh,points.node(),nativeNodes,nodes).diagnosed(reason,"not_attempted");
        }
        // Remote/rejected labels are not a veto on an independently proven OCR section.
        // Preserve the full native tree for actual ancestry and local promotion checks.
        var nearby=nativeNodes.stream().filter(n->n.enabled() && mentionsPoints(n)
            && Semantic.samePosition(n.box(),evidence.box())).toList();
        List<Semantic.Node> targets=new ArrayList<>();
        // Keep native split-label support; the synthetic observation is only evidence
        // for its real shared parent, never a dispatch handle.
        if(points.node()!=null && points.node().id()<0 && Semantic.samePosition(points.node().box(),evidence.box())) {
            var result=nativeTarget(fresh,points.node(),nativeNodes,nodes);
            if(result.context().equals("promotion_rejected"))return result.diagnosed("context_rejected","not_attempted");
            if(result.target()!=null)targets.add(result.target());
        }
        for(var n:nearby) {
            if(unsafe(n,n,nodes))return new Result(null,"promotion_rejected").diagnosed("context_rejected","not_attempted");
            if(!Semantic.points(Semantic.normalize(Semantic.label(n))))continue;
            var result=nativeTarget(fresh,n,nativeNodes,nodes);
            if(result.context().equals("promotion_rejected"))return result.diagnosed("context_rejected","not_attempted");
            if(result.target()!=null)targets.add(result.target());
        }
        // Collapse true ancestry and duplicate query handles, not equal-geometry sibling targets.
        var all=List.copyOf(targets);
        targets.removeIf(a->all.stream().anyMatch(b->a.id()!=b.id() && inSubtree(b,a,nodes)));
        List<Semantic.Node> distinct=new ArrayList<>();
        for(var n:targets)if(distinct.stream().noneMatch(a->sameNativeTarget(a,n)))distinct.add(n);
        if(distinct.size()>1)return new Result(null,"ambiguous_points_regions").diagnosed("ambiguous_distinct_targets","not_attempted");
        if(distinct.size()==1)return new Result(distinct.get(0),"points").diagnosed(reason,"not_attempted");
        if(reason.equals("none"))reason="no_usable_target";
        var result=structure(fresh,evidence,nodes);
        return result.diagnosed(reason,result.context());
    }
    private static boolean sameNativeTarget(Semantic.Node a,Semantic.Node b) {
        return a.id()==b.id() || ((a.id()>=100000) != (b.id()>=100000)) && a.box().equals(b.box())
            && a.text().equals(b.text()) && a.description().equals(b.description()) && a.resourceId().equals(b.resourceId());
    }
    private static Result nativeTarget(Semantic.Scene fresh,Semantic.Node label,List<Semantic.Node> nativeNodes,Map<Integer,Semantic.Node> nodes) {
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
