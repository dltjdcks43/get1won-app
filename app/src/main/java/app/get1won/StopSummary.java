package app.get1won;

/** Short report strings only: no screenshot text, node handles or reusable action targets. */
final class StopSummary {
    private static String line(String value) {
        String s=value==null?"없음":value.replaceAll("[\\r\\n]+"," ");
        return s.substring(0,Math.min(180,s.length()));
    }
    private static String evidence(String summary,String key) {
        var match=java.util.regex.Pattern.compile(key+"=(not found|found source=([^ ]+))").matcher(summary);
        return key+"="+(match.find()?match.group(1):"not observed");
    }
    static String format(String version,Engine e,String ocr,String transition) {
        return "버전 / commit: "+line(version)+"\nstate="+e.state+"\ncycle="+e.cycleId+" / completed="+e.completed
            +"\npause reason: "+line(e.reason)+"\nlastSuccess: "+line(e.lastSuccess)+"\nlastAction: "+line(e.lastAction)
            +"\n"+evidence(e.lastSemanticResult,"points")+"\n"+evidence(e.lastSemanticResult,"anchor")+"\n"+evidence(e.lastSemanticResult,"ad")
            +"\n마지막 OCR: "+line(ocr)+"\n마지막 전환: "+line(transition);
    }
}
