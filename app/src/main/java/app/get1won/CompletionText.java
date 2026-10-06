package app.get1won;
public final class CompletionText {
    public static String normalize(CharSequence text){return text==null?"":text.toString().replaceAll("[\\s\\p{Z}]+", "");}
    public static boolean matches(CharSequence text){String t=normalize(text);return t.equals("1원받았어요") || t.equals("1원받았어요.");}
    public static boolean waiting(CharSequence text){String t=normalize(text);return t.contains("3초구경해요") || t.contains("3초구경해주세요");}
}
