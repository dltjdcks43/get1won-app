package app.get1won;
/** Locked at Start. Overlays are excluded by the Android window selector. */
public record TargetWindow(String pkg,int id) {
    public boolean matches(String observedPackage,int observedWindow) { return pkg.equals(observedPackage) && id==observedWindow; }
}
