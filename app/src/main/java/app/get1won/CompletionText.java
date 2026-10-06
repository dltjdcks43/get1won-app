package app.get1won;

public final class CompletionText {
    private CompletionText(){}
    public static boolean matches(CharSequence text){return text!=null && text.toString().replaceAll("[\\s\\u00a0]+","").equals("1원받았어요");}
}
