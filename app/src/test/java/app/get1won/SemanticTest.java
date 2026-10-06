package app.get1won;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SemanticTest {
 static Semantic.Box b(int l,int t,int r,int d){return new Semantic.Box(l,t,r,d);}
 static Semantic.Node n(int id,int parent,String text,Semantic.Box b,boolean click){return new Semantic.Node(id,parent,text,b,click,true,"Accessibility");}
 static Semantic.Scene scene(List<Semantic.Node> ns){return new Semantic.Scene(ns,b(0,0,1000,2000));}
 static List<Semantic.Node> home(){return new ArrayList<>(List.of(n(0,-1,"",b(0,0,1000,2000),false),n(1,0,"",b(20,200,980,380),true),n(2,1,"내 포인트",b(30,210,500,270),false),n(3,1,"4,342원",b(30,280,500,350),false),n(4,0,"다시 혜택 구경하고\n1원 받아요",b(20,800,900,900),false),n(5,0,"어떤 브랜드",b(20,950,980,1150),true)) );}
 @Test public void selectsCardAndPointsContainer(){var f=Semantic.inspect(scene(home()));assertTrue(f.home());assertEquals(5,f.ad().id());assertEquals(1,f.pointsTarget().id());}
 @Test public void changingProductAndAmountDoNotChangeSelection(){for(int i=0;i<100;i++){var ns=home();ns.set(3,n(3,1,i+"원",b(30,280,500,350),false));ns.set(5,n(5,0,"브랜드"+i,b(20,950,980,1150),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}}
 @Test public void twoCardsAtSameHeightAreAmbiguous(){var ns=home();ns.add(n(6,0,"두번째",b(10,960,990,1170),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void containedSiblingStillAmbiguous(){var ns=home();ns.add(n(6,0,"다른 카드",b(30,970,900,1050),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void nestedChildDoesNotBecomeSecondCard(){var ns=home();ns.add(n(6,5,"상품",b(30,970,900,1050),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void otherButtonsAboveAnchorAreIgnored(){var ns=home();ns.add(n(6,0,"출금",b(600,250,900,350),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void historyRowsAreNeverCards(){var ns=home();ns.set(5,n(5,0,"광고 보고 1원 받기",b(20,950,980,1150),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void noClickableCardMeansNoGuessedPosition(){var ns=home();ns.set(5,n(5,0,"광고 이미지",b(20,950,980,1150),false));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void invisibleTextDoesNotConfirmHome(){var ns=home();var old=ns.get(2);ns.set(2,new Semantic.Node(old.id(),old.parent(),old.text(),old.box(),false,false,"Accessibility"));assertFalse(Semantic.inspect(scene(ns)).home());}
 @Test public void ocrAnchorUsesActualAccessibleCard(){var ns=home();ns.set(4,new Semantic.Node(4,-1,"다시 구경하고 1원 받아요",b(20,800,900,900),false,true,"OCR"));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void duplicateUnrelatedAnchorsAreAmbiguous(){var ns=home();ns.add(n(6,0,"다시 혜택 구경하고 1원 받아요",b(20,1500,980,1600),false));assertNull(Semantic.inspect(scene(ns)).anchor());}
}
