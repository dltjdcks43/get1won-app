package app.get1won;
/** One bounded capture lease; frame timestamps must follow the actual UI hide. */
final class OverlayCaptureGate {
    private Object owner;private long hiddenAt;
    synchronized void hide(Object request,long now){owner=request;hiddenAt=now;}
    synchronized boolean owns(Object request){return owner==request && owner!=null;}
    synchronized boolean hidden(){return owner!=null;}
    synchronized boolean ready(Object request,long now){return owns(request) && now-hiddenAt>=60_000_000L && now-hiddenAt<700_000_000L;}
    synchronized boolean accepts(Object request,long stamp,long now){return ready(request,now) && stamp>hiddenAt && stamp<=now+50_000_000L && now-stamp<=250_000_000L;}
    synchronized boolean release(Object request){if(!owns(request))return false;owner=null;return true;}
    synchronized void clear(){owner=null;}
}
