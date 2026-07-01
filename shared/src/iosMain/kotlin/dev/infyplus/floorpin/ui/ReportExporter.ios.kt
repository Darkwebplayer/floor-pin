package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberReportExporter(): (html: String, jobName: String) -> Unit = { _, _ -> }
