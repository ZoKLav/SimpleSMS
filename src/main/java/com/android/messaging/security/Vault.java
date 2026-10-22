package com.android.messaging.security;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.os.SystemClock;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** SQLite stores only opaque ids and authenticated encrypted blobs, including all indexes/metadata. */
public final class Vault {
 private static Db db;private static PublicKey publicKey;private static long authenticatedAt;private static byte[] groupKey;
 private static final Map<Long,JSONObject> cache=new HashMap<>();
 // All record mutations go through this class; unlock loads once and writes maintain the cache.
 private static final Map<String,Long> threads=new LinkedHashMap<String,Long>(128,0.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Long> e){return size()>2048;}};private static EncryptedPrefs prefs;private static boolean sessionLocked=true;
 private Vault(){}
 private static final class Db extends SQLiteOpenHelper {
  Db(Context c){super(c,"private-v1.db",null,1);}
  @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE records (_id INTEGER PRIMARY KEY AUTOINCREMENT, blob BLOB NOT NULL)");d.execSQL("CREATE TABLE chunks (record INTEGER NOT NULL, position INTEGER NOT NULL, blob BLOB NOT NULL, PRIMARY KEY(record,position))");}
  @Override public void onUpgrade(SQLiteDatabase d,int old,int now){throw new IllegalStateException("Unsupported vault version");}
 }
 private static void init(Context c)throws Exception{if(publicKey==null)publicKey=VaultKeys.publicKey(c);if(db==null)db=new Db(c.getApplicationContext());}
 public static synchronized boolean unlocked(){return authenticatedAt!=0&&(!sessionLocked||SystemClock.elapsedRealtime()-authenticatedAt<240000)&&groupKey!=null;}
 public static synchronized void require(){if(!unlocked())throw new SecurityException("Unlock SimpleSMS to read encrypted storage");}
 public static synchronized void lock(){authenticatedAt=0;if(groupKey!=null)Arrays.fill(groupKey,(byte)0);groupKey=null;cache.clear();threads.clear();if(prefs!=null){EncryptedPrefs old=prefs;new android.os.Handler(android.os.Looper.getMainLooper()).post(old::forget);}prefs=null;}
 public static synchronized void unlock(Context c)throws Exception{
  lock();init(c);SQLiteDatabase d=db.getWritableDatabase();
  try(Cursor x=d.rawQuery("SELECT COUNT(*) FROM records",null)){x.moveToFirst();if(x.getLong(0)==0){byte[] key=new byte[32];new SecureRandom().nextBytes(key);JSONObject config=new JSONObject().put("kind","config").put("group",android.util.Base64.encodeToString(key,android.util.Base64.NO_WRAP));append(c,config);Arrays.fill(key,(byte)0);}}
  load(c);for(JSONObject record:cache.values())if("config".equals(record.optString("kind"))){groupKey=android.util.Base64.decode(record.getString("group"),android.util.Base64.NO_WRAP);break;}
  if(groupKey==null||groupKey.length!=32){lock();throw new GeneralSecurityException("Encrypted storage configuration is missing");}
  sessionLocked=VaultKeys.lockEnabled(c);authenticatedAt=SystemClock.elapsedRealtime();final long session=authenticatedAt;Context app=c.getApplicationContext();if(sessionLocked)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{synchronized(Vault.class){if(authenticatedAt!=session||!sessionLocked)return;}if(app instanceof PrivateApplication)((PrivateApplication)app).expireSession();else lock();},240000);prefs=new EncryptedPrefs(c.getApplicationContext());
  // One-time migration of the original app's plaintext preferences/drafts; no message deletion.
  SharedPreferences old=c.getSharedPreferences("settings",Context.MODE_PRIVATE);if(!old.getAll().isEmpty()){prefs.importLegacy(old.getAll());if(!old.edit().clear().commit())throw new java.io.IOException("Could not clear legacy preferences");if(android.os.Build.VERSION.SDK_INT>=24)c.deleteSharedPreferences("settings");}
  c.deleteSharedPreferences("send_state");
 }
 private static void load(Context c)throws Exception{
  try(Cursor x=db.getReadableDatabase().query("records",new String[]{"_id","blob"},null,null,null,null,"_id ASC")){
   Set<Long> existing=new HashSet<>();while(x.moveToNext()){long id=x.getLong(0);existing.add(id);if(cache.containsKey(id))continue;java.io.ByteArrayOutputStream encrypted=new java.io.ByteArrayOutputStream();encrypted.write(x.getBlob(1));try(Cursor chunks=db.getReadableDatabase().query("chunks",new String[]{"blob"},"record=?",new String[]{""+id},null,null,"position ASC")){while(chunks.moveToNext())encrypted.write(chunks.getBlob(0));}byte[] plain=VaultKeys.open(c,encrypted.toByteArray());try{JSONObject value=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(value.getLong("_id")!=id)throw new GeneralSecurityException("Encrypted record identity mismatch");cache.put(id,value);}finally{Arrays.fill(plain,(byte)0);}}cache.keySet().retainAll(existing);
  }
 }
 public static synchronized List<JSONObject> records(Context c)throws Exception{require();init(c);List<JSONObject> out=new ArrayList<>();List<Long> ids=new ArrayList<>(cache.keySet());Collections.sort(ids);for(long id:ids)out.add(new JSONObject(cache.get(id).toString()));return out;}
 public static synchronized long append(Context c,JSONObject value)throws Exception{
  init(c);SQLiteDatabase d=db.getWritableDatabase();d.beginTransaction();long id;JSONObject copy=new JSONObject(value.toString());
  try{ContentValues empty=new ContentValues();empty.put("blob",new byte[0]);id=d.insertOrThrow("records",null,empty);copy.put("_id",id);byte[] plain=copy.toString().getBytes(StandardCharsets.UTF_8);byte[] sealed;try{sealed=Envelope.seal(publicKey,plain);}finally{Arrays.fill(plain,(byte)0);}ContentValues v=new ContentValues();v.put("blob",Arrays.copyOf(sealed,Math.min(sealed.length,65536)));int position=0;for(int at=65536;at<sealed.length;at+=65536){ContentValues chunk=new ContentValues();chunk.put("record",id);chunk.put("position",position++);chunk.put("blob",Arrays.copyOfRange(sealed,at,Math.min(sealed.length,at+65536)));d.insertOrThrow("chunks",null,chunk);}if(d.update("records",v,"_id=?",new String[]{""+id})!=1)throw new java.io.IOException("Encrypted record write failed");d.setTransactionSuccessful();}finally{d.endTransaction();}
  if(unlocked())cache.put(id,copy);changed(c);return id;
 }

 /** Each replacement is atomic; the durable transition policy permits both keys after interruption. */
 public static synchronized void setAppLock(Context c,boolean enabled)throws Exception{
  require();PublicKey next=VaultKeys.beginChange(c,enabled);SQLiteDatabase d=db.getWritableDatabase();
  // Media changes are individually atomic, and remain readable if the database transaction rolls back.
  java.io.File media=new java.io.File(c.getNoBackupFilesDir(),"media");java.io.File[] files=media.listFiles();java.util.Set<String> names=new java.util.HashSet<>();if(files!=null)for(java.io.File f:files){String n=f.getName();if(n.endsWith(".bak"))n=n.substring(0,n.length()-4);if(n.matches("[a-f0-9-]{36}"))names.add(n);}
  for(String name:names){java.io.File f=new java.io.File(media,name);
   android.util.AtomicFile file=new android.util.AtomicFile(f);byte[] plain=VaultKeys.open(c,file.readFully());byte[] sealed;try{sealed=Envelope.seal(next,plain);}finally{Arrays.fill(plain,(byte)0);}
   java.io.FileOutputStream out=null;try{out=file.startWrite();out.write(sealed);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
  }
  d.beginTransaction();try{
   List<Long> ids=new ArrayList<>();try(Cursor x=d.query("records",new String[]{"_id"},null,null,null,null,null)){while(x.moveToNext())ids.add(x.getLong(0));}
   for(long id:ids){java.io.ByteArrayOutputStream old=new java.io.ByteArrayOutputStream();try(Cursor x=d.query("records",new String[]{"blob"},"_id=?",new String[]{""+id},null,null,null)){if(!x.moveToFirst())throw new java.io.IOException("Record missing");old.write(x.getBlob(0));}
    try(Cursor x=d.query("chunks",new String[]{"blob"},"record=?",new String[]{""+id},null,null,"position ASC")){while(x.moveToNext())old.write(x.getBlob(0));}
    byte[] plain=VaultKeys.open(c,old.toByteArray());byte[] sealed;try{sealed=Envelope.seal(next,plain);}finally{Arrays.fill(plain,(byte)0);}
    ContentValues v=new ContentValues();v.put("blob",Arrays.copyOf(sealed,Math.min(65536,sealed.length)));d.update("records",v,"_id=?",new String[]{""+id});d.delete("chunks","record=?",new String[]{""+id});int pos=0;
    for(int at=65536;at<sealed.length;at+=65536){ContentValues chunk=new ContentValues();chunk.put("record",id);chunk.put("position",pos++);chunk.put("blob",Arrays.copyOfRange(sealed,at,Math.min(sealed.length,at+65536)));d.insertOrThrow("chunks",null,chunk);}
   }d.setTransactionSuccessful();
  }finally{d.endTransaction();}
  VaultKeys.finishChange(c,enabled);publicKey=null;sessionLocked=enabled;authenticatedAt=SystemClock.elapsedRealtime();if(enabled){final long session=authenticatedAt;Context app=c.getApplicationContext();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{synchronized(Vault.class){if(authenticatedAt!=session||!sessionLocked)return;}if(app instanceof PrivateApplication)((PrivateApplication)app).expireSession();else lock();},240000);}changed(c);
 }
 public static synchronized String saveAttachment(Context c,byte[] data)throws Exception{
  init(c);java.io.File directory=new java.io.File(c.getNoBackupFilesDir(),"media");if(!directory.exists()&&!directory.mkdirs())throw new java.io.IOException("Cannot create encrypted media storage");String name=UUID.randomUUID().toString();byte[] sealed=Envelope.seal(publicKey,data);android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(directory,name));java.io.FileOutputStream out=null;try{out=file.startWrite();out.write(sealed);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}return name;
 }
 public static synchronized byte[] readAttachment(Context c,String name)throws Exception{
  require();if(name==null||!name.matches("[a-f0-9-]{36}"))throw new java.io.IOException("Invalid encrypted attachment reference");init(c);android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(new java.io.File(c.getNoBackupFilesDir(),"media"),name));if(file.getBaseFile().length()>10L*com.android.messaging.sms.MmsTransport.MAX_BYTES+4096)throw new java.io.IOException("Encrypted attachment exceeds size limit");byte[] sealed=file.readFully();return VaultKeys.open(c,sealed);
 }
 public static byte[] checkedAttachment(Context c,String name,String digest)throws Exception{byte[] bytes=readAttachment(c,name);byte[] expected=android.util.Base64.decode(digest,android.util.Base64.NO_WRAP);if(!java.security.MessageDigest.isEqual(expected,java.security.MessageDigest.getInstance("SHA-256").digest(bytes))){Arrays.fill(bytes,(byte)0);throw new GeneralSecurityException("Encrypted attachment identity mismatch");}return bytes;}
 public static synchronized void eraseAttachment(Context c,String name)throws Exception{if(name!=null&&name.matches("[a-f0-9-]{36}"))new android.util.AtomicFile(new java.io.File(new java.io.File(c.getNoBackupFilesDir(),"media"),name)).delete();}
 public static synchronized JSONObject verify(Context c,long id)throws Exception{
  require();init(c);java.io.ByteArrayOutputStream encrypted=new java.io.ByteArrayOutputStream();
  try(Cursor row=db.getReadableDatabase().query("records",new String[]{"blob"},"_id=?",new String[]{""+id},null,null,null)){if(!row.moveToFirst())throw new java.io.IOException("Encrypted copy is missing");encrypted.write(row.getBlob(0));}
  try(Cursor chunks=db.getReadableDatabase().query("chunks",new String[]{"blob"},"record=?",new String[]{""+id},null,null,"position ASC")){while(chunks.moveToNext())encrypted.write(chunks.getBlob(0));}
  byte[] plain=VaultKeys.open(c,encrypted.toByteArray());try{JSONObject result=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(result.getLong("_id")!=id)throw new GeneralSecurityException("Encrypted record identity mismatch");return result;}finally{Arrays.fill(plain,(byte)0);}
 }
 public static synchronized void event(Context c,long id,JSONObject flags)throws Exception{append(c,new JSONObject().put("kind","event").put("message",id).put("flags",flags));}
 public static synchronized long thread(String address)throws Exception{require();Long cached=threads.get(address);if(cached!=null)return cached;javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(groupKey,"HmacSHA256"));byte[] digest=mac.doFinal(canonical(address).getBytes(StandardCharsets.UTF_8));long id=java.nio.ByteBuffer.wrap(digest).getLong()&Long.MAX_VALUE;long result=id==0?1:id;threads.put(address,result);return result;}
 public static String canonical(String address){TreeSet<String> group=new TreeSet<>();for(String a:(address==null?"Unknown":address).split("[,;]")){a=a.trim();String n=android.telephony.PhoneNumberUtils.normalizeNumber(a);group.add(a.contains("@")?"e:"+a.toLowerCase(java.util.Locale.US):n!=null&&!n.isEmpty()?"p:"+n:"u:"+a.toLowerCase(java.util.Locale.US));}return android.text.TextUtils.join(",",group);}
 public static synchronized SharedPreferences preferences(Context c){if(!unlocked()||prefs==null)return EncryptedPrefs.locked();return prefs;}
 public static Uri changes(Context c){return Uri.parse("content://"+c.getPackageName()+".private/messages");}
 private static void changed(Context c){c.getContentResolver().notifyChange(changes(c),null);}
 public static synchronized void erase(Context c,long id)throws Exception{require();init(c);db.getWritableDatabase().beginTransaction();try{db.getWritableDatabase().delete("chunks","record=?",new String[]{""+id});db.getWritableDatabase().delete("records","_id=?",new String[]{""+id});db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}cache.remove(id);changed(c);}
}
