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
    static boolean points(String s) { return s.equals("내포인트") || s.matches("내포인트(?:잔액)?[:：]?[0-9,]+(?:[Pp]|원|포인트)?(?:출금)?[›>]?"); }
    private static boolean pointsContext(Node n,Map<Integer,Node> byId) {
        // Match the selector: nonblank text is authoritative; description is only a fallback.
        String own=normalize(label(n));
        if(own.contains("내포인트") && !points(own) || pointsEvent(own))return false;
        for(Node region:byId.values())if(region!=n && region.enabled && !points(normalize(region.text)) && localPointsContext(region,n,byId))
            for(String raw:List.of(region.text,region.description)) {
                // Generic promo words do not prove that an ancestor describes this points target.
                String s=normalize(raw);if(s.contains("내포인트") && !points(s))return false;
            }
        return true;
    }
    private static boolean localPointsContext(Node region,Node candidate,Map<Integer,Node> byId) {
        if(region.box.height()>candidate.box.height()*4
            || !(region.box.contains(candidate.box) || samePosition(region.box,candidate.box)))return false;
        boolean linked=isAncestor(region,candidate,byId) || samePosition(region.box,candidate.box);
        // OCR and platform query copies may lack a parent; use a corresponding native label.
        if(!linked)for(Node peer:byId.values())if(peer.enabled && pointLabel(peer)
            && samePosition(peer.box,candidate.box) && isAncestor(region,peer,byId)){linked=true;break;}
        if(!linked)return false;
        // A real nested balance/withdrawal region separates the points label from page promotions.
        for(Node inner:byId.values())if(inner!=region && inner!=candidate && inner.enabled
            && inner.box.contains(candidate.box) && inner.box.height()<=candidate.box.height()*4
            && isAncestor(region,inner,byId) && pointsRegionContents(inner,candidate,byId))return false;
        return true;
    }
    private static boolean pointsRegionContents(Node region,Node candidate,Map<Integer,Node> byId) {
        boolean balance=false,withdrawal=false;
        for(Node child:byId.values())if(child.enabled && isAncestor(region,child,byId)) {
            int gap=Math.max(0,Math.max(child.box.top-candidate.box.bottom,candidate.box.top-child.box.bottom));
            if(gap>candidate.box.height()*2)continue;
            String s=normalize(label(child));
            balance|=s.matches("(?:잔액)?[0-9,]+(?:원|[Pp]|포인트)");withdrawal|=s.equals("출금");
        }
        return balance && withdrawal;
    }
    private static boolean pointsEvent(String s) {
        return s.contains("광고") || s.contains("이벤트") || s.contains("알림") || s.contains("동의") || s.contains("출석");
    }
    private record PointIdentity(Box box,String text,String description) {}
    private static PointIdentity pointIdentity(Node n) { return new PointIdentity(n.box,n.text,n.description); }
    private static List<Node> pointsNodes(List<Node> nodes) {
        Map<Integer,Node> byId=new HashMap<>();for(Node n:nodes)byId.put(n.id,n);
        Set<PointIdentity> rejected=new HashSet<>();
        for(Node n:nodes)if((points(normalize(n.text)) || points(normalize(n.description))) && !pointsContext(n,byId))rejected.add(pointIdentity(n));
        return nodes.stream().filter(n->!rejected.contains(pointIdentity(n))).toList();
    }
    public record PointsEvidence(Node node,int candidates,String reason) {
        public String summary(){return "points candidates="+candidates+" reject="+reason;}
    }
    private static boolean pointLabel(Node n) { return points(normalize(n.text)) || points(normalize(n.description)); }
    private static boolean pointsNext(Node a,Node b) {
        Box x=a.box,y=b.box;int h=Math.min(x.height(),y.height());
        if(h<=0 || Math.max(x.height(),y.height())>h*2 || x.overlaps(y))return false;
        int vertical=Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top);
        int horizontal=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        return y.left>=x.right && y.left-x.right<=h && vertical>=h*.6
            || y.top>=x.bottom && y.top-x.bottom<=h*.5 && horizontal>=Math.min(x.width(),y.width())*.5;
    }
    private static List<Node> pointsBlocks(Scene scene) {
        List<Node> out=new ArrayList<>(scene.nodes);Map<Integer,Node> byId=new HashMap<>();
        for(Node n:scene.nodes)byId.put(n.id,n);
        List<Node> left=scene.nodes.stream().filter(n->n.enabled && normalize(label(n)).equals("내")).toList();
        List<Node> right=scene.nodes.stream().filter(n->n.enabled && normalize(label(n)).equals("포인트")).toList();
        if(left.size()>32 || right.size()>32)return out;
        Set<Node> parents=new HashSet<>();int id=-2;
        for(Node a:left)for(Node b:right)if(pointsNext(a,b) && scene.screen.contains(a.box.union(b.box))) {
            Node combined=new Node(id--,a.parent==b.parent?a.parent:-1,"내 포인트",a.box.union(b.box),false,true,
                a.source.equals(b.source)?a.source:"Accessibility+OCR");
            if(!pointsContext(a,byId) || !pointsContext(b,byId) || !pointsContext(combined,byId))continue;
            out.add(combined);
            for(Node parent:scene.nodes)if(pointLabel(parent) && isAncestor(parent,a,byId) && isAncestor(parent,b,byId))parents.add(parent);
        }
        out.removeAll(parents);return out;
    }
    public static PointsEvidence pointsEvidence(Scene scene) {
        List<Node> blocks=pointsBlocks(scene),accepted=pointsNodes(blocks);
        int candidates=(int)blocks.stream().filter(n->n.enabled &&
            (normalize(n.text).contains("내포인트") || normalize(n.description).contains("내포인트"))).count();
        Node selected=unique(accepted,Semantic::points);
        boolean any=accepted.stream().anyMatch(n->n.enabled && pointLabel(n));
        return new PointsEvidence(selected,candidates,selected!=null?"none":any?"ambiguous_distinct_targets":candidates>0?"context_rejected":"label_missing");
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
        return unique(nodes,matcher,(a,b)->samePosition(a.box,b.box));
    }
    private static Node unique(List<Node> nodes, Predicate<String> matcher,java.util.function.BiPredicate<Node,Node> same) {
        List<Node> candidates=nodes.stream().filter(n->n.enabled && (matcher.test(normalize(n.text)) || matcher.test(normalize(n.description)))).toList();
        Map<Integer,Node> byId=new HashMap<>();
        for(Node n:nodes)if(n.id>=0)byId.put(n.id,n);
        List<Node> all=candidates;
        candidates=all.stream().filter(n->all.stream().noneMatch(child->child!=n && isAncestor(n,child,byId))).toList();
        for(int i=0;i<candidates.size();i++) for(int j=i+1;j<candidates.size();j++)
            if(!same.test(candidates.get(i),candidates.get(j))) return null;
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
    private record AnchorPiece(Node node,String text,int mask) {}
    public record AnchorEvidence(Node node,int candidates,String reason) {
        public String summary(){return "anchor candidates="+candidates+" reject="+reason;}
    }
    private static String anchorText(String text) {
        // Punctuation/spacing are not semantic evidence. No edit-distance matching for 구경.
        return normalize(text).replaceAll("[\\p{P}]+", "");
    }
    private static boolean anchorExcluded(String s) {
        return s.contains("동의") || s.contains("알림") || s.contains("출석") || s.contains("출금")
            || s.contains("내포인트") || s.contains("확인하기") || s.contains("적립이벤트") || s.contains("받았");
    }
    private static int anchorMask(String text,String source) {
        String s=anchorText(text);
        // Only common OCR glyph confusion immediately next to 원; never guess the browse token.
        boolean won=s.matches(".*(?<![0-9])1원.*") || source.contains("OCR") && s.matches(".*(?<![A-Za-z0-9])[Il|]원.*");
        return (s.contains("구경")?1:0) | (won?2:0) | (s.contains("받")?4:0);
    }
    /** Evidence is local to this scene. At most three contributing pieces, no exact phrase whitelist. */
    public static AnchorEvidence anchorEvidence(Scene scene) {
        List<AnchorPiece> pieces=new ArrayList<>();List<Node> candidates=new ArrayList<>();
        Map<Integer,Node> byId=new HashMap<>();List<Node> hierarchy=new ArrayList<>();
        int allMask=0,rejected=0,oversized=0;
        for(Node n:scene.nodes) {
            byId.put(n.id,n);
            hierarchy.add(new Node(n.id,n.parent,"",n.box,n.clickable,n.enabled,n.source,"",n.resourceId));
            if(!n.enabled)continue;
            Set<String> labels=new LinkedHashSet<>(List.of(n.text,n.description));
            for(String raw:labels) {
                String text=anchorText(raw);int mask=anchorMask(raw,n.source);if(mask==0)continue;
                if(anchorExcluded(text)){rejected++;continue;}
                allMask|=mask;
                if(text.length()>80 || n.box.width()==0 || n.box.height()==0 || !scene.screen.contains(n.box)
                    || n.box.height()>scene.screen.height()/5 || n.box.width()>scene.screen.width()){oversized++;continue;}
                if(mask==7)candidates.add(anchor(normalize(raw))?n:
                    new Node(n.id,n.parent,"구경 1원 받아요",n.box,n.clickable,n.enabled,n.source,"",n.resourceId));
                else pieces.add(new AnchorPiece(n,text,mask));
            }
        }
        if(pieces.size()>128 || candidates.size()>128)return new AnchorEvidence(null,candidates.size(),"candidate_limit");
        Set<Node> parents=new HashSet<>();
        for(AnchorPiece a:pieces)for(AnchorPiece b:pieces) {
            if((a.mask|b.mask)==a.mask || (a.mask|b.mask)==b.mask || !anchorNear(a,b))continue;
            int mask=a.mask|b.mask;
            if(mask==7)addAnchor(scene,candidates,parents,byId,List.of(a,b));
            else for(AnchorPiece c:pieces)if((mask|c.mask)==7 && anchorNear(b,c))
                addAnchor(scene,candidates,parents,byId,List.of(a,b,c));
            if(candidates.size()>256)return new AnchorEvidence(null,candidates.size(),"candidate_limit");
        }
        candidates.removeAll(parents);hierarchy.addAll(candidates);
        Node selected=unique(hierarchy,Semantic::anchor,(a,b)->sameAnchorObservation(a,b,scene.screen));
        String reason=selected!=null?"none":!candidates.isEmpty()?"ambiguous_distinct_targets":
            allMask==7?"spatial_or_context_rejected":rejected>0?"excluded_event":oversized>0?"region_too_large":
            (allMask&1)==0?"browse_missing":(allMask&2)==0?"one_won_missing":"receive_missing";
        return new AnchorEvidence(selected,candidates.size(),reason);
    }
    private static String anchorPhrase(Node n) {
        return anchorText(anchorMask(n.text,n.source)==7?n.text:n.description);
    }
    private static boolean sameAnchorObservation(Node a,Node b,Box screen) {
        if(samePosition(a.box,b.box))return true;
        boolean crossSource=a.source.equals("Accessibility") && b.source.equals("OCR")
            || b.source.equals("Accessibility") && a.source.equals("OCR");
        if(!crossSource || !anchorPhrase(a).equals(anchorPhrase(b)))return false;
        Box x=a.box,y=b.box;int h=Math.min(x.height(),y.height());
        int vertical=Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top);
        int horizontal=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        // Same literal phrase, shared text row; allow native row padding around OCR ink only.
        return h>0 && Math.max(x.height(),y.height())<=Math.min(h*4,screen.height()/8)
            && vertical>=h*.8 && Math.abs(x.cy()-y.cy())<=h*.5
            && horizontal>=Math.min(x.width(),y.width())*.9;
    }
    private static boolean anchorNear(AnchorPiece a,AnchorPiece b) {
        if(a.node.id==b.node.id)return false;
        Box x=a.node.box,y=b.node.box;int h=Math.min(x.height(),y.height());
        if(h<=0 || Math.max(x.height(),y.height())>h*2.5)return false;
        int vertical=Math.min(x.bottom,y.bottom)-Math.max(x.top,y.top);
        boolean mixed=!a.node.source.equals(b.node.source);
        // Overlapping cross-source boxes may denote complementary observations of the same row.
        boolean sameRow=mixed && samePosition(x,y) && vertical>=h*.6;
        boolean row=y.cx()>x.cx() && y.left>=x.right-h/3 && y.left-x.right<=h*(mixed?1.5:2) && vertical>=h*.6;
        int horizontal=Math.min(x.right,y.right)-Math.max(x.left,y.left);
        boolean line=y.top>=x.bottom-h/5 && y.top-x.bottom<=h && horizontal>=Math.min(x.width(),y.width())*.5;
        return sameRow || row || line;
    }
    private static void addAnchor(Scene scene,List<Node> candidates,Set<Node> parents,Map<Integer,Node> byId,List<AnchorPiece> parts) {
        Box box=parts.get(0).node.box;int h=Integer.MAX_VALUE;String source=parts.get(0).node.source;
        for(AnchorPiece p:parts){box=box.union(p.node.box);h=Math.min(h,p.node.box.height());if(!source.equals(p.node.source))source="Accessibility+OCR";}
        if(box.height()>Math.min(h*5,scene.screen.height()/5) || box.width()>Math.min(h*24,scene.screen.width()))return;
        // Do not skip over an intervening opt-in/event label to assemble unrelated words.
        for(Node n:scene.nodes)if(n.enabled && box.contains(n.box)
            && (anchorExcluded(anchorText(n.text)) || anchorExcluded(anchorText(n.description))))return;
        candidates.add(new Node(-1,-1,"구경 1원 받아요",box,false,true,source));
        for(Node n:scene.nodes)if((anchor(normalize(n.text)) || anchor(normalize(n.description)))
            && parts.stream().allMatch(child->isAncestor(n,child.node,byId)))parents.add(n);
    }
    static boolean waiting(String s) { return s.contains("3초") && s.contains("구경"); }
    static boolean complete(String s) { return s.contains("1원") && s.contains("받았"); }
    public static Found inspect(Scene scene) {
        List<Node> blocks=blocks(scene.nodes);
        Node points=pointsEvidence(scene).node(),anchor=anchorEvidence(scene).node();
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
            // OCR lines support native cards; they are not independent card targets.
            if(n.source.equals("Accessibility") && adRejection(scene,anchor,n)==null)cards.add(n);
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
        explain(out,"points",found.points,pointsNodes(pointsBlocks(scene)),Semantic::points);
        out.append("\n").append(pointsEvidence(scene).summary());
        out.append("\n").append(anchorEvidence(scene).summary());
        out.append("\nad=").append(describe(found.ad));
        if(found.anchor==null)out.append(" reason=anchor_missing_or_ambiguous");
        else {
            Map<String,Integer> reasons=new TreeMap<>();int eligible=0,ocrSupport=0;
            for(Node n:scene.nodes){
                if(n.source.equals("OCR")){if(adRejection(scene,found.anchor,n)==null)ocrSupport++;continue;}
                if(!n.source.equals("Accessibility"))continue;
                String why=adRejection(scene,found.anchor,n);if(why==null)eligible++;else reasons.merge(why,1,Integer::sum);
            }
            out.append(" nativeCandidates=").append(eligible).append(" ocrSupport=").append(ocrSupport);
            if(found.ad==null)out.append(" reason=").append(eligible>0?"ambiguous_cards":"no_eligible_card").append(" rejected=").append(reasons);
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
