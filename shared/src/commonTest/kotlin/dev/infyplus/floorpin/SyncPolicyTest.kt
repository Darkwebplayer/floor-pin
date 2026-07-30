package dev.infyplus.floorpin

import dev.infyplus.floorpin.data.sync.MAX_ATTEMPTS
import dev.infyplus.floorpin.data.sync.OpOutcome
import dev.infyplus.floorpin.data.sync.entityRank
import dev.infyplus.floorpin.data.sync.opOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outbox dead-letter policy. A mistake here deletes work the user cannot get back, and the
 * failure is invisible — the offline banner clears and the app looks synced.
 */
class SyncPolicyTest {

    @Test fun terminalVerdictsDequeue() {
        listOf("applied", "stale", "missing").forEach {
            assertEquals(OpOutcome.Dequeue, opOutcome(it, attempts = 0L), "verdict=$it")
        }
    }

    @Test fun rejectedCountsTowardDeadLetter() =
        assertEquals(OpOutcome.Bump, opOutcome("rejected", attempts = 0L))

    @Test fun rejectedDeadLettersOnLastAttempt() =
        assertEquals(OpOutcome.DeadLetter, opOutcome("rejected", attempts = MAX_ATTEMPTS - 1L))

    // The regression that mattered: the server aborts a batch mid-way and returns no result for
    // this op. Treating that as a rejection is what destroyed whole offline sessions.
    @Test fun missingVerdictRetriesWithoutCountingAnAttempt() {
        assertEquals(OpOutcome.Retry, opOutcome(null, attempts = 0L))
        assertEquals(OpOutcome.Retry, opOutcome(null, attempts = MAX_ATTEMPTS - 1L))
        assertEquals(OpOutcome.Retry, opOutcome(null, attempts = MAX_ATTEMPTS + 99L))
    }

    @Test fun unknownVerdictIsTreatedAsNoVerdict() =
        assertEquals(OpOutcome.Retry, opOutcome("something_new_from_the_server", attempts = 0L))

    @Test fun parentsSortBeforeChildren() {
        assertTrue(entityRank("projects") < entityRank("locations"))
        assertTrue(entityRank("locations") < entityRank("issues"))
    }

    // An issue created in the same millisecond as its location must still be sent second.
    @Test fun entityRankBeatsEqualTimestamps() {
        val queue = listOf("issues" to 100L, "locations" to 100L, "projects" to 100L)
        val sorted = queue.sortedWith(compareBy({ entityRank(it.first) }, { it.second }))
        assertEquals(listOf("projects", "locations", "issues"), sorted.map { it.first })
    }
}
