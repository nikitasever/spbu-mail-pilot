package ru.local.spbumailpilot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Query text and advanced search criteria stay in the retained ViewModel, never in navigation state or logs. */
final class NativeMailSearch {
    static final int MAX_EMPTY_PAGE_HOPS = 4;
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOSCOW_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).withZone(MOSCOW);

    static final class Criteria {
        final String query;
        final String from;
        final String start;
        final String end;
        final boolean attachments;
        final boolean unread;
        final boolean isAdvanced;
        private final SearchQuery.Filters filters;

        Criteria(String query, String from, String start, String end, boolean attachments, boolean unread) {
            this.query = NativeMailSearch.query(query == null ? "" : query);
            this.from = sender(from);
            this.start = start == null ? "" : start.trim();
            this.end = end == null ? "" : end.trim();
            this.attachments = attachments;
            this.unread = unread;
            this.isAdvanced = !this.from.isEmpty() || !this.start.isEmpty() || !this.end.isEmpty() || attachments || unread;
            try {
                JSONObject json = new JSONObject()
                    .put("query", this.query)
                    .put("from", this.from)
                    .put("start", this.start)
                    .put("end", this.end)
                    .put("attachments", attachments)
                    .put("unread", unread);
                this.filters = new SearchQuery.Filters(json.toString());
            } catch (IllegalArgumentException invalid) {
                throw invalid;
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Invalid search criteria", invalid);
            }
        }

        static Criteria simple(String query) {
            return new Criteria(query == null ? "" : query, "", "", "", false, false);
        }

        boolean isEmpty() {
            return query.isEmpty() && !isAdvanced;
        }

        SearchQuery.Filters toFilters() {
            return filters;
        }

        String key() {
            return isEmpty() ? "" : filters.key();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Criteria)) return false;
            Criteria that = (Criteria) other;
            return attachments == that.attachments
                && unread == that.unread
                && query.equals(that.query)
                && from.equals(that.from)
                && start.equals(that.start)
                && end.equals(that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, from, start, end, attachments, unread);
        }

        @Override
        public String toString() {
            return "Criteria(advanced=" + isAdvanced + ",hasQuery=" + !query.isEmpty() + ")";
        }
    }

    static String query(String value) {
        if (value == null || value.length() > 200 || value.matches("(?s).*\\p{Cntrl}.*"))
            throw new IllegalArgumentException("Invalid search");
        return value.trim();
    }

    static String sender(String value) {
        if (value == null) return "";
        if (value.length() > 200 || value.matches("(?s).*\\p{Cntrl}.*"))
            throw new IllegalArgumentException("Invalid from");
        return value.trim();
    }

    static boolean matches(String filter, boolean unread, Boolean attachments) {
        switch (filter) {
            case "all": return true;
            case "unread": return unread;
            case "attachments": return Boolean.TRUE.equals(attachments);
            default: throw new IllegalArgumentException("Invalid filter");
        }
    }

    static JSONObject filterOffline(JSONObject page, Criteria criteria) throws Exception {
        if (page == null) return null;
        JSONObject copy = new JSONObject(page.toString());
        if (criteria == null || !criteria.isAdvanced) return copy;
        JSONArray rows = copy.getJSONArray("messages");
        JSONArray candidates = new JSONArray();
        String needle = criteria.from.toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = new JSONObject(rows.getJSONObject(i).toString());
            if (!needle.isEmpty()) {
                String combined = (item.optString("sender") + " " + item.optString("email")).toLowerCase(Locale.ROOT);
                if (!combined.contains(needle)) continue;
            }
            String rawDate = item.optString("date");
            if (rawDate.endsWith("Z")) {
                try {
                    item.put("date", MOSCOW_ISO.format(Instant.parse(rawDate)));
                } catch (Exception ignored) {}
            }
            candidates.put(item);
        }
        copy.put("messages", candidates);
        JSONObject filtered = SearchQuery.filtered(copy, criteria.toFilters());
        JSONArray matched = filtered.getJSONArray("messages");
        for (int i = 0; i < matched.length(); i++) {
            JSONObject item = matched.getJSONObject(i);
            String uid = item.optString("uid");
            for (int j = 0; j < rows.length(); j++) {
                JSONObject orig = rows.getJSONObject(j);
                if (uid.equals(orig.optString("uid"))) {
                    item.put("date", orig.optString("date"));
                    break;
                }
            }
        }
        return filtered;
    }

    static boolean shouldContinueEmptyPage(NativeMailPage page, int previousCount, int hops) {
        return page != null && page.messages.length() == previousCount && page.more && hops < MAX_EMPTY_PAGE_HOPS;
    }

    static boolean sameCursor(String owner, String folder, String query, String otherOwner, String otherFolder, String otherQuery) {
        return owner.equals(otherOwner) && folder.equals(otherFolder) && query.equals(otherQuery);
    }

    static boolean sameCursor(
        String owner,
        String folder,
        Criteria criteria,
        String validity,
        String snapshot,
        String otherOwner,
        String otherFolder,
        Criteria otherCriteria,
        String otherValidity,
        String otherSnapshot
    ) {
        String key = criteria == null ? "" : criteria.key();
        String otherKey = otherCriteria == null ? "" : otherCriteria.key();
        return owner.equals(otherOwner)
            && folder.equals(otherFolder)
            && key.equals(otherKey)
            && Objects.equals(validity, otherValidity)
            && Objects.equals(snapshot, otherSnapshot);
    }
}
