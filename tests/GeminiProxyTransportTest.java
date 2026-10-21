package ru.local.spbumailpilot;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.util.concurrent.*;

public final class GeminiProxyTransportTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(args[0])){keys.load(in,"changeit".toCharArray());}
  X509Certificate cert=(X509Certificate)keys.getCertificate("fixture");
  StringBuilder pin=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))pin.append(String.format("%02x",b&255));
  GeminiProxyTransport.verifyCertificate(new X509Certificate[]{cert},pin.toString());
  try{GeminiProxyTransport.verifyCertificate(new X509Certificate[]{cert});throw new AssertionError("Foreign pin accepted");}catch(CertificateException expected){}
  try{new GeminiProxyTransport("bad\r\npassword");throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(!new GeminiProxyTransport(new String(new char[48]).replace('\0','a')).toString().contains("aaaa"));
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"changeit".toCharArray());
  SSLContext server=SSLContext.getInstance("TLS");server.init(km.getKeyManagers(),null,null);
  for(boolean valid:new boolean[]{true,false}){
   try(SSLServerSocket listener=(SSLServerSocket)server.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
    ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
    Future<Boolean> received=worker.submit(()->{try(SSLSocket accepted=(SSLSocket)listener.accept()){
     accepted.setSoTimeout(5000);int value=accepted.getInputStream().read();accepted.getOutputStream().write(43);accepted.getOutputStream().flush();return value==42;
    }catch(IOException rejected){if(valid)throw rejected;return false;}});
    try(Socket client=GeminiProxyTransport.proxySockets(valid?pin.toString():GeminiProxyTransport.CERT_SHA256).createSocket()){
     client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),listener.getLocalPort()),5000);client.setSoTimeout(5000);
     try{client.getOutputStream().write(42);client.getOutputStream().flush();if(valid)check(client.getInputStream().read()==43);if(!valid)throw new AssertionError("Wrong pin sent data");}
     catch(SSLException rejected){if(valid)throw rejected;}
    }
    check(received.get(7,TimeUnit.SECONDS)==valid);worker.shutdownNow();
   }
  }
  // Exercise the actual OkHttp route: auth is inside proxy TLS, Google TLS starts after CONNECT.
  try(SSLServerSocket listener=(SSLServerSocket)server.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
   ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
   Future<Boolean> tunneled=worker.submit(()->{try(SSLSocket accepted=(SSLSocket)listener.accept()){
    accepted.setSoTimeout(5000);InputStream in=accepted.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();
    int value;while((value=in.read())!=-1){header.write(value);if(header.size()>8192)throw new AssertionError();if(header.toString("US-ASCII").endsWith("\r\n\r\n"))break;}
    String text=header.toString("US-ASCII");check(text.startsWith("CONNECT generativelanguage.googleapis.com:443 HTTP/1.1"));
    check(text.contains("Proxy-Authorization: Basic "));check(!text.toLowerCase().contains("x-goog-api-key"));
    accepted.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("US-ASCII"));accepted.getOutputStream().flush();
    return in.read()==22; // TLS handshake record for the independently verified Google connection.
   }});
   okhttp3.OkHttpClient client=GeminiProxyTransport.client(new String(new char[48]).replace('\0','a'),new InetSocketAddress(InetAddress.getLoopbackAddress(),listener.getLocalPort()),pin.toString(),()->true);
   try(okhttp3.Response response=client.newCall(new okhttp3.Request.Builder().url("https://generativelanguage.googleapis.com/").header("x-goog-api-key","fixture-not-a-secret").build()).execute()){throw new AssertionError("Unverified origin accepted");}catch(IOException expected){}
   check(tunneled.get(7,TimeUnit.SECONDS));client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();worker.shutdownNow();
  }
  System.out.println("GeminiProxyTransportTest passed: TLS accepted pinned certificate, rejected foreign certificate before application bytes.");
 }
}
