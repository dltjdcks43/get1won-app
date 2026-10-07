package app.get1won;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Model regressions only; these do not verify Android input delivery. */
public class Beta3RegressionTest {
    private static Semantic.Node n(int id,int parent,String text,String desc,int top,int bottom,boolean clickable) {
        return new Semantic.Node(id,parent,text,new Semantic.Box(40,top,900,bottom),clickable,true,"Accessibility",desc,"");
    }
    private static Semantic.Scene home(Semantic.Node... points) {
        List<Semantic.Node> nodes=new ArrayList<>(List.of(points));
        nodes.add(n(20,-1,"다시 혜택 구경하고 1원 받아요","",800,860,false));
        nodes.add(n(21,-1,"광고 상품","",940,1060,true));
        return SemanticTest.scene(nodes.toArray(new Semantic.Node[0]));
    }
    @Test public void pointsLabelBalanceAndWithdrawalPermitFirstAd() {
        for(String text:List.of("내 포인트","내 포인트 5,285원","내 포인트 5,285원 출금")) {
            var f=Semantic.inspect(home(n(1,-1,text,"",200,260,true)));
            assertNotNull(f.points());assertNotNull(f.anchor());assertNotNull(f.ad());
            Engine e=new Engine(s->{});e.start(1,0);assertEquals(Engine.Action.AD,e.observe(f,1).action());
        }
    }
    @Test public void parentDescriptionChildLabelAndQueryCopiesAreOneRegion() {
        var parent=n(1,-1,"","내 포인트 5,285원 출금",150,400,true);
        var child=n(2,1,"내 포인트","",180,240,false);
        var copy=n(100000,-1,parent.text(),parent.description(),150,400,true);
        var labelCopy=n(100001,-1,child.text(),child.description(),180,240,false);
        var f=Semantic.inspect(home(parent,child,copy,labelCopy));assertNotNull(f.points());assertEquals(child.box(),f.points().box());
    }
    @Test public void separateLabelBalanceWithdrawalChildren() {
        var f=Semantic.inspect(home(n(1,-1,"","",150,400,true),n(2,1,"내 포인트","",180,240,false),n(3,1,"5,285원","",260,320,false),n(4,1,"출금","",330,390,true)));
        assertNotNull(f.points());assertEquals(2,f.points().id());assertNotNull(f.ad());
    }
    @Test public void distantPointsStayAmbiguous() {
        assertNull(Semantic.inspect(home(n(1,-1,"내 포인트","",200,260,true),n(2,-1,"내 포인트","",1500,1560,true))).points());
    }
    @Test public void unrelatedMenuAndRewardCopyCannotBecomePoints() {
        for(String text:List.of("내 포인트 알림 설정","내 포인트 출금","내 포인트 1원 받기","내 포인트 5,285원 출금 이벤트")) {
            assertNull(Semantic.inspect(home(n(1,-1,text,"",200,260,true))).points());
            var menu=n(1,-1,"",text,180,300,true);
            var label=n(2,1,"내 포인트","",200,260,false);
            var query=n(100000,-1,"내 포인트","",200,260,false);
            assertNull(Semantic.inspect(home(menu,label,query)).points());
        }
    }
    @Test public void fullPageDescriptionDoesNotVetoLocalPointsRegion() {
        var page=n(0,-1,"","내 포인트 5,285원 출금 다른 이벤트",0,2000,false);
        assertNotNull(Semantic.inspect(home(page,n(1,0,"내 포인트","",200,260,true))).points());
    }
    @Test public void clickableTitleUsesItsRealCardAncestorBeyondOldThreeTimesHeight() {
        var anchor=n(20,-1,"구경하고 1원 받아요","",800,860,false);
        var title=n(3,2,"상품","",940,970,true);
        var wrapper=n(2,1,"","",930,1050,false);
        var card=n(1,-1,"","",900,1100,true);
        var unrelated=n(4,-1,"","",920,990,true);
        var scene=SemanticTest.scene(anchor,title,wrapper,card,unrelated);
        assertSame(card,Semantic.adClickTarget(scene,anchor,title));
        assertSame(card,Semantic.inspect(scene).ad());
        String log=Semantic.targetDiagnostics(scene,title,card);
        assertTrue(log.contains("selectedId=3 targetId=1"));assertTrue(log.contains("parent=2"));assertTrue(log.contains("parent=1"));
    }
    @Test public void excludedOrWholeScreenAncestorIsNotClicked() {
        var anchor=n(20,-1,"구경하고 1원 받아요","",800,860,false);
        var title=n(3,1,"상품","",940,1000,false);
        for(var parent:List.of(n(1,-1,"","",0,2200,true),n(1,-1,"동의하고 1원 받기","",900,1100,true))) {
            assertSame(title,Semantic.adClickTarget(SemanticTest.scene(anchor,parent,title),anchor,title));
        }
    }
    @Test public void acceptedClickThenMissingPointsRetriesFreshMovedAdOnlyAfterOneSecond() {
        List<String> logs=new ArrayList<>();Engine e=new Engine(logs::add);e.start(1,0);
        var first=e.observe(Semantic.inspect(home(n(1,-1,"내 포인트","",200,260,true))),1);e.submitted(first,true,2);
        var fresh=Semantic.inspect(SemanticTest.scene(n(20,-1,"구경하고 1원 받아요","",800,860,false),n(22,-1,"새 광고","",970,1090,true)));
        assertNull(e.observe(fresh,1001));var retry=e.observe(fresh,1002);
        assertEquals(2,retry.attempt());assertSame(fresh.ad(),retry.target());assertNotEquals(first.target().box(),retry.target().box());
        assertEquals(0,e.actions[0]);assertEquals("없음",e.lastSuccess);
        e.gestureSubmitted(retry,true,1003);e.gestureResult(retry,true,1004);assertEquals(0,e.actions[0]);
        e.observe(Semantic.inspect(SemanticTest.scene(n(30,-1,"3초 구경해주세요","",400,460,false))),1005);
        assertEquals(1,e.actions[0]);assertEquals(Engine.State.REWARD,e.state);
        assertTrue(logs.stream().anyMatch(s->s.contains("transition=false")));
    }
    @Test public void remainingHomeAnchorCannotConfirmAdEntryWhenPointsDisappears() {
        Engine e=new Engine(s->{});e.start(1,0);var d=e.observe(Semantic.inspect(home(n(1,-1,"내 포인트","",200,260,true))),1);e.submitted(d,true,2);
        var f=Semantic.inspect(home(n(30,-1,"3초 구경해주세요","",400,460,false)));
        assertNull(e.observe(f,1002));assertEquals(0,e.actions[0]);assertEquals(Engine.State.AD_ENTRY,e.state);
    }
    @Test public void overlayDeferDoesNotConsumeAttemptAndUsesNextSnapshot() {
        Engine e=new Engine(s->{});e.start(1,0);var f=Semantic.inspect(home(n(1,-1,"내 포인트","",200,260,true)));
        var old=e.observe(f,1);e.defer(old);var fresh=e.observe(Semantic.inspect(home(n(1,-1,"내 포인트","",200,260,true))),2);
        assertEquals(1,fresh.attempt());assertFalse(e.current(old));assertNotSame(old.target(),fresh.target());assertEquals(0,e.actions[0]);
    }
}
