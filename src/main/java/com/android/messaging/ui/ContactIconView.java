package com.android.messaging.ui;
import android.content.Context;import android.graphics.*;import android.util.AttributeSet;import androidx.appcompat.widget.AppCompatImageView;import com.android.messaging.util.AppPrefs;import com.android.messaging.util.UiUtil;
public class ContactIconView extends AppCompatImageView{
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private String initial="?";private int color;
 public ContactIconView(Context c){super(c);}public ContactIconView(Context c,AttributeSet a){super(c,a);}public ContactIconView(Context c,AttributeSet a,int style){super(c,a,style);}
 public void bind(String name,String address){String label=name==null?"":name.trim();initial=label.isEmpty()?"?":label.substring(0,label.offsetByCodePoints(0,1)).toUpperCase(java.util.Locale.getDefault());color=AppPrefs.conversationColor(getContext(),address);setContentDescription(null);invalidate();}
 protected void onDraw(Canvas canvas){float size=Math.min(getWidth(),getHeight()),left=(getWidth()-size)/2f,top=(getHeight()-size)/2f;paint.setColor(color);canvas.drawRoundRect(left,top,left+size,top+size,size*.34f,size*.34f,paint);paint.setColor(UiUtil.readable(color));paint.setTextSize(size*.42f);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(initial,getWidth()/2f,getHeight()/2f-(paint.ascent()+paint.descent())/2f,paint);}
}
