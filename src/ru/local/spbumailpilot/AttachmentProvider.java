package ru.local.spbumailpilot;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Non-exported, read-only provider. Only a selected viewer receives a URI grant. */
public final class AttachmentProvider extends ContentProvider {
    static final String AUTHORITY="ru.local.spbumailpilot.attachments";
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        try{
            if(!AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new IOException();
            File base=new File(getContext().getCacheDir(),"attachments").getCanonicalFile();
            File file=new File(base,uri.getPathSegments().get(0)).getCanonicalFile();
            if(!base.equals(file.getParentFile())||!file.isFile())throw new IOException();return file;
        }catch(IOException e){throw new FileNotFoundException("Attachment unavailable");}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){try{return AttachmentFiles.mime(file(uri).getName(),null);}catch(Exception e){return "application/octet-stream";}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor result=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?file.getName():OpenableColumns.SIZE.equals(columns[i])?file.length():null;}result.addRow(row);return result;}catch(Exception e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
