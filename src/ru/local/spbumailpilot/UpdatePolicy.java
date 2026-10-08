package ru.local.spbumailpilot;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
final class UpdatePolicy {
    static boolean allowed(URL url){String host=url.getHost();return "https".equals(url.getProtocol())&&url.getUserInfo()==null&&url.getPort()==-1&&(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com"));}
    static void copy(InputStream in,String sha,File target,long limit)throws Exception{
        if(!sha.matches("[a-fA-F0-9]{64}"))throw new IOException("Invalid checksum");
        File partial=new File(target.getParentFile(),"update.part");MessageDigest digest=MessageDigest.getInstance("SHA-256");boolean ok=false;
        try{
            try(OutputStream out=new FileOutputStream(partial)){byte[] buffer=new byte[32768];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("APK too large");digest.update(buffer,0,n);out.write(buffer,0,n);}}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!hex.toString().equalsIgnoreCase(sha))throw new IOException("Checksum mismatch");
            if(target.exists()&&!target.delete())throw new IOException("Cannot replace APK");if(!partial.renameTo(target))throw new IOException("Cannot finish download");ok=true;
        }finally{if(!ok)partial.delete();}
    }
}
