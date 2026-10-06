package app.get1won;

/** A user-owned request has no arrival timeout. Only cancellation/generation invalidates it. */
public final class StartRequest {
    private final ScreenStability quiet=new ScreenStability();
    private long generation;
    private boolean pending;
    public synchronized void request(long token){generation=token;pending=true;quiet.beginInitial();}
    public synchronized void cancel(){pending=false;quiet.clear();}
    public synchronized boolean pending(){return pending;}
    public synchronized boolean frame(long token,long time,boolean target,boolean blocked,float[] screen){
        if(!pending || token!=generation)return false;
        if(!target || blocked){quiet.beginInitial();return false;}
        if(!quiet.accept(screen,time))return false;
        pending=false;return true;
    }
}
