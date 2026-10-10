package app.get1won;

/** Input routing for the current observation. Never searches parents or requests OCR. */
final class PointsDispatch {
    enum Mode { NATIVE, GESTURE, DEFER, INVALID }
    record Plan(Mode mode,Semantic.Box box) {}
    static Plan plan(Semantic.Node target,boolean exactHandle,Semantic.Box window,Semantic.Box display,
                     boolean current,boolean sameWindow,boolean overlayMoved) {
        if(!current || !sameWindow || overlayMoved)return new Plan(Mode.DEFER,null);
        if(target==null)return new Plan(Mode.INVALID,null);
        if(!WindowGeometry.input(target.box(),window,display))return new Plan(Mode.DEFER,null);
        boolean nativeClick=target.id()>=0 && target.source().equals("Accessibility") && target.clickable() && exactHandle;
        return new Plan(nativeClick?Mode.NATIVE:Mode.GESTURE,target.box());
    }
}
