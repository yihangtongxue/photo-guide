package com.example.photoguide;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;

final class ReferenceView extends View {
    private final Bitmap image; private final float[][] p; private final Paint paint=new Paint(3);
    private static final int[][] EDGES={{11,12},{11,13},{13,15},{12,14},{14,16},{11,23},{12,24},{23,24},{23,25},{25,27},{24,26},{26,28}};
    ReferenceView(Context c,float[][] points) { super(c); p=points; image=BitmapFactory.decodeFile(new File(c.getFilesDir(),"reference.jpg").getPath()); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (image==null) return;
        float scale=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight()); float w=image.getWidth()*scale,h=image.getHeight()*scale,x=(getWidth()-w)/2,y=(getHeight()-h)/2;
        paint.setAlpha(255); canvas.drawBitmap(image,null,new RectF(x,y,x+w,y+h),paint);
        paint.setColor(0xddbaffd9); paint.setStrokeWidth(3*getResources().getDisplayMetrics().density); paint.setStrokeCap(Paint.Cap.ROUND);
        for (int[] edge:EDGES) if (p[edge[0]][2]>.5 && p[edge[1]][2]>.5) canvas.drawLine(x+p[edge[0]][0]*w,y+p[edge[0]][1]*h,x+p[edge[1]][0]*w,y+p[edge[1]][1]*h,paint);
    }
}
