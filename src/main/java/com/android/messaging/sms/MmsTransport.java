package com.android.messaging.sms;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.telephony.*;
import com.android.messaging.security.*;
import com.google.android.mms.pdu_alt.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class MmsTransport {
 public static final int MAX_BYTES=1024*1024;public static final String SEND="mms.send",DOWNLOAD="mms.download",CONTROL="mms.control";
 private MmsTransport(){}
 private static void supported(){if(Build.VERSION.SDK_INT<26)throw new IllegalStateException("Private MMS transport requires Android 8 or later");}
 public static byte[] readBounded(InputStream in,int max)throws IOException{if(in==null)throw new IOException("No data");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()+n>max)throw new IOException("Media exceeds the size limit");out.write(block,0,n);}return out.toByteArray();}
 static SmsManager manager(int sub){if(sub<0||sub==Integer.MAX_VALUE)sub=SubscriptionManager.getDefaultSmsSubscriptionId();if(sub<0||sub==Integer.MAX_VALUE)throw new IllegalStateException("Select a default SMS SIM in Android settings");return SmsManager.getSmsManagerForSubscriptionId(sub);}
 private static PendingIntent callback(Context c,String action,Uri transfer,long id){Intent i=new Intent(c,MmsResultReceiver.class).setAction(action).setData(transfer).putExtra("message",id);return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_ONE_SHOT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
 public static long send(Context c,String destination,String text,Uri media)throws Exception{
  supported();Vault.require();if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as the default SMS app first");long thread=Vault.thread(destination);int sub=SubscriptionManager.getDefaultSmsSubscriptionId();SmsManager sm=manager(sub);Set<String> recipients=new LinkedHashSet<>();for(String a:destination.split("[,;]"))if(!a.trim().isEmpty())recipients.add(a.trim());if(recipients.isEmpty())throw new IllegalArgumentException("Enter a recipient");
  int limit=MAX_BYTES;android.os.Bundle config=sm.getCarrierConfigValues();if(config!=null)limit=Math.min(limit,config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,MAX_BYTES));if(limit<32768)throw new IOException("Invalid carrier MMS size limit");
  SendReq request=new SendReq();request.setDate(System.currentTimeMillis()/1000);request.setTo(EncodedStringValue.encodeStrings(recipients.toArray(new String[0])));request.setContentType("application/vnd.wap.multipart.related".getBytes(StandardCharsets.US_ASCII));PduBody body=new PduBody();StringBuilder smil=new StringBuilder("<smil><body><par dur=\"8000ms\">");
  if(text!=null&&!text.isEmpty()){body.addPart(part("text/plain","text.txt",text.getBytes(StandardCharsets.UTF_8)));smil.append("<text src=\"text.txt\"/>");}
  if(media!=null){String mime=c.getContentResolver().getType(media);if(mime==null||!(mime.startsWith("image/")||mime.startsWith("audio/")||mime.startsWith("video/")))throw new IOException("Choose image, audio or video media");byte[] data;if(mime.startsWith("image/")){try(InputStream in=c.getContentResolver().openInputStream(media)){data=readBounded(in,limit-16384);}catch(IOException large){data=resizeImage(c,media,limit-16384);mime="image/jpeg";}}else try(InputStream in=c.getContentResolver().openInputStream(media)){data=readBounded(in,limit-16384);}body.addPart(part(mime,"media",data));smil.append('<').append(mime.startsWith("image/")?"img":mime.startsWith("audio/")?"audio":"video").append(" src=\"media\"/>");}
  smil.append("</par></body></smil>");body.addPart(0,part("application/smil","smil.xml",smil.toString().getBytes(StandardCharsets.UTF_8)));request.setBody(body);byte[] bytes=new PduComposer(c,request).make();if(bytes==null||bytes.length>limit)throw new IOException("MMS exceeds the carrier size limit");long id=TransportStore.store(c,MmsStore.record(request,4,sub));Uri transfer=TransferProvider.outgoing(c,bytes,"application/vnd.wap.mms-message");
  try{sm.sendMultimediaMessage(c,transfer,null,null,callback(c,SEND,transfer,id));}catch(Exception e){TransferProvider.release(transfer);TransportStore.event(c,id,new JSONObject().put("type",5));throw e;}return thread;
 }
 private static PduPart part(String mime,String name,byte[] bytes){PduPart p=new PduPart();p.setContentType(mime.getBytes(StandardCharsets.US_ASCII));p.setName(name.getBytes(StandardCharsets.US_ASCII));p.setContentId(name.getBytes(StandardCharsets.US_ASCII));p.setContentLocation(name.getBytes(StandardCharsets.US_ASCII));p.setCharset(106);p.setData(bytes);return p;}
 public static void download(Context c,SmsRepository.Message message)throws Exception{
  supported();Vault.require();if(!SmsRole.isDefault(c))throw new SecurityException("Set SimpleSMS as default first");
  if(message.shared&&StorageMode.enabled(c)){long stored=TransportStore.store(c,new JSONObject().put("address",message.address).put("body",message.body).put("date",message.date).put("type",message.type).put("read",message.read).put("locked",message.locked).put("mms",true).put("pending",true).put("location",message.location).put("transaction",message.transaction).put("subscription",message.subscription).put("source",message.source),true);message=SmsRepository.message(c,stored);if(message==null)throw new IOException("Could not prepare MMS download");}
  Uri url=Uri.parse(message.location==null?"":message.location);if(!"http".equalsIgnoreCase(url.getScheme())&&!"https".equalsIgnoreCase(url.getScheme()))throw new IOException("Invalid carrier MMS location");SmsManager sm=manager(message.subscription);Uri transfer=TransferProvider.incoming(c,message.id,message.location,message.subscription);try{sm.downloadMultimediaMessage(c,message.location,transfer,null,callback(c,DOWNLOAD,transfer,message.id));}catch(Exception e){TransferProvider.release(transfer);throw e;}
 }
 public static void control(Context c,GenericPdu p,String location,int sub)throws Exception{supported();SmsManager sm=manager(sub);byte[] data=new PduComposer(c,p).make();if(data==null)throw new IOException("Invalid carrier acknowledgement");Uri transfer=TransferProvider.outgoing(c,data,"application/vnd.wap.mms-message");android.os.Bundle config=sm.getCarrierConfigValues();String target=config!=null&&config.getBoolean("enabledNotifyWapMMSC",false)?location:null;try{sm.sendMultimediaMessage(c,transfer,target,null,callback(c,CONTROL,transfer,-1));}catch(Exception e){TransferProvider.release(transfer);throw e;}}
 public static void acknowledge(Context c,byte[] downloaded,String location,int sub)throws Exception{GenericPdu p=new PduParser(downloaded).parse();if(p instanceof RetrieveConf){byte[] id=((RetrieveConf)p).getTransactionId();if(id!=null)control(c,new AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION,id),location,sub);}}
 private static byte[] resizeImage(Context c,Uri uri,int limit)throws IOException {
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
  try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
  if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Cannot decode image");
  options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1600||options.outHeight/options.inSampleSize>1600)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;Bitmap image;
  try(InputStream in=c.getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,options);}
  if(image==null)throw new IOException("Cannot decode image");
  try{for(int attempt=0;attempt<8;attempt++){
   for(int quality:new int[]{85,65,45}){ByteArrayOutputStream out=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("Cannot compress image");if(out.size()<=limit)return out.toByteArray();}
   if(image.getWidth()<100||image.getHeight()<100)break;Bitmap smaller=Bitmap.createScaledBitmap(image,Math.max(1,image.getWidth()*3/4),Math.max(1,image.getHeight()*3/4),true);if(smaller!=image){image.recycle();image=smaller;}
  }throw new IOException("Image cannot fit carrier MMS limit");}finally{image.recycle();}
 }
}
