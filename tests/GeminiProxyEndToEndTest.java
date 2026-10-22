package ru.local.spbumailpilot;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import javax.net.ssl.*;
import okhttp3.*;

/** Completes both TLS layers and a POST using a local origin with a fixture CA. */
public final class GeminiProxyEndToEndTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static String headers(InputStream in)throws IOException {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;
  while((b=in.read())!=-1){bytes.write(b);if(bytes.size()>8192)throw new IOException("Fixture header limit");
   if(bytes.toString("US-ASCII").endsWith("\r\n\r\n"))return bytes.toString("US-ASCII");}
  throw new EOFException();
 }
 public static void main(String[] args)throws Exception {
  KeyStore keys=KeyStore.getInstance("PKCS12");
  try(InputStream in=new FileInputStream(args[0])){keys.load(in,"changeit".toCharArray());}
  X509Certificate cert=(X509Certificate)keys.getCertificate("fixture");
  StringBuilder pin=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))pin.append(String.format("%02x",b&255));
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"changeit".toCharArray());
  SSLContext server=SSLContext.getInstance("TLS");server.init(km.getKeyManagers(),null,null);
  TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
  X509TrustManager trust=(X509TrustManager)tm.getTrustManagers()[0];
  SSLContext originTrust=SSLContext.getInstance("TLS");originTrust.init(null,new TrustManager[]{trust},null);
  ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
  OkHttpClient client=null;
  try(SSLServerSocket listener=(SSLServerSocket)server.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
   Future<Boolean> received=worker.submit(()->{
    try(SSLSocket outer=(SSLSocket)listener.accept()){
     outer.setSoTimeout(5000);String connect=headers(outer.getInputStream());
     check(connect.startsWith("CONNECT generativelanguage.googleapis.com:443 HTTP/1.1"));
     check(connect.contains("Proxy-Authorization: Basic "));
     check(!connect.toLowerCase().contains("x-goog-api-key"));
     outer.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));outer.getOutputStream().flush();
     try(SSLSocket inner=(SSLSocket)server.getSocketFactory().createSocket(outer,"generativelanguage.googleapis.com",443,true)){
      inner.setUseClientMode(false);inner.setSoTimeout(5000);inner.startHandshake();
      InputStream in=inner.getInputStream();String request=headers(in);
      check(request.startsWith("POST /fixture HTTP/1.1"));
      check(request.toLowerCase().contains("x-goog-api-key: fixture-not-a-secret"));
      check(!request.toLowerCase().contains("proxy-authorization"));
      int length=-1;for(String line:request.split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
      check(length>0&&length<1024);byte[] body=new byte[length];new DataInputStream(in).readFully(body);
      check(new String(body,StandardCharsets.UTF_8).equals("{\"fixture\":true}"));
      byte[] reply="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
      inner.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      inner.getOutputStream().write(reply);inner.getOutputStream().flush();return true;
     }
    }
   });
   // The fixture CA is trusted only here; production retains normal Google trust/hostname checks.
   client=GeminiProxyTransport.client("fixture-password",new InetSocketAddress(InetAddress.getLoopbackAddress(),listener.getLocalPort()),pin.toString(),()->true)
    .newBuilder().sslSocketFactory(originTrust.getSocketFactory(),trust).build();
   Request request=new Request.Builder().url("https://generativelanguage.googleapis.com/fixture").header("x-goog-api-key","fixture-not-a-secret")
    .post(RequestBody.create("{\"fixture\":true}",MediaType.get("application/json"))).build();
   try(Response response=client.newCall(request).execute()){check(response.code()==200);check(response.body().string().equals("{\"ok\":true}"));}
   check(received.get(7,TimeUnit.SECONDS));
  }finally{if(client!=null){client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}worker.shutdownNow();}
  System.out.println("PASS: local TLS proxy, authenticated CONNECT, verified origin TLS, isolated POST headers/body and JSON response.");
 }
}
