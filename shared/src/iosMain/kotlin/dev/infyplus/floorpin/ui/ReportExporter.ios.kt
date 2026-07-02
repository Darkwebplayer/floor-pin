package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberReportExporter(): (html: String, jobName: String, baseUrl: String) -> Unit = { _, _, _ -> }
