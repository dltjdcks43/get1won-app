package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TreeWalkTest {
    private static final class N { boolean visible;List<N> children=new ArrayList<>();N(boolean v){visible=v;} }
    private int visited,released;private final List<N> found=new ArrayList<>();
    private TreeWalk.Access<N> access(){return new TreeWalk.Access<>(){
        public int children(N n){return n.children.size();}
        public N child(N n,int i){return n.children.get(i);}
        public void visit(N n,int id,int parent){visited++;if(n.visible)found.add(n);}
        public void release(N n){released++;}
    };}
    @Test public void I_moreThan600PreservesValidNodes(){N root=new N(true);for(int i=0;i<800;i++)root.children.add(new N(true));var r=TreeWalk.collect(root,access(),2000,()->0,80);assertEquals(801,r.visited());assertEquals(801,found.size());assertEquals(801,released);assertFalse(r.partial());}
    @Test public void J_invisibleParentStillVisitsChild(){N root=new N(false),child=new N(true);root.children.add(child);TreeWalk.collect(root,access(),2000,()->0,80);assertEquals(List.of(child),found);assertEquals(2,released);}
    @Test public void budgetKeepsPartialAndReleasesEveryObtainedNode(){N root=new N(true);for(int i=0;i<2100;i++)root.children.add(new N(true));var r=TreeWalk.collect(root,access(),2000,()->0,80);assertTrue(r.partial());assertEquals(2000,found.size());assertEquals(2000,released);}
    @Test public void deadlineReleasesQueuedNodes(){N root=new N(true);root.children.add(new N(true));long[] times={0,20,40,80};int[] index={0};var r=TreeWalk.collect(root,access(),2000,()->times[Math.min(index[0]++,3)],80);assertTrue(r.partial());assertEquals(1,found.size());assertEquals(2,released);}
}
