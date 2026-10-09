package app.get1won;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Beta11RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,int x,int y,int w,int h,boolean click) {
        return new Semantic.Node(id,parent,text,new Semantic.Box(x,y,x+w,y+h),click,true,"Accessibility");
    }
    private static Semantic.Node ocr() {return new Semantic.Node(100,-1,"내 포인트",new Semantic.Box(40,200,300,260),false,true,"OCR");}
    private static Semantic.Scene scene(boolean below,Semantic.Node... extra) {
        var nodes=new ArrayList<>(List.of(n(10,-1,"",20,below?270:180,900,below?70:160,true),
            n(11,10,"잔액 5,789원",40,280,220,40,false),n(12,10,"출금",700,280,100,40,false)));
        nodes.addAll(List.of(extra));return SemanticTest.scene(nodes.toArray(new Semantic.Node[0]));
    }
    private static void success(Semantic.Scene s,String reason) {
        var r=PointsTarget.resolve(s,ocr());assertNotNull(r.target());assertEquals(10,r.target().id());
        assertEquals(reason,r.nativeReason());assertEquals("points_structure",r.structure());
    }
    @Test public void missingNativeLabelUsesStructure(){success(scene(false),"label_missing");}
    @Test public void unrelatedContextRejectedCandidateDoesNotBlockStructure() {
        var s=scene(false,n(20,-1,"내 포인트 이벤트",0,1400,950,120,false),n(21,20,"내 포인트",40,1420,260,60,false));
        assertEquals("context_rejected",Semantic.pointsEvidence(s).reason());success(s,"context_rejected");
    }
    @Test public void remoteNativeAmbiguityDoesNotBlockLocalProof() {
        success(scene(false,n(20,-1,"내 포인트",40,1000,260,60,true),n(30,-1,"내 포인트",40,1500,260,60,true)),"ambiguous_distinct_targets");
    }
    @Test public void positionMismatchAndDetachedPriorityCopyPermitStructure() {
        success(scene(false,n(20,-1,"내 포인트",40,1400,260,60,true)),"position_mismatch");
        success(scene(false,n(100000,-1,"내 포인트",40,200,260,60,false)),"no_usable_target");
    }
    @Test public void actualDistinctNativeTargetsAtOcrLocationAreRejected() {
        for(int[] ids:List.of(new int[]{20,30},new int[]{100001,100010})) {
        var s=scene(false,n(ids[0],-1,"내 포인트",40,200,260,60,true),n(ids[1],-1,"내 포인트",40,200,260,60,true));
        var r=PointsTarget.resolve(s,ocr());assertNull(r.target());assertEquals("ambiguous_distinct_targets",r.nativeReason());assertEquals("not_attempted",r.structure());
        }
    }
    @Test public void correspondingNativePromotionBlocksOtherwiseSafeSiblingStructure() {
        var s=scene(false,n(20,-1,"알림 받고 포인트 받기",20,180,900,160,true),n(21,20,"내 포인트",40,200,260,60,false));
        var r=PointsTarget.resolve(s,ocr());assertNull(r.target());assertEquals("context_rejected",r.nativeReason());assertEquals("not_attempted",r.structure());
    }
    @Test public void nonContainingBalanceRowIsAcceptedOnlyWithBothDescendants() {
        assertFalse(scene(true).nodes().get(0).box().contains(ocr().box()));success(scene(true),"label_missing");
        var nodes=new ArrayList<>(scene(true).nodes());nodes.removeIf(n->n.id()==12);
        var r=PointsTarget.resolve(new Semantic.Scene(nodes,scene(true).screen()),ocr());assertNull(r.target());assertEquals("no_points_structure",r.structure());
    }
    @Test public void nearPromoRowWithBalanceAndWithdrawalIsRejected() {
        var nodes=new ArrayList<>(scene(true).nodes());nodes.set(0,n(10,-1,"페이스페이 혜택",20,270,900,70,true));
        var r=PointsTarget.resolve(new Semantic.Scene(nodes,scene(true).screen()),ocr());assertNull(r.target());assertEquals("promotion_rejected",r.structure());
    }
    @Test public void distantOrHorizontallyUnrelatedRowIsNotLinked() {
        for(int[] pos:List.of(new int[]{20,700},new int[]{600,270})) {
            var s=SemanticTest.scene(n(10,-1,"",pos[0],pos[1],350,70,true),
                n(11,10,"123원",pos[0],pos[1],150,30,false),n(12,10,"출금",pos[0]+180,pos[1],100,30,false));
            assertNull(PointsTarget.resolve(s,ocr()).target());
        }
        var nodes=new ArrayList<>(scene(false).nodes());nodes.set(1,n(11,10,"123원",40,1400,200,40,false));
        assertNull(PointsTarget.resolve(new Semantic.Scene(nodes,scene(false).screen()),ocr()).target());
    }
    @Test public void separateProvenStructuresRemainAmbiguous() {
        var r=PointsTarget.resolve(scene(true,n(20,-1,"",20,270,900,70,true),
            n(21,20,"123원",40,280,220,40,false),n(22,20,"출금",700,280,100,40,false)),ocr());
        assertNull(r.target());assertEquals("ambiguous_points_regions",r.structure());
    }
    @Test public void duplicateQueryHandleIsNotASeparateNativeTarget() {
        var nativeLabel=n(20,-1,"내 포인트",40,200,260,60,true);
        var copy=n(100000,-1,"내 포인트",40,200,260,60,true);
        assertNotNull(PointsTarget.resolve(scene(false,nativeLabel,copy),ocr()).target());
    }
    @Test public void nativeSplitLabelStillResolvesItsRealParentWithoutStructureFallback() {
        var s=SemanticTest.scene(n(10,-1,"",20,180,900,160,true),
            n(11,10,"내",40,200,40,60,false),n(12,10,"포인트",85,200,215,60,false));
        var r=PointsTarget.resolve(s,ocr());assertEquals(10,r.target().id());assertEquals("not_attempted",r.structure());
    }
    @Test public void diagnosticsKeepBothReasonsOnOneShortLineWithoutBalance() {
        var r=PointsTarget.resolve(scene(false,n(20,-1,"내 포인트",40,1000,260,60,true),n(30,-1,"내 포인트",40,1500,260,60,true)),ocr());
        String line=r.selectedSummary(ocr());assertEquals(1,line.lines().count());assertTrue(line.length()<180);
        assertTrue(line.contains("native=ambiguous_distinct_targets structure=points_structure"));assertFalse(line.contains("5,789"));
    }
}
