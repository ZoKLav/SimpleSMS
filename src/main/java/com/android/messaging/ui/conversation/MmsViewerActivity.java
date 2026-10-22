package com.android.messaging.ui.conversation;

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
        new Thread(()->{try{SmsRepository.Message m=SmsRepository.message(this,id);if(m==null||!m.mms)throw new IllegalStateException("Message no longer available");runOnUiThread(()->{if(!active(token))return;content.removeAllViews();if(m.body!=null&&!m.body.isEmpty())label(m.body);if(m.parts.isEmpty()&&(m.body==null||m.body.isEmpty()))label("This MMS has no displayable content.");for(SmsRepository.Part part:m.parts){Button b=new Button(this);b.setText((part.name==null||part.name.isEmpty()?"Attachment":part.name)+" · "+part.mime);b.setOnClickListener(v->open(part));content.addView(b);}if(m.parts.size()==1)open(m.parts.get(0));});}catch(Exception e){runOnUiThread(()->{if(active(token)){content.removeAllViews();label("Cannot display MMS: "+e.getMessage());}});}},"mms-view-load").start();
    }
    private boolean active(int token){return token==generation&&!stopped&&!isFinishing()&&Vault.unlocked();}
    private LinearLayout display;
    private void open(SmsRepository.Part part){
        release();if(display!=null)content.removeView(display);display=new LinearLayout(this);display.setOrientation(LinearLayout.VERTICAL);content.addView(display);
        TextView loading=new TextView(this);loading.setText("Loading attachment…");display.addView(loading);
        final int token=++generation;
        new Thread(()->{byte[] bytes=null;try{
            bytes=SharedMessages.attachment(this,part);final byte[] loaded=bytes;bytes=null;
            runOnUiThread(()->{if(!active(token)){Arrays.fill(loaded,(byte)0);return;}display.removeAllViews();show(part.mime,loaded);});
        }catch(Exception e){if(bytes!=null)Arrays.fill(bytes,(byte)0);runOnUiThread(()->{if(active(token)){display.removeAllViews();error("Cannot read attachment: "+e.getMessage());}});}},"mms-view-attachment").start();
    }
    private void show(String mime,byte[] bytes){
        try{
            if(mime.startsWith("image/")){
                imageView=new ImageView(this);imageView.setAdjustViewBounds(true);imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);display.addView(imageView,new LinearLayout.LayoutParams(-1,-2));
                if(Build.VERSION.SDK_INT>=28){
                    image=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),(decoder,info,src)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float ratio=Math.min(1f,2048f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
                    // Animated drawables may continue reading their source until stopped.
                    imageBytes=bytes;imageView.setImageDrawable(image);if(image instanceof Animatable)((Animatable)image).start();
                }else{
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2048||options.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);Arrays.fill(bytes,(byte)0);if(bitmap==null)throw new IllegalArgumentException("Unsupported image format");imageView.setImageBitmap(bitmap);
                }
            }else if(mime.startsWith("audio/")||mime.startsWith("video/")){
                source=new MemoryMedia(bytes);player=new MediaPlayer();final MediaPlayer media=player;player.setDataSource(source);player.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
                if(mime.startsWith("video/")){SurfaceView surface=new SurfaceView(this);display.addView(surface,new LinearLayout.LayoutParams(-1,UiUtil.dp(this,240)));surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){if(player==media)media.setDisplay(h);}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){if(player==media){if(prepared)media.pause();media.setDisplay(null);}}});}
                Button play=new Button(this);play.setText("Play");play.setEnabled(false);display.addView(play);play.setOnClickListener(v->{if(player==null||!prepared)return;if(player.isPlaying()){player.pause();play.setText("Play");}else{player.start();play.setText("Pause");}});
                seek=new SeekBar(this);display.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&player!=null&&prepared)player.seekTo(value);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
                player.setOnPreparedListener(p->{if(player!=p)return;prepared=true;play.setEnabled(true);seek.setMax(Math.max(1,p.getDuration()));handler.post(progress);});
                player.setOnCompletionListener(p->{if(player==p)play.setText("Play");});player.setOnErrorListener((p,what,extra)->{if(player==p){release();error("This device cannot play this media format.");}return true;});player.prepareAsync();
            }else if(mime.startsWith("text/")){
                TextView text=new TextView(this);text.setText(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));text.setSaveEnabled(false);display.addView(text);Arrays.fill(bytes,(byte)0);
            }else{Arrays.fill(bytes,(byte)0);error("This attachment format is not supported by the built-in viewer: "+mime);}
        }catch(Exception|OutOfMemoryError e){Arrays.fill(bytes,(byte)0);release();error("Cannot display this attachment on this device.");}
    }
    private byte[] imageBytes;private Bitmap bitmap;
    private void label(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(17);v.setSaveEnabled(false);v.setPadding(0,8,0,16);content.addView(v);}
    private void error(String text){TextView v=new TextView(this);v.setText(text);display.addView(v);}
    private void release(){handler.removeCallbacks(progress);prepared=false;if(player!=null){player.release();player=null;}if(source!=null){source.close();source=null;}if(image instanceof Animatable)((Animatable)image).stop();if(imageView!=null)imageView.setImageDrawable(null);image=null;imageView=null;if(bitmap!=null){bitmap.recycle();bitmap=null;}if(imageBytes!=null){Arrays.fill(imageBytes,(byte)0);imageBytes=null;}seek=null;}
    @Override protected void onStop(){stopped=true;++generation;release();content.removeAllViews();display=null;super.onStop();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
}
