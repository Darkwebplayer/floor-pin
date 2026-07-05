package dev.infyplus.floorpin.domain

data class SelectOption(val value: String, val label: String)

object IssueCategory {
    val options = listOf(
        SelectOption("architectural", "Architectural"),
        SelectOption("cleaning", "Cleaning"),
        SelectOption("mep_electrical", "MEP-Electrical"),
    )

    fun labelFor(value: String?): String? = value?.let { v -> options.firstOrNull { it.value == v }?.label ?: v }
}

object IssueType {
    val options = listOf(
        SelectOption("acoustic_walling", "Acoustic Walling"),
        SelectOption("carpet", "Carpet"),
        SelectOption("ceiling", "Ceiling"),
        SelectOption("doors", "Doors"),
    )

    fun labelFor(value: String?): String? = value?.let { v -> options.firstOrNull { it.value == v }?.label ?: v }
}

object IssueItem {
    val options = listOf(
        SelectOption("general_snag", "General Snag"),
        SelectOption("custom", "Custom"),
    )

    fun labelFor(value: String?): String? = value?.let { v -> options.firstOrNull { it.value == v }?.label ?: v }
}
