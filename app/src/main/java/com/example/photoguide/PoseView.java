package com.example.photoguide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Lightweight built-in pose diagrams, scaled to the available space. */
public class PoseView extends View {
    private final Paint paint = new Paint(3);
    private int pose;
    public PoseView(Context context) { super(context); }
    public void setPose(int pose) { this.pose = Math.max(0, Math.min(pose, 3)); invalidate(); }
    private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, paint); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 120f, getHeight() / 150f);
        paint.setColor(0xff547a74); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(60, 25, 13, paint);
        line(canvas, 60, 38, 60, 84);
        if (pose == 1) {
            line(canvas, 60, 47, 42, 65); line(canvas, 42, 65, 48, 83);
            line(canvas, 60, 47, 74, 65); line(canvas, 74, 65, 70, 83);
            line(canvas, 60, 84, 49, 110); line(canvas, 49, 110, 47, 138);
            line(canvas, 60, 84, 69, 109); line(canvas, 69, 109, 78, 138);
        } else if (pose == 2) {
            line(canvas, 60, 47, 44, 67); line(canvas, 44, 67, 53, 83);
            line(canvas, 60, 47, 77, 67); line(canvas, 77, 67, 72, 83);
            line(canvas, 60, 84, 82, 100); line(canvas, 82, 100, 80, 138);
            line(canvas, 60, 84, 48, 100); line(canvas, 48, 100, 43, 138);
        } else {
            line(canvas, 60, 47, 42, 67); line(canvas, 42, 67, 40, 86);
            if (pose == 3) { line(canvas, 60, 47, 80, 62); line(canvas, 80, 62, 72, 34); }
            else { line(canvas, 60, 47, 78, 67); line(canvas, 78, 67, 80, 86); }
            line(canvas, 60, 84, 48, 111); line(canvas, 48, 111, 46, 138);
            line(canvas, 60, 84, 70, 111); line(canvas, 70, 111, 77, 138);
        }
        canvas.restore();
    }
}
