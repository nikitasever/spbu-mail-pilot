package ru.local.spbumailpilot

/**
 * Pure conversation navigation guard. No Android, storage or JSON dependencies so it can be
 * unit tested on a plain JVM. It retains only scope-qualified references (owner/folder plus a
 * message reference), never subjects, bodies or raw headers.
 */
internal data class ThreadScope(val owner: String, val folder: String, val query: String, val filter: String) {
    fun usable(): Boolean = owner.isNotBlank() && folder.isNotBlank()
    fun key(): String = owner + '\u0000' + folder + '\u0000' + query + '\u0000' + filter
}

internal data class ThreadAnchor(val folder: String, val validity: String, val uid: String) {
    fun stable(): Boolean = folder.isNotBlank() && validity.matches(VALID) && uid.matches(VALID)
    companion object { private val VALID = Regex("[1-9][0-9]{0,19}") }
}

internal data class ThreadNav(
    val scope: ThreadScope? = null,
    val anchor: ThreadAnchor? = null,
    val readerFromThread: Boolean = false
)

/** Deterministic transitions used by NativeInboxModel. Pure functions, no side effects. */
internal object ThreadNavigation {
    fun reScope(current: ThreadNav, scope: ThreadScope): ThreadNav =
        if (current.scope == scope) current else ThreadNav(scope = scope)

    fun open(current: ThreadNav, scope: ThreadScope, anchor: ThreadAnchor): ThreadNav =
        if (!scope.usable() || !anchor.stable()) current
        else ThreadNav(scope = scope, anchor = anchor, readerFromThread = false)

    fun openMember(current: ThreadNav): ThreadNav =
        if (current.anchor == null) current else current.copy(readerFromThread = true)

    fun back(current: ThreadNav): ThreadNav =
        if (current.readerFromThread) current.copy(readerFromThread = false) else ThreadNav(scope = current.scope)

    fun clear(current: ThreadNav): ThreadNav = ThreadNav(scope = current.scope)

    /** A member may open only when the active conversation still matches the captured UI scope. */
    fun activeMember(current: ThreadNav, scope: ThreadScope): Boolean =
        current.anchor != null && current.scope == scope
}
