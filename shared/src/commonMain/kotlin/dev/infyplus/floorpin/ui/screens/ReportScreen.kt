package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.Config
import dev.infyplus.floorpin.db.FloorPlan
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.db.Photo
import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppCard
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.StatusBadge
import dev.infyplus.floorpin.ui.rememberReportExporter
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Danger
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.Success
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
private fun fmtDate(ms: Long): String =
    if (ms <= 0) "—" else runCatching { Instant.fromEpochMilliseconds(ms).toString().take(10) }.getOrDefault("—")

@Composable
fun ReportScreen(container: AppContainer, floorPlanId: String, onBack: () -> Unit) {
    val locations by remember { container.data.locations.observeByFloorPlan(floorPlanId) }.collectAsStateWithLifecycle(emptyList())
    val issues by remember { container.data.issues.observeByFloorPlan(floorPlanId) }.collectAsStateWithLifecycle(emptyList())
    val photos by remember { container.data.issues.observePhotosByFloorPlan(floorPlanId) }.collectAsStateWithLifecycle(emptyList())
    var floorPlan by remember { mutableStateOf<FloorPlan?>(null) }
    LaunchedEffect(floorPlanId) { floorPlan = container.data.floorPlans.byId(floorPlanId) }

    val issuesByLoc = remember(issues) { issues.groupBy { it.locationId } }
    val photosByIssue = remember(photos) { photos.groupBy { it.issueId } }
    val withIssues = remember(locations, issuesByLoc) { locations.filter { (issuesByLoc[it.id]?.size ?: 0) > 0 } }

    val total = issues.size
    val open = issues.count { it.status == IssueStatus.OPEN.wire }
    val resolved = issues.count { it.status == IssueStatus.RESOLVED.wire }

    val exporter = rememberReportExporter()
    val planName = floorPlan?.name ?: "Floor plan"

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = "Inspection report", crumb = planName, onBack = onBack) {
            AppButton("Export PDF", onClick = {
                exporter(buildReportHtml(planName, withIssues, issuesByLoc, locations.size, total, open, resolved), "FloorPin — $planName")
            }, small = true, leadingIcon = AppIcons.Download)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        ) {
            item {
                AppCard(elevated = true, modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    Column(Modifier.padding(32.dp)) {
                        Text("DEFECT INSPECTION REPORT", style = MaterialTheme.typography.labelSmall, color = Accent)
                        Text(planName, style = MaterialTheme.typography.displayMedium, color = Ink, modifier = Modifier.padding(top = 8.dp))
                        // summary
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SummaryBox("Locations", locations.size.toString(), Modifier.weight(1f))
                            SummaryBox("Total issues", total.toString(), Modifier.weight(1f))
                            SummaryBox("Open", open.toString(), Modifier.weight(1f), Danger)
                            SummaryBox("Resolved", resolved.toString(), Modifier.weight(1f), Success)
                        }
                        floorPlan?.let { fp ->
                            floorPlanImageUrl(fp)?.let { url ->
                                AsyncImage(url, "Floor plan", Modifier.fillMaxWidth().padding(top = 24.dp).background(SurfaceWarm, RoundedCornerShape(6.dp)), contentScale = ContentScale.FillWidth)
                            }
                        }
                    }
                }
            }

            if (withIssues.isEmpty()) {
                item { Text("No issues logged for this plan yet.", color = Muted, modifier = Modifier.padding(24.dp)) }
            }

            items(withIssues, key = { it.id }) { loc ->
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(top = 24.dp)) {
                    Text(loc.name, style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(BorderColor))
                    (issuesByLoc[loc.id] ?: emptyList()).forEach { issue ->
                        ReportIssueRow(issue, photosByIssue[issue.id] ?: emptyList())
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = Ink) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(value, style = MaterialTheme.typography.headlineLarge, color = valueColor, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ReportIssueRow(issue: Issue, photos: List<Photo>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(issue.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (!issue.description.isNullOrBlank()) Text(issue.description!!, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(IssueStatus.fromWire(issue.status))
                Text(fmtDate(issue.createdAt), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        if (photos.isNotEmpty()) {
            photos.first().imageKey?.let {
                AsyncImage("${Config.BASE_URL}/files/$it", "Evidence", Modifier.size(120.dp).background(SurfaceWarm, RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

private fun esc(s: String?): String = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun buildReportHtml(
    planName: String,
    withIssues: List<Location>,
    issuesByLoc: Map<String, List<Issue>>,
    locationCount: Int,
    total: Int,
    open: Int,
    resolved: Int,
): String {
    val blocks = withIssues.joinToString("") { loc ->
        val rows = (issuesByLoc[loc.id] ?: emptyList()).joinToString("") { i ->
            """<div class="issue"><h4>${esc(i.title)}</h4><p>${esc(i.description)}</p>
               <span class="badge ${i.status}">${esc(IssueStatus.fromWire(i.status).label)}</span> · ${fmtDate(i.createdAt)}</div>"""
        }
        """<div class="loc"><h3>${esc(loc.name)}</h3>$rows</div>"""
    }
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          body{font-family:-apple-system,system-ui,sans-serif;color:#273951;padding:24px}
          h1{color:#061b31;font-weight:300;font-size:32px;margin:0 0 4px}
          .eyebrow{color:#533afd;font-size:11px;letter-spacing:.1em;text-transform:uppercase}
          .summary{display:flex;gap:16px;margin:24px 0}
          .box{border:1px solid #e5edf5;border-radius:6px;padding:12px;flex:1}
          .box .k{font-size:11px;text-transform:uppercase;color:#64748d}
          .box .v{font-size:28px;color:#061b31}
          .loc{margin-top:24px} .loc h3{font-size:20px;color:#061b31;border-bottom:1px solid #e5edf5;padding-bottom:6px}
          .issue{padding:12px 0;border-bottom:1px dashed #e5edf5}
          .issue h4{margin:0;color:#061b31} .issue p{margin:6px 0;color:#273951;font-size:14px}
          .badge{font-size:12px;padding:2px 8px;border-radius:4px;background:#f6f9fc;border:1px solid #e5edf5}
          .badge.open{color:#b3164a} .badge.resolved{color:#108c3d} .badge.in_progress{color:#9b6829}
        </style></head><body>
        <div class="eyebrow">Defect inspection report</div>
        <h1>${esc(planName)}</h1>
        <div class="summary">
          <div class="box"><div class="k">Locations</div><div class="v">$locationCount</div></div>
          <div class="box"><div class="k">Total issues</div><div class="v">$total</div></div>
          <div class="box"><div class="k">Open</div><div class="v" style="color:#ea2261">$open</div></div>
          <div class="box"><div class="k">Resolved</div><div class="v" style="color:#15be53">$resolved</div></div>
        </div>
        $blocks
        <p style="margin-top:32px;color:#64748d;font-size:12px">FloorPin · Defect management &amp; inspection</p>
        </body></html>
    """.trimIndent()
}
