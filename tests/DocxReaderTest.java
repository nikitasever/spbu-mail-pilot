package ru.local.spbumailpilot;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class DocxReaderTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static File zip(String xml,Charset charset)throws Exception{
  File file=Files.createTempFile("docx-fixture-",".docx").toFile();try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){out.putNextEntry(new ZipEntry("word/document.xml"));out.write(xml.getBytes(charset));out.closeEntry();out.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));out.write("External relationship fixture: https://example.invalid/not-read".getBytes("UTF-8"));out.closeEntry();}return file;
 }
 static String document(String body){return "<w:document xmlns:w=\""+DocxReader.WORD+"\"><w:body>"+body+"</w:body></w:document>";}
 static void refused(String xml)throws Exception{try{DocxReader.read(zip(xml,StandardCharsets.UTF_8));throw new AssertionError();}catch(IOException|org.xml.sax.SAXException expected){}}
 public static void main(String[] args)throws Exception{
  String content="<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Заголовок 😀</w:t></w:r></w:p>"+
   "<w:p><w:pPr><w:numPr/></w:pPr><w:r><w:rPr><w:i/><w:u w:val=\"single\"/></w:rPr><w:t>Текст &amp; дата</w:t><w:tab/><w:br/></w:r><w:del><w:r><w:delText>Удалённое</w:delText><w:t>Скрытое удалённое</w:t></w:r></w:del><w:r><w:instrText>EXECUTE FIELD</w:instrText><w:drawing/><w:t>&lt;script&gt;</w:t></w:r></w:p>"+
   "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Колонка1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Колонка2</w:t></w:r></w:p></w:tc></w:tr></w:tbl>";
  DocxReader data=DocxReader.read(zip(document(content),StandardCharsets.UTF_8));check(data.blocks.size()==3);check(data.blocks.get(0).heading==1&&data.blocks.get(0).centered&&data.blocks.get(0).runs.get(0).bold);check(data.blocks.get(0).text().equals("Заголовок 😀"));check(data.blocks.get(1).list&&data.blocks.get(1).runs.get(0).italic&&data.blocks.get(1).runs.get(0).underline);check(data.blocks.get(1).text().contains("[Изображение]")&&data.blocks.get(1).text().contains("<script>"));check(!data.blocks.get(1).text().contains("Удалённое")&&!data.blocks.get(1).text().contains("EXECUTE"));check(data.blocks.get(2).text().equals("Колонка1  |  Колонка2"));
  check(DocxReader.read(zip(document(content),StandardCharsets.UTF_16LE)).blocks.get(0).text().equals("Заголовок 😀"));check(DocxReader.read(zip(document(content).replace(DocxReader.WORD,DocxReader.STRICT),StandardCharsets.UTF_8)).blocks.size()==3);
  refused("<!DOCTYPE document [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"+document("<w:p><w:r><w:t>&xxe;</w:t></w:r></w:p>"));refused("<!DOCTYPE document SYSTEM 'https://example.invalid/external.dtd'>"+document(""));refused("<document/>");
  String longText=String.join("",Collections.nCopies(DocxReader.MAX_TEXT+1,"x"));refused(document("<w:p><w:r><w:t>"+longText+"</w:t></w:r></w:p>"));
  StringBuilder deep=new StringBuilder();for(int i=0;i<70;i++)deep.append("<w:sdt>");deep.append("<w:p><w:r><w:t>Deep</w:t></w:r></w:p>");for(int i=0;i<70;i++)deep.append("</w:sdt>");refused(document(deep.toString()));refused(document("<w:p><w:r><w:t><w:r>Nested</w:r></w:t></w:r></w:p>"));
  String bomb=document("<w:p><w:r><w:t>"+String.join("",Collections.nCopies(DocxReader.MAX_XML,"a"))+"</w:t></w:r></w:p>");File compressed=zip(bomb,StandardCharsets.UTF_8);check(compressed.length()<20000);try{DocxReader.read(compressed);throw new AssertionError();}catch(IOException expected){}
  check(DocxReader.docx("Документ.DOCX",null)&&!DocxReader.docx("old.doc",null));
  System.out.println("PASS: actual DOCX ZIP/XML decoding, Unicode/UTF16/strict OOXML, heading/run styles, table rows, tracked-deletion/field skipping, literal markup/image placeholders, XXE/depth/text/ZIP expansion bounds. No external relationship opened.");
 }
}
