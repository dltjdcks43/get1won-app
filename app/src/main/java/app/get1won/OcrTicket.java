package app.get1won;
/** Action epoch changes only on decisions, not on unrelated content events. */
public record OcrTicket(long id,long generation,long cycle,Engine.State state,long revision,int window,String pkg,long requested,String fingerprint,Semantic.Box region,Semantic.Box overlay) {
    public boolean current(long generation,long cycle,Engine.State state,long actionEpoch) {
        return this.generation==generation && this.cycle==cycle && this.state==state && this.revision==actionEpoch;
    }
    public boolean matches(int window,String pkg,String unused,long frameTime,long now) {
        return this.window==window && this.pkg.equals(pkg) && frameTime>=requested && now>=frameTime && now-frameTime<=2500;
    }
}
