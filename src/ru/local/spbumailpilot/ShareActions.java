package ru.local.spbumailpilot;

import android.content.*;
import android.net.Uri;
import java.io.File;

/** Shares to the user's chosen app. It never selects a recipient or sends a message itself. */
final class ShareActions {
    static void text(Context context,String text){
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);
        context.startActivity(Intent.createChooser(send,"Поделиться текстом"));
    }
    static void file(Context context,File file,String mime,String name){
        Uri uri=new Uri.Builder().scheme("content").authority(AttachmentProvider.AUTHORITY).appendPath(file.getName()).build();
        Intent send=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri);
        send.setClipData(ClipData.newUri(context.getContentResolver(),name,uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(send,"Поделиться файлом"));
    }
}
