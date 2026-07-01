package dev.infyplus.floorpin.domain

/** Issue lifecycle. Wire values match the API enum (open|in_progress|resolved|closed). */
enum class IssueStatus(val wire: String, val label: String) {
    OPEN("open", "Open"),
    IN_PROGRESS("in_progress", "In progress"),
    RESOLVED("resolved", "Resolved"),
    CLOSED("closed", "Closed");

    companion object {
        fun fromWire(s: String?): IssueStatus =
            entries.firstOrNull { it.wire == s } ?: OPEN
    }
}

/** Issue priority. Wire values match the API enum (low|medium|high|critical). */
enum class IssuePriority(val wire: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    CRITICAL("critical", "Critical");

    companion object {
        fun fromWire(s: String?): IssuePriority =
            entries.firstOrNull { it.wire == s } ?: MEDIUM
    }
}

/** Worst (most urgent) status across a location's issues; null = no issues (plain pin). */
fun worstStatus(statuses: List<IssueStatus>): IssueStatus? = when {
    statuses.isEmpty() -> null
    statuses.any { it == IssueStatus.OPEN } -> IssueStatus.OPEN
    statuses.any { it == IssueStatus.IN_PROGRESS } -> IssueStatus.IN_PROGRESS
    statuses.any { it == IssueStatus.RESOLVED } -> IssueStatus.RESOLVED
    else -> IssueStatus.CLOSED
}
