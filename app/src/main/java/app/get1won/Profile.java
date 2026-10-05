package app.get1won;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class Profile {
    public static final int SAMPLE_W=160,SAMPLE_H=48;
    public Point a,b;
    public int width,height,rotation;
    public String targetPackage="";
    public int repeats=1,timeoutSeconds=15,testDelay=3000;
    public final Map<String,Template> templates=new HashMap<>();
    public record Template(Rect rect,float[] pixels) {}
    public synchronized boolean ready(){return a!=null && b!=null && !targetPackage.isEmpty() && templates.keySet().containsAll(List.of("reward","waiting","home","screenB"));}
    public static float[] sample(Image image,Rect r) {
        if(r.left<0 || r.top<0 || r.right>image.getWidth() || r.bottom>image.getHeight() || r.width()<16 || r.height()<12)
            throw new IllegalArgumentException("ROI가 화면 밖이거나 너무 작습니다");
        Image.Plane p=image.getPlanes()[0]; ByteBuffer buf=p.getBuffer();float[] out=new float[SAMPLE_W*SAMPLE_H];
        for(int y=0;y<SAMPLE_H;y++) for(int x=0;x<SAMPLE_W;x++) {
            int sx=r.left+(int)((x+0.5)*r.width()/SAMPLE_W),sy=r.top+(int)((y+0.5)*r.height()/SAMPLE_H);
            int index=sy*p.getRowStride()+sx*p.getPixelStride();
            out[y*SAMPLE_W+x]=((buf.get(index)&255)*0.299f+(buf.get(index+1)&255)*0.587f+(buf.get(index+2)&255)*0.114f)/255f;
        } return out;
    }
    public synchronized String summary() { return "1번 위치: "+(a==null?"미설정":a)+"\n3번 위치: "+(b==null?"미설정":b)+"\n등록 영역: "+templates.keySet()+"\n대상: "+targetPackage; }
    public synchronized void save(Context c) {
        try {
            JSONObject o=new JSONObject();o.put("width",width).put("height",height).put("rotation",rotation).put("package",targetPackage).put("repeats",repeats).put("timeout",timeoutSeconds).put("testDelay",testDelay);
            if(a!=null)o.put("a",new JSONArray(new int[]{a.x,a.y}));if(b!=null)o.put("b",new JSONArray(new int[]{b.x,b.y}));
            JSONObject ts=new JSONObject();for(var e:templates.entrySet()) {
                Rect r=e.getValue().rect;JSONArray data=new JSONArray();for(float f:e.getValue().pixels)data.put(f);
                ts.put(e.getKey(),new JSONObject().put("rect",new JSONArray(new int[]{r.left,r.top,r.right,r.bottom})).put("pixels",data));
            }o.put("templates",ts);
            android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getFilesDir(),"profile.json"));
            FileOutputStream stream=null;try{stream=file.startWrite();stream.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(stream);}catch(Exception ex){if(stream!=null)file.failWrite(stream);throw ex;}
        }catch(Exception e){AppState.log("설정 저장 실패: "+e.getMessage());}
    }
    public synchronized void load(Context c) {
        File f=new File(c.getFilesDir(),"profile.json");if(!f.exists())return;
        try(FileInputStream in=new FileInputStream(f)) {
            JSONObject o=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            width=o.getInt("width");height=o.getInt("height");rotation=o.optInt("rotation");targetPackage=o.getString("package");repeats=o.getInt("repeats");timeoutSeconds=o.getInt("timeout");testDelay=o.optInt("testDelay",3000);
            JSONArray p=o.optJSONArray("a");if(p!=null)a=new Point(p.getInt(0),p.getInt(1));p=o.optJSONArray("b");if(p!=null)b=new Point(p.getInt(0),p.getInt(1));
            JSONObject ts=o.getJSONObject("templates");for(Iterator<String> it=ts.keys();it.hasNext();) {
                String key=it.next();JSONObject t=ts.getJSONObject(key);JSONArray r=t.getJSONArray("rect"),data=t.getJSONArray("pixels");
                if(data.length()!=SAMPLE_W*SAMPLE_H)continue;float[] values=new float[data.length()];for(int i=0;i<values.length;i++)values[i]=(float)data.getDouble(i);
                templates.put(key,new Template(new Rect(r.getInt(0),r.getInt(1),r.getInt(2),r.getInt(3)),values));
            }
        }catch(Exception ex){templates.clear();AppState.log("설정을 다시 등록하세요: "+ex.getMessage());}
    }
}
