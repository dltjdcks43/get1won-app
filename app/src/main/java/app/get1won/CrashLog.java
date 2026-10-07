package app.get1won;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
/** Last uncaught Java exception, private to this app and never uploaded. */
final class CrashLog {
 private static boolean installed;private static final AtomicBoolean writing=new AtomicBoolean();
 static synchronized void install(Context context){
  if(installed)return;installed=true;File file=new File(context.getFilesDir(),"last-crash.txt");
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   if(writing.compareAndSet(false,true))try(Writer out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){out.write("thread="+thread.getName()+"\n"+describe(error,AppState.engine.state,AppState.engine.cycleId));}catch(Exception ignored){}finally{writing.set(false);}
   if(previous!=null)previous.uncaughtException(thread,error);else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
  });
 }
 static String describe(Throwable error,Engine.State state,long cycle){StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));return "exception="+error.getClass().getName()+"\nmessage="+error.getMessage()+"\nstate="+state+"\ncycleId="+cycle+"\n"+stack;}
 static String read(Context context){File file=new File(context.getFilesDir(),"last-crash.txt");if(!file.exists())return "저장된 오류가 없어요.";try{return new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}catch(IOException ex){return "오류 기록을 읽지 못했어요: "+ex.getMessage();}}
}
