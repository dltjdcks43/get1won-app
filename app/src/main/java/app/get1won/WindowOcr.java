package app.get1won;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.*;

/** API 34 window capture + bundled Korean OCR. One image in flight, never saved or uploaded. */
public final class WindowOcr implements AutoCloseable {
    public static volatile boolean ready,preparationFailed;
    public static volatile String info="화면 확인이 꺼져 있어요.";
    public interface Result {
        void success(OcrTicket ticket,List<Semantic.Node> nodes,long timestamp);
        void failure(OcrTicket ticket,String stage);
    }
    private final AccessibilityService service;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"LocalKoreanOcr"));
    private final TextRecognizer recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    private volatile boolean closed;
    private boolean busy=true,disposed,capturing;
    public WindowOcr(AccessibilityService service) {
        this.service=service;ready=false;preparationFailed=false;info="한국어 인식 준비 중";
        worker.execute(()->{
            Bitmap warm=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            try {
                recognizer.process(InputImage.fromBitmap(warm,0)).addOnCompleteListener(worker,task->{
                    warm.recycle();main.post(()->{busy=false;if(closed){dispose();return;}ready=task.isSuccessful();preparationFailed=!ready;info=ready?"한국어 인식 준비됨":"한국어 인식 준비 실패";});
                });
            } catch(RuntimeException e) { warm.recycle();main.post(()->{busy=false;if(closed)dispose();else{preparationFailed=true;info="한국어 인식 준비 실패";}}); }
        });
    }
    public boolean busy() { return busy; }
    public boolean request(OcrTicket ticket,Result result) {
        if(closed || busy || !ready)return false;
        busy=true;capturing=true;info="window screenshot 요청";
        try {
            service.takeScreenshotOfWindow(ticket.window(),service.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
                @Override public void onFailure(int code) { finish(ticket,result,null,0,"screenshot 실패 code="+code); }
                @Override public void onSuccess(AccessibilityService.ScreenshotResult shot) {
                    capturing=false;
                    if(closed){shot.getHardwareBuffer().close();busy=false;dispose();return;}
                    AppState.log("screenshot 성공 window="+ticket.window());
                    worker.execute(()->process(ticket,result,shot));
                }
            });
        } catch(RuntimeException e) { finish(ticket,result,null,0,"screenshot 요청 실패"); }
        return true;
    }
    private void process(OcrTicket ticket,Result result,AccessibilityService.ScreenshotResult shot) {
        Bitmap hardware=null,image=null;
        try(HardwareBuffer buffer=shot.getHardwareBuffer()) {
            if(closed){finish(ticket,result,null,0,"중지됨");return;}
            // Window-local pixels map to the current window rectangle, not full-display coordinates.
            // A mismatch is not guessed; e.g. rotation/resize during capture requires a fresh request.
            if((long)buffer.getWidth()*buffer.getHeight()>16000000L){finish(ticket,result,null,0,"screenshot 이미지 처리 크기 제한");return;}
            if(buffer.getWidth()!=ticket.region().width() || buffer.getHeight()!=ticket.region().height()) {
                finish(ticket,result,null,0,"screenshot 크기와 window bounds 불일치: image="+buffer.getWidth()+"x"+buffer.getHeight()+" window="+ticket.region());return;
            }
            hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());
            if(hardware==null)throw new IllegalStateException("buffer");
            image=hardware.copy(Bitmap.Config.ARGB_8888,false);
            if(image==null)throw new IllegalStateException("bitmap");
            Bitmap input=image;
            recognizer.process(InputImage.fromBitmap(input,0)).addOnCompleteListener(worker,task->{
                try {
                    if(!task.isSuccessful()){finish(ticket,result,null,0,"OCR 실패");return;}
                    List<Semantic.Node> nodes=new ArrayList<>();
                    outer: for(Text.TextBlock block:task.getResult().getTextBlocks())for(Text.Line line:block.getLines()) {
                        Rect r=line.getBoundingBox();if(r==null || r.isEmpty())continue;
                        Semantic.Box b=new Semantic.Box(ticket.region().left()+r.left,ticket.region().top()+r.top,ticket.region().left()+r.right,ticket.region().top()+r.bottom);
                        if(ticket.region().contains(b))nodes.add(new Semantic.Node(nodes.size(),-1,line.getText(),b,false,true,"OCR"));
                        if(nodes.size()>=400)break outer;
                    }
                    finish(ticket,result,nodes,shot.getTimestamp(),null);
                } catch(RuntimeException e) { finish(ticket,result,null,0,"OCR 결과 처리 실패"); }
                finally { input.recycle(); }
            });
            image=null; // Completion listener owns the software bitmap until ML Kit is finished.
        } catch(RuntimeException e) { finish(ticket,result,null,0,"screenshot/OCR 변환 실패"); }
        finally { if(hardware!=null)hardware.recycle();if(image!=null)image.recycle(); }
    }
    private void finish(OcrTicket ticket,Result result,List<Semantic.Node> nodes,long time,String failure) {
        main.post(()->{
            busy=false;capturing=false;
            if(closed){dispose();return;}
            info=failure==null?"한국어 인식 완료":failure;
            if(failure==null)result.success(ticket,nodes,time);else result.failure(ticket,failure);
        });
    }
    @Override public void close() { closed=true;ready=false;info="화면 확인이 꺼져 있어요.";if(!busy || capturing)dispose(); }
    private void dispose() { if(disposed)return;disposed=true;worker.execute(recognizer::close);worker.shutdown(); }
}
