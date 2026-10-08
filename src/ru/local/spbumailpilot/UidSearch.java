package ru.local.spbumailpilot;

import com.sun.mail.iap.Response;
import com.sun.mail.imap.protocol.IMAPResponse;
import com.sun.mail.iap.ProtocolException;
import java.util.Arrays;

final class UidSearch {
    static long[] parse(Response[] responses) throws ProtocolException {
        long[] values = new long[1024];
        int size = 0;
        boolean found = false;
        for (Response response : responses) {
            if (response instanceof IMAPResponse && ((IMAPResponse) response).keyEquals("SEARCH")) {
                found = true;
                long value;
                while ((value = response.readLong()) > 0) {
                    if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
                    values[size++] = value;
                }
            }
        }
        if (!found) throw new ProtocolException("Missing UID search response");
        return Arrays.copyOf(values, size);
    }
}
