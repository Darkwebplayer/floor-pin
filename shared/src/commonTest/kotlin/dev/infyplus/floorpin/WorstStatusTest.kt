package dev.infyplus.floorpin

import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.domain.worstStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorstStatusTest {
    @Test fun emptyIsNull() = assertNull(worstStatus(emptyList()))

    @Test fun openWins() = assertEquals(
        IssueStatus.OPEN,
        worstStatus(listOf(IssueStatus.RESOLVED, IssueStatus.OPEN, IssueStatus.IN_PROGRESS)),
    )

    @Test fun inProgressOverResolved() = assertEquals(
        IssueStatus.IN_PROGRESS,
        worstStatus(listOf(IssueStatus.RESOLVED, IssueStatus.IN_PROGRESS, IssueStatus.CLOSED)),
    )

    @Test fun resolvedOverClosed() = assertEquals(
        IssueStatus.RESOLVED,
        worstStatus(listOf(IssueStatus.CLOSED, IssueStatus.RESOLVED)),
    )

    @Test fun wireRoundTrip() {
        assertEquals(IssueStatus.IN_PROGRESS, IssueStatus.fromWire("in_progress"))
        assertEquals(IssueStatus.OPEN, IssueStatus.fromWire("nonsense"))
    }
}
