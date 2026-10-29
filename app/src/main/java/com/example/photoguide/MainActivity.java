package com.example.photoguide;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.io.File;

public class MainActivity extends Activity {
    private static final int PICK = 1, CAPTURE = 2, NOTIFY = 3;
    private ImageView preview; private TextView status; private Button start, choose; private boolean ready, busy, notificationAsked;
    private final PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build());
    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density*x+.5f); }
    private TextView text(String s, int size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(Color.rgb(248,249,247)); getWindow().setNavigationBarColor(Color.rgb(248,249,247)); getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this); LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(24),dp(32),dp(24),dp(32)); root.setBackgroundColor(0xfff8f9f7); scroll.addView(root); setContentView(scroll);
        TextView title = text("姿势参考",30,0xff19231f); title.setTypeface(null,1); root.addView(title);
        TextView desc = text("导入喜欢的照片，带着参考打开相机。",15,0xff65716b); desc.setPadding(0,dp(8),0,dp(24)); root.addView(desc);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setBackground(background(0xffe9eeeb,20)); root.addView(preview,new LinearLayout.LayoutParams(-1,dp(290)));
        status = text("选择一张人物照片开始",15,0xff53635b); status.setGravity(Gravity.CENTER); status.setPadding(0,dp(14),0,dp(18)); root.addView(status);
        choose = new Button(this); choose.setText("选择参考图"); choose.setAllCaps(false); root.addView(choose,new LinearLayout.LayoutParams(-1,dp(54)));
        start = new Button(this); start.setText("开启拍摄指导"); start.setAllCaps(false); start.setTextColor(Color.WHITE); start.setBackground(background(0xff315f50,16)); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,dp(54)); sp.topMargin=dp(12); root.addView(start,sp);
        TextView hint = text("也可从其他应用的分享菜单发送图片到这里。开启时需允许悬浮窗和屏幕录制；拍照仍由系统相机完成。",14,0xff65716b); hint.setLineSpacing(dp(4),1); hint.setPadding(0,dp(22),0,dp(10)); root.addView(hint);
        Button stop = new Button(this); stop.setText("结束拍摄指导"); root.addView(stop);
        choose.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE); try { startActivityForResult(i,PICK); } catch (ActivityNotFoundException e) { status.setText("无法打开图片选择器"); } });
        start.setOnClickListener(v -> startGuide()); stop.setOnClickListener(v -> { stopService(new Intent(this,GuideService.class)); status.setText(ready ? "指导已结束，参考图已保存" : "选择一张人物照片开始"); });
        try { PoseTools.read(this); Bitmap b = BitmapFactory.decodeFile(new File(getFilesDir(),"reference.jpg").getPath()); if (b != null) { preview.setImageBitmap(b); ready=true; status.setText("参考图已准备好"); } } catch (Exception ignored) {} update();
        if (state == null) receive(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); receive(intent); }
    private void receive(Intent i) { if (i != null && Intent.ACTION_SEND.equals(i.getAction()) && i.getType()!=null && i.getType().startsWith("image/")) { Uri uri = i.getParcelableExtra(Intent.EXTRA_STREAM); if (uri != null) analyze(uri); else status.setText("无法读取分享的图片"); } }
    private void update() { start.setEnabled(ready && !busy); choose.setEnabled(!busy); start.setAlpha(start.isEnabled()?1f:.45f); }
    private void analyze(Uri uri) {
        busy=true; update(); status.setText("正在分析人物姿势…");
        new Thread(() -> {
            try {
                Bitmap b = PoseTools.load(this,uri);
                detector.process(InputImage.fromBitmap(b,0)).addOnSuccessListener(pose -> {
                    float[][] points = PoseTools.points(pose,b.getWidth(),b.getHeight());
                    if (!PoseTools.usable(points)) { busy=false; status.setText("未识别到清晰的躯干，请换一张人物照片"); update(); return; }
                    try { PoseTools.save(this,b,points); preview.setImageBitmap(b); ready=true; status.setText("分析完成，可以开启拍摄指导"); }
                    catch (Exception e) { ready=false; status.setText("保存参考图失败，请重试"); }
                    busy=false; update();
                }).addOnFailureListener(e -> { busy=false; status.setText("分析失败，请换一张图片重试"); update(); });
            } catch (Exception e) { runOnUiThread(() -> { busy=false; status.setText("无法读取图片，请换一张重试"); update(); }); }
        }).start();
    }
    private void startGuide() {
        if (!ready || busy) return;
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this,"请允许显示在其他应用上层，返回后再次点击开启",Toast.LENGTH_LONG).show(); try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); } catch (ActivityNotFoundException e) { Toast.makeText(this,"请在设置中允许悬浮窗",Toast.LENGTH_LONG).show(); } return; }
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY); return; }
        requestCapture();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) { super.onRequestPermissionsResult(requestCode,permissions,results); if (requestCode==NOTIFY) { notificationAsked=true; if (results.length==0 || results[0]!=PackageManager.PERMISSION_GRANTED) Toast.makeText(this,"通知未开启，可在悬浮窗内关闭指导",Toast.LENGTH_LONG).show(); requestCapture(); } }
    private void requestCapture() { try { startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),CAPTURE); } catch (RuntimeException e) { status.setText("无法请求屏幕录制授权"); } }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==PICK && resultCode==RESULT_OK && data!=null && data.getData()!=null) analyze(data.getData());
        if (requestCode==CAPTURE) {
            if (resultCode!=RESULT_OK || data==null) { status.setText("未授权屏幕录制，指导未开启"); return; }
            try {
                stopService(new Intent(this,GuideService.class));
                Intent service = new Intent(this,GuideService.class).putExtra("result",resultCode).putExtra("data",data); startForegroundService(service);
                try { startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); } catch (ActivityNotFoundException e) { Toast.makeText(this,"请手动打开系统相机",Toast.LENGTH_LONG).show(); }
            } catch (RuntimeException e) { status.setText("无法启动指导，请检查系统权限"); }
        }
    }
    @Override protected void onDestroy() { detector.close(); super.onDestroy(); }
}
