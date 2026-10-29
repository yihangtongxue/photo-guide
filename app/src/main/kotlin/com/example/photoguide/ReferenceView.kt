package com.example.photoguide

import android.content.Context
import android.graphics.*
import android.view.View
import java.io.File

class ReferenceView(context: Context, private val points: Array<FloatArray>) : View(context) {
    private val image = BitmapFactory.decodeFile(File(context.filesDir, "reference.jpg").path)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = image ?: return
        val scale = minOf(width.toFloat()/b.width, height.toFloat()/b.height); val w = b.width*scale; val h = b.height*scale; val x = (width-w)/2; val y = (height-h)/2
        paint.color = Color.WHITE; canvas.drawBitmap(b, null, RectF(x,y,x+w,y+h), paint)
        paint.color = 0xffbaffd9.toInt(); paint.strokeWidth = 3*resources.displayMetrics.density; paint.strokeCap = Paint.Cap.ROUND
        PoseTools.edges.forEach { (a,z) -> if (points[a][2] > .5f && points[z][2] > .5f) canvas.drawLine(x+points[a][0]*w,y+points[a][1]*h,x+points[z][0]*w,y+points[z][1]*h,paint) }
    }
}
