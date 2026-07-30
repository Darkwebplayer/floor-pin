package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.infyplus.floorpin.domain.IssuePriority
import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppCard
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.ImageLightbox
import dev.infyplus.floorpin.ui.components.StatusBadge
import dev.infyplus.floorpin.data.remote.sniffImageMime
import dev.infyplus.floorpin.ui.components.downscaleImage
import dev.infyplus.floorpin.ui.components.photoImageUrl
import dev.infyplus.floorpin.ui.rememberReportExporter
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Danger
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.Success
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import floorpin.shared.generated.resources.Res
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Longest edge for issue photos embedded in the PDF. They render into a 180x200 CSS px box,
 *  which is ~560px at the print raster's 300dpi — 800 leaves headroom without bloating the HTML. */
private const val REPORT_PHOTO_EDGE = 800
private const val REPORT_PHOTO_QUALITY = 75

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
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = "Inspection report", crumb = planName, onBack = onBack) {
            AppButton(
                if (exporting) "Exporting…" else "Export PDF",
                loading = exporting,
                onClick = {
                    exportError = null; exporting = true
                    scope.launch {
                        val failed = exportPdf(container, exporter, floorPlan, planName, locations, issues, withIssues, issuesByLoc, photosByIssue, total, open, resolved)
                        if (failed > 0) exportError = "$failed image(s) could not be loaded; exported with placeholders."
                        exporting = false
                    }
                },
                small = true, leadingIcon = AppIcons.Download,
            )
        }
        exportError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
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
                        Text(planName, style = MaterialTheme.typography.headlineLarge, color = Ink, modifier = Modifier.padding(top = 8.dp))
                        // summary
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SummaryBox("Locations", locations.size.toString(), Modifier.weight(1f))
                            SummaryBox("Total issues", total.toString(), Modifier.weight(1f))
                            SummaryBox("Open", open.toString(), Modifier.weight(1f), Danger)
                            SummaryBox("Resolved", resolved.toString(), Modifier.weight(1f), Success)
                        }
                        floorPlan?.let { fp ->
                            floorPlanImageUrl(fp)?.let { url ->
                                PlanWithPins(fp, url, locations, issues, issuesByLoc)
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
            Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Dot/pin color for an issue status (mirrors the viewer legend). */
private fun pinStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    IssueStatus.OPEN.wire -> Danger
    IssueStatus.IN_PROGRESS.wire -> androidx.compose.ui.graphics.Color(0xFFC98A2C)
    IssueStatus.RESOLVED.wire -> Success
    IssueStatus.CLOSED.wire -> Muted
    else -> Accent
}

/** Report plan image with every location pin + issue dot overlaid, matching the live plan state. */
@Composable
private fun PlanWithPins(fp: FloorPlan, url: String, locations: List<Location>, issues: List<Issue>, issuesByLoc: Map<String, List<Issue>>) {
    val ar = ((fp.width ?: 0.0) / (fp.height ?: 0.0)).toFloat().let { if (it.isFinite() && it > 0f) it else 1.4f }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 24.dp).aspectRatio(ar).background(SurfaceWarm, RoundedCornerShape(6.dp))) {
        AsyncImage(url, "Floor plan", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        val w = maxWidth; val h = maxHeight
        locations.forEach { loc ->
            val statuses = (issuesByLoc[loc.id] ?: emptyList()).map { it.status }
            val c = when {
                statuses.any { it == IssueStatus.OPEN.wire } -> Danger
                statuses.any { it == IssueStatus.IN_PROGRESS.wire } -> androidx.compose.ui.graphics.Color(0xFFC98A2C)
                statuses.any { it == IssueStatus.RESOLVED.wire } -> Success
                statuses.isNotEmpty() -> Muted
                else -> Accent
            }
            Box(
                Modifier.offset(x = w * (loc.x.toFloat() / 100f) - 7.dp, y = h * (loc.y.toFloat() / 100f) - 7.dp)
                    .size(14.dp).background(c, CircleShape).border(2.dp, White, CircleShape),
            )
        }
        issues.forEach { i ->
            val ix = i.x ?: return@forEach
            val iy = i.y ?: return@forEach
            Box(
                Modifier.offset(x = w * (ix.toFloat() / 100f) - 5.dp, y = h * (iy.toFloat() / 100f) - 5.dp)
                    .size(10.dp).background(pinStatusColor(i.status), CircleShape).border(1.5.dp, White, CircleShape),
            )
        }
    }
}

@Composable
private fun ReportIssueRow(issue: Issue, photos: List<Photo>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("#${issue.id.take(8)} · ${issue.title}", style = MaterialTheme.typography.titleMedium, color = Ink)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(IssueStatus.fromWire(issue.status))
                Text(IssuePriority.fromWire(issue.priority).label, style = MaterialTheme.typography.labelSmall, color = Ink)
                Text(fmtDate(issue.createdAt), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            listOfNotNull(issue.category?.takeIf { it.isNotBlank() }, issue.type?.takeIf { it.isNotBlank() })
                .joinToString(" · ").takeIf { it.isNotBlank() }
                ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
            issue.item?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            issue.assignedTo?.takeIf { it.isNotBlank() }?.let { Text("Assigned to $it", style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(top = 4.dp)) }
            if (!issue.description.isNullOrBlank()) Text(issue.description!!, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp))
            coord(issue.x)?.let { cx -> coord(issue.y)?.let { cy -> Text("x:$cx / y:$cy", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 4.dp)) } }
        }
        if (photos.isNotEmpty()) {
            var lightboxUrl by remember { mutableStateOf<String?>(null) }
            photoImageUrl(photos.first())?.let { url ->
                AsyncImage(url, "Evidence", Modifier.size(120.dp).background(SurfaceWarm, RoundedCornerShape(6.dp)).clickable { lightboxUrl = url }, contentScale = ContentScale.Crop)
            }
            ImageLightbox(lightboxUrl) { lightboxUrl = null }
        }
    }
}

private fun esc(s: String?): String = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Fill color for a status dot/badge (mirrors the viewer legend). */
private fun statusColor(status: String): String = when (status) {
    IssueStatus.OPEN.wire -> "#ea2261"
    IssueStatus.IN_PROGRESS.wire -> "#c98a2c"
    IssueStatus.RESOLVED.wire -> "#15be53"
    IssueStatus.CLOSED.wire -> "#64748d"
    else -> "#533afd"
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)
private suspend fun exportPdf(
    container: AppContainer,
    exporter: (String, String, String) -> Unit,
    floorPlan: FloorPlan?,
    planName: String,
    allLocations: List<Location>,
    allIssues: List<Issue>,
    withIssues: List<Location>,
    issuesByLoc: Map<String, List<Issue>>,
    photosByIssue: Map<String, List<Photo>>,
    total: Int,
    open: Int,
    resolved: Int,
): Int {
    val planImageUrl = floorPlan?.let { floorPlanImageUrl(it) }
    val seen = mutableSetOf<String>()
    planImageUrl?.let { seen.add(it) }
    for (loc in withIssues) {
        for (issue in issuesByLoc[loc.id] ?: emptyList()) {
            // all evidence photos (the reference report shows a full gallery, not just the first)
            photosByIssue[issue.id]?.forEach { p ->
                seen.add("${Config.BASE_URL}/files/${p.imageKey}")
            }
        }
    }

    // Fetch concurrently — awaiting each image in turn made a 40-photo export take tens of seconds.
    // Bounded so a large report doesn't open 40 sockets against the Worker at once.
    val gate = Semaphore(6)
    val fetched = coroutineScope {
        seen.map { url ->
            // Off the main dispatcher: decoding and base64-ing megabytes of image would jank the UI.
            async(Dispatchers.Default) {
                gate.withPermit {
                    runCatching {
                        val bytes: ByteArray = container.http.get(url).body()
                        // Issue photos print into a ~180x200px box, so the full 1600px upload was
                        // ~8x the pixels needed; base64'd, that alone could OOM the print WebView.
                        // The plan image prints near full page width, so it stays as uploaded.
                        val out = if (url == planImageUrl) bytes else downscaleImage(bytes, REPORT_PHOTO_EDGE, REPORT_PHOTO_QUALITY)
                        url to "data:${sniffImageMime(out)};base64,${Base64.encode(out)}"
                    }.getOrNull()
                }
            }
        }.awaitAll()
    }
    val dataUris = fetched.filterNotNull().toMap()
    val failed = fetched.count { it == null }

    // Bundled asset, not a network fetch — a failure here shouldn't count toward `failed`.
    val footerUri = runCatching { "data:image/png;base64,${Base64.encode(Res.readBytes("drawable/footer.png"))}" }
        .getOrDefault("")

    exporter(
        buildReportHtml(planName, planImageUrl, allLocations, allIssues, withIssues, issuesByLoc, photosByIssue, dataUris, allLocations.size, total, open, resolved, footerUri),
        "FloorPin — $planName",
        Config.BASE_URL
    )
    return failed
}

private fun coord(v: Double?): Int? = v?.let { (it + 0.5).toInt() }

private fun buildReportHtml(
    planName: String,
    planImageUrl: String?,
    allLocations: List<Location>,
    allIssues: List<Issue>,
    withIssues: List<Location>,
    issuesByLoc: Map<String, List<Issue>>,
    photosByIssue: Map<String, List<Photo>>,
    dataUris: Map<String, String>,
    locationCount: Int,
    total: Int,
    open: Int,
    resolved: Int,
    footerUri: String,
): String {
    // One page per location, each starting fresh — never on the plan-image page.
    val pages = withIssues.joinToString("") { loc ->
        val rows = (issuesByLoc[loc.id] ?: emptyList()).joinToString("") { i ->
            val photos = photosByIssue[i.id] ?: emptyList()
            val primary = photos.firstOrNull()?.let { dataUris["${Config.BASE_URL}/files/${it.imageKey}"] }
                ?.let { """<img class="primary" src="$it" />""" } ?: ""
            val gallery = photos.drop(1).mapNotNull { dataUris["${Config.BASE_URL}/files/${it.imageKey}"] }
                .joinToString("") { """<img src="$it" />""" }
                .let { if (it.isBlank()) "" else """<div class="gallery">$it</div>""" }

            val catType = listOfNotNull(i.category?.takeIf { it.isNotBlank() }, i.type?.takeIf { it.isNotBlank() })
                .joinToString(" · ").let { if (it.isBlank()) "" else """<div class="cat">$it</div>""" }
            val item = i.item?.takeIf { it.isNotBlank() }?.let { """<div class="item">${esc(it)}</div>""" } ?: ""
            val assigned = i.assignedTo?.takeIf { it.isNotBlank() }
                ?.let { """<div class="meta"><span class="k">Assigned to</span> <span class="assignee">${esc(it)}</span></div>""" } ?: ""
            val cx = coord(i.x); val cy = coord(i.y)
            val coords = if (cx != null && cy != null) """<div class="coords">x:$cx / y:$cy</div>""" else ""

            """
            <div class="issue">
              <div class="ihead">#${esc(i.id.take(8))} - ${esc(i.title)}</div>
              <div class="itop">
                <div class="ileft">
                  <div class="badges">
                    <span class="badge ${i.status}">${esc(IssueStatus.fromWire(i.status).label)}</span>
                    <span class="prio">${esc(IssuePriority.fromWire(i.priority).label)}</span>
                  </div>
                  $assigned
                  $catType
                  $item
                  <div class="meta"><span class="k">Location</span> ${esc(loc.name)}</div>
                  <div class="meta"><span class="k">Created</span> ${fmtDate(i.createdAt)}</div>
                  ${if (!i.description.isNullOrBlank()) """<p class="desc">${esc(i.description)}</p>""" else ""}
                </div>
                <div class="iright">$coords$primary</div>
              </div>
              $gallery
            </div>
            """
        }
        """<section class="page loc"><div class="locbar">${esc(planName)} / ${esc(loc.name)}</div>$rows</section>"""
    }

    // Plan image with live location pins + issue dots overlaid, matching the current plan state.
    val overlay = buildString {
        for (loc in allLocations) {
            val statuses = (issuesByLoc[loc.id] ?: emptyList()).map { IssueStatus.fromWire(it.status).wire }
            val worst = statuses.firstOrNull { it == IssueStatus.OPEN.wire }
                ?: statuses.firstOrNull { it == IssueStatus.IN_PROGRESS.wire }
                ?: statuses.firstOrNull { it == IssueStatus.RESOLVED.wire }
                ?: statuses.firstOrNull()
            val c = worst?.let { statusColor(it) } ?: "#533afd"
            append("""<div class="pin" style="left:${loc.x}%;top:${loc.y}%;background:$c"><span class="pinlabel">${esc(loc.name)}</span></div>""")
        }
        for (i in allIssues) {
            val ix = i.x ?: continue; val iy = i.y ?: continue
            append("""<div class="dot" style="left:$ix%;top:$iy%;background:${statusColor(i.status)}"></div>""")
        }
    }
    val planBlock = planImageUrl?.let { url ->
        dataUris[url]?.let { """<div class="planwrap"><img class="plan" src="$it" />$overlay</div>""" }
    } ?: ""

    return """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          body{font-family:-apple-system,system-ui,sans-serif;color:#273951;margin:0}
          /* The footer needs two cooperating parts — neither works alone:
             1. .pgfoot is position:fixed, which is what makes Chromium repeat it on
                every sheet. Its offsets MUST stay positive: a negative `bottom` drops
                it outside the page box and Chromium reflows it onto the *next* sheet's
                top, so page 1 loses it entirely (verified at -11px and -20px).
             2. Being fixed, it reserves no space in the flow, so text would run under
                it. The repeating <tfoot> spacer reserves an equal band on every page.
             .fspace must stay >= footer height + bottom offset (22 + 5). */
          @page{margin-bottom:13.5mm}
          table.rep{width:100%;border-collapse:collapse}
          tfoot{display:table-footer-group}
          tfoot td{padding:0}
          .fspace{height:30px}
          .pgfoot{position:fixed;left:5px;bottom:5px;display:flex;align-items:center;gap:6px}
          .pgfoot img{height:22px;width:22px}
          .fmeta{display:flex;flex-direction:column;line-height:1.15}
          .fname{font-size:11px;font-weight:700;color:#061b31}
          .ftag{font-size:8px;color:#64748d}
          .page{padding:24px 28px}
          .page.loc{page-break-before:always}
          h1{color:#061b31;font-weight:300;font-size:32px;margin:0 0 4px}
          .eyebrow{color:#533afd;font-size:11px;letter-spacing:.1em;text-transform:uppercase}
          .summary{display:flex;gap:16px;margin:24px 0}
          .box{border:1px solid #e5edf5;border-radius:6px;padding:12px;flex:1}
          .box .k{font-size:11px;text-transform:uppercase;color:#64748d}
          .box .v{font-size:28px;color:#061b31}
          .planwrap{position:relative;display:inline-block;max-width:100%;margin:8px auto 0;text-align:left;break-inside:avoid;page-break-inside:avoid}
          .planrow{text-align:center}
          .plan{display:block;max-width:100%;max-height:150mm;border:1px solid #e5edf5;border-radius:6px}
          .pin{position:absolute;transform:translate(-50%,-100%);width:14px;height:14px;border-radius:50% 50% 50% 0;
               transform-origin:bottom;border:2px solid #fff;box-shadow:0 1px 2px rgba(0,0,0,.3)}
          .pin{transform:translate(-50%,-100%) rotate(-45deg)}
          .pinlabel{position:absolute;left:50%;top:120%;transform:translateX(-50%) rotate(45deg);white-space:nowrap;
               font-size:9px;color:#061b31;background:rgba(255,255,255,.9);padding:0 3px;border-radius:3px}
          .dot{position:absolute;transform:translate(-50%,-50%);width:9px;height:9px;border-radius:50%;border:1.5px solid #fff}
          .locbar{background:#eef1e0;border:1px solid #e0e4cf;border-radius:4px;padding:8px 12px;font-size:15px;color:#061b31;margin-bottom:16px}
          .issue{padding:4px 0 16px;border-bottom:1px solid #e5edf5;margin-bottom:16px}
          .ihead{font-size:17px;font-weight:700;color:#061b31;border-bottom:2px solid #9aa4b1;padding-bottom:6px;margin-bottom:10px}
          .itop{display:flex;gap:16px;justify-content:space-between}
          .ileft{flex:1}
          .iright{text-align:right;flex-shrink:0}
          .coords{font-size:12px;color:#061b31;margin-bottom:6px}
          .primary{max-width:180px;max-height:200px;border-radius:4px;border:1px solid #e5edf5}
          .badges{display:flex;align-items:center;gap:10px;margin-bottom:8px}
          .badge{font-size:13px;font-weight:700;padding:4px 14px;border-radius:3px;background:#f6f9fc;border:1px solid #e5edf5}
          .badge.open{background:#f6a623;color:#061b31} .badge.resolved{background:#15be53;color:#fff}
          .badge.in_progress{background:#c98a2c;color:#fff} .badge.closed{background:#e5edf5;color:#64748d}
          .prio{font-size:14px;color:#273951}
          .cat,.item{font-size:14px;color:#273951;margin:2px 0}
          .meta{font-size:14px;color:#273951;margin:2px 0} .meta .k{color:#64748d}
          .assignee{color:#c0392b}
          .desc{font-size:14px;color:#273951;margin:8px 0 0}
          .gallery{border:1px solid #9b1c1c;border-radius:4px;padding:10px;margin-top:12px;display:flex;flex-wrap:wrap;gap:10px}
          .gallery img{max-width:180px;max-height:200px;border-radius:3px}
        </style></head><body>
        ${if (footerUri.isBlank()) "" else """<div class="pgfoot"><img src="$footerUri" /><div class="fmeta"><span class="fname">uba</span><span class="ftag">snagging inspection services</span></div></div>"""}
        <table class="rep">
        ${if (footerUri.isBlank()) "" else """<tfoot><tr><td><div class="fspace"></div></td></tr></tfoot>"""}
        <tbody><tr><td>
        <section class="page">
          <div class="eyebrow">Defect inspection report</div>
          <h1>${esc(planName)}</h1>
          <div class="summary">
            <div class="box"><div class="k">Locations</div><div class="v">$locationCount</div></div>
            <div class="box"><div class="k">Total issues</div><div class="v">$total</div></div>
            <div class="box"><div class="k">Open</div><div class="v" style="color:#ea2261">$open</div></div>
            <div class="box"><div class="k">Resolved</div><div class="v" style="color:#15be53">$resolved</div></div>
          </div>
          <div class="planrow">$planBlock</div>
        </section>
        $pages
        </td></tr></tbody></table>
        </body></html>
    """.trimIndent()
}
