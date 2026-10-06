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
    private static boolean anchor(String t){String s=CompletionText.normalize(t);return s.equals("다시혜택구경하고1원받아요") || s.equals("다시구경하고1원받아요");}
    private static Node unique(Scene s,java.util.function.Predicate<String> match){
        Node found=null;
        for(Node n:s.nodes)if(n.enabled && n.box.valid() && match.test(n.text)){
            if(found!=null && !found.box.contains(n.box) && !n.box.contains(found.box))return null;
            if(found==null || found.box.contains(n.box))found=n;
        }
        return found;
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
        candidates.removeIf(n->candidates.stream().anyMatch(other->other!=n && other.box.contains(n.box) && !other.box.equals(n.box)));
        candidates.sort(Comparator.comparingInt(n->n.box.top));
        if(candidates.isEmpty())return null;
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
