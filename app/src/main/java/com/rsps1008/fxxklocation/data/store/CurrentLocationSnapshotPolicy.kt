package com.rsps1008.fxxklocation.data.store

/**
 * Decides when the current mock location needs a durable DataStore snapshot.
 * The monotonic elapsed time supplied by the caller makes this deterministic in
 * tests and avoids dependence on wall-clock changes.
 */
internal class CurrentLocationSnapshotPolicy(
    private val intervalMillis: Long
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be positive" }
    }

    private var lastAttemptedAtMillis: Long? = null

    fun shouldPersist(nowMillis: Long, force: Boolean = false): Boolean {
        if (force) return true

        val lastAttemptedAt = lastAttemptedAtMillis ?: return true
        return nowMillis - lastAttemptedAt >= intervalMillis
    }

    fun markAttempted(nowMillis: Long) {
        lastAttemptedAtMillis = nowMillis
    }

    fun reset() {
        lastAttemptedAtMillis = null
    }
}
