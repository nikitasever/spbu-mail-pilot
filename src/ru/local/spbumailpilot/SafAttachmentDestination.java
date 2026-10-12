package ru.local.spbumailpilot;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/** Uses only the folder explicitly granted by ACTION_OPEN_DOCUMENT_TREE. No filesystem permission. */
final class SafAttachmentDestination implements BulkAttachments.Destination {
    private final ContentResolver resolver;private final Uri tree,parent;
    SafAttachmentDestination(ContentResolver resolver,Uri tree){
        if(tree==null||!"content".equals(tree.getScheme())||!DocumentsContract.isTreeUri(tree))throw new IllegalArgumentException("Not a granted document tree");
        this.resolver=resolver;this.tree=tree;this.parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
    }
    public Set<String> names()throws Exception{
        Set<String> names=new HashSet<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor cursor=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor==null)throw new java.io.IOException("Cannot list destination");while(cursor.moveToNext())names.add(cursor.getString(0));
        }return names;
    }
    public BulkAttachments.Target create(String name,String mime)throws Exception{
        // Interrupted processes leave an unmistakable .part document, never a final-looking file.
        final Uri created=DocumentsContract.createDocument(resolver,parent,mime,"mail-"+java.util.UUID.randomUUID().toString()+".part");
        if(created==null)throw new java.io.IOException("Cannot create document");
        return new BulkAttachments.Target(){
            private Uri uri=created;
            public OutputStream open()throws Exception{return resolver.openOutputStream(uri,"w");}
            public void finish()throws Exception{
                // Refresh names after transfer in case another app/user created a matching file meanwhile.
                String finalName=BulkAttachments.uniqueName(name,names());
                Uri renamed;
                try{renamed=DocumentsContract.renameDocument(resolver,uri,finalName);}catch(UnsupportedOperationException unsupported){throw new BulkAttachments.UnsupportedDestination();}
                if(renamed==null)throw new BulkAttachments.UnsupportedDestination();
                uri=renamed;
            }
            public void delete()throws Exception{if(!DocumentsContract.deleteDocument(resolver,uri))throw new java.io.IOException("Cannot remove partial document");}
        };
    }
}
