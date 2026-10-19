package ru.local.spbumailpilot;

import org.json.*;
import java.util.*;

/**
 * Host (main) tests for the pure conversation policy. These exercise the real
 * {@link NativeMailThreads} implementation; they do not reimplement grouping.
 */
public class NativeMailThreadsTest {
    static final String ACCOUNT = "fixture@example.invalid";
    static final String FOLDER = "INBOX";
    static final String VALIDITY = "7";

    static void check(boolean value) { if (!value) throw new AssertionError(); }

    static JSONObject row(String uid, String subject, Object messageId, Object references, Object inReplyTo) {
        return row(ACCOUNT, FOLDER, VALIDITY, uid, subject, messageId, references, inReplyTo);
    }

    static JSONObject row(String account, String folder, String validity, String uid, String subject,
                          Object messageId, Object references, Object inReplyTo) {
        JSONObject item = new JSONObject()
            .put("account", account)
            .put("folder", folder)
            .put("validity", validity)
            .put("uid", uid)
            .put("subject", subject);
        if (messageId != null) item.put("messageId", messageId);
        if (references != null) item.put("references", references);
        if (inReplyTo != null) item.put("inReplyTo", inReplyTo);
        return item;
    }

    public static void main(String[] args) throws Exception {
        JSONArray rows = new JSONArray()
            .put(row("6", "Different subject", "<c@example>", new JSONArray().put("<b@example>"), null))
            .put(row("5", "Same subject", "<solo@example>", null, null))
            .put(row("4", "Reply", "<b@example>", new JSONArray().put("<a@example>"), "<a@example>"))
            .put(row("2", "Root", "<a@example>", null, null));
        JSONArray groups = NativeMailThreads.groups(rows, true);
        check(groups.length() == 2);
        check(groups.getJSONObject(0).getJSONArray("indices").toString().equals("[0,2,3]"));
        check(groups.getJSONObject(0).getJSONObject("anchor").getString("uid").equals("2"));
        check(groups.getJSONObject(1).getJSONArray("indices").toString().equals("[1]"));

        // Repeated own Message-ID is ambiguous and must not claim ownership.
        JSONArray dupOwn = new JSONArray()
            .put(row("2", "Child", "<b@example>", new JSONArray().put("<a@example>"), null))
            .put(row("1", "Ambiguous", "<a@example> <a@example>", null, null));
        check(NativeMailThreads.groups(dupOwn, true).length() == 2);

        System.out.println("NativeMailThreadsTest passed");
    }
}
