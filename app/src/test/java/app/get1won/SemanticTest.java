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
 @Test public void changingProductAndAmountDoNotChangeSelection(){for(int i=0;i<3;i++){var ns=home();ns.set(3,n(3,1,i+"원",b(30,280,500,350),false));ns.set(5,n(5,0,"브랜드"+i,b(20,950,980,1150),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}}
 @Test public void twoCardsAtSameHeightAreAmbiguous(){var ns=home();ns.add(n(6,0,"두번째",b(10,960,990,1170),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void containedSiblingStillAmbiguous(){var ns=home();ns.add(n(6,0,"다른 카드",b(30,970,900,1050),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void nestedChildDoesNotBecomeSecondCard(){var ns=home();ns.add(n(6,5,"상품",b(30,970,900,1050),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void otherButtonsAboveAnchorAreIgnored(){var ns=home();ns.add(n(6,0,"출금",b(600,250,900,350),true));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void historyRowsAreNeverCards(){var ns=home();ns.set(5,n(5,0,"광고 보고 1원 받기",b(20,950,980,1150),true));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void noClickableCardMeansNoGuessedPosition(){var ns=home();ns.set(5,n(5,0,"광고 이미지",b(20,950,980,1150),false));assertNull(Semantic.inspect(scene(ns)).ad());}
 @Test public void invisibleTextDoesNotConfirmHome(){var ns=home();var old=ns.get(2);ns.set(2,new Semantic.Node(old.id(),old.parent(),old.text(),old.box(),false,false,"Accessibility"));assertFalse(Semantic.inspect(scene(ns)).home());}
 @Test public void ocrAnchorUsesActualAccessibleCard(){var ns=home();ns.set(4,new Semantic.Node(4,-1,"다시 구경하고 1원 받아요",b(20,800,900,900),false,true,"OCR"));assertEquals(5,Semantic.inspect(scene(ns)).ad().id());}
 @Test public void duplicateUnrelatedAnchorsAreAmbiguous(){var ns=home();ns.add(n(6,0,"다시 혜택 구경하고 1원 받아요",b(20,1500,980,1600),false));assertNull(Semantic.inspect(scene(ns)).anchor());}
 @Test public void anchorAcceptsViewingRewardPhrasesAndWhitespace(){
  for(String phrase:List.of("다시 구경하고 1원 받아요","다시 혜택 구경하고 1원 받아요","여기서 혜택 구경하고 1원 받아요","여기서 구경하면 1원 받아요","한번 더 구경하고 1원 받아요")){
   for(String text:List.of(phrase,phrase.replace(" ","\n  "))){
    var ns=home();ns.set(4,n(4,0,text,b(20,800,900,900),false));var f=Semantic.inspect(scene(ns));
    assertNotNull(text,f.anchor());assertTrue(text,f.home());assertEquals(5,f.ad().id());
   }
  }
 }
 @Test public void anchorRejectsOtherOneWonEvents(){
  for(String text:List.of("1원 받고 혜택 알림 켜기","쿠폰 혜택 알림 동의하고 1원 받기","동의문 10초 만에 동의하고 1원 받기","매장 혜택 소식 받고 1원 받기")){
   var ns=home();ns.set(4,n(4,0,text,b(20,800,900,900),false));var f=Semantic.inspect(scene(ns));
   assertNull(text,f.anchor());assertFalse(text,f.home());assertNull(text,f.ad());
  }
 }
 @Test public void missingClickableCardUsesNearbyOcrTitle(){
  var ns=home();ns.set(5,new Semantic.Node(5,-1,"매번 다른 광고 상품 제목",b(80,950,900,1050),false,true,"OCR"));
  var f=Semantic.inspect(scene(ns));assertEquals(5,f.ad().id());assertFalse(f.ad().clickable());
 }
 @Test public void ocrAdRejectsOtherEventsAndAmbiguousBoxes(){
  var ns=home();ns.set(5,new Semantic.Node(5,-1,"알림 동의하고 1원 받기",b(80,950,900,1050),false,true,"OCR"));assertNull(Semantic.inspect(scene(ns)).ad());
  ns.set(5,new Semantic.Node(5,-1,"광고 상품 제목",b(20,950,450,1050),false,true,"OCR"));ns.add(new Semantic.Node(6,-1,"다른 상품 제목",b(460,950,950,1050),false,true,"OCR"));assertNull(Semantic.inspect(scene(ns)).ad());
 }
 @Test public void pointsWithoutClickableParentUsesObservedOcrBox(){
  var ns=home();ns.set(1,n(1,0,"",b(20,200,980,380),false));ns.set(2,new Semantic.Node(2,-1,"내 포인트",b(30,210,500,270),false,true,"OCR"));assertEquals(ns.get(2),Semantic.inspect(scene(ns)).pointsTarget());
 }
 private Semantic.Scene mergedHome(boolean pointsDuplicate,boolean anchorDuplicate){
  var fresh=scene(List.of(n(0,-1,"내 포인트",b(40,200,250,260),false),n(1,-1,"다시 구경하면 1원 받아요",b(40,800,700,880),false),n(2,-1,"광고 카드",b(40,920,900,1060),true)));
  List<Semantic.Node> ocr=new ArrayList<>();
  if(pointsDuplicate)ocr.add(n(0,-1,"내 포인트",b(43,203,248,262),false));
  if(anchorDuplicate)ocr.add(n(1,-1,"다시 구경하면 1원 받아요",b(43,803,705,884),false));
  ocr.add(n(2,-1,"변하는 광고 제목",b(60,933,800,1010),false));
  return Semantic.mergeOcr(fresh,ocr);
 }
 @Test public void mergedShiftedPointsKeepsAccessibility(){var f=Semantic.inspect(mergedHome(true,false));assertNotNull(f.points());assertEquals("Accessibility",f.points().source());assertTrue(f.home());}
 @Test public void mergedShiftedAnchorKeepsAccessibility(){var f=Semantic.inspect(mergedHome(false,true));assertNotNull(f.anchor());assertEquals("Accessibility",f.anchor().source());}
 @Test public void productionMergeKeepsHomeAndAdWithBothDuplicates(){var f=Semantic.inspect(mergedHome(true,true));assertTrue(f.home());assertEquals(2,f.ad().id());assertEquals("Accessibility",f.points().source());assertEquals("Accessibility",f.anchor().source());assertFalse(Semantic.homeCheck(f).contains("HOME FAIL"));}
 @Test public void distantMergedAnchorRemainsAmbiguous(){var fresh=mergedHome(true,true);var f=Semantic.inspect(Semantic.mergeOcr(fresh,List.of(n(0,-1,"다시 구경하면 1원 받아요",b(40,1500,700,1580),false))));assertNull(f.anchor());assertFalse(f.home());assertTrue(Semantic.homeCheck(f).contains("anchor missing"));}
 @Test public void duplicateSelectionIsOrderIndependentAndLargeBlockCannotBridge(){
  var ns=new ArrayList<>(mergedHome(true,true).nodes());Collections.reverse(ns);var f=Semantic.inspect(scene(ns));assertTrue(f.home());assertEquals("Accessibility",f.anchor().source());
  ns.add(n(50,-1,"다시 구경하면 1원 받아요",b(10,780,950,1800),false));ns.add(n(51,-1,"다시 구경하면 1원 받아요",b(40,1500,700,1580),false));assertNull(Semantic.inspect(scene(ns)).anchor());
 }
 @Test public void homeFailureLogNamesEachMissingElement(){String log=Semantic.homeCheck(Semantic.inspect(scene(List.of())));assertTrue(log.contains("points missing"));assertTrue(log.contains("anchor missing"));assertTrue(log.contains("ad missing"));}
}
