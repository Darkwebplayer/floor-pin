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
        listOf("applied", "stale").forEach {
            assertEquals(OpOutcome.Dequeue, opOutcome("update", it, attempts = 0L), "verdict=$it")
        }
    }

    // A delete the server can't find already reached the state we wanted.
    @Test fun missingDeleteIsDone() =
        assertEquals(OpOutcome.Dequeue, opOutcome("delete", "missing", attempts = 0L))

    // But an update can be `missing` because the create ahead of it in the queue came back
    // missing_parent. Dropping it loses the edit: the create later applies the pre-edit values.
    @Test fun missingUpdateRetriesRatherThanLosingTheEdit() {
        assertEquals(OpOutcome.Bump, opOutcome("update", "missing", attempts = 0L))
        assertEquals(OpOutcome.DeadLetter, opOutcome("update", "missing", attempts = MAX_ATTEMPTS - 1L))
    }

    // The server classifies `rejected` as permanent, so retrying it is five wasted round trips.
    @Test fun rejectedDeadLettersImmediately() =
        assertEquals(OpOutcome.DeadLetter, opOutcome("update", "rejected", attempts = 0L))

    // Retryable, but bounded: if the parent op was itself dead-lettered the parent never arrives.
    @Test fun missingParentRetriesButIsBounded() {
        assertEquals(OpOutcome.Bump, opOutcome("update", "missing_parent", attempts = 0L))
        assertEquals(OpOutcome.DeadLetter, opOutcome("update", "missing_parent", attempts = MAX_ATTEMPTS - 1L))
    }

    @Test fun transientServerFailureRetriesButIsBounded() {
        assertEquals(OpOutcome.Bump, opOutcome("update", "failed", attempts = 0L))
        assertEquals(OpOutcome.DeadLetter, opOutcome("update", "failed", attempts = MAX_ATTEMPTS - 1L))
    }

    // The regression that mattered: the server aborts a batch mid-way and returns no result for
    // this op. Treating that as a rejection is what destroyed whole offline sessions.
    @Test fun missingVerdictRetriesWithoutCountingAnAttempt() {
        assertEquals(OpOutcome.Retry, opOutcome("update", null, attempts = 0L))
        assertEquals(OpOutcome.Retry, opOutcome("update", null, attempts = MAX_ATTEMPTS - 1L))
        assertEquals(OpOutcome.Retry, opOutcome("update", null, attempts = MAX_ATTEMPTS + 99L))
    }

    // A status added server-side that this build predates must never delete work.
    @Test fun unknownVerdictIsTreatedAsNoVerdict() {
        assertEquals(OpOutcome.Retry, opOutcome("update", "something_new_from_the_server", attempts = 0L))
        assertEquals(OpOutcome.Retry, opOutcome("update", "something_new_from_the_server", attempts = MAX_ATTEMPTS + 9L))
    }

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
