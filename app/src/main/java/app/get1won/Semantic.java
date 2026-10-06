package app.get1won;

import java.util.*;

/** Immutable, platform-independent visible scene. No saved screen coordinates. */
public final class Semantic {
    public record Box(int left,int top,int right,int bottom){
        public int width(){return right-left;} public int height(){return bottom-top;}
        public int cx(){return (left+right)/2;} public int cy(){return (top+bottom)/2;}
        public boolean valid(){return width()>0 && height()>0;}
        public boolean intersects(Box b){return left<b.right && right>b.left && top<b.bottom && bottom>b.top;}
        public boolean contains(Box b){return left<=b.left && right>=b.right && top<=b.top && bottom>=b.bottom;}
    }
    public record Node(int id,int parent,String text,Box box,boolean clickable,boolean enabled,String source){}
    public record Scene(List<Node> nodes,Box screen){
        public Scene{nodes=List.copyOf(nodes);}
        public Node byId(int id){for(Node n:nodes)if(n.id==id)return n;return null;}
    }
    public record Found(Node points,Node anchor,Node complete,Node waiting,Node all,Node historyEntry,Node ad,Node pointsTarget){
        public boolean home(){return points!=null && anchor!=null && !reward() && !history();}
        public boolean history(){return all!=null && historyEntry!=null;}
        public boolean reward(){return complete!=null || waiting!=null;}
    }
    private static boolean points(String t){return CompletionText.normalize(t).equals("내포인트");}
    private static boolean anchor(String t){String s=CompletionText.normalize(t);return s.contains("구경") && s.contains("1원") && s.contains("받");}
    private static boolean sameObservation(Box a,Box b){
        if(a.contains(b) || b.contains(a))return true;
        long overlap=(long)Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        long union=(long)a.width()*a.height()+(long)b.width()*b.height()-overlap;
        if(overlap>0 && overlap>=union*.5)return true;
        long dx=(long)a.cx()-b.cx(),dy=(long)a.cy()-b.cy(),height=Math.min(a.height(),b.height());
        return dx*dx+dy*dy<=height*height && Math.abs(dy)<=height*.5 && Math.min(a.width(),b.width())>=Math.max(a.width(),b.width())*.5;
    }
    private static Node unique(Scene s,java.util.function.Predicate<String> match){
        List<Node> observations=new ArrayList<>();Node found=null;
        for(Node n:s.nodes)if(n.enabled && n.box.valid() && match.test(n.text)){
            // Compare every observation: a large OCR block must not bridge two distant labels.
            for(Node previous:observations)if(!sameObservation(previous.box,n.box))return null;
            observations.add(n);
            if(found==null || ("Accessibility".equals(n.source) && !"Accessibility".equals(found.source)) ||
                    (n.source.equals(found.source) && (long)n.box.width()*n.box.height()<(long)found.box.width()*found.box.height()))found=n;
        }
        return found;
    }
    /** The production Accessibility + OCR merge, shared with regression tests. */
    public static Scene mergeOcr(Scene fresh,List<Node> text){
        List<Node> combined=new ArrayList<>(fresh.nodes());int id=combined.stream().mapToInt(Node::id).max().orElse(-1)+1;
        for(Node n:text)combined.add(new Node(id++,-1,n.text(),n.box(),false,true,"OCR"));
        return new Scene(combined,fresh.screen());
    }
    public static String homeCheck(Found f){
        StringBuilder log=new StringBuilder("HOME CHECK points=").append(f.points!=null?"found":"not found").append(" anchor=").append(f.anchor!=null?"found":"not found").append(" ad=").append(f.ad!=null?"found":"not found");
        Node[] nodes={f.points,f.anchor,f.ad};String[] names={"points","anchor","ad"};
        for(int i=0;i<nodes.length;i++){Node n=nodes[i];if(n!=null)log.append("\n").append(names[i]).append(" source=").append(n.source).append(" text=").append(n.text).append(" bounds=").append(n.box);}
        List<String> missing=new ArrayList<>();if(f.points==null)missing.add("points missing");if(f.anchor==null)missing.add("anchor missing");if(f.ad==null)missing.add("ad missing");
        if(!missing.isEmpty())log.append("\nHOME FAIL reason: ").append(String.join(", ",missing));
        else if(!f.home())log.append("\nHOME FAIL reason: conflicting reward/history evidence");
        return log.toString();
    }
    private static Node first(Scene s,java.util.function.Predicate<String> match){for(Node n:s.nodes)if(n.enabled && n.box.valid() && match.test(n.text))return n;return null;}
    public static Found inspect(Scene s){
        Node p=unique(s,Semantic::points),a=unique(s,Semantic::anchor);
        Node c=unique(s,CompletionText::matches),w=first(s,CompletionText::waiting);
        Node all=unique(s,t->CompletionText.normalize(t).equals("전체"));
        Node h=first(s,t->CompletionText.normalize(t).equals("광고보고1원받기"));
        return new Found(p,a,c,w,all,h,ad(s,a),clickParent(s,p));
    }
    public static Node clickParent(Scene s,Node n){
        if(n==null)return null;Node current=n;
        for(int depth=0;depth<8 && current!=null;depth++,current=s.byId(current.parent)){
            if(current.enabled && current.clickable && current.box.height()<s.screen.height()/2 && current.box.contains(n.box))return current;
        }
        // OCR can join a text-free accessible container by its current bounds.
        Node best=null;
        for(Node c:s.nodes)if(c.enabled && c.clickable && c.box.contains(n.box) && c.box.height()<s.screen.height()/2 && (best==null || c.box.width()*c.box.height()<best.box.width()*best.box.height()))best=c;
        return best!=null?best:n;
    }
    private static boolean descendant(Scene s,Node child,Node parent){
        Node n=s.byId(child.parent);
        for(int depth=0;depth<40 && n!=null;depth++,n=s.byId(n.parent))if(n.id==parent.id)return true;
        return false;
    }
    private static Node ocrAd(Scene s,Node anchor){
        List<Node> nearby=new ArrayList<>();
        for(Node n:s.nodes){
            if(!n.enabled || !"OCR".equals(n.source) || n.text.isBlank() || !n.box.valid() || !s.screen.contains(n.box))continue;
            int gap=n.box.top-anchor.box.bottom;
            if(gap<0 || gap>Math.min(s.screen.height()/8,anchor.box.height()*4))continue;
            if(n.box.width()<anchor.box.width()/4 || n.box.height()>s.screen.height()/5 || n.box.right<=anchor.box.left || n.box.left>=anchor.box.right)continue;
            nearby.add(n);
        }
        // Nested OCR lines and blocks describe the same content; retain the full block.
        nearby.removeIf(n->nearby.stream().anyMatch(o->o!=n && o.box.contains(n.box) && (!o.box.equals(n.box) || o.id<n.id)));
        nearby.sort(Comparator.comparingInt(n->n.box.top));
        if(nearby.isEmpty())return null;Node first=nearby.get(0);
        if(nearby.size()>1 && nearby.get(1).box.top<first.box.bottom)return null;
        String t=CompletionText.normalize(first.text);
        if(t.length()<4 || t.contains("1원") || t.contains("내포인트") || t.contains("출금") || t.contains("동의") || t.contains("알림") || anchor(t))return null;
        return first; // Nonclickable OCR node: the platform adapter taps this observed box.
    }
    public static Node ad(Scene s,Node anchor){
        if(anchor==null)return null;List<Node> candidates=new ArrayList<>();
        for(Node n:s.nodes){
            if(!n.enabled || !n.clickable || !n.box.valid() || n.box.top<anchor.box.bottom || !s.screen.contains(n.box))continue;
            int gap=n.box.top-anchor.box.bottom;
            if(gap>Math.min(s.screen.height()/4,anchor.box.height()*8))continue;
            if(n.box.width()<anchor.box.width()/2 || n.box.height()>s.screen.height()/3)continue;
            if(n.box.right<=anchor.box.left || n.box.left>=anchor.box.right)continue;
            // Reject unrelated controls even when adjacent. Never select point-history rows.
            String t=CompletionText.normalize(n.text);
            if(t.equals("전체") || t.contains("광고보고1원받기") || t.contains("출금") || t.equals("내포인트"))continue;
            candidates.add(n);
        }
        // Nested clickable children represent one card; use the outer complete card.
        candidates.removeIf(n->candidates.stream().anyMatch(other->other!=n && other.box.contains(n.box) && descendant(s,n,other)));
        candidates.sort(Comparator.comparingInt(n->n.box.top));
        if(candidates.isEmpty())return ocrAd(s,anchor);
        Node best=candidates.get(0);
        if(candidates.size()>1 && candidates.get(1).box.top<best.box.bottom)return null;
        // Text and card must share a meaningful subtree when both expose hierarchy.
        if(anchor.parent>=0 && best.parent>=0){
            Set<Integer> ancestors=new HashSet<>();Node n=anchor;
            for(int i=0;i<4 && n!=null;i++,n=s.byId(n.parent))ancestors.add(n.parent);
            boolean linked=false;n=best;
            for(int i=0;i<4 && n!=null;i++,n=s.byId(n.parent))if(ancestors.contains(n.parent)){linked=true;break;}
            if(!linked)return null;
        }
        return best;
    }
}
