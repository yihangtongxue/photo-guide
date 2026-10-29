package com.example.photoguide;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class GuideService extends Service {
    private static final String CHANNEL = "photo_guide";
    private static final String STOP = "com.example.photoguide.STOP";
    private WindowManager windows;
    private View panel;
    private WindowManager.LayoutParams params;
    private PoseView diagram;
    private TextView title, tip;
    private int pose, scene;
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate() {
        super.onCreate(); windows = (WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "拍摄指导", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        pose = Math.max(0, Math.min(3, intent == null ? 0 : intent.getIntExtra("pose", 0)));
        scene = Math.max(0, Math.min(3, intent == null ? 0 : intent.getIntExtra("scene", 0)));
        Intent stop = new Intent(this, GuideService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("拍摄指导正在显示").setContentText("点击返回助手，或关闭悬浮窗")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "关闭", stopIntent).build()).build();
        startForeground(1, notification);
        try { if (panel == null) showPanel(); updateContent(); }
        catch (RuntimeException e) { Toast.makeText(this, "悬浮窗无法显示，请检查权限", Toast.LENGTH_LONG).show(); stopSelf(); }
        return START_NOT_STICKY;
    }
    private void showPanel() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(6), dp(10), dp(8)); box.setBackgroundColor(0xf2f5faf6);
        TextView drag = new TextView(this); drag.setText("☰ 拖动这里移动"); drag.setTextColor(0xff315d55); drag.setTextSize(14); drag.setPadding(0, dp(4), 0, dp(8)); box.addView(drag);
        title = new TextView(this); title.setTextColor(0xff193d36); title.setTextSize(15); box.addView(title);
        diagram = new PoseView(this); LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(dp(76), dp(95)); image.gravity = Gravity.CENTER_HORIZONTAL; box.addView(diagram, image);
        tip = new TextView(this); tip.setTextColor(0xff263d3a); tip.setTextSize(12); box.addView(tip);
        LinearLayout buttons = new LinearLayout(this);
        Button next = new Button(this); next.setText("换姿势"); next.setTextSize(12); next.setPadding(0, 0, 0, 0); buttons.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button close = new Button(this); close.setText("关闭"); close.setTextSize(12); close.setPadding(0, 0, 0, 0); buttons.addView(close, new LinearLayout.LayoutParams(0, dp(48), 1)); box.addView(buttons);
        next.setOnClickListener(v -> { pose = (pose + 1) % Content.POSES.length; updateContent(); }); close.setOnClickListener(v -> stopSelf());
        params = new WindowManager.LayoutParams(dp(210), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; params.x = dp(12); params.y = dp(100);
        drag.setOnTouchListener(new View.OnTouchListener() {
            int x, y; float downX, downY;
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN: x = params.x; y = params.y; downX = event.getRawX(); downY = event.getRawY(); return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                        int maxX = Math.max(0, metrics.widthPixels - panel.getWidth());
                        int maxY = Math.max(0, metrics.heightPixels - panel.getHeight());
                        params.x = Math.max(0, Math.min(maxX, x + (int)(event.getRawX() - downX)));
                        params.y = Math.max(0, Math.min(maxY, y + (int)(event.getRawY() - downY)));
                        windows.updateViewLayout(panel, params); return true;
                    case android.view.MotionEvent.ACTION_UP: return true;
                    default: return false;
                }
            }
        });
        windows.addView(box, params); panel = box;
    }
    private void updateContent() { title.setText(Content.POSES[pose] + " · " + Content.SCENES[scene]); diagram.setPose(pose); tip.setText(Content.TIPS[pose]); }
    @Override public void onDestroy() { if (panel != null) { windows.removeView(panel); panel = null; } super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
