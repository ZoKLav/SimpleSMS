package com.android.messaging.security;
import java.io.*;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Public-key sealed records: locked receivers can encrypt, only authenticated users decrypt. */
public final class Envelope {
 private static final byte[] MAGIC={'S','S','V','1'};
 private static final OAEPParameterSpec OAEP=new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT);
 private Envelope(){}
 public static byte[] seal(PublicKey publicKey,byte[] plain)throws GeneralSecurityException,IOException{
  byte[] raw=new byte[32];new SecureRandom().nextBytes(raw);
  try{
   Cipher wrap=Cipher.getInstance("RSA/ECB/OAEPPadding");wrap.init(Cipher.ENCRYPT_MODE,publicKey,OAEP);byte[] wrapped=wrap.doFinal(raw);
   Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(raw,"AES"));
   ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.write(MAGIC);out.writeShort(wrapped.length);out.write(wrapped);out.write(aes.getIV());byte[] header=b.toByteArray();aes.updateAAD(header);out.write(aes.doFinal(plain));return b.toByteArray();
  }finally{Arrays.fill(raw,(byte)0);}
 }
 /** A durable key transition can contain ciphertext under either key until it commits. */
 public static byte[] openAny(PrivateKey[] keys,byte[] sealed)throws GeneralSecurityException,IOException{
  GeneralSecurityException failure=new GeneralSecurityException("No available encryption key could open this record");
  for(PrivateKey key:keys)if(key!=null)try{return open(key,sealed);}catch(GeneralSecurityException|IOException e){failure.addSuppressed(e);}
  throw failure;
 }
 public static byte[] open(PrivateKey privateKey,byte[] sealed)throws GeneralSecurityException,IOException{
  if(sealed.length<6+128+12+16)throw new IOException("Invalid encrypted record");DataInputStream in=new DataInputStream(new ByteArrayInputStream(sealed));byte[] magic=new byte[4];in.readFully(magic);if(!Arrays.equals(MAGIC,magic))throw new IOException("Unsupported encrypted record");int length=in.readUnsignedShort();if(length<128||length>512||sealed.length<6+length+28)throw new IOException("Invalid wrapped key");byte[] wrapped=new byte[length],iv=new byte[12];in.readFully(wrapped);in.readFully(iv);
  Cipher wrap=Cipher.getInstance("RSA/ECB/OAEPPadding");wrap.init(Cipher.DECRYPT_MODE,privateKey,OAEP);byte[] raw=wrap.doFinal(wrapped);
  try{if(raw.length!=32)throw new IOException("Invalid data key");Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,iv));int header=6+length+12;aes.updateAAD(sealed,0,header);return aes.doFinal(sealed,header,sealed.length-header);}finally{Arrays.fill(raw,(byte)0);}
 }
}
