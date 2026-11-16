package com.android.messaging.ui.conversation;
import android.graphics.*;import android.graphics.drawable.Drawable;
public final class MessageBubbleBackground extends Drawable{
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final float[] corners;
 public MessageBubbleBackground(int color,boolean incoming,float density){paint.setColor(color);float r=20*density,s=6*density;corners=new float[]{r,r,r,r,incoming?r:s,incoming?r:s,incoming?s:r,incoming?s:r};}
 protected void onBoundsChange(Rect bounds){path.reset();path.addRoundRect(new RectF(bounds),corners,Path.Direction.CW);}public void draw(Canvas canvas){canvas.drawPath(path,paint);}public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
