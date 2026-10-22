package com.android.messaging.security;

import android.media.MediaDataSource;
import java.util.Arrays;

/** Seekable media source with no decrypted disk copy; close wipes the owned buffer. */
public final class MemoryMedia extends MediaDataSource {
    private byte[] bytes;
    public MemoryMedia(byte[] value){if(value==null)throw new IllegalArgumentException("Missing media");bytes=value;}
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int size){
        if(buffer==null)throw new NullPointerException("buffer");
        if(offset<0||size<0||offset>buffer.length-size)throw new IndexOutOfBoundsException();
        if(size==0)return 0;
        if(bytes==null||position<0||position>=bytes.length)return -1;
        int n=Math.min(size,bytes.length-(int)position);System.arraycopy(bytes,(int)position,buffer,offset,n);return n;
    }
    @Override public synchronized long getSize(){return bytes==null?0:bytes.length;}
    @Override public synchronized void close(){if(bytes!=null)Arrays.fill(bytes,(byte)0);bytes=null;}
}
