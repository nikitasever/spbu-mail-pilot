package ru.local.spbumailpilot;

import java.io.StringReader;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Text extraction only: never executes HTML or fetches remote images. */
final class DesktopHtml {
    static final int FROM_HTML_MODE_COMPACT=63;
    static String fromHtml(String html,int flags){
        StringBuilder result=new StringBuilder();
        try{
            new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback(){
                private int hidden;
                private boolean block(HTML.Tag tag){return tag.isBlock()||tag==HTML.Tag.BR||tag==HTML.Tag.LI;}
                private void newline(){if(result.length()>0&&result.charAt(result.length()-1)!='\n')result.append('\n');}
                public void handleStartTag(HTML.Tag tag,MutableAttributeSet attributes,int position){
                    if(tag==HTML.Tag.SCRIPT||tag==HTML.Tag.STYLE||tag==HTML.Tag.HEAD)hidden++;
                    if(hidden==0&&block(tag))newline();
                }
                public void handleEndTag(HTML.Tag tag,int position){
                    if(tag==HTML.Tag.SCRIPT||tag==HTML.Tag.STYLE||tag==HTML.Tag.HEAD)hidden=Math.max(0,hidden-1);
                    if(hidden==0&&block(tag))newline();
                }
                public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet attributes,int position){if(hidden==0&&tag==HTML.Tag.BR)newline();}
                public void handleText(char[] text,int position){if(hidden==0)result.append(text);}
            },true);
        }catch(java.io.IOException invalid){throw new IllegalArgumentException("HTML text unavailable");}
        return result.toString().trim();
    }
}
