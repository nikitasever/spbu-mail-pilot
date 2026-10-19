package ru.local.spbumailpilot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure JVM conversation grouping policy for the native Compose inbox. Mirrors the maintained
 * legacy WebView mailGroups() rules without mutating caller JSON or merging by subject.
 */
public final class NativeMailThreads {
    private static final int MAX_FIELD_IDS = 100;
    private static final int MAX_RAW_HEADER = 32768;
    private static final int MAX_SCOPE_LENGTH = 998;
    private static final String UID = "[1-9][0-9]{0,19}";
    private static final Pattern ID = Pattern.compile("<[^<>\\s@]+@[^<>\\s@]+>");

    private NativeMailThreads() {}

    /**
     * Extracts validated identifiers from one header value that may arrive as raw text, a JSON
     * array of raw texts, JSONObject.NULL or null. Delegates all matching/validation to the
     * existing {@link MailThreadHeaders} validator and bounds the result to 100 identifiers.
     */
    public static List<String> headerIds(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null || value == JSONObject.NULL) return new ArrayList<>(result);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            int limit = Math.min(array.length(), MAX_FIELD_IDS);
            for (int i = 0; i < limit && result.size() < MAX_FIELD_IDS; i++) {
                Object item = array.opt(i);
                if (item instanceof String) add(result, new String[]{(String) item});
            }
        } else if (value instanceof String) {
            add(result, new String[]{(String) value});
        }
        return new ArrayList<>(result);
    }

    private static void add(LinkedHashSet<String> result, String[] header) {
        for (String id : MailThreadHeaders.ids(header)) {
            if (result.size() >= MAX_FIELD_IDS) return;
            result.add(id);
        }
    }

    /**
     * Occurrence-preserving identifier extraction for a row's own Message-ID. Matching the legacy
     * parser, duplicates count as separate occurrences so an ambiguous own header such as
     * {@code "<a@x> <a@x>"} is never normalised into one usable identifier. Still bounded to 100
     * identifiers and validated through {@link MailThreadHeaders#valid(String)}.
     */
    public static List<String> headerIdOccurrences(Object value) {
        List<String> result = new ArrayList<>();
        // Only References can be an array. Message-ID and In-Reply-To are raw strings.
        if (value instanceof String) collect(result, (String) value);
        return result;
    }

    private static void collect(List<String> out, String raw) {
        if (raw == null || raw.length() > MAX_RAW_HEADER) return;
        Matcher matcher = ID.matcher(raw);
        while (matcher.find() && out.size() < MAX_FIELD_IDS) {
            String candidate = matcher.group();
            if (MailThreadHeaders.valid(candidate)) out.add(candidate);
        }
    }

    public static JSONArray groups(JSONArray rows) throws JSONException {
        return groups(rows, true);
    }

    /**
     * Groups a visible slice in input order. Returns a new JSONArray of objects with
     * {@code indices} (int array of member positions in {@code rows}, newest/representative first)
     * and {@code anchor} (oldest member's folder/validity/uid reference).
     */
    public static JSONArray groups(JSONArray rows, boolean enabled) throws JSONException {
        int size = rows == null ? 0 : rows.length();
        int[] parent = new int[size];
        for (int i = 0; i < size; i++) parent[i] = i;
        if (enabled && size > 1) link(rows, parent, size);
        Map<Integer, List<Integer>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) grouped.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        JSONArray out = new JSONArray();
        for (Map.Entry<Integer, List<Integer>> entry : grouped.entrySet()) {
            JSONObject group = new JSONObject();
            JSONArray indices = new JSONArray();
            for (int index : entry.getValue()) indices.put(index);
            group.put("indices", indices);
            JSONObject anchor = anchor(rows, entry.getValue());
            if (anchor != null) group.put("anchor", anchor);
            out.put(group);
        }
        return out;
    }

    private static void link(JSONArray rows, int[] parent, int size) {
        // Structured keys avoid any separator collision between scope components and identifiers.
        Map<List<String>, Integer> owners = new HashMap<>();
        for (int i = 0; i < size; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String account = stringField(row, "account");
            String folder = stringField(row, "folder");
            String validity = stringField(row, "validity");
            String uid = stringField(row, "uid");
            if (!validScope(account, folder, validity, uid)) continue;
            List<String> own = headerIdOccurrences(stringField(row, "messageId"));
            if (own.size() == 1) {
                owners.putIfAbsent(Arrays.asList(account, folder, validity, own.get(0)), i);
            }
        }
        for (int i = 0; i < size; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String account = stringField(row, "account");
            String folder = stringField(row, "folder");
            String validity = stringField(row, "validity");
            String uid = stringField(row, "uid");
            if (!validScope(account, folder, validity, uid)) continue;
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            List<String> own = headerIdOccurrences(stringField(row, "messageId"));
            if (own.size() == 1) refs.add(own.get(0));
            refs.addAll(headerIds(row.opt("references")));
            refs.addAll(headerIds(stringField(row,"inReplyTo")));
            for (String ref : refs) {
                Integer owner = owners.get(Arrays.asList(account, folder, validity, ref));
                if (owner != null) union(parent, i, owner);
            }
        }
    }

    private static String stringField(JSONObject row, String key) {
        Object value = row.opt(key);
        return value instanceof String ? (String) value : null;
    }

    private static boolean validScope(String account, String folder, String validity, String uid) {
        if (account == null || account.isEmpty() || account.length() > MAX_SCOPE_LENGTH || hasControl(account)) return false;
        if (folder == null || folder.isEmpty() || folder.length() > MAX_SCOPE_LENGTH || hasControl(folder)) return false;
        if (validity == null || !validity.matches(UID)) return false;
        return uid != null && uid.matches(UID);
    }

    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f) return true;
        }
        return false;
    }

    private static JSONObject anchor(JSONArray rows, List<Integer> members) throws JSONException {
        if (members.isEmpty()) return null;
        JSONObject oldest = rows.optJSONObject(members.get(members.size() - 1));
        if (oldest == null) return null;
        return new JSONObject()
            .put("folder", oldest.optString("folder", "INBOX"))
            .put("validity", oldest.optString("validity"))
            .put("uid", oldest.optString("uid"));
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }
}
