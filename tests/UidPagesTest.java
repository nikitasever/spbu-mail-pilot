package ru.local.spbumailpilot;

import java.util.Arrays;
import java.util.HashSet;
import com.sun.mail.iap.Response;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.protocol.IMAPResponse;

public class UidPagesTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        for (int count : new int[]{0,1,49,50,51,137,2301}) {
            long[] source = new long[count];
            for (int i=0;i<count;i++) source[i]=4290000000L+i*7L;
            UidPages pages = new UidPages(source);
            if (count>0) source[0]=1; // Caller mutation must not change the snapshot.
            HashSet<Long> visited = new HashSet<>();
            int offset=0;
            while(offset<count) {
                long[] page=pages.page(offset);
                check(page.length==Math.min(50,count-offset));
                for(long uid:page) check(visited.add(uid)&&uid>=4290000000L);
                offset=pages.next(offset);
            }
            check(visited.size()==count&&pages.page(count).length==0);
            try { pages.page(-1);throw new AssertionError(); } catch(IllegalArgumentException expected) {}
            try { pages.page(count+1);throw new AssertionError(); } catch(IllegalArgumentException expected) {}
        }
        UidPages shuffled=new UidPages(new long[]{90,2,80,4});
        check(Arrays.equals(shuffled.page(0),new long[]{2,4,80,90}));
        check(Arrays.equals(UidSearch.parse(new Response[]{new IMAPResponse("* SEARCH 1 22 4294967295"),new Response("A1 OK Search complete")}),new long[]{1,22,4294967295L}));
        check(UidSearch.parse(new Response[]{new IMAPResponse("* SEARCH"),new Response("A1 OK Search complete")}).length==0);
        StringBuilder reply=new StringBuilder("* SEARCH");
        for(int i=1;i<=2301;i++)reply.append(' ').append(i*3);
        long[] parsed=UidSearch.parse(new Response[]{new IMAPResponse(reply.toString())});
        check(parsed.length==2301&&parsed[2300]==6903);
        try { UidSearch.parse(new Response[]{new Response("A1 OK")});throw new AssertionError(); } catch(ProtocolException expected) {}
        System.out.println("PASS: stable UID pages, all entries exactly once, gaps, 32-bit UIDs, boundaries, empty/search response parsing and missing-response failure.");
    }
}
