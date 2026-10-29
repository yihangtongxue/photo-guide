package com.example.photoguide

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.File

class MainActivity : ComponentActivity() {
    private val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
    private var image by mutableStateOf<Bitmap?>(null)
    private var points by mutableStateOf<Array<FloatArray>?>(null)
    private var status by mutableStateOf("选择一张人物照片开始")
    private var busy by mutableStateOf(false)
    private var notificationAsked = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try { points = PoseTools.read(this); image = BitmapFactory.decodeFile(File(filesDir, "reference.jpg").path); if (image != null) status = "参考图已准备好" else points = null } catch (_: Exception) { }
        setContent {
            val context = LocalContext.current
            val colors = if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("photo-guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("带着喜欢的姿势，轻松拍出自己的照片。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Card(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val b = image; val p = points
                                if (b != null && p != null) ReferenceImage(b, p, Modifier.fillMaxSize())
                                else Text("在这里预览参考图与姿势", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { startGuide() }, enabled = image != null && points != null && !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("开始拍摄指导") }
                        OutlinedButton(onClick = { pick() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(if (image == null) "从相册导入参考图" else "更换参考图") }
                        Text("也可在小红书等应用的系统分享菜单中发送图片。开启指导时，需要悬浮窗与屏幕录制权限；拍照由系统相机完成。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state == null) receive(intent)
    }
    @Composable private fun ReferenceImage(b: Bitmap, p: Array<FloatArray>, modifier: Modifier = Modifier) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            Image(b.asImageBitmap(), contentDescription = "参考图及姿势骨架", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val scale = minOf(size.width / b.width, size.height / b.height); val w = b.width * scale; val h = b.height * scale
                val x = (size.width - w) / 2; val y = (size.height - h) / 2
                PoseTools.edges.forEach { (a, z) -> if (p[a][2] > .5f && p[z][2] > .5f) drawLine(Color(0xffbaffd9), Offset(x+p[a][0]*w,y+p[a][1]*h), Offset(x+p[z][0]*w,y+p[z][1]*h), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round) }
            }
        }
    }
    private fun pick() { try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 1) } catch (_: ActivityNotFoundException) { status = "无法打开图片选择器" } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Suppress("DEPRECATION") private fun receive(i: Intent?) { if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("image/") == true) { val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM); if (uri != null) analyze(uri) else status = "无法读取分享的图片" } }
    private fun analyze(uri: Uri) {
        busy = true; status = "正在分析人物姿势…"
        Thread {
            try {
                val b = PoseTools.load(this, uri)
                detector.process(InputImage.fromBitmap(b, 0)).addOnSuccessListener { pose ->
                    val p = PoseTools.points(pose, b.width, b.height)
                    if (!PoseTools.usable(p)) status = "未识别到清晰的躯干，请换一张人物照片"
                    else try { PoseTools.save(this, b, p); image = b; points = p; status = "分析完成，可以开始拍摄指导" } catch (_: Exception) { status = "保存参考图失败，请重试"; image = null; points = null }
                    busy = false
                }.addOnFailureListener { status = "分析失败，请换一张图片重试"; busy = false }
            } catch (_: Exception) { runOnUiThread { status = "无法读取图片，请换一张重试"; busy = false } }
        }.start()
    }
    private fun startGuide() {
        if (image == null || points == null || busy) return
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "请允许显示在其他应用上层，返回后再次点击开始", Toast.LENGTH_LONG).show(); try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: ActivityNotFoundException) { status = "请在设置中允许悬浮窗" }; return }
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3); return }
        requestCapture()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 3) { notificationAsked = true; if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "通知未开启，可在悬浮窗内关闭指导", Toast.LENGTH_LONG).show(); requestCapture() } }
    private fun requestCapture() { try { startActivityForResult((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), 2) } catch (_: RuntimeException) { status = "无法请求屏幕录制授权" } }
    @Deprecated("Activity result API") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) data?.data?.let { analyze(it) }
        if (requestCode == 2) {
            if (resultCode != RESULT_OK || data == null) { status = "未授权屏幕录制，指导未开启"; return }
            try { stopService(Intent(this, GuideService::class.java)); startForegroundService(Intent(this, GuideService::class.java).putExtra("result", resultCode).putExtra("data", data)); try { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } catch (_: ActivityNotFoundException) { Toast.makeText(this, "请手动打开系统相机", Toast.LENGTH_LONG).show() } } catch (_: RuntimeException) { status = "无法启动指导，请检查系统权限" }
        }
    }
    override fun onDestroy() { detector.close(); super.onDestroy() }
}
