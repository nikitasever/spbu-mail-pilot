package ru.local.spbumailpilot;

/** Query text stays in the retained ViewModel, never in navigation state or logs. */
final class NativeMailSearch {
    static String query(String value) {
        if(value==null||value.length()>200||value.matches("(?s).*\\p{Cntrl}.*"))
            throw new IllegalArgumentException("Invalid search");
        return value.trim();
    }
    static boolean matches(String filter,boolean unread,Boolean attachments) {
        switch(filter) {
            case "all":return true;
            case "unread":return unread;
            case "attachments":return Boolean.TRUE.equals(attachments);
            default:throw new IllegalArgumentException("Invalid filter");
        }
    }
    static boolean sameCursor(String owner,String folder,String query,String otherOwner,String otherFolder,String otherQuery) {
        return owner.equals(otherOwner)&&folder.equals(otherFolder)&&query.equals(otherQuery);
    }
}
