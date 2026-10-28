package ru.local.spbumailpilot;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
public final class GeminiCancellationTest {
    static final class Transport extends HttpsURLConnection {
        ByteArrayOutputStream sent=new ByteArrayOutputStream();boolean closed;Runnable beforeOutput=()->{},beforeInput=()->{};
        Transport()throws Exception{super(new URL("https://fixture.invalid/"));}
        public void connect(){}public void disconnect(){closed=true;}public boolean usingProxy(){return false;}
        public String getCipherSuite(){return "fixture";}public java.security.cert.Certificate[] getLocalCertificates(){return null;}public java.security.cert.Certificate[] getServerCertificates(){return null;}
        public OutputStream getOutputStream(){beforeOutput.run();return sent;}public int getResponseCode(){return 200;}
        public InputStream getInputStream(){beforeInput.run();return new ByteArrayInputStream(new byte[0]);}
    }
    static JSONArray items()throws Exception{return new JSONArray().put(new JSONObject().put("message",new JSONObject()).put("text","fixture"));}
    static void refuse(GeminiDigest digest,Transport transport,java.util.function.BooleanSupplier current)throws Exception{
        try{digest.exchange(transport,"fixture-key",items(),"fixture",current);throw new AssertionError();}catch(IOException expected){if(!expected.getMessage().equals("Отменено"))throw expected;}
        if(!transport.closed)throw new AssertionError("Connection not closed");
    }
    public static void main(String[] args)throws Exception {
        GeminiDigest first=new GeminiDigest(),second=new GeminiDigest();
        Transport disabled=new Transport();refuse(first,disabled,()->false);if(disabled.sent.size()!=0)throw new AssertionError("Body sent while disabled");
        Transport changed=new Transport();changed.beforeOutput=GeminiDigest::cancelAll;refuse(first,changed,()->true);if(changed.sent.size()!=0)throw new AssertionError("Body sent after cancellation");
        Transport own=new Transport();own.beforeOutput=second::cancel;refuse(second,own,()->true);if(own.sent.size()!=0)throw new AssertionError();
        Transport empty=new Transport();empty.beforeInput=GeminiDigest::cancelAll;refuse(first,empty,()->true);
        final Runnable[] delayed=new Runnable[1];Transport old=new Transport();old.beforeOutput=()->{delayed[0]=first.cancellation();};refuse(first,old,()->true);
        Transport newer=new Transport();newer.beforeOutput=()->delayed[0].run();newer.beforeInput=()->{if(newer.closed||newer.sent.size()==0)throw new AssertionError("Old cancellation hit a new request");first.cancel();};refuse(first,newer,()->true);
        System.out.println("PASS GeminiCancellation: disabled/invalidated requests do not write body, global/instance cancellation, no stale EOF result, disconnect on failure");
    }
}
