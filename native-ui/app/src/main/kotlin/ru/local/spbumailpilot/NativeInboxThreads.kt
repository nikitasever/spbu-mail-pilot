package ru.local.spbumailpilot

import org.json.JSONArray
import org.json.JSONObject

/** A conversation built from already-filtered, currently visible loaded messages. */
internal data class ThreadGroupView(val anchor: MailRef, val members: List<NativeMail>)

/** One row of the inbox list: either an individual message or a multi-message conversation. */
internal sealed class InboxDisplayRow {
    abstract val key: Any

    data class Single(val mail: NativeMail) : InboxDisplayRow() {
        override val key: Any get() = listOf(mail.ref.folder, mail.ref.validity, mail.ref.uid)
    }

    data class Group(val anchor: MailRef, val members: List<NativeMail>) : InboxDisplayRow() {
        // Key on the representative first row so appending older pages does not move the item.
        override val key: Any get() = listOf("thread", members[0].ref.folder, members[0].ref.validity, members[0].ref.uid)
    }
}

/**
 * Groups the supplied visible rows through the Java policy. Grouping happens after the existing
 * filter/category/snooze filtering, so hidden or snoozed mail never leaks into a conversation.
 */
internal fun groupVisibleThreads(rows: List<NativeMail>, account: String, enabled: Boolean): List<ThreadGroupView> {
    if (rows.isEmpty()) return emptyList()
    val headers = JSONArray()
    for (mail in rows) {
        headers.put(JSONObject()
            .put("account", account)
            .put("folder", mail.ref.folder)
            .put("validity", mail.ref.validity)
            .put("uid", mail.ref.uid)
            .put("messageId", mail.messageId)
            .put("references", idArray(mail.references))
            .put("inReplyTo", mail.inReplyTo))
    }
    val groups = NativeMailThreads.groups(headers, enabled)
    val result = ArrayList<ThreadGroupView>(groups.length())
    for (g in 0 until groups.length()) {
        val group = groups.getJSONObject(g)
        val indices = group.getJSONArray("indices")
        val members = (0 until indices.length()).map { rows[indices.getInt(it)] }
        if (members.isEmpty()) continue
        val anchorJson = group.optJSONObject("anchor")
        val anchor = if (anchorJson != null) MailRef(
            anchorJson.optString("folder", members[0].ref.folder),
            anchorJson.optString("uid", members[0].ref.uid),
            anchorJson.optString("validity", members[0].ref.validity))
        else members[0].ref
        result.add(ThreadGroupView(anchor, members))
    }
    return result
}

/**
 * Builds the rendered rows for the current toggle. Grouping runs first over all supplied rows so a
 * conversation is retained as a whole when ANY member matches [matches]; the representative row is
 * still the first/newest loaded member. Counts therefore describe whole loaded groups. When grouping
 * is disabled every individual row is filtered directly by [matches].
 */
internal fun buildDisplayRows(
    rows: List<NativeMail>,
    account: String,
    threadsEnabled: Boolean,
    matches: (NativeMail) -> Boolean = { true }
): List<InboxDisplayRow> {
    if (!threadsEnabled) return rows.filter(matches).map { InboxDisplayRow.Single(it) }
    val result = ArrayList<InboxDisplayRow>()
    for (group in groupVisibleThreads(rows, account, true)) {
        if (!group.members.any(matches)) continue
        if (group.members.size <= 1) result.add(InboxDisplayRow.Single(group.members.first()))
        else result.add(InboxDisplayRow.Group(group.anchor, group.members))
    }
    return result
}

internal fun sameRef(left: MailRef, right: MailRef): Boolean =
    left.folder == right.folder && left.validity == right.validity && left.uid == right.uid

internal fun refKey(ref: MailRef): List<String> = listOf(ref.folder, ref.validity, ref.uid)

/**
 * Resolves the original chain that contains [anchor] by grouping [loadedRows], then keeps only the
 * members present in [displayRefs] (refs of the currently eligible, already snooze-filtered display
 * rows). This supports a single surviving member and never returns a snoozed member, because
 * [displayRefs] is built from rows that already exclude snoozed mail.
 */
internal fun conversationMembers(
    loadedRows: List<NativeMail>,
    account: String,
    anchor: MailRef,
    displayRefs: Set<List<String>>
): List<NativeMail> {
    val chain = groupVisibleThreads(loadedRows, account, true).find { group ->
        group.members.any { sameRef(it.ref, anchor) }
    } ?: return emptyList()
    return chain.members.filter { displayRefs.contains(refKey(it.ref)) }
}

private fun idArray(values: List<String>): JSONArray {
    val array = JSONArray()
    for (value in values) array.put(value)
    return array
}
