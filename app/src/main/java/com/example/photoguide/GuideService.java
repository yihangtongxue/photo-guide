package com.example.photoguide;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.nio.ByteBuffer;

public class GuideService extends Service {
    private static final String STOP="com.example.photoguide.STOP", CHANNEL="guide";
    private WindowManager windows; private View panel; private WindowManager.LayoutParams params; private TextView tip;
    private MediaProjection projection; private VirtualDisplay display; private ImageReader reader; private HandlerThread thread; private Handler worker;
    private PoseDetector detector; private float[][] reference; private volatile boolean processing, closing; private long lastFrame; private int width,height,density;
    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density*x+.5f); }
    @Override public void onCreate() { super.onCreate(); windows=(WindowManager)getSystemService(WINDOW_SERVICE); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"拍摄指导",NotificationManager.IMPORTANCE_LOW)); }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        try {
            Intent stop = new Intent(this,GuideService.class).setAction(STOP);
            PendingIntent stopIntent=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("拍摄指导进行中").setContentText("屏幕内容正在用于端侧姿势分析").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"结束",stopIntent).build()).build();
            startForeground(1,notification);
            if (!Settings.canDrawOverlays(this)) throw new IllegalStateException("悬浮窗权限未开启");
            reference=PoseTools.read(this); showPanel();
            detector=PoseDetection.getClient(new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());
            thread=new HandlerThread("screen-analysis"); thread.start(); worker=new Handler(thread.getLooper());
            Intent data=intent.getParcelableExtra("data");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),data);
            if (projection==null) throw new IllegalStateException("屏幕录制授权无效");
            projection.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { stopSelf(); } },new Handler(Looper.getMainLooper()));
            DisplayMetrics metrics=new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics); density=metrics.densityDpi;
            width=480; height=Math.max(1,metrics.heightPixels*width/metrics.widthPixels);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r -> frame(r),worker);
            display=projection.createVirtualDisplay("姿势指导",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
        } catch (Exception e) { Toast.makeText(this,"无法开启指导："+e.getMessage(),Toast.LENGTH_LONG).show(); stopSelf(); }
        return START_NOT_STICKY;
    }
    private void frame(ImageReader source) {
        Image image=null;
        try {
            image=source.acquireLatestImage(); if (image==null || processing || closing || SystemClock.elapsedRealtime()-lastFrame<700) return;
            lastFrame=SystemClock.elapsedRealtime(); Image.Plane plane=image.getPlanes()[0]; ByteBuffer buffer=plane.getBuffer(); int stride=plane.getRowStride()/plane.getPixelStride();
            Bitmap padded=Bitmap.createBitmap(stride,height,Bitmap.Config.ARGB_8888); padded.copyPixelsFromBuffer(buffer); Bitmap bitmap=Bitmap.createBitmap(padded,0,0,width,height); if (padded!=bitmap) padded.recycle();
            // Do not let the reference image in our own overlay be mistaken for the subject.
            if (panel != null && params != null) { DisplayMetrics m=new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(m); Canvas canvas=new Canvas(bitmap); Paint mask=new Paint(); mask.setColor(Color.BLACK); canvas.drawRect((float)params.x*width/m.widthPixels, (float)params.y*height/m.heightPixels, (float)(params.x+panel.getWidth())*width/m.widthPixels, (float)(params.y+panel.getHeight())*height/m.heightPixels, mask); }
            processing=true;
            detector.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(p -> {
                String message=PoseTools.advice(reference,PoseTools.points(p,bitmap.getWidth(),bitmap.getHeight()));
                if (!closing && tip!=null) tip.setText(message);
            }).addOnFailureListener(e -> { if (!closing && tip!=null) tip.setText("画面暂时无法分析，请检查录屏和相机画面"); }).addOnCompleteListener(t -> { bitmap.recycle(); processing=false; });
        } catch (Exception e) { processing=false; } finally { if (image!=null) image.close(); }
    }
    private void showPanel() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(dp(10),dp(6),dp(10),dp(8)); GradientDrawable bg=new GradientDrawable(); bg.setColor(0xeeffffff); bg.setCornerRadius(dp(16)); box.setBackground(bg); box.setElevation(dp(8));
        LinearLayout header=new LinearLayout(this); TextView drag=new TextView(this); drag.setText("⋮⋮  拖动"); drag.setTextColor(0xff415c50); drag.setTextSize(13); drag.setPadding(0,dp(8),0,dp(8)); header.addView(drag,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=new TextView(this); close.setText("关闭 ✕"); close.setTextColor(0xff415c50); close.setPadding(dp(8),dp(8),0,dp(8)); close.setOnClickListener(v -> stopSelf()); header.addView(close); box.addView(header);
        ReferenceView picture=new ReferenceView(this,reference); box.addView(picture,new LinearLayout.LayoutParams(-1,dp(180)));
        tip=new TextView(this); tip.setText("正在寻找人物…"); tip.setTextSize(13); tip.setTextColor(0xff243d32); tip.setPadding(0,dp(8),0,dp(3)); box.addView(tip);
        params=new WindowManager.LayoutParams(dp(178),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT); params.gravity=Gravity.TOP|Gravity.START; params.x=dp(12); params.y=dp(100);
        drag.setOnTouchListener(new View.OnTouchListener() { int x,y; float dx,dy; public boolean onTouch(View v,MotionEvent e) { switch(e.getActionMasked()) { case MotionEvent.ACTION_DOWN: x=params.x; y=params.y; dx=e.getRawX(); dy=e.getRawY(); return true; case MotionEvent.ACTION_MOVE: DisplayMetrics m=getResources().getDisplayMetrics(); params.x=Math.max(0,Math.min(m.widthPixels-box.getWidth(),x+(int)(e.getRawX()-dx))); params.y=Math.max(0,Math.min(m.heightPixels-box.getHeight(),y+(int)(e.getRawY()-dy))); try { windows.updateViewLayout(box,params); } catch (Exception ignored) {} return true; case MotionEvent.ACTION_UP: return true; default: return false; } } });
        windows.addView(box,params); panel=box;
    }
    @Override public void onDestroy() { closing=true; if (display!=null) display.release(); if (projection!=null) projection.stop(); if (reader!=null) reader.close(); if (thread!=null) thread.quitSafely(); if (detector!=null) detector.close(); if (panel!=null) { try { windows.removeView(panel); } catch (Exception ignored) {} panel=null; } super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
