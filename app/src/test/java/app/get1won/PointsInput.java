package app.get1won;
// Historical beta9-12 policy fixture only; not shipped or used by beta13 dispatch.

import java.util.List;

/** POINTS dispatch policy only. No handles, retained OCR coordinates, or success counters. */
final class PointsInput {
    private long cycle=-1;
    private boolean nativeAttempted;
    private int gestures;
    private void cycle(long current){if(cycle!=current){cycle=current;nativeAttempted=false;gestures=0;}}
    boolean preferNative(long current){cycle(current);return !nativeAttempted && gestures==0;}
    void nativeAttempted(long current){cycle(current);nativeAttempted=true;}
    boolean canGesture(long current){cycle(current);return gestures<2;}
    int gestureAttempted(long current){cycle(current);return ++gestures;}
    record Check(Semantic.Box box,String reason) {boolean allowed(){return box!=null;}}
    static Check check(Engine engine,OcrTicket ticket,List<Semantic.Node> ocr,Semantic.Scene merged,
                       String pkg,int window,long frameTime,long now) {
        if(engine.state!=Engine.State.WAIT_FOR_POINTS && engine.state!=Engine.State.POINTS_ENTRY)return new Check(null,"wrong_state");
        if(!ticket.current(engine.generation,engine.cycleId,engine.state,engine.actionEpoch)
            || !ticket.matches(window,pkg,"",frameTime,now) || !ticket.region().equals(merged.screen()))return new Check(null,"stale_ocr_or_window");
        var home=Semantic.inspect(merged);
        if(home.anchor()==null || home.ad()==null || home.waiting()!=null || home.complete()!=null || home.history())return new Check(null,"home_not_confirmed");
        var exact=ocr.stream().filter(n->n.enabled() && n.source().equals("OCR")
            && Semantic.normalize(n.text()).equals("내포인트")).toList();
        var point=Semantic.unique(exact,s->s.equals("내포인트"));
        if(point==null)return new Check(null,exact.isEmpty()?"ocr_points_missing":"ocr_points_ambiguous");
        var b=point.box();var screen=merged.screen();
        if(b.width()==0 || b.height()==0 || !screen.contains(b) || b.bottom()>screen.top()+screen.height()*.45
            || b.height()>screen.height()*.12 || b.bottom()>home.anchor().box().top())return new Check(null,"outside_upper_points_area");
        for(var n:merged.nodes())if(n.enabled() && promotion(Semantic.normalize(Semantic.label(n)))
            && n.box().height()<=b.height()*3 && close(n.box(),b))return new Check(null,"local_promotion");
        return new Check(b,"home_ocr_points");
    }
    private static boolean promotion(String s) {
        return s.contains("알림") || s.contains("포인트받기") || s.contains("내근처혜택")
            || s.contains("이벤트") || s.contains("동의") || s.contains("출석") || s.contains("페이스페이혜택");
    }
    private static boolean close(Semantic.Box a,Semantic.Box b) {
        int dx=Math.max(0,Math.max(a.left()-b.right(),b.left()-a.right()));
        int dy=Math.max(0,Math.max(a.top()-b.bottom(),b.top()-a.bottom()));
        return a.overlaps(b) || dx==0 && dy<=b.height()/3 || dy==0 && dx<=b.height()/2;
    }
}
