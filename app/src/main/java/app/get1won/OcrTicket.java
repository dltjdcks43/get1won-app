package app.get1won;

/** Every OCR callback is bound to a session, cycle, state, window and fresh frame. */
public record OcrTicket(long id,long generation,long cycle,Engine.State state,long revision,int window,String pkg,long requested,String fingerprint,Semantic.Box region,Semantic.Box overlay) {
    public boolean current(long generation,long cycle,Engine.State state,long revision) {
        return this.generation==generation && this.cycle==cycle && this.state==state && this.revision==revision;
    }
    public boolean matches(int window,String pkg,String fingerprint,long frameTime,long now) {
        return this.window==window && this.pkg.equals(pkg) && this.fingerprint.equals(fingerprint) && frameTime>=requested && now>=frameTime && now-frameTime<=2500;
    }
}
