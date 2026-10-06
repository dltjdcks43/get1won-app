package app.get1won;
import java.util.*;
public class ThreeCycleSelfCheck {
 static long time=1_000_000_000L;
 static final Engine engine=new Engine(s->{});
 static final TestAudit audit=new TestAudit();
 static Semantic.Node node(int id,String text,int top,int bottom,boolean click,String source){return new Semantic.Node(id,-1,text,new Semantic.Box(100,top,900,bottom),click,true,source);}
 static Semantic.Found inspect(Semantic.Node... n){
  var fresh=new Semantic.Scene(List.of(n),new Semantic.Box(0,0,1000,2000));List<Semantic.Node> duplicates=new ArrayList<>();
  for(var node:n){String t=CompletionText.normalize(node.text());if(t.equals("내포인트") || (t.contains("구경") && t.contains("1원"))){var b=node.box();duplicates.add(new Semantic.Node(0,-1,node.text(),new Semantic.Box(b.left()+3,b.top()+3,b.right()+5,b.bottom()+4),false,true,"OCR"));}}
  return Semantic.inspect(Semantic.mergeOcr(fresh,duplicates));
 }
 static Engine.Effect frame(Semantic.Found f){time+=10_000_000L;return engine.frame(new Engine.Frame(engine.generation(),time,"fixture",f,true));}
 static void deliver(Engine.Effect effect,int step,boolean complete){if(effect==null || effect.step()!=step)throw new AssertionError("step "+step);if((step==1 && effect.target().id()!=3) || (step==3 && effect.target().id()!=1))throw new AssertionError("wrong target");audit.action(step,complete,false,false);engine.acknowledge(effect,true,time);}
 public static void main(String[] args){
  engine.start(time,3,"fixture");
  String[] anchors={"다시 구경하고 1원 받아요","여기서 혜택 구경하고 1원 받아요","여기서 구경하면 1원 받아요"};
  for(int cycle=0;cycle<3;cycle++){
   var home=inspect(node(1,"내 포인트",200,260,cycle==0,cycle==0?"Accessibility":"OCR"),node(2,anchors[cycle],800,880,false,"OCR"),node(3,"변하는 광고 상품 제목 "+cycle,920,1020,cycle==0,cycle==0?"Accessibility":"OCR"),node(4,"알림 동의하고 1원 받기",1500,1580,true,"Accessibility"));
   var adRequest=frame(home);
   if(cycle==0){engine.acknowledge(adRequest,true,time);time+=600_000_000L;adRequest=frame(home);if(engine.state!=Engine.State.OPEN_REWARD_AD || engine.actions[0]!=0)throw new AssertionError("HOME falsely confirmed");}
   deliver(adRequest,1,false);
   if(frame(home)!=null || engine.state!=Engine.State.OPEN_REWARD_AD)throw new AssertionError("HOME falsely confirmed");
   if(frame(inspect(node(10,"3초 구경해요",200,260,false,"OCR")))!=null)throw new AssertionError("early BACK");
   if(engine.state!=Engine.State.WAIT_REWARD_COMPLETE || engine.actions[0]!=cycle+1)throw new AssertionError("unconfirmed ad");
   deliver(frame(inspect(node(11,"1원 받았어요",200,260,false,"OCR"))),2,true);
   deliver(frame(home),3,true);
   deliver(frame(inspect(node(12,"전체",200,260,false,"Accessibility"),node(13,"광고 보고 1원 받기",400,460,false,"Accessibility"))),4,true);
   audit.cycleReturned();
   // HOME return with the next cycle's anchor is supplied at the next loop iteration.
   if(cycle==2 && frame(home)!=null)throw new AssertionError("fourth cycle");
   System.out.println("cycle "+(cycle+1)+": ad target / completion / BACK / points target / history / BACK = PASS (simulated platform)");
  }
  if(!Arrays.equals(audit.values(),new int[]{3,0,0,0,0,0,0}) || !Arrays.equals(engine.actions,new long[]{3,3,3,3}) || engine.completed!=3)throw new AssertionError("audit");
  System.out.println("wrong order=0; before-completion BACK=0; other event target=0. Android gestures and OCR runtime are NOT exercised.");
 }
}
