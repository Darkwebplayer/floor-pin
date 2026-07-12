package dev.infyplus.floorpin.domain

data class SelectOption(val value: String, val label: String)

object IssueCategory {
    fun labelFor(value: String?): String? = value
}

object IssueType {
    val options = listOf(
        SelectOption("Architectural", "Architectural"),
        SelectOption("BARRING", "BARRING"),
        SelectOption("Below Ceiling", "Below Ceiling"),
        SelectOption("CLEANING", "CLEANING"),
        SelectOption("Drilling", "Drilling"),
        SelectOption("Earthworks", "Earthworks"),
        SelectOption("General observations", "General observations"),
        SelectOption("Interior-Design", "Interior-Design"),
        SelectOption("MEP-ELV", "MEP-ELV"),
        SelectOption("MEP-Electrical", "MEP-Electrical"),
        SelectOption("MEP-Mechanical", "MEP-Mechanical"),
        SelectOption("SAFETY", "SAFETY"),
        SelectOption("SUPPORT", "SUPPORT"),
        SelectOption("Steel and Glass", "Steel and Glass"),
    )

    fun labelFor(value: String?): String? = value?.let { v -> options.firstOrNull { it.value == v }?.label ?: v }
}

object IssueItem {
    fun labelFor(value: String?): String? = value
}
