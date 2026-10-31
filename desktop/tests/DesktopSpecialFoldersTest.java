package ru.local.spbumailpilot;
import java.util.Properties;
import javax.mail.Session;
import com.sun.mail.imap.IMAPFolder;
/** Shared real reader, in-memory IMAP folder fixture with deliberately nonstandard names. */
public class DesktopSpecialFoldersTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        MailMoveTest.Memory memory=new MailMoveTest.Memory(Session.getInstance(new Properties()));
        java.lang.reflect.Field name=IMAPFolder.class.getDeclaredField("fullName");name.setAccessible(true);
        name.set(memory.archive,"University/Old letters");name.set(memory.trash,"University/Removed");name.set(memory.junk,"University/Unwanted");
        MailReader reader=new MailReader();java.lang.reflect.Field store=MailReader.class.getDeclaredField("store");store.setAccessible(true);store.set(reader,memory);
        check(reader.moveMail("INBOX","42","7","archive").getString("destination").equals("University/Old letters"));
        check(reader.moveMail("INBOX","42","7","trash").getString("destination").equals("University/Removed"));
        check(reader.moveMail("INBOX","42","7","spam").getString("destination").equals("University/Unwanted"));
        check(memory.moves==3);
        System.out.println("PASS special mail folders: shared MOVE resolver honors SPECIAL-USE attributes over English-name guesses (fixture, nonstandard server paths).");
    }
}
