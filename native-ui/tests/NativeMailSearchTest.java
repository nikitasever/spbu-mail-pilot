package ru.local.spbumailpilot;

import org.json.JSONArray;
import org.json.JSONObject;

public class NativeMailSearchTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        check(NativeMailSearch.query("  отчёт  ").equals("отчёт"));
        check(NativeMailSearch.query("").isEmpty());
        for (String bad : new String[]{null, "a\nb", "a\u0000b", "x".repeat(201)}) {
            try {
                NativeMailSearch.query(bad);
                throw new AssertionError("Accepted invalid query");
            } catch (IllegalArgumentException expected) {}
        }
        for (String badFrom : new String[]{"a\nb", "a\rb", "x".repeat(201)}) {
            try {
                new NativeMailSearch.Criteria("", badFrom, "", "", false, false);
                throw new AssertionError("Accepted invalid from");
            } catch (IllegalArgumentException expected) {}
        }

        NativeMailSearch.Criteria empty = NativeMailSearch.Criteria.simple("   ");
        check(empty.isEmpty() && !empty.isAdvanced && empty.key().isEmpty());

        NativeMailSearch.Criteria byFromA = new NativeMailSearch.Criteria("отчёт", "dean@spbu.ru", "2026-10-01", "2026-10-16", true, false);
        NativeMailSearch.Criteria byFromB = new NativeMailSearch.Criteria("отчёт", "office@spbu.ru", "2026-10-01", "2026-10-16", true, false);
        check(!byFromA.isEmpty() && byFromA.isAdvanced);
        check(!byFromA.key().equals(byFromB.key()));
        check(!byFromA.toString().contains("отчёт") && !byFromA.toString().contains("dean@spbu.ru"));

        for (String[] badDates : new String[][]{
            {"2026-02-29", ""},
            {"", "2026-13-01"},
            {"2026-10-20", "2026-10-10"}
        }) {
            try {
                new NativeMailSearch.Criteria("", "", badDates[0], badDates[1], false, false);
                throw new AssertionError("Accepted invalid dates");
            } catch (IllegalArgumentException expected) {}
        }

        check(NativeMailSearch.matches("all", false, false));
        check(NativeMailSearch.matches("unread", true, false));
        check(!NativeMailSearch.matches("unread", false, true));
        check(NativeMailSearch.matches("attachments", false, true));
        check(!NativeMailSearch.matches("attachments", true, false));
        check(!NativeMailSearch.matches("attachments", true, null));

        // Offline filtering with Moscow timezone date boundaries, sender/email substring, attachments, and unread.
        JSONArray messages = new JSONArray()
            .put(new JSONObject().put("uid", "10").put("validity", "7").put("folder", "INBOX")
                .put("sender", "Деканат").put("email", "dean@spbu.ru")
                .put("date", "2026-10-15T21:30:00Z") // 2026-10-16 00:30 MSK
                .put("seen", false).put("hasAttachments", true))
            .put(new JSONObject().put("uid", "9").put("validity", "7").put("folder", "INBOX")
                .put("sender", "Деканат").put("email", "dean@spbu.ru")
                .put("date", "2026-10-15T20:30:00Z") // 2026-10-15 23:30 MSK
                .put("seen", false).put("hasAttachments", true))
            .put(new JSONObject().put("uid", "8").put("validity", "7").put("folder", "INBOX")
                .put("sender", "Деканат").put("email", "dean@spbu.ru")
                .put("date", "2026-10-16T10:00:00Z")
                .put("seen", true).put("hasAttachments", true));
        JSONObject rawPage = new JSONObject()
            .put("folder", "INBOX").put("validity", "7").put("snapshot", "offline:1")
            .put("nextOffset", 20).put("total", 40).put("hasMore", true)
            .put("messages", messages);

        NativeMailSearch.Criteria msk16UnreadAtt = new NativeMailSearch.Criteria("", "декан", "2026-10-16", "2026-10-16", true, true);
        JSONObject filteredOffline = NativeMailSearch.filterOffline(rawPage, msk16UnreadAtt);
        check(filteredOffline.getJSONArray("messages").length() == 1);
        check(filteredOffline.getJSONArray("messages").getJSONObject(0).getString("uid").equals("10"));
        check(filteredOffline.getInt("nextOffset") == 20 && filteredOffline.getBoolean("hasMore") && filteredOffline.getInt("total") == 40);

        // Empty filtered page continuation with hasMore=true and bounded hops.
        JSONObject emptyFilteredFirst = new JSONObject()
            .put("folder", "INBOX").put("validity", "7").put("snapshot", "s1")
            .put("nextOffset", 20).put("total", 100).put("hasMore", true)
            .put("messages", new JSONArray());
        NativeMailPage p1 = NativeMailPage.first(emptyFilteredFirst, "INBOX");
        check(NativeMailSearch.shouldContinueEmptyPage(p1, 0, 0));

        JSONObject nonEmptySecond = new JSONObject()
            .put("folder", "INBOX").put("validity", "7").put("snapshot", "s1")
            .put("nextOffset", 40).put("total", 100).put("hasMore", true)
            .put("messages", new JSONArray().put(messages.getJSONObject(0)));
        NativeMailPage p2 = p1.append(nonEmptySecond);
        check(!NativeMailSearch.shouldContinueEmptyPage(p2, 0, 1));
        check(p2.messages.length() == 1 && p2.next == 40 && p2.more && p2.total == 100);
        check(!NativeMailSearch.shouldContinueEmptyPage(p1, 0, NativeMailSearch.MAX_EMPTY_PAGE_HOPS));

        check(NativeMailSearch.sameCursor("account-1", "INBOX", "q", "account-1", "INBOX", "q"));
        check(!NativeMailSearch.sameCursor("account-1", "INBOX", byFromA, "7", "s1", "account-1", "INBOX", byFromB, "7", "s1"));
        check(NativeMailSearch.sameCursor("account-1", "INBOX", byFromA, "7", "s1", "account-1", "INBOX", byFromA, "7", "s1"));
        System.out.println("PASS NativeMailSearch: validation, criteria keys, Moscow date offline filter, empty-page continuation and cursor isolation");
    }
}
