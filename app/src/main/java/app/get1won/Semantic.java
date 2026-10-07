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
        for(int i=0;i<candidates.size();i++) for(int j=i+1;j<candidates.size();j++)
            if(!samePosition(candidates.get(i).box,candidates.get(j).box)) return null;
        return candidates.stream().min(Comparator.comparingInt((Node n)->n.source.equals("Accessibility")?0:1).thenComparingLong(n->(long)n.box.width()*n.box.height())).orElse(null);
    }
    private static boolean adjacent(Node a, Node b) {
        Box x=a.box,y=b.box; int overlap=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        int gap=Math.max(x.top,y.top)-Math.min(x.bottom,y.bottom);
        return !x.overlaps(y) && gap>=0 && gap<2*Math.max(x.height(),y.height()) && overlap>Math.min(x.width(),y.width())*.5;
    }
    private static List<Node> blocks(List<Node> original) {
        List<Node> result=new ArrayList<>(original);
        for(int i=0;i<original.size();i++) {
            Node a=original.get(i); String x=normalize(a.text);
            if(!(x.contains("구경") || x.contains("1원") || x.contains("3초"))) continue;
            for(int j=i+1;j<original.size();j++) {
                Node b=original.get(j); String y=normalize(b.text),joined=x+y;
                if(!a.enabled || !b.enabled || !adjacent(a,b)) continue;
                if((!anchor(x) && !anchor(y) && anchor(joined)) || (!waiting(x) && !waiting(y) && waiting(joined)) || (!complete(x) && !complete(y) && complete(joined)))
                    result.add(new Node(-1,-1,a.text+" "+b.text,a.box.union(b.box),false,true,a.source.equals(b.source)?a.source:"Accessibility+OCR"));
            }
        }
        return result;
    }
    static boolean waiting(String s) { return s.contains("3초") && s.contains("구경"); }
    static boolean complete(String s) { return s.contains("1원") && s.contains("받았"); }
    public static Found inspect(Scene scene) {
        List<Node> blocks=blocks(scene.nodes);
        Node points=unique(blocks,s->s.equals("내포인트")),anchor=unique(blocks,Semantic::anchor);
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
            Box b=n.box,a=anchor.box;
            if(!n.enabled || b.top<a.bottom || b.top-a.bottom>Math.min(scene.screen.height()/4,a.height()*6) || b.width()<Math.max(a.width()/3,scene.screen.width()/5) || b.height()<a.height()/4 || b.height()>scene.screen.height()/4 || b.width()<b.height()*1.4 || Math.min(a.right,b.right)-Math.max(a.left,b.left)<=0 || (excluded(n.text) || excluded(n.description))) continue;
            boolean badChild=scene.nodes.stream().anyMatch(c->b.contains(c.box) && (excluded(c.text) || excluded(c.description)));
            boolean title=!normalize(n.text).isEmpty() || !normalize(n.description).isEmpty() || scene.nodes.stream().anyMatch(c->b.contains(c.box) && normalize(c.text).length()>1);
            if(!badChild && title && (n.clickable || !normalize(n.text).isEmpty() || !normalize(n.description).isEmpty())) cards.add(n);
        }
        cards.sort(Comparator.comparingInt((Node n)->n.box.top).thenComparingInt(n->n.clickable?0:1));
        if(cards.isEmpty()) return null;
        Node first=cards.get(0);
        for(Node n:cards) if(n.clickable && n.box.contains(first.box)) { first=n; break; }
        for(Node n:cards) if(n!=first && Math.abs(n.box.top-first.box.top)<anchor.box.height() && !n.box.contains(first.box) && !first.box.contains(n.box) && !samePosition(n.box,first.box)) return null;
        return first;
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
