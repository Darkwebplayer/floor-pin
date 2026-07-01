package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

/** Returns a function that renders the given HTML to the platform's print/PDF flow. */
@Composable
expect fun rememberReportExporter(): (html: String, jobName: String) -> Unit
