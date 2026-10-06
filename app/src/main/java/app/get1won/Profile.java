package app.get1won;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;

/** Two positions plus automatic text verification or an optional completion image. */
public final class Profile {
    public static final int SAMPLE_W=160,SAMPLE_H=48;
    public Point a,b;
    public Rect roi;
    public float[] template;
    public int width,height,rotation;
    public String targetPackage="";
    public volatile int repeats=1,testDelay=6100;
    public volatile boolean randomTest;
    public volatile boolean autoVerified,setupDone;
    public volatile int panelSize=2;
    public synchronized boolean positionsReady(){return a!=null && b!=null && !targetPackage.isEmpty();}
    public synchronized boolean imageReady(){return roi!=null && template!=null;}
    public synchronized boolean ready(){return positionsReady() && (autoVerified || imageReady());}
    public synchronized void invalidate(){a=null;b=null;roi=null;template=null;targetPackage="";autoVerified=false;setupDone=false;}
    public synchronized boolean geometry(int w,int h,int r){return width==w && height==h && rotation==r;}
    public synchronized void setGeometry(int w,int h,int r){if(!geometry(w,h,r))invalidate();width=w;height=h;rotation=r;}
    public static void sample(Image image,Rect rect,float[] out,int sw,int sh){
        if(rect.left<0 || rect.top<0 || rect.right>image.getWidth() || rect.bottom>image.getHeight() || rect.width()<1 || rect.height()<1 || out.length!=sw*sh)throw new IllegalArgumentException("감지 영역이 화면 밖입니다");
        Image.Plane p=image.getPlanes()[0];ByteBuffer buf=p.getBuffer();
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
            int sx=rect.left+(int)((x+.5)*rect.width()/sw),sy=rect.top+(int)((y+.5)*rect.height()/sh);
            int offset=sy*p.getRowStride()+sx*p.getPixelStride();
            out[y*sw+x]=((buf.get(offset)&255)*.299f+(buf.get(offset+1)&255)*.587f+(buf.get(offset+2)&255)*.114f)/255f;
        }
    }
    public synchronized String setupSummary(){return "1번 위치: "+(a==null?"미설정":"설정됨")+"\n3번 위치: "+(b==null?"미설정":"설정됨")+"\n완료 표시 영역: "+(template==null?"미설정":"설정됨");}
    public synchronized void save(Context c){
        try{
            JSONObject o=new JSONObject().put("version",3).put("width",width).put("height",height).put("rotation",rotation).put("package",targetPackage).put("repeats",repeats).put("testDelay",testDelay).put("randomTest",randomTest).put("autoVerified",autoVerified).put("setupDone",setupDone).put("panelSize",panelSize);
            if(a!=null)o.put("a",new JSONArray(new int[]{a.x,a.y}));if(b!=null)o.put("b",new JSONArray(new int[]{b.x,b.y}));
            if(roi!=null && template!=null){o.put("roi",new JSONArray(new int[]{roi.left,roi.top,roi.right,roi.bottom}));JSONArray data=new JSONArray();for(float v:template)data.put(v);o.put("template",data);}
            android.util.AtomicFile f=new android.util.AtomicFile(new File(c.getFilesDir(),"profile.json"));FileOutputStream out=null;
            try{out=f.startWrite();out.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception ex){if(out!=null)f.failWrite(out);throw ex;}
        }catch(Exception ex){AppState.log("설정 저장 실패: "+ex.getMessage());}
    }
    public synchronized void load(Context c){
        File f=new File(c.getFilesDir(),"profile.json");if(!f.exists())return;
        try(FileInputStream in=new FileInputStream(f)){
            JSONObject o=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            if(o.optInt("version")<2 || o.optInt("version")>3){invalidate();return;}
            width=o.getInt("width");height=o.getInt("height");rotation=o.getInt("rotation");targetPackage=o.optString("package");repeats=o.optInt("repeats",1);testDelay=o.optInt("testDelay",6100);randomTest=o.optBoolean("randomTest");
            JSONArray p=o.optJSONArray("a");if(p!=null)a=new Point(p.getInt(0),p.getInt(1));p=o.optJSONArray("b");if(p!=null)b=new Point(p.getInt(0),p.getInt(1));
            JSONArray r=o.optJSONArray("roi"),data=o.optJSONArray("template");
            if(r!=null && data!=null && data.length()==SAMPLE_W*SAMPLE_H){roi=new Rect(r.getInt(0),r.getInt(1),r.getInt(2),r.getInt(3));template=new float[data.length()];for(int i=0;i<template.length;i++)template[i]=(float)data.getDouble(i);}
            autoVerified=o.optBoolean("autoVerified");setupDone=o.optBoolean("setupDone",positionsReady() && imageReady());panelSize=Math.max(0,Math.min(3,o.optInt("panelSize",2)));
        }catch(Exception ex){invalidate();AppState.log("설정을 다시 지정하세요");}
    }
}
