package ru.local.spbumailpilot;
import java.util.*;
public class NativeReaderTargetsTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  for(String uri:new String[]{"javascript:alert(1)","intent://example.invalid", "file:///tmp/private", "content://private/file", "https://user:pass@example.invalid/", "https://example.invalid:99999/", "https://example.invalid/\r\n", "https://example.invalid/\u202eexe"})check(NativeReaderTargets.web(uri)==null);
  check(NativeReaderTargets.web("https://example.invalid/path?q=1#part").equals("https://example.invalid/path?q=1#part"));
  NativeReaderTargets found=NativeReaderTargets.from("См. https://example.invalid/a(b). Ещё (https://example.invalid/test). https://example.invalid/a(b) Тел.: +7 812 326-49-56");
  check(found.links.equals(Arrays.asList("https://example.invalid/a(b)","https://example.invalid/test")));
  check(found.phones.equals(Arrays.asList("+78123264956")));
  try{found.links.add("bad");throw new AssertionError();}catch(UnsupportedOperationException expected){}
  StringBuilder many=new StringBuilder();for(int i=0;i<21;i++)many.append("https://example.invalid/").append(i).append(' ');
  check(NativeReaderTargets.from(many.toString()).links.size()==20);check(NativeReaderTargets.from(many.toString()).limited);
  check(NativeReaderTargets.from(" ".repeat(200001)+"https://example.invalid/hidden").links.isEmpty());
  check(NativeReaderTargets.from(null).links.isEmpty());check(NativeReaderTargets.share("Тема","Имя","Текст").equals("Тема: Тема\nОт: Имя\n\nТекст"));
  try{NativeReaderTargets.share("","","x".repeat(140000));throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS NativeReaderTargets: scheme/authority/control rejection, punctuation, dedupe, phones, immutable output, limits and explicit share text");
 }
}
