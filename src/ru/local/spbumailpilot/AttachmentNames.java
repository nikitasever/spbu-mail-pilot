package ru.local.spbumailpilot;
/** Same octet-aware MIME decoder for names and embedded filename words. */
final class AttachmentNames {static String decode(String raw){return raw==null?"Вложение":MimeWords.decode(raw);}}
