package app.get1won;
import java.util.function.Consumer;

/** Screen-space geometry, independent of window IDs and screenshot dimensions. */
final class WindowGeometry {
    private Semantic.Box lastWindow,lastDisplay;
    static boolean visible(Semantic.Box window,Semantic.Box display) {
        return window.width()>0 && window.height()>0 && display.width()>0 && display.height()>0 && display.contains(window);
    }
    static boolean input(Semantic.Box target,Semantic.Box window,Semantic.Box display) {
        return visible(window,display) && target.width()>0 && target.height()>0
            && window.contains(target) && display.contains(target)
            && target.cx()>=display.left() && target.cx()<display.right()
            && target.cy()>=display.top() && target.cy()<display.bottom()
            && target.cx()>=0 && target.cy()>=0;
    }
    boolean observe(Semantic.Box window,Semantic.Box display,Consumer<String> log) {
        if(visible(window,display)){lastWindow=lastDisplay=null;return true;}
        if(!window.equals(lastWindow) || !display.equals(lastDisplay)) {
            log.accept("window geometry transient: bounds="+window+" -> reobserve");
            lastWindow=window;lastDisplay=display;
        }
        return false;
    }
}
