package com.example.photoguide

import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class GuideService : Service() {
    private lateinit var windows: WindowManager
    private var panel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var tip: TextView? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var detector: PoseDetector? = null
    private lateinit var reference: Array<FloatArray>
    @Volatile private var processing = false
    @Volatile private var closing = false
    private var lastFrame = 0L
    private var blackFrames = 0
    private var width = 0; private var height = 0
    private val main = Handler(Looper.getMainLooper())
    private fun dp(x: Int) = (resources.displayMetrics.density*x+.5f).toInt()
    override fun onCreate() { super.onCreate(); windows = getSystemService(WINDOW_SERVICE) as WindowManager; (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("guide", "拍摄指导", NotificationManager.IMPORTANCE_LOW)) }
    @Suppress("DEPRECATION") override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == STOP) { stopSelf(); return START_NOT_STICKY }
        try {
            val stop = PendingIntent.getService(this, 1, Intent(this, GuideService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, "guide").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("拍摄指导进行中").setContentText("屏幕内容正在用于端侧姿势分析").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "结束", stop).build()).build()
            startForeground(1, notification)
            check(Settings.canDrawOverlays(this)) { "悬浮窗权限未开启" }
            reference = PoseTools.read(this); showPanel()
            detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
            val workerThread = HandlerThread("screen-analysis").also { it.start(); thread = it }; val worker = Handler(workerThread.looper)
            val data = intent.getParcelableExtra<Intent>("data") ?: error("屏幕录制授权无效")
            projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(intent.getIntExtra("result", Activity.RESULT_CANCELED), data) ?: error("屏幕录制授权无效")
            projection!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, main)
            val metrics = DisplayMetrics(); windows.defaultDisplay.getRealMetrics(metrics)
            width = 480; height = maxOf(1, metrics.heightPixels * width / metrics.widthPixels)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { it.setOnImageAvailableListener({ source -> frame(source) }, worker) }
            display = projection!!.createVirtualDisplay("姿势指导", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, worker)
        } catch (e: Exception) { Toast.makeText(this, "无法开启指导：${e.message}", Toast.LENGTH_LONG).show(); stopSelf() }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION") private fun frame(source: ImageReader) {
        var bitmap: Bitmap? = null
        try {
            val image = source.acquireLatestImage() ?: return
            image.use {
                if (processing || closing || SystemClock.elapsedRealtime()-lastFrame < 700) return
                lastFrame = SystemClock.elapsedRealtime()
                val plane = it.planes[0]; val stride = plane.rowStride/plane.pixelStride
                val padded = Bitmap.createBitmap(stride, height, Bitmap.Config.ARGB_8888); padded.copyPixelsFromBuffer(plane.buffer)
                bitmap = Bitmap.createBitmap(padded, 0, 0, width, height); if (padded !== bitmap) padded.recycle()
            }
            val b = bitmap ?: return
            // Ignore the overlay before assessing the screen or running pose detection.
            val m = DisplayMetrics(); windows.defaultDisplay.getRealMetrics(m)
            val panelView = panel; val layout = params
            if (panelView != null && layout != null) Canvas(b).drawRect(layout.x.toFloat()*width/m.widthPixels, layout.y.toFloat()*height/m.heightPixels, (layout.x+panelView.width).toFloat()*width/m.widthPixels, (layout.y+panelView.height).toFloat()*height/m.heightPixels, Paint().apply { color = Color.BLACK })
            // Only report sustained, nearly entirely black captures; camera UI may prevent detection.
            var dark = 0; var count = 0
            for (y in 0 until height step 12) for (x in 0 until width step 12) { val c = b.getPixel(x,y); if (Color.red(c) < 12 && Color.green(c) < 12 && Color.blue(c) < 12) dark++; count++ }
            blackFrames = if (dark > count * .98) blackFrames + 1 else 0
            if (blackFrames >= 3) { main.post { if (!closing) tip?.text = "系统相机禁止录屏，实时纠正不可用，仅显示参考图" }; b.recycle(); return }
            processing = true
            detector!!.process(InputImage.fromBitmap(b, 0)).addOnSuccessListener { p -> if (!closing) tip?.text = PoseTools.advice(reference, PoseTools.points(p,b.width,b.height)) }.addOnFailureListener { if (!closing) tip?.text = "画面暂时无法分析，请检查录屏和相机画面" }.addOnCompleteListener { b.recycle(); processing = false }
        } catch (_: Exception) { bitmap?.recycle(); processing = false }
    }
    private fun showPanel() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(6),dp(12),dp(12)); background = GradientDrawable().apply { setColor(0xfff7faf7.toInt()); cornerRadius = dp(20).toFloat() }; elevation = dp(8).toFloat() }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val drag = TextView(this).apply { text = "⋮⋮  拖动"; setTextColor(0xff51665b.toInt()); textSize = 13f; setPadding(0,dp(10),0,dp(10)) }
        header.addView(drag, LinearLayout.LayoutParams(0,-2,1f))
        val close = TextView(this).apply { text = "关闭 ✕"; contentDescription = "关闭拍摄指导"; setTextColor(0xff315f50.toInt()); textSize = 14f; setPadding(dp(8),dp(10),0,dp(10)); setOnClickListener { stopSelf() } }
        header.addView(close); box.addView(header)
        box.addView(ReferenceView(this,reference).apply { background = GradientDrawable().apply { setColor(0xffe5ede7.toInt()); cornerRadius = dp(12).toFloat() }; clipToOutline = true }, LinearLayout.LayoutParams(-1,dp(180)))
        tip = TextView(this).apply { text = "正在寻找人物…"; textSize = 13f; setTextColor(0xff243d32.toInt()); setPadding(0,dp(10),0,0); setLineSpacing(dp(3).toFloat(),1f) }; box.addView(tip)
        val layout = WindowManager.LayoutParams(dp(190),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(100) }; params = layout
        drag.setOnTouchListener(object : View.OnTouchListener { var x=0; var y=0; var dx=0f; var dy=0f
            override fun onTouch(v: View, e: MotionEvent): Boolean = when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=layout.x; y=layout.y; dx=e.rawX; dy=e.rawY; true }
                MotionEvent.ACTION_MOVE -> { val m=resources.displayMetrics; layout.x=(x+(e.rawX-dx).toInt()).coerceIn(0,maxOf(0,m.widthPixels-box.width)); layout.y=(y+(e.rawY-dy).toInt()).coerceIn(0,maxOf(0,m.heightPixels-box.height)); try { windows.updateViewLayout(box,layout) } catch (_: Exception) {}; true }
                MotionEvent.ACTION_UP -> { v.performClick(); true }; else -> false
            }
        })
        windows.addView(box,layout); panel=box
    }
    override fun onDestroy() { closing=true; display?.release(); projection?.stop(); reader?.close(); thread?.quitSafely(); detector?.close(); panel?.let { try { windows.removeView(it) } catch (_: Exception) {} }; panel=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val STOP = "com.example.photoguide.STOP" }
}
