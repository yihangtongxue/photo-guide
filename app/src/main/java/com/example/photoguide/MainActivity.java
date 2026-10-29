package com.example.photoguide;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    private Spinner scene, pose;
    private TextView plan, poseTip;
    private PoseView diagram;
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xff263d3a); return t; }
    private void add(LinearLayout layout, android.view.View view) { layout.addView(view, new LinearLayout.LayoutParams(-1, -2)); }
    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter); return s;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(28), dp(24), dp(32)); root.setBackgroundColor(0xfff5f8f5); scroll.addView(root); setContentView(scroll);
        TextView title = text("拍照小助手", 28); title.setTypeface(null, 1); add(root, title);
        TextView intro = text("先选场景和姿势，带着参考打开系统相机。", 15); intro.setPadding(0, dp(6), 0, dp(20)); add(root, intro);
        add(root, text("1  拍前规划", 20)); scene = spinner(Content.SCENES); add(root, scene);
        plan = text("", 16); plan.setLineSpacing(dp(4), 1); plan.setPadding(0, dp(10), 0, dp(24)); add(root, plan);
        add(root, text("2  姿势参考", 20)); pose = spinner(Content.POSES); add(root, pose);
        diagram = new PoseView(this); LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(120), dp(150)); imageParams.gravity = Gravity.CENTER_HORIZONTAL; root.addView(diagram, imageParams);
        poseTip = text("", 16); poseTip.setPadding(0, dp(8), 0, dp(20)); add(root, poseTip);
        Button start = new Button(this); start.setText("开启悬浮窗并打开相机"); add(root, start);
        Button stop = new Button(this); stop.setText("关闭悬浮窗"); add(root, stop);
        TextView note = text("悬浮窗可拖动，点击“换姿势”切换参考；拍摄与照片保存由系统相机完成。", 14); note.setPadding(0, dp(10), 0, 0); add(root, note);
        scene.setOnItemSelectedListener(listener(() -> plan.setText(Content.PLANS[scene.getSelectedItemPosition()])));
        pose.setOnItemSelectedListener(listener(() -> { int i = pose.getSelectedItemPosition(); diagram.setPose(i); poseTip.setText(Content.TIPS[i]); }));
        start.setOnClickListener(v -> startGuide());
        stop.setOnClickListener(v -> { stopService(new Intent(this, GuideService.class)); Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show(); });
    }
    private android.widget.AdapterView.OnItemSelectedListener listener(Runnable action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { action.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
    }
    private void startGuide() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请允许悬浮窗，返回后再次点击开启", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "请在系统设置中允许本应用显示悬浮窗", Toast.LENGTH_LONG).show(); }
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1); return;
        }
        launchGuide();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == 1) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) launchGuide();
            else Toast.makeText(this, "请在系统设置中允许通知，以便随时关闭悬浮窗", Toast.LENGTH_LONG).show();
        }
    }
    private void launchGuide() {
        if (!Settings.canDrawOverlays(this)) { startGuide(); return; }
        Intent service = new Intent(this, GuideService.class).putExtra("pose", pose.getSelectedItemPosition()).putExtra("scene", scene.getSelectedItemPosition());
        try {
            startForegroundService(service);
            Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            startActivity(camera);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未找到系统相机，请手动打开相机", Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            stopService(service); Toast.makeText(this, "无法启动悬浮窗，请检查系统权限后重试", Toast.LENGTH_LONG).show();
        }
    }
}
