package ru.local.spbumailpilot;

import okhttp3.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Private TLS CONNECT hop; Google TLS remains independently platform-verified. */
final class GeminiProxyTransport implements GeminiDigest.Transport {
 static final String HOST="185.221.196.212";
 static final int PORT=2053;
 static final String CERT_SHA256="318b2e48ba51b8c936e8ff22ebe3bf1b3241d46579f88a1f0150a26ffc17d3f4";
 static boolean validPassword(String value){
  if(value==null||value.isEmpty()||value.length()>256||value.trim().isEmpty())return false;
  for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return false;
  return true;
 }
 private final String password;
 GeminiProxyTransport(String password){if(!validPassword(password))throw new IllegalArgumentException("Некорректный пароль прокси");this.password=password;}
 @Override public String toString(){return "GeminiProxyTransport(redacted)";}
 static void verifyCertificate(X509Certificate[] chain)throws CertificateException {verifyCertificate(chain,CERT_SHA256);}
 static void verifyCertificate(X509Certificate[] chain,String fingerprint)throws CertificateException {
  if(chain==null||chain.length==0)throw new CertificateException("Missing proxy certificate");
  chain[0].checkValidity();
  try{
   byte[] actual=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
   byte[] expected=new byte[32];for(int i=0;i<32;i++)expected[i]=(byte)Integer.parseInt(fingerprint.substring(i*2,i*2+2),16);
   if(!MessageDigest.isEqual(expected,actual))throw new CertificateException("Proxy certificate changed");
  }catch(NoSuchAlgorithmException unavailable){throw new CertificateException(unavailable);}
 }
 static javax.net.SocketFactory proxySockets()throws GeneralSecurityException {return proxySockets(CERT_SHA256);}
 static javax.net.SocketFactory proxySockets(String fingerprint)throws GeneralSecurityException {
  SSLContext tls=SSLContext.getInstance("TLS");
  tls.init(null,new TrustManager[]{new X509TrustManager(){
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
   public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("Not a server");}
   public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{verifyCertificate(chain,fingerprint);}
  }},null);
  final SSLSocketFactory factory=tls.getSocketFactory();
  return new javax.net.SocketFactory(){
   private Socket secure(Socket socket){SSLSocket ssl=(SSLSocket)socket;java.util.List<String> protocols=new java.util.ArrayList<>();for(String name:ssl.getSupportedProtocols())if(name.equals("TLSv1.2")||name.equals("TLSv1.3"))protocols.add(name);ssl.setEnabledProtocols(protocols.toArray(new String[0]));return ssl;}
   public Socket createSocket()throws IOException{return secure(factory.createSocket());}
   public Socket createSocket(String host,int port)throws IOException{return secure(factory.createSocket(host,port));}
   public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return secure(factory.createSocket(host,port,local,localPort));}
   public Socket createSocket(InetAddress host,int port)throws IOException{return secure(factory.createSocket(host,port));}
   public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return secure(factory.createSocket(host,port,local,localPort));}
  };
 }
 static OkHttpClient client(String password,InetSocketAddress address,String fingerprint,BooleanSupplier current)throws GeneralSecurityException {
  return client(password,address,fingerprint,current,new Attempt());
 }
 static final class Attempt {
  volatile boolean googleTlsStarted,authRejected;
  volatile int googleStatus;
  IOException describe(IOException error){
   if(GeminiDigest.RESPONSE_TOO_LARGE.equals(error.getMessage()))return error;
   if(googleStatus==0||googleStatus==200||error instanceof InterruptedIOException||error instanceof SSLException||error instanceof SocketException||error instanceof EOFException)return failure(error);
   // Preserve a deliberate HTTP/API rejection after its response was received.
   return error;
  }
  GeminiDigest.ConnectionFailure failure(IOException error){
   GeminiDigest.ConnectionStep step;
   String message=error.getMessage()==null?"":error.getMessage();
   if(authRejected||message.startsWith("Unexpected response code for CONNECT: 407"))step=GeminiDigest.ConnectionStep.PROXY_AUTH;
   else if(!googleTlsStarted&&message.startsWith("Unexpected response code for CONNECT: 403"))step=GeminiDigest.ConnectionStep.PROXY_DENIED;
   else if(!googleTlsStarted&&(message.startsWith("Unexpected response code for CONNECT: 502")||message.startsWith("Unexpected response code for CONNECT: 503")))step=GeminiDigest.ConnectionStep.PROXY_UPSTREAM;
   else if(error instanceof SSLException)step=googleTlsStarted?GeminiDigest.ConnectionStep.GOOGLE_TLS:GeminiDigest.ConnectionStep.PROXY_TLS;
   else if(error instanceof InterruptedIOException)step=googleTlsStarted?GeminiDigest.ConnectionStep.GOOGLE_TIMEOUT:GeminiDigest.ConnectionStep.PROXY_TIMEOUT;
   else if(!googleTlsStarted&&(error instanceof ConnectException||error instanceof UnknownHostException||error instanceof NoRouteToHostException))step=GeminiDigest.ConnectionStep.PROXY_CONNECT;
   else step=googleTlsStarted?GeminiDigest.ConnectionStep.GOOGLE_IO:GeminiDigest.ConnectionStep.PROXY_TUNNEL;
   return new GeminiDigest.ConnectionFailure(step,error);
  }
 }
 static OkHttpClient client(String password,InetSocketAddress address,String fingerprint,BooleanSupplier current,Attempt attempt)throws GeneralSecurityException {
  return new OkHttpClient.Builder()
   .proxy(new Proxy(Proxy.Type.HTTP,address))
   .socketFactory(proxySockets(fingerprint))
   .proxyAuthenticator((route,response)->{
    if(!"CONNECT".equals(response.request().method()))return null;
    if(response.request().header("Proxy-Authorization")!=null){attempt.authRejected=true;return null;}
    if(!current.getAsBoolean())return null;
    return response.request().newBuilder().header("Proxy-Authorization",Credentials.basic("gemini",password,java.nio.charset.StandardCharsets.UTF_8)).build();
   })
   .eventListener(new EventListener(){
    @Override public void secureConnectStart(Call call){attempt.googleTlsStarted=true;}
    @Override public void responseHeadersEnd(Call call,Response response){attempt.googleStatus=response.code();}
   })
   .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
   .connectTimeout(10,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build();
 }
 @Override public String post(String endpoint,String key,String body,BooleanSupplier current,Consumer<Runnable> cancellation)throws Exception {
  return exchange(endpoint,key,body,current,cancellation,false);
 }
 /** Explicit route test: no API key, email text or attachment leaves the phone. */
 int checkConnection(BooleanSupplier current,Consumer<Runnable> cancellation)throws Exception {
  return Integer.parseInt(exchange("https://generativelanguage.googleapis.com/",null,null,current,cancellation,true));
 }
 static Request connectionCheckRequest(){return new Request.Builder().url("https://generativelanguage.googleapis.com/").get().build();}
 private String exchange(String endpoint,String key,String body,BooleanSupplier current,Consumer<Runnable> cancellation,boolean probe)throws Exception {
  URI uri=URI.create(endpoint);
  if(!"https".equals(uri.getScheme())||!"generativelanguage.googleapis.com".equals(uri.getHost())||uri.getPort()!=-1)throw new IOException("Invalid Gemini destination");
  // socketFactory is used for the raw proxy hop only. OkHttp layers Google's normal TLS over CONNECT.
  Attempt attempt=new Attempt();
  OkHttpClient client=client(password,new InetSocketAddress(HOST,PORT),CERT_SHA256,current,attempt);
  if(probe)client=client.newBuilder().readTimeout(10,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).build();
  Request request=probe?connectionCheckRequest():new Request.Builder().url(endpoint).header("x-goog-api-key",key)
   .post(RequestBody.create(body,MediaType.get("application/json; charset=utf-8"))).build();
  Call call=client.newCall(request);
  cancellation.accept(call::cancel);
  try{
   if(!current.getAsBoolean())throw new IOException("Отменено");
   try(Response response=call.execute()){
    if(!current.getAsBoolean())throw new IOException("Отменено");
    if(probe)return Integer.toString(response.code());
    if(response.code()!=200){
     String message=GeminiDigest.status(response.code());
     if(response.code()==400||response.code()==403)try(InputStream input=response.body().byteStream()){message=GeminiDigest.remoteError(response.code(),input,current);}
     throw new IOException(message);
    }
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    try(InputStream input=response.body().byteStream()){
     byte[] bytes=new byte[4096];int n;
     while((n=input.read(bytes))!=-1){if(!current.getAsBoolean())throw new IOException("Отменено");if(out.size()+n>262144)throw new IOException(GeminiDigest.RESPONSE_TOO_LARGE);out.write(bytes,0,n);}
    }
    return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
   }
  }catch(IOException failure){
   if(!current.getAsBoolean())throw failure;
   throw attempt.describe(failure);
  }finally{call.cancel();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
 }
}
