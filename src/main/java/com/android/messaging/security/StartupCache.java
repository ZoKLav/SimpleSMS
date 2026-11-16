package com.android.messaging.security;

import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Optional encrypted acceleration only. Every hit is bound to the actual database ciphertext. */
final class StartupCache {
    static final int LIMIT=32*1024*1024;
    interface Open { byte[] run(byte[] sealed)throws Exception; }
    private final Map<Long,JSONObject> entries=new HashMap<>();
    static byte[] digest(byte[] sealed)throws Exception{return MessageDigest.getInstance("SHA-256").digest(sealed);}
    static StartupCache read(File path,Open open){
        StartupCache result=new StartupCache();byte[] plain=null;
        try{
            AtomicFile file=new AtomicFile(path);if(!path.exists()||path.length()>LIMIT+4096)return result;
            plain=open.run(file.readFully());if(plain.length>LIMIT)return result;
            JSONObject root=new JSONObject(new String(plain,StandardCharsets.UTF_8));
            if(!"startup-cache-v1".equals(root.optString("purpose")))return result;
            JSONArray rows=root.getJSONArray("entries");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);long id=row.getLong("id");if(id<=0||row.getJSONObject("record").getLong("_id")!=id)throw new IllegalArgumentException("Cache identity mismatch");result.entries.put(id,row);}
        }catch(Exception unavailable){result.entries.clear();}finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
        return result;
    }
    JSONObject record(long id,byte[] sealed)throws Exception{
        JSONObject row=entries.get(id);if(row==null)return null;
        try{if(!MessageDigest.isEqual(Base64.decode(row.getString("hash"),Base64.NO_WRAP),digest(sealed)))return null;return new JSONObject(row.getJSONObject("record").toString());}
        catch(IllegalArgumentException malformed){return null;}
    }
    static byte[] encode(Map<Long,JSONObject> records,Map<Long,byte[]> digests)throws Exception{
        JSONArray rows=new JSONArray();for(Map.Entry<Long,JSONObject> item:records.entrySet()){
            byte[] hash=digests.get(item.getKey());if(hash==null)return null;
            rows.put(new JSONObject().put("id",item.getKey()).put("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).put("record",item.getValue()));
        }
        byte[] plain=new JSONObject().put("purpose","startup-cache-v1").put("entries",rows).toString().getBytes(StandardCharsets.UTF_8);
        if(plain.length>LIMIT){Arrays.fill(plain,(byte)0);return null;}return plain;
    }
    static void write(File path,byte[] sealed)throws Exception{
        AtomicFile file=new AtomicFile(path);FileOutputStream out=null;
        try{out=file.startWrite();out.write(sealed);file.finishWrite(out);}catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
}
