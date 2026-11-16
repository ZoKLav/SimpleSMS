package com.android.messaging.ui.conversation;

import com.android.messaging.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import com.android.messaging.security.MemoryMedia;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.android.messaging.security.Vault;
import com.android.messaging.sms.SharedMessages;
import com.android.messaging.sms.SmsRepository;
import com.android.messaging.util.BaseActivity;
import com.android.messaging.util.UiUtil;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Explicitly opened MMS viewer. Decrypted attachments stay in RAM, never cache files. */
public final class MmsViewerActivity extends BaseActivity {
    private LinearLayout content;
    private MediaPlayer player;
    private MemoryMedia source;
    private Drawable image;
    private ImageView imageView;
    private SeekBar seek;
    private SurfaceHolder videoHolder;
    private SurfaceHolder.Callback videoCallback;
    private boolean prepared,stopped;
    private int generation;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable progress=new Runnable(){public void run(){if(player!=null&&prepared&&seek!=null){try{seek.setProgress(player.getCurrentPosition());}catch(IllegalStateException ignored){}handler.postDelayed(this,500);}}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setTitle("MMS viewer");
        if(getSupportActionBar()!=null)getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);int pad=UiUtil.dp(this,16);content.setPadding(pad,pad,pad,pad);scroll.addView(content);setContentView(scroll);
        label("Loading message…");
    }
    @Override protected void onResume(){super.onResume();if(!Vault.unlocked()||isFinishing())return;stopped=false;load();}
    private void load(){
        release();display=null;content.removeAllViews();label("Loading message…");
        final int token=++generation;long id=getIntent().getLongExtra("message_id",0);
        final android.content.Context app=getApplicationContext();
        com.android.messaging.util.UiWork.run(this,()->{SmsRepository.Message message=SmsRepository.message(app,id);if(message==null||!message.mms)throw new IllegalStateException("Message no longer available");return message;},(activity,message,error)->activity.loaded(token,message,error));
    }
    private void loaded(int token,SmsRepository.Message message,Exception error){
        if(!active(token))return;content.removeAllViews();if(error!=null){label("Cannot display MMS: "+error.getMessage());return;}
        if(message.body!=null&&!message.body.isEmpty())label(message.body);if(message.parts.isEmpty()&&(message.body==null||message.body.isEmpty()))label("This MMS has no displayable content.");
        for(SmsRepository.Part part:message.parts){Button button=new Button(this);button.setText(getString(com.android.messaging.R.string.attachment_label,part.name==null||part.name.isEmpty()?getString(com.android.messaging.R.string.attachment):part.name,part.mime));button.setOnClickListener(view->open(part));content.addView(button);}if(message.parts.size()==1)open(message.parts.get(0));
    }
    private boolean active(int token){return token==generation&&!stopped&&!isFinishing()&&Vault.unlocked();}
    private LinearLayout display;
    private void open(SmsRepository.Part part){
        release();if(display!=null)content.removeView(display);display=new LinearLayout(this);display.setOrientation(LinearLayout.VERTICAL);content.addView(display);
        TextView loading=new TextView(this);loading.setText(R.string.text_loading_attachment);display.addView(loading);
        final int token=++generation;
        final android.content.Context app=getApplicationContext();
        com.android.messaging.util.UiWork.run(this,com.android.messaging.util.Workers.READ,()->SharedMessages.attachment(app,part),(activity,bytes,error)->{
            if(!activity.active(token)){if(bytes!=null)Arrays.fill(bytes,(byte)0);return;}activity.display.removeAllViews();if(error!=null)activity.error("Cannot read attachment: "+error.getMessage());else activity.show(part.mime,bytes);
        },bytes->Arrays.fill(bytes,(byte)0));
    }
    private void show(String mime,byte[] bytes){
        try{
            if(mime.startsWith("image/")){
                final int token=generation;
                com.android.messaging.util.UiWork.run(this,com.android.messaging.util.Workers.READ,()->decodeImage(bytes),(activity,decoded,error)->{
                    if(!activity.active(token)){if(decoded!=null)decoded.close();return;}if(error!=null){activity.error("Cannot display this image on this device.");return;}
                    try{activity.showImage(decoded);}catch(Exception|OutOfMemoryError failure){decoded.close();activity.release();activity.error("Cannot display this image on this device.");}
                },DecodedImage::close);
            }else if(mime.startsWith("audio/")||mime.startsWith("video/")){
                source=new MemoryMedia(bytes);player=new MediaPlayer();final MediaPlayer media=player;player.setDataSource(source);player.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
                if(mime.startsWith("video/")){SurfaceView surface=new SurfaceView(this);display.addView(surface,new LinearLayout.LayoutParams(-1,UiUtil.dp(this,240)));videoHolder=surface.getHolder();videoCallback=new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){if(player==media)media.setDisplay(h);}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){if(player==media){if(prepared)media.pause();media.setDisplay(null);}}};videoHolder.addCallback(videoCallback);}
                Button play=new Button(this);play.setText(R.string.text_play);play.setEnabled(false);display.addView(play);play.setOnClickListener(v->{if(player==null||!prepared)return;if(player.isPlaying()){player.pause();play.setText(R.string.text_play);}else{player.start();play.setText(R.string.text_pause);}});
                seek=new SeekBar(this);display.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&player!=null&&prepared)player.seekTo(value);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
                player.setOnPreparedListener(p->{if(player!=p)return;prepared=true;play.setEnabled(true);seek.setMax(Math.max(1,p.getDuration()));handler.post(progress);});
                player.setOnCompletionListener(p->{if(player==p)play.setText(R.string.text_play);});player.setOnErrorListener((p,what,extra)->{if(player==p){release();error("This device cannot play this media format.");}return true;});player.prepareAsync();
            }else if(mime.startsWith("text/")){
                TextView text=new TextView(this);text.setText(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));text.setSaveEnabled(false);display.addView(text);Arrays.fill(bytes,(byte)0);
            }else{Arrays.fill(bytes,(byte)0);error("This attachment format is not supported by the built-in viewer: "+mime);}
        }catch(Exception|OutOfMemoryError e){Arrays.fill(bytes,(byte)0);release();error("Cannot display this attachment on this device.");}
    }
    private static final class DecodedImage{
        Drawable drawable;Bitmap bitmap;byte[] source;
        void close(){if(drawable instanceof Animatable)((Animatable)drawable).stop();if(drawable!=null)drawable.setCallback(null);if(bitmap!=null)bitmap.recycle();if(source!=null)Arrays.fill(source,(byte)0);drawable=null;bitmap=null;source=null;}
    }
    @androidx.annotation.WorkerThread private static DecodedImage decodeImage(byte[] bytes){
        try{DecodedImage decoded=new DecodedImage();if(Build.VERSION.SDK_INT>=28){decoded.drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),(decoder,info,src)->{int width=info.getSize().getWidth(),height=info.getSize().getHeight();float ratio=Math.min(1f,2048f/Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*ratio)),Math.max(1,(int)(height*ratio)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});decoded.source=bytes;}
        else{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2048||options.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;options.inJustDecodeBounds=false;decoded.bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);Arrays.fill(bytes,(byte)0);if(decoded.bitmap==null)throw new IllegalArgumentException("Unsupported image format");}return decoded;
        }catch(Exception|OutOfMemoryError failure){Arrays.fill(bytes,(byte)0);throw new IllegalArgumentException("Cannot decode image",failure);}
    }
    @androidx.annotation.MainThread private void showImage(DecodedImage decoded){
        imageView=new ImageView(this);imageView.setAdjustViewBounds(true);imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);display.addView(imageView,new LinearLayout.LayoutParams(-1,-2));image=decoded.drawable;bitmap=decoded.bitmap;imageBytes=decoded.source;decoded.drawable=null;decoded.bitmap=null;decoded.source=null;if(image!=null){imageView.setImageDrawable(image);if(image instanceof Animatable)((Animatable)image).start();}else imageView.setImageBitmap(bitmap);
    }
    private byte[] imageBytes;private Bitmap bitmap;
    private void label(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(17);v.setSaveEnabled(false);v.setPadding(0,8,0,16);content.addView(v);}
    private void error(String text){TextView v=new TextView(this);v.setText(text);display.addView(v);}
    private void release(){
        handler.removeCallbacks(progress);prepared=false;if(videoHolder!=null&&videoCallback!=null)videoHolder.removeCallback(videoCallback);videoHolder=null;videoCallback=null;MediaPlayer media=player;player=null;
        if(media!=null){try{media.setOnPreparedListener(null);media.setOnCompletionListener(null);media.setOnErrorListener(null);media.setDisplay(null);}catch(RuntimeException ignored){}try{media.release();}catch(RuntimeException ignored){}}
        if(source!=null){source.close();source=null;}if(image instanceof Animatable)try{((Animatable)image).stop();}catch(RuntimeException ignored){}if(imageView!=null)imageView.setImageDrawable(null);if(image!=null)image.setCallback(null);image=null;imageView=null;if(bitmap!=null){if(!bitmap.isRecycled())bitmap.recycle();bitmap=null;}if(imageBytes!=null){Arrays.fill(imageBytes,(byte)0);imageBytes=null;}seek=null;
    }
    @Override protected void onStop(){stopped=true;++generation;release();content.removeAllViews();display=null;super.onStop();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
}
