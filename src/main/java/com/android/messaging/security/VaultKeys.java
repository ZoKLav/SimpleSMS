package com.android.messaging.security;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.math.BigInteger;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.io.*;
import org.json.JSONObject;
/** Only access policy is plaintext. Message data and encryption keys are never stored here. */
public final class VaultKeys {
 private static final String AUTH="simplesms.private.rsa.v1",OPEN="simplesms.private.rsa.optional.v1";
 private VaultKeys(){}
 private static String alias(boolean locked){return locked?AUTH:OPEN;}
 private static KeyStore store()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);return s;}
 private static AtomicFile policy(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"access-policy"));}
 private static JSONObject state(Context c)throws Exception{
  AtomicFile file=policy(c);if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
  KeyStore s=store();if(s.containsAlias(AUTH))return new JSONObject().put("locked",true);
  if(s.containsAlias(OPEN))return new JSONObject().put("locked",false);
  if(c.getDatabasePath("private-v1.db").exists())throw new GeneralSecurityException("The original encryption key is missing. Existing encrypted data cannot be recovered.");
  return new JSONObject().put("locked",false);
 }
 private static void save(Context c,JSONObject value)throws Exception{AtomicFile file=policy(c);FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
 public static synchronized boolean lockEnabled(Context c){try{JSONObject mode=state(c);return mode.getBoolean("locked")||mode.optBoolean("target",false);}catch(Exception e){return true;}}
 public static synchronized String pendingChange(Context c){try{JSONObject mode=state(c);return mode.has("target")?(mode.getBoolean("target")?"on":"off"):null;}catch(Exception e){return null;}}
 public static synchronized void ensure(Context c)throws Exception{
  boolean auth=state(c).getBoolean("locked");KeyStore s=store();if(!s.containsAlias(alias(auth))){
   if(c.getDatabasePath("private-v1.db").exists())throw new GeneralSecurityException("The original encryption key is missing. Existing encrypted data cannot be recovered.");
   prepare(c,auth);save(c,new JSONObject().put("locked",auth));
  }validate(auth);
 }
 public static synchronized void prepare(Context c,boolean auth)throws Exception{
  if(auth){KeyguardManager km=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);if(km==null||!km.isDeviceSecure())throw new GeneralSecurityException("Set a secure device screen lock to enable app lock");}
  if(!store().containsAlias(alias(auth))){try{generate(auth,true);}catch(Exception unavailable){generate(auth,false);}}
  validate(auth);
 }
 private static void validate(boolean auth)throws Exception{PrivateKey key=(PrivateKey)store().getKey(alias(auth),null);KeyInfo info=KeyFactory.getInstance(key.getAlgorithm(),"AndroidKeyStore").getKeySpec(key,KeyInfo.class);
  if(info.getOrigin()!=KeyProperties.ORIGIN_GENERATED||info.isUserAuthenticationRequired()!=auth)throw new GeneralSecurityException("Encryption key policy mismatch");
  if(auth&&(!info.isInsideSecureHardware()||!info.isUserAuthenticationRequirementEnforcedBySecureHardware()))throw new GeneralSecurityException("App lock needs hardware-enforced device authentication on this device. You can use the app with app lock off.");
 }
 private static void generate(boolean auth,boolean strong)throws Exception{
  KeyGenParameterSpec.Builder b=new KeyGenParameterSpec.Builder(alias(auth),KeyProperties.PURPOSE_DECRYPT|KeyProperties.PURPOSE_ENCRYPT).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).setUserAuthenticationRequired(auth).setInvalidatedByBiometricEnrollment(false).setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=SimpleSMS local storage")).setCertificateSerialNumber(BigInteger.ONE).setCertificateNotBefore(new Date(0)).setCertificateNotAfter(new Date(4102444800000L));
  if(auth){if(Build.VERSION.SDK_INT>=30)b.setUserAuthenticationParameters(300,KeyProperties.AUTH_DEVICE_CREDENTIAL);else b.setUserAuthenticationValidityDurationSeconds(300);if(Build.VERSION.SDK_INT>=28)b.setUnlockedDeviceRequired(true);}
  if(Build.VERSION.SDK_INT>=28&&strong)b.setIsStrongBoxBacked(true);
  KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");g.initialize(b.build());g.generateKeyPair();
 }
 private static PublicKey publicFor(boolean auth)throws Exception{return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(store().getCertificate(alias(auth)).getPublicKey().getEncoded()));}
 public static synchronized PublicKey publicKey(Context c)throws Exception{ensure(c);return publicFor(state(c).getBoolean("locked"));}
 public static synchronized byte[] open(Context c,byte[] sealed)throws Exception{
  JSONObject mode=state(c);boolean auth=mode.getBoolean("locked");PrivateKey active=(PrivateKey)store().getKey(alias(auth),null);
  PrivateKey target=mode.has("target")?(PrivateKey)store().getKey(alias(mode.getBoolean("target")),null):null;
  return Envelope.openAny(new PrivateKey[]{active,target},sealed);
 }
 public static synchronized PublicKey beginChange(Context c,boolean target)throws Exception{prepare(c,target);JSONObject mode=state(c);mode.put("target",target);save(c,mode);return publicFor(target);}
 public static synchronized void finishChange(Context c,boolean target)throws Exception{save(c,new JSONObject().put("locked",target));try{store().deleteEntry(alias(!target));}catch(Exception ignored){/* Completed ciphertext no longer uses the retired key. */}}
}
