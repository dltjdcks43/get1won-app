package app.get1won;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Immutable observations of one application window; no Android nodes or fixture IDs. */
public final class Semantic {
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");
    public static String normalize(String text) { return SPACE.matcher(text == null ? "" : text).replaceAll(""); }
    public record Box(int left, int top, int right, int bottom) {
        public int width() { return Math.max(0, right-left); }
        public int height() { return Math.max(0, bottom-top); }
        public int cx() { return left+width()/2; }
        public int cy() { return top+height()/2; }
        public boolean overlaps(Box b) { return left<b.right && b.left<right && top<b.bottom && b.top<bottom; }
        public boolean contains(Box b) { return left<=b.left && top<=b.top && right>=b.right && bottom>=b.bottom; }
        public Box union(Box b) { return new Box(Math.min(left,b.left),Math.min(top,b.top),Math.max(right,b.right),Math.max(bottom,b.bottom)); }
    }
    public record Node(int id, int parent, String text, Box box, boolean clickable, boolean enabled, String source, String description, String resourceId) {
        public Node(int id,int parent,String text,Box box,boolean clickable,boolean enabled,String source) { this(id,parent,text,box,clickable,enabled,source,"",""); }
    }
    public record Scene(List<Node> nodes, Box screen) { public Scene { nodes=List.copyOf(nodes); } }
    public record Found(Node points, Node anchor, Node ad, Node waiting, Node complete, boolean history) {
        public boolean home() { return points!=null && anchor!=null; }
        public String summary() { return "HOME CHECK points="+describe(points)+" anchor="+describe(anchor)+" ad="+describe(ad)+" waiting="+(waiting!=null)+" complete="+(complete!=null)+" history="+history; }
        public String failure() { return "HOME FAIL reason: "+(points==null?"points missing; ":"")+(anchor==null?"anchor missing; ":"")+(ad==null?"ad missing":""); }
    }
    private static String describe(Node n) { return n==null?"not found":"found source="+n.source+" bounds="+n.box; }
    // Match the v2.2 adapter: use description only when the text is empty.
    static String label(Node n) { return n.text.isBlank()?n.description:n.text; }
    static boolean points(String s) { return s.equals("내포인트") || s.matches("내포인트[0-9,]+(?:[Pp]|원|포인트)?(?:출금)?[›>]?"); }
    private static boolean pointsContext(Node n,Map<Integer,Node> byId) {
        Node current=n;Set<Integer> seen=new HashSet<>();
        for(int depth=0;current!=null && depth<32 && seen.add(current.id);depth++,current=byId.get(current.parent)) {
            // Inspect only labels describing this points region, not arbitrary surrounding page copy.
            for(String raw:List.of(current.text,current.description)) {
                String s=normalize(raw);
                if((current==n || current.box.height()<=n.box.height()*4) && s.contains("내포인트") && !points(s))return false;
            }
        }
        return true;
    }
    private record PointIdentity(Box box,String text,String description) {}
    private static PointIdentity pointIdentity(Node n) { return new PointIdentity(n.box,n.text,n.description); }
    private static List<Node> pointsNodes(List<Node> nodes) {
        Map<Integer,Node> byId=new HashMap<>();for(Node n:nodes)byId.put(n.id,n);
        Set<PointIdentity> rejected=new HashSet<>();
        for(Node n:nodes)if((points(normalize(n.text)) || points(normalize(n.description))) && !pointsContext(n,byId))rejected.add(pointIdentity(n));
        return nodes.stream().filter(n->!rejected.contains(pointIdentity(n))).toList();
    }
    static boolean anchor(String s) { return s.contains("구경") && s.contains("1원") && s.contains("받"); }
    static boolean samePosition(Box a, Box b) {
        long intersection=(long)Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        long union=(long)a.width()*a.height()+(long)b.width()*b.height()-intersection;
        int h=Math.min(a.height(),b.height());
        // Accessibility TextView bounds can cover a whole row while OCR encloses ink only.
        int vertical=Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        int tolerance=Math.max(1,h/4);
        boolean rowContains=(a.left<=b.left+tolerance && a.right>=b.right-tolerance || b.left<=a.left+tolerance && b.right>=a.right-tolerance)
            && vertical>=h*.6 && Math.max(a.height(),b.height())<=h*2.5;
        return union>0 && (rowContains || intersection/(double)union>=.5 || h>0 && Math.hypot(a.cx()-b.cx(),a.cy()-b.cy())<=h && Math.abs(a.cy()-b.cy())<=h*.5 && Math.min(a.width(),b.width())>=Math.max(a.width(),b.width())*.5);
    }
    /** Pairwise agreement prevents a large container from bridging distant duplicate labels. */
    static Node unique(List<Node> nodes, Predicate<String> matcher) {
        List<Node> candidates=nodes.stream().filter(n->n.enabled && (matcher.test(normalize(n.text)) || matcher.test(normalize(n.description)))).toList();
        Map<Integer,Node> byId=new HashMap<>();
        for(Node n:nodes)if(n.id>=0)byId.put(n.id,n);
        List<Node> all=candidates;
        candidates=all.stream().filter(n->all.stream().noneMatch(child->child!=n && isAncestor(n,child,byId))).toList();
        for(int i=0;i<candidates.size();i++) for(int j=i+1;j<candidates.size();j++)
            if(!samePosition(candidates.get(i).box,candidates.get(j).box)) return null;
        return candidates.stream().min(Comparator.comparingInt((Node n)->n.source.equals("Accessibility")?0:1).thenComparingLong(n->(long)n.box.width()*n.box.height())).orElse(null);
    }
    private static boolean isAncestor(Node ancestor,Node child,Map<Integer,Node> byId) {
        if(ancestor.id<0 || child.id<0 || !ancestor.source.equals("Accessibility") || !child.source.equals("Accessibility") || !ancestor.box.contains(child.box))return false;
        Node cursor=child;
        for(int depth=0;depth<32 && cursor.parent>=0;depth++) {
            cursor=byId.get(cursor.parent);if(cursor==null)return false;
            if(cursor.id==ancestor.id)return true;
            // A priority-query copy can have another snapshot id, but the same native bounds/label.
            if(cursor.box.equals(ancestor.box) && cursor.text.equals(ancestor.text) && cursor.description.equals(ancestor.description) && cursor.resourceId.equals(ancestor.resourceId))return true;
        }
        return false;
    }
    private static boolean adjacent(Node a, Node b) {
        Box x=a.box,y=b.box; int overlap=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        int gap=Math.max(x.top,y.top)-Math.min(x.bottom,y.bottom);
        return !x.overlaps(y) && gap>=0 && gap<2*Math.max(x.height(),y.height()) && overlap>Math.min(x.width(),y.width())*.5;
    }
    private static List<Node> blocks(List<Node> original) {
        List<Node> result=new ArrayList<>(original);
        for(int i=0;i<original.size();i++) {
            Node a=original.get(i); String x=normalize(label(a));
            if(!(x.contains("구경") || x.contains("1원") || x.contains("3초"))) continue;
            for(int j=i+1;j<original.size();j++) {
                Node b=original.get(j); String y=normalize(label(b)),joined=x+y;
                if(!a.enabled || !b.enabled || !adjacent(a,b)) continue;
                if((!waiting(x) && !waiting(y) && waiting(joined)) || (!complete(x) && !complete(y) && complete(joined)))
                    result.add(new Node(-1,-1,label(a)+" "+label(b),a.box.union(b.box),false,true,a.source.equals(b.source)?a.source:"Accessibility+OCR"));
            }
        }
        return result;
    }
    /** Anchor-only reconstruction: at most three short, ordered fragments from one source. */
    private static List<Node> anchorBlocks(List<Node> original) {
        List<Node> result=new ArrayList<>(original);
        List<Node> pieces=original.stream().filter(n->n.enabled && n.box.width()>0 && n.box.height()>0)
            .filter(n->{String s=normalize(label(n));return !s.isEmpty() && !anchor(s) && s.length()<=32
                && s.replaceAll("다시|여기서|혜택|구경하고|구경하면|구경|1원|받아요", "").isEmpty();}).toList();
        // Bound pathological trees; never truncate and choose an arbitrary subset of targets.
        if(pieces.size()>128)return result;
        Map<Integer,Node> byId=new HashMap<>();for(Node n:original)byId.put(n.id,n);
        Set<Node> parents=new HashSet<>();
        for(Node a:pieces)for(Node b:pieces)if(anchorNext(a,b)) {
            addAnchor(result,parents,original,byId,List.of(a,b));
            if(!anchor(normalize(label(a)+label(b))))for(Node c:pieces)
                if(c!=a && anchorNext(b,c) && compactAnchor(a,b,c))addAnchor(result,parents,original,byId,List.of(a,b,c));
        }
        result.removeAll(parents);
        return result;
    }
    private static boolean anchorNext(Node a,Node b) {
        if(a==b || !a.source.equals(b.source) || a.box.overlaps(b.box))return false;
        Box x=a.box,y=b.box;int h=Math.min(x.height(),y.height());
        if(h<=0 || Math.max(x.height(),y.height())>h*2)return false;
        int vertical=Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top);
        boolean row=y.left>=x.right && y.left-x.right<=h*2 && vertical>=h*.6;
        int horizontal=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        boolean line=y.top>=x.bottom && y.top-x.bottom<=h && horizontal>=Math.min(x.width(),y.width())*.5;
        return row || line;
    }
    private static boolean compactAnchor(Node a,Node b,Node c) {
        Box box=a.box.union(b.box).union(c.box);
        int h=Math.min(a.box.height(),Math.min(b.box.height(),c.box.height()));
        return !a.box.overlaps(c.box) && box.height()<=h*5
            && box.width()<=a.box.width()+b.box.width()+c.box.width()+h*4;
    }
    private static void addAnchor(List<Node> result,Set<Node> parents,List<Node> original,Map<Integer,Node> byId,List<Node> parts) {
        String text=parts.stream().map(Semantic::label).reduce("",(a,b)->a+" "+b);
        if(!anchor(normalize(text)))return;
        Box box=parts.get(0).box;for(Node n:parts)box=box.union(n.box);
        result.add(new Node(-1,-1,text,box,false,true,parts.get(0).source));
        // Only proven native ancestors may be replaced by their reconstructed child label.
        for(Node n:original)if((anchor(normalize(n.text)) || anchor(normalize(n.description)))
            && parts.stream().allMatch(child->isAncestor(n,child,byId)))parents.add(n);
    }
    static boolean waiting(String s) { return s.contains("3초") && s.contains("구경"); }
    static boolean complete(String s) { return s.contains("1원") && s.contains("받았"); }
    public static Found inspect(Scene scene) {
        List<Node> blocks=blocks(scene.nodes);
        Node points=unique(pointsNodes(blocks),Semantic::points),anchor=unique(anchorBlocks(scene.nodes),Semantic::anchor);
        Node waiting=unique(blocks,Semantic::waiting),complete=unique(blocks,Semantic::complete);
        // Any waiting observation vetoes completion, even when waiting itself is ambiguous.
        if(waiting==null) waiting=blocks.stream().filter(n->n.enabled && (waiting(normalize(n.text)) || waiting(normalize(n.description)))).findFirst().orElse(null);
        return new Found(points,anchor,anchor==null?null:ad(scene,anchor),waiting,complete,history(blocks));
    }
    static boolean excluded(String text) {
        String s=normalize(text);
        return s.contains("내포인트") || s.contains("출금") || s.contains("확인하기") || s.contains("1원") || s.contains("동의") || s.contains("알림");
    }
    private static Node ad(Scene scene, Node anchor) {
        List<Node> cards=new ArrayList<>();
        for(Node n:scene.nodes) {
            if(adRejection(scene,anchor,n)==null)cards.add(n);
        }
        cards.sort(Comparator.comparingInt((Node n)->n.box.top).thenComparingInt(n->n.clickable?0:1));
        if(cards.isEmpty()) return null;
        Node first=cards.get(0);
        first=adClickTarget(scene,anchor,first);
        for(Node n:cards) if(n!=first && Math.abs(n.box.top-first.box.top)<anchor.box.height() && !n.box.contains(first.box) && !first.box.contains(n.box) && !samePosition(n.box,first.box)) return null;
        return first;
    }
    /** Prefer an actual eligible card ancestor, even if a title exposes a no-op click. */
    public static Node adClickTarget(Scene scene,Node anchor,Node selected) {
        if(selected==null || anchor==null)return selected;
        Map<Integer,Node> byId=new HashMap<>();for(Node n:scene.nodes)byId.put(n.id,n);
        Node current=byId.get(selected.parent);Set<Integer> seen=new HashSet<>();
        for(int depth=0;current!=null && depth<32 && seen.add(current.id);depth++,current=byId.get(current.parent))
            if(current.clickable && current.box.contains(selected.box) && adRejection(scene,anchor,current)==null)return current;
        return selected;
    }
    public static String targetDiagnostics(Scene scene,Node selected,Node target) {
        Map<Integer,Node> byId=new HashMap<>();for(Node n:scene.nodes)byId.put(n.id,n);
        StringBuilder out=new StringBuilder("selectedId="+selected.id+" targetId="+target.id+" bounds="+target.box+" clickable="+target.clickable+" parentChain=");
        Set<Integer> seen=new HashSet<>();Node current=selected;
        for(int depth=0;current!=null && depth<32 && seen.add(current.id);depth++,current=byId.get(current.parent))
            out.append("{").append(current.id).append(" parent=").append(current.parent).append(" bounds=").append(current.box).append(" clickable=").append(current.clickable).append("}");
        return out.toString();
    }
    private static String adRejection(Scene scene,Node anchor,Node n) {
        Box b=n.box,a=anchor.box;
        if(!n.enabled)return "disabled";
        if(b.top<a.bottom)return "above_anchor";
        if(b.top-a.bottom>Math.min(scene.screen.height()/4,a.height()*6))return "too_far_below_anchor";
        if(b.width()<Math.max(a.width()/3,scene.screen.width()/5))return "too_narrow";
        if(b.height()<a.height()/4 || b.height()>scene.screen.height()/4)return "height_outside_card_range";
        if(b.width()<b.height()*1.4)return "not_horizontal_card";
        if(Math.min(a.right,b.right)-Math.max(a.left,b.left)<=0)return "outside_anchor_column";
        if(excluded(label(n)))return "excluded_label";
        if(scene.nodes.stream().anyMatch(c->b.contains(c.box) && excluded(label(c))))return "excluded_child";
        boolean title=!normalize(label(n)).isEmpty() || scene.nodes.stream().anyMatch(c->b.contains(c.box) && normalize(label(c)).length()>1);
        if(!title)return "no_title";
        return n.clickable || !normalize(label(n)).isEmpty()?null:"no_clickable_or_text_target";
    }
    /** Bounded diagnostic text, only in the in-memory advanced log; not persisted by Diagnostics. */
    public static String diagnostics(Scene scene,Found found) {
        List<Node> blocks=blocks(scene.nodes);
        StringBuilder out=new StringBuilder(found.summary());
        explain(out,"points",found.points,pointsNodes(blocks),Semantic::points);
        explain(out,"anchor",found.anchor,anchorBlocks(scene.nodes),Semantic::anchor);
        out.append("\nad=").append(describe(found.ad));
        if(found.ad!=null)out.append(detail(found.ad));
        else if(found.anchor==null)out.append(" reason=anchor_missing_or_ambiguous");
        else {
            Map<String,Integer> reasons=new TreeMap<>();int eligible=0;
            for(Node n:scene.nodes){String why=adRejection(scene,found.anchor,n);if(why==null)eligible++;else reasons.merge(why,1,Integer::sum);}
            out.append(" reason=").append(eligible>0?"ambiguous_cards":"no_eligible_card").append(" rejected=").append(reasons);
        }
        return out.toString();
    }
    private static void explain(StringBuilder out,String name,Node selected,List<Node> nodes,Predicate<String> matcher) {
        out.append("\n").append(name).append("=").append(describe(selected));
        if(selected!=null){out.append(detail(selected));return;}
        List<Node> matches=nodes.stream().filter(n->n.enabled && (matcher.test(normalize(n.text)) || matcher.test(normalize(n.description)))).toList();
        out.append(" reason=").append(matches.isEmpty()?"no_matching_label":"ambiguous_distinct_targets");
        if(matches.isEmpty())matches=nodes.stream().filter(n->name.equals("points")?(normalize(n.text).contains("내포인트") || normalize(n.description).contains("내포인트")):(label(n).contains("구경") || label(n).contains("1원"))).toList();
        for(Node n:matches.stream().limit(4).toList())out.append(" candidate{").append(describe(n)).append(detail(n)).append("}");
    }
    private static String detail(Node n) {
        String text=normalize(n.text),desc=normalize(n.description);
        boolean description=n.text.isBlank() || (!points(text) && !anchor(text) && (points(desc) || anchor(desc)));
        return " text="+diagnosticText(n.text)+" description="+diagnosticText(n.description)+" selector="+(description?"description":"text")+" clickable="+n.clickable;
    }
    private static String diagnosticText(String text) {
        String s=normalize(text);
        if(s.isEmpty())return "[empty]";
        if(!(s.contains("내포인트") || s.contains("구경") || s.contains("동의") || s.contains("알림")))return "[제목/기타 문구 생략]";
        s=s.replaceAll("[0-9][0-9,]*", "#");
        return s.substring(0,Math.min(64,s.length()));
    }
    private static boolean history(List<Node> nodes) {
        boolean tab=unique(nodes,s->s.equals("전체"))!=null;
        boolean heading=unique(nodes,s->s.equals("포인트내역") || s.equals("적립내역") || s.equals("이용내역"))!=null;
        boolean filters=unique(nodes,s->s.equals("적립"))!=null && unique(nodes,s->s.equals("사용"))!=null;
        if(!tab && !heading && !filters)return false;
        List<Node> rows=new ArrayList<>();
        for(Node n:nodes) {
            String s=normalize(n.text.isBlank()?n.description:n.text);
            if(!((s.contains("원") || s.contains("포인트") || s.contains("P")) && (s.contains("적립") || s.contains("사용") || s.contains("받기") || s.contains("+") || s.contains("-")))) continue;
            if(rows.stream().noneMatch(r->samePosition(r.box,n.box))) rows.add(n);
        }
        for(Node a:rows) for(Node b:rows) if(a!=b && !a.box.overlaps(b.box) && Math.abs(a.box.left-b.box.left)<=Math.max(a.box.height(),b.box.height())) return true;
        return false;
    }
    public static Node clickParent(Scene scene, Node text) {
        if(text==null) return null;
        Node current=text;
        Set<Integer> seen=new HashSet<>();
        for(int depth=0;depth<12 && current!=null && seen.add(current.id);depth++) {
            if(current.enabled && current.clickable && current.box.contains(text.box) && current.box.height()<=text.box.height()*3 && scene.screen.contains(current.box))return current;
            int parent=current.parent;
            current=parent<0?null:scene.nodes.stream().filter(n->n.id==parent).findFirst().orElse(null);
        }
        return scene.nodes.stream().filter(n->n.enabled && n.clickable && n.box.contains(text.box) && n.box.height()<=text.box.height()*3 && scene.screen.contains(n.box)).min(Comparator.comparingLong(n->(long)n.box.width()*n.box.height())).orElse(text);
    }
    public static Scene mergeOcr(Scene fresh, List<Node> ocr) {
        List<Node> all=new ArrayList<>(fresh.nodes);
        int id=all.stream().mapToInt(Node::id).max().orElse(0)+1;
        for(Node n:ocr) all.add(new Node(id++,-1,n.text,n.box,false,true,"OCR"));
        return new Scene(all,fresh.screen);
    }
}
