package com.wtz.libvideomaker.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class TextUtils {

    public static Bitmap drawText(String text, float textSizePixels, int paddingLeft, int paddingRight,
                                  int paddingTop, int paddingBottom, int textColor, int bgColor) {
        Paint paint = new Paint();
        paint.setColor(textColor);
        paint.setTextSize(textSizePixels);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        float fontWidth = paint.measureText(text, 0, text.length());
        float fontTop = paint.getFontMetrics().top;
        float fontBottom = paint.getFontMetrics().bottom;

        int bmpWidth = (int) (fontWidth + paddingLeft + paddingRight);
        int bmpHeight = (int) ((fontBottom - fontTop) + paddingTop + paddingBottom);
        Bitmap bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        bitmap.eraseColor(Color.argb(0,0,0,0));

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bgColor);

        // 绘制文字
        // ----------top
        // ----------ascent
        // xxxxxxxxxx
        // ----------baseline
        // xxxxxxxxxx
        // ----------descent
        // ----------bottom
        // 因为 fontTop = fontTopY - baseline，绘制矩形的左上角顶点为(0, 0)
        // 所以 baseline = fontTopY - fontTop 也就是 baseline = 0 - fontTop
        canvas.drawText(text, paddingLeft, 0 - fontTop + paddingTop, paint);

        return bitmap;
    }

}
