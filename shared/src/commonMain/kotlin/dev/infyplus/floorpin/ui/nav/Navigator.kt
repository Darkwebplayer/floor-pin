package dev.infyplus.floorpin.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Destinations. Top-level (Projects/Staff) reset the stack; the rest drill in. */
sealed interface Screen {
    data object Projects : Screen
    data class FloorPlans(val projectId: String, val projectName: String) : Screen
    data class Viewer(val floorPlanId: String, val floorPlanName: String) : Screen
    data class Report(val floorPlanId: String) : Screen
    data object Staff : Screen
}

/** Minimal back-stack navigator — simpler than the alpha multiplatform nav lib. */
class Navigator(start: Screen) {
    val stack: SnapshotStateList<Screen> = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) { stack.add(screen) }
    fun replaceRoot(screen: Screen) { stack.clear(); stack.add(screen) }
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
