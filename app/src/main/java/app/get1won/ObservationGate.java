package app.get1won;
import java.util.List;
import java.util.function.Consumer;
/** Shared production/test boundary for window and asynchronous OCR results. Main-thread only. */
public final class ObservationGate {
    private final Engine engine;private final Consumer<String> log;private TargetWindow target;
    public ObservationGate(Engine engine,Consumer<String> log){this.engine=engine;this.log=log;}
    public void bind(String pkg,int window){target=new TargetWindow(pkg,window);}
    public boolean window(String pkg,int id){
        if(!engine.active() || target==null)return false;
        if(!target.pkg().equals(pkg)){engine.pause("다른 앱으로 전환되어 멈췄어요.");return false;}
        if(target.id()!=id){
            log.accept("window 변경 "+target.id()+" → "+id+" / package 유지");
            target=new TargetWindow(pkg,id);engine.windowChanged();
        }
        return true;
    }
    public boolean current(OcrTicket t){return engine.active() && t.current(engine.generation,engine.cycleId,engine.state,engine.actionEpoch);}
    public Semantic.Found merge(OcrTicket t,Semantic.Scene fresh,List<Semantic.Node> ocr,String pkg,int window,long frameTime,long now){
        if(!current(t) || !window(pkg,window))return null;
        if(!t.matches(window,pkg,"",frameTime,now) || !t.region().equals(fresh.screen())){log.accept("OCR 폐기: 오래된 frame 또는 window 크기 변경");return null;}
        return Semantic.inspect(Semantic.mergeOcr(fresh,ocr));
    }
    public void failure(OcrTicket t,String stage){if(current(t))log.accept(stage+" / 현재 화면 재관찰; 임의 클릭 없음");}
}
