package com.example.photoguide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.pose.Pose
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs

object PoseTools {
    val ids = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
    val edges = arrayOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)
    fun load(c: Context, uri: Uri): Bitmap {
        val bytes = c.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(16384); var size = 0L
            while (true) { val n = input.read(buf); if (n == -1) break; size += n; if (size > 40L * 1024 * 1024) throw IOException("图片超过 40 MB"); out.write(buf, 0, n) }; out.toByteArray()
        } ?: throw IOException("无法读取图片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options()
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("图片格式不受支持")
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
    fun points(pose: Pose, width: Int, height: Int): Array<FloatArray> = Array(33) { FloatArray(3) }.also { p ->
        ids.forEach { id -> pose.getPoseLandmark(id)?.let { p[id][0] = it.position.x / width; p[id][1] = it.position.y / height; p[id][2] = it.inFrameLikelihood } }
    }
    fun usable(p: Array<FloatArray>) = listOf(11, 12, 23, 24).all { p[it][2] > .5f }
    fun save(c: Context, b: Bitmap, p: Array<FloatArray>) {
        c.openFileOutput("reference.jpg", Context.MODE_PRIVATE).use { if (!b.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException() }
        val a = JSONArray(); p.forEach { point -> val v = JSONArray(); point.forEach { v.put(it.toDouble()) }; a.put(v) }
        c.openFileOutput("pose.json", Context.MODE_PRIVATE).use { it.write(a.toString().toByteArray(Charsets.UTF_8)) }
    }
    fun read(c: Context): Array<FloatArray> {
        val a = JSONArray(c.openFileInput("pose.json").bufferedReader().use { it.readText() })
        return Array(33) { i -> FloatArray(3) { j -> a.getJSONArray(i).getDouble(j).toFloat() } }
    }
    fun advice(r: Array<FloatArray>, p: Array<FloatArray>): String {
        if (!usable(p)) return "未识别到完整躯干，请让人物进入画面"
        val rcx = (r[11][0]+r[12][0]+r[23][0]+r[24][0])/4; val pcx = (p[11][0]+p[12][0]+p[23][0]+p[24][0])/4
        val rh = abs((r[23][1]+r[24][1]-r[11][1]-r[12][1])/2); val ph = abs((p[23][1]+p[24][1]-p[11][1]-p[12][1])/2)
        if (rh < .025f || ph < .025f) return "请让人物躯干清晰出现在画面中"
        for (side in 0..1) { val shoulder = 11 + side; val wrist = 15 + side
            if (r[wrist][2] > .6f && p[wrist][2] > .6f) {
                val delta = (p[wrist][1]-p[shoulder][1])/ph - (r[wrist][1]-r[shoulder][1])/rh
                if (abs(delta) > .45f) { val label = if (p[shoulder][0] < p[12-side][0]) "画面左侧的手" else "画面右侧的手"; return (if (delta > 0) "抬高" else "放低") + label }
            }
        }
        if (pcx - rcx > .18f) return "人物尝试向画面左侧移动"
        if (pcx - rcx < -.18f) return "人物尝试向画面右侧移动"
        if (ph < rh * .65f) return "人物尝试靠近镜头"
        if (ph > rh * 1.5f) return "人物尝试远离镜头"
        return "姿势接近参考，微调后拍摄"
    }
}
