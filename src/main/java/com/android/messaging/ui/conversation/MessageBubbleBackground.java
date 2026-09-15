package com.android.messaging.ui.conversation;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
public final class MessageBubbleBackground extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    public MessageBubbleBackground(int color, boolean incoming, float density) {
        paint.setColor(color); radius = 20 * density;
    }
    @Override public void draw(Canvas canvas) { canvas.drawRoundRect(new RectF(getBounds()), radius, radius, paint); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
