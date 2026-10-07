package app.get1won;

import android.app.*;
import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded local diagnostics: persist changes, plus uncaught stack and OS exit/ANR trace. */
final class Diagnostics {
    private static File directory;private static Handler disk;private static volatile String latest="";private static String written="";
    static synchronized void install(Context context) {
        if(directory!=null)return;directory=context.getFilesDir();HandlerThread thread=new HandlerThread("LocalDiagnostics");thread.start();disk=new Handler(thread.getLooper());
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((threadName,error)->{
            write("last-error.txt",describe(error));
            if(previous!=null)previous.uncaughtException(threadName,error);else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
    }
    static String describe(Throwable error) { StringWriter out=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(out));return latest+"\n"+out; }
    static synchronized void record(Engine e) {
        latest="state="+e.state+"\ncycleId="+e.cycleId+"\ncompleted="+e.completed+"\nlastSuccess="+e.lastSuccess+"\nlastAction="+e.lastAction+"\nlastSemanticResult="+e.lastSemanticResult;
        if(disk!=null && !written.equals(latest)){disk.removeCallbacks(save);disk.postDelayed(save,300);}
    }
    private static final Runnable save=()->{String current; synchronized(Diagnostics.class){current=latest;written=current;}write("last-state.txt",current);};
    static void error(Throwable error){if(disk!=null)disk.post(()->write("last-error.txt",describe(error)));}
    private static synchronized void write(String name,String text) {
        if(directory==null)return;
        android.util.AtomicFile file=new android.util.AtomicFile(new File(directory,name));FileOutputStream stream=null;
        try {stream=file.startWrite();stream.write(text.getBytes(StandardCharsets.UTF_8));file.finishWrite(stream);}catch(IOException e){if(stream!=null)file.failWrite(stream);}
    }
    private static String file(String name) { try {return new String(java.nio.file.Files.readAllBytes(new File(directory,name).toPath()),StandardCharsets.UTF_8);}catch(IOException e){return "기록 없음";} }
    static void read(Context c,java.util.function.Consumer<String> result) {
        disk.post(()->{
            String text=file("last-state.txt")+"\n"+file("last-error.txt");
            for(ApplicationExitInfo exit:c.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(c.getPackageName(),0,3)) {
                text+="\nprocessExit reason="+exit.getReason()+" time="+exit.getTimestamp()+" "+exit.getDescription();
                if(exit.getReason()==ApplicationExitInfo.REASON_ANR)try(InputStream stream=exit.getTraceInputStream()){if(stream!=null)text+="\n"+new String(stream.readNBytes(24000),StandardCharsets.UTF_8);}catch(IOException ignored){}
            }
            String output=text;new Handler(Looper.getMainLooper()).post(()->result.accept(output));
        });
    }
}
