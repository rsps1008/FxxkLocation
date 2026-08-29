package com.rsps1008.fxxklocation.data.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationSnapshotPolicyTest {
    @Test
    fun attemptsImmediatelyThenThrottles() {
        val policy = CurrentLocationSnapshotPolicy(intervalMillis = 30_000L)

        assertTrue(policy.shouldPersist(nowMillis = 1_000L))
        policy.markAttempted(nowMillis = 1_000L)

        assertFalse(policy.shouldPersist(nowMillis = 30_999L))
        assertTrue(policy.shouldPersist(nowMillis = 31_000L))
    }

    @Test
    fun forcePersistsBeforeInterval() {
        val policy = CurrentLocationSnapshotPolicy(intervalMillis = 30_000L)
        policy.markAttempted(nowMillis = 1_000L)

        assertTrue(policy.shouldPersist(nowMillis = 2_000L, force = true))
    }

    @Test
    fun resetAllowsImmediateAttempt() {
        val policy = CurrentLocationSnapshotPolicy(intervalMillis = 30_000L)
        policy.markAttempted(nowMillis = 1_000L)

        policy.reset()

        assertTrue(policy.shouldPersist(nowMillis = 2_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveInterval() {
        CurrentLocationSnapshotPolicy(intervalMillis = 0L)
    }
}
