package ru.local.spbumailpilot;

import java.util.Arrays;

/** A stable UID snapshot is independent of later sequence-number changes. */
final class UidPages {
    static final int PAGE_SIZE = 50;
    private final long[] uids;
    UidPages(long[] values) { uids = values.clone(); Arrays.sort(uids); }
    int size() { return uids.length; }
    long[] page(int offset) {
        if (offset < 0 || offset > uids.length) throw new IllegalArgumentException("Invalid page offset");
        int end = uids.length - offset;
        return Arrays.copyOfRange(uids, Math.max(0, end - PAGE_SIZE), end);
    }
    int next(int offset) { return offset + page(offset).length; }
}
