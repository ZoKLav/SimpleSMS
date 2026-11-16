package com.android.messaging.util;
import com.android.messaging.sms.SmsRepository.Message;import com.android.messaging.sms.SmsRepository.Part;import java.util.Objects;
public final class MessageContent{
 private MessageContent(){}public static boolean same(Message a,Message b){
  if(a.date!=b.date||a.dateSent!=b.dateSent||a.type!=b.type||a.status!=b.status||a.error!=b.error||a.read!=b.read||a.subscription!=b.subscription||a.providerThread!=b.providerThread||a.locked!=b.locked||a.mms!=b.mms||a.pending!=b.pending||a.shared!=b.shared||!Objects.equals(a.body,b.body)||!Objects.equals(a.address,b.address)||!Objects.equals(a.location,b.location)||!Objects.equals(a.transaction,b.transaction)||!Objects.equals(a.source,b.source)||a.parts.size()!=b.parts.size())return false;
  for(int i=0;i<a.parts.size();i++){Part x=a.parts.get(i),y=b.parts.get(i);if(x.shared!=y.shared||!Objects.equals(x.mime,y.mime)||!Objects.equals(x.name,y.name)||!Objects.equals(x.data,y.data)||!Objects.equals(x.digest,y.digest))return false;}return true;
 }
}
