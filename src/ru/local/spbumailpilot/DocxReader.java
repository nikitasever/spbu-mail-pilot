package ru.local.spbumailpilot;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Bounded text preview of WordprocessingML. Does not evaluate fields or open relationships. */
final class DocxReader {
    static final int MAX_XML=2*1024*1024,MAX_TEXT=200000,MAX_RUNS=12000,MAX_BLOCKS=5000;
    static final String WORD="http://schemas.openxmlformats.org/wordprocessingml/2006/main",STRICT="http://purl.oclc.org/ooxml/wordprocessingml/main";
    static final class Run {final String text;final boolean bold,italic,underline;Run(String text,boolean b,boolean i,boolean u){this.text=text;bold=b;italic=i;underline=u;}}
    static final class Block {final List<Run> runs=new ArrayList<>();int heading;boolean centered,list;String text(){StringBuilder s=new StringBuilder();for(Run r:runs)s.append(r.text);return s.toString();}}
    final List<Block> blocks=new ArrayList<>();private int characters,runs,nodes;
    static boolean docx(String name,String mime){return name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".docx")||"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(mime);}
    static DocxReader read(File file)throws Exception{
        if(!file.isFile()||file.length()==0||file.length()>AttachmentFiles.MAX_BYTES)throw new IOException("Document unavailable");
        byte[] data;
        try(ZipFile archive=new ZipFile(file)){
            if(archive.size()>5000)throw new IOException("Too many document parts");
            ZipEntry entry=archive.getEntry("word/document.xml");if(entry==null||entry.isDirectory()||entry.getSize()>MAX_XML)throw new IOException("Document XML unavailable/large");
            try(InputStream in=archive.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_XML)throw new IOException("Document XML too large");out.write(buffer,0,n);}data=out.toByteArray();
            }
        }
        String xml=decode(data);
        if(java.util.regex.Pattern.compile("(?is)<\\s*!\\s*(DOCTYPE|ENTITY)\\b").matcher(xml).find())throw new IOException("Unsafe XML");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
        DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((a,b)->{throw new org.xml.sax.SAXException("External XML refused");});
        Document parsed=builder.parse(new InputSource(new StringReader(xml)));Element root=parsed.getDocumentElement();
        if(!word(root)||!"document".equals(root.getLocalName()))throw new IOException("Not a Word document");
        DocxReader result=new DocxReader();result.walk(root,result.blocks,0);return result;
    }
    private static String decode(byte[] data)throws Exception{
        Charset charset=StandardCharsets.UTF_8;
        if(data.length>=2){int a=data[0]&255,b=data[1]&255;if(a==255&&b==254||a==60&&b==0)charset=StandardCharsets.UTF_16LE;else if(a==254&&b==255||a==0&&b==60)charset=StandardCharsets.UTF_16BE;}
        String xml=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();if(xml.startsWith("\ufeff"))xml=xml.substring(1);
        java.util.regex.Matcher declared=java.util.regex.Pattern.compile("(?is)^\\s*<\\?xml[^?]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(xml);
        if(declared.find()&&!declared.group(1).matches("(?i)UTF-8|UTF-16|UTF-16LE|UTF-16BE"))throw new IOException("Unsupported XML encoding");return xml;
    }
    private static boolean word(Node n){return n instanceof Element&&(WORD.equals(n.getNamespaceURI())||STRICT.equals(n.getNamespaceURI()));}
    private static Element child(Element parent,String name){for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(word(n)&&name.equals(n.getLocalName()))return (Element)n;return null;}
    private static String attr(Element e,String key){if(e==null)return "";return e.getAttributeNS(e.getNamespaceURI(),key);}
    private static boolean flag(Element props,String key){if(props==null)return false;Element e=child(props,key);String val=attr(e,"val");return e!=null&&!val.equals("0")&&!val.equalsIgnoreCase("false")&&!val.equals("none");}
    private void visit(int depth)throws IOException{if(depth>64||++nodes>100000)throw new IOException("Document structure too deep/large");}
    private void add(Block block,String text,boolean b,boolean i,boolean u)throws IOException{
        if(text.isEmpty())return;characters+=text.length();if(characters>MAX_TEXT||++runs>MAX_RUNS)throw new IOException("Document text too large");block.runs.add(new Run(text,b,i,u));
    }
    private void walk(Element parent,List<Block> target,int depth)throws IOException{
        visit(depth);
        for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(word(node)){
            Element element=(Element)node;String name=element.getLocalName();
            if(name.equals("del")||name.equals("drawing")||name.equals("object")||name.equals("pPr")||name.equals("sectPr"))continue;
            if(name.equals("p")){
                Block block=new Block();Element props=child(element,"pPr");String style=attr(props==null?null:child(props,"pStyle"),"val");java.util.regex.Matcher heading=java.util.regex.Pattern.compile("(?i)heading([1-6])").matcher(style);if(heading.matches())block.heading=Integer.parseInt(heading.group(1));
                block.centered=props!=null&&"center".equals(attr(child(props,"jc"),"val"));block.list=props!=null&&child(props,"numPr")!=null;
                content(element,block,false,false,false,depth+1);target.add(block);
            }else if(name.equals("tr")){
                Block row=new Block();boolean first=true;
                for(Node cell=element.getFirstChild();cell!=null;cell=cell.getNextSibling())if(word(cell)&&"tc".equals(cell.getLocalName())){
                    if(!first)add(row,"  |  ",false,false,false);first=false;List<Block> cells=new ArrayList<>();walk((Element)cell,cells,depth+1);
                    boolean initial=true;for(Block value:cells){if(!initial)add(row," / ",false,false,false);initial=false;row.runs.addAll(value.runs);}
                }
                if(!row.runs.isEmpty())target.add(row);
            }else if(name.equals("altChunk")){Block block=new Block();add(block,"[Внешняя часть документа]",false,false,false);target.add(block);}
            else walk(element,target,depth+1);
            if(target.size()>MAX_BLOCKS)throw new IOException("Too many paragraphs");
        }
    }
    private void content(Element parent,Block block,boolean bold,boolean italic,boolean underline,int depth)throws IOException{
        visit(depth);
        if("r".equals(parent.getLocalName())){Element props=child(parent,"rPr");bold=flag(props,"b");italic=flag(props,"i");underline=flag(props,"u");}
        for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(word(node)){
            Element e=(Element)node;String name=e.getLocalName();
            if(name.equals("t")){for(Node part=e.getFirstChild();part!=null;part=part.getNextSibling())if(part instanceof Element)throw new IOException("Nested text element");add(block,e.getTextContent(),bold,italic,underline);}
            else if(name.equals("tab"))add(block,"\t",bold,italic,underline);
            else if(name.equals("br")||name.equals("cr"))add(block,"\n",bold,italic,underline);
            else if(name.equals("drawing")||name.equals("pict"))add(block,"[Изображение]",false,false,false);
            else if(name.equals("object"))add(block,"[Встроенный объект]",false,false,false);
            else if(!name.equals("del")&&!name.equals("instrText")&&!name.equals("pPr")&&!name.equals("rPr"))content(e,block,bold,italic,underline,depth+1);
        }
    }
}
