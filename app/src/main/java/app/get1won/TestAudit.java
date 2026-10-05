package app.get1won;

/** Independent observer of delivered test UI events, not automation requests. */
public final class TestAudit {
    private int expected=1;
    public int normal,duplicateClicks,earlyBack,wrongOrder,beforeCompletion,transitionClicks,lateActions;
    public void action(int step,boolean completionVisible,boolean transitioning,boolean stopped){
        if(stopped)lateActions++;
        if(transitioning){if(step==1 || step==3)transitionClicks++;else earlyBack++;}
        if(step==2 && !completionVisible){beforeCompletion++;earlyBack++;}
        if(step!=expected){wrongOrder++;if(step==1 || step==3)duplicateClicks++;}
        expected=step==4?1:step+1;
    }
    public void cycleReturned(){normal++;}
    public int[] values(){return new int[]{normal,duplicateClicks,earlyBack,wrongOrder,beforeCompletion,transitionClicks,lateActions};}
    public String summary(){return "정상 사이클: "+normal+"\n중복 클릭: "+duplicateClicks+" / 너무 빠른 BACK: "+earlyBack+"\n잘못된 순서: "+wrongOrder+" / 완료 전 BACK: "+beforeCompletion+"\n전환 중 클릭: "+transitionClicks+" / 중지 후 동작: "+lateActions;}
}
