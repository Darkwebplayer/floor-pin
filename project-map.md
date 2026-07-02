# FloorPin — Project Map

## Description
Kotlin Multiplatform (Android + iOS) inspection/snagging app. Inspectors upload floor plan images, drop pins to mark issues, attach photos, and export PDF reports. Backend is Cloudflare Workers (Hono + D1 + R2 + Better Auth for Google OAuth, invite-only).

## Data Model (SQLDelight — `floorpin.sq`)

Timestamps are epoch ms (`INTEGER`). IDs are client/server UUIDs (`TEXT`). Offline-first: all writes go local + outbox → `POST /api/sync`.

| Table | Fields | Notes |
|-------|--------|-------|
| **project** | `id`, `name`, `description`, `createdAt`, `updatedAt` | Top-level container |
| **floorPlan** | `id`, `projectId`, `name`, `sub`, `imageKey`, `imageUrl`, `width`, `height`, `createdAt`, `updatedAt` | Building plan images uploaded to R2 |
| **location** | `id`, `floorPlanId`, `name`, `x`, `y`, `createdAt`, `updatedAt` | Draggable pins on the floor plan canvas |
| **issue** | `id`, `locationId`, `title`, `description`, `status`, `priority`, `type`, `category`, `item`, `assignedTo`, `x`, `y`, `createdAt`, `updatedAt`, `resolvedAt` | Snags/defects under a location pin |
| **photo** | `id`, `issueId`, `imageKey`, `imageUrl`, `caption`, `createdAt` | Images attached to issues (stored in R2) |
| **activity** | `id`, `entityType`, `entityId`, `action`, `actor`, `summary`, `createdAt` | Audit log entries |
| **outbox** | `opId`, `entity`, `op`, `entityId`, `payload`, `updatedAt`, `attempts` | Queued offline mutations for sync |

### Relationships
```
project 1──N floorPlan
floorPlan 1──N location
location 1──N issue
issue 1──N photo
```

### Issue enums
| Field | Values |
|-------|--------|
| `status` | `open`, `in_progress`, `resolved`, `closed` |
| `priority` | `low`, `medium`, `high`, `critical` |

### DB migrations (`migrations/`)
- `1.sqm` — added `width`/`height` to floorPlan, `createdAt` to location, `category`/`assignedTo` to issue, `caption` to photo
- `2.sqm` — added `item` to issue

## Important Folders
- **`shared/src/commonMain/`** — bulk of the app (KMP shared code)
  - `ui/screens/` — all screens: login, projects, floor plans, viewer (canvas), report, staff/admin
  - `ui/screens/viewer/` — floor plan canvas with pan/zoom/pin interaction (ViewerScreen, Inspector, ViewModel)
  - `ui/components/` — reusable: adaptive nav scaffold, hand-ported SVG icons, top bar, widgets, photo annotation marker
  - `ui/nav/` — custom back-stack navigator (no Jetpack Navigation)
  - `ui/theme/` — Material3 theme, custom colors, typography, spacing
  - `data/repo/` — offline-first repos + outbox pattern for sync
  - `data/remote/` — Ktor HTTP client, all DTOs + mappers
  - `data/sync/` — background outbox flush loop, sync state
  - `data/auth/` — Google sign-in, token storage, session manager
  - `data/db/` — SQLDelight schema (`floorpin.sq`), database factory
- **`shared/src/androidMain/`** — platform actuals: Google auth (Credential Manager), image picker, WebView PDF export, EncryptedSharedPreferences
- **`shared/src/iosMain/`** — iOS platform actuals
- **`androidApp/`** — thin Android shell: MainActivity (builds AppContainer, calls App())
- **`iosApp/`** — thin iOS shell: SwiftUI wrapper around ComposeViewController
- **`gradle/`** — libs.versions.toml version catalog

## Out of the Norm
- **Custom navigator** — sealed `Screen` + `SnapshotStateList` back-stack; no Jetpack Navigation or Voyager
- **Manual Ktor multipart** — raw `PartData` objects instead of `formData{}` DSL (avoids duplicate Content-Disposition headers breaking Cloudflare Workers)
- **Hand-ported SVG icons** — `AppIcons.kt` converts SVG path data into Compose `ImageVector` objects (avoids Material icon artifacts on multiplatform)
- **Client-generated UUIDs** — app creates its own IDs for offline records (idempotent sync)
- **Last-write-wins sync** — outbox carries `updatedAt`; server skips stale ops. `Outbox.enqueue` coalesces pending `update` ops for the same entity into one (merging fields, newest wins) to bound queue growth. `SyncEngine` retries indefinitely while offline (network failure never counts attempts), but dead-letters an op after `MAX_ATTEMPTS` (5) server *rejections* so one poison op can't block the queue.
- **Manual DI** — `AppContainer` class, built once per platform; no Hilt/Koin
- **Single `floorpin.sq` file** — all SQLDelight schema + queries in one file (7 tables, full CRUD + upserts)
- **HTML→PDF via WebView** — report export builds HTML in Kotlin, renders in platform WebView, prints via PrintDocumentAdapter. Images are pre-fetched through the auth'd Ktor client and embedded as base64 data URIs (avoids WebView auth/CORS/ORB issues). JS image-load detection via `@JavascriptInterface` waits for all `<img>` elements to settle before printing. WebView is briefly attached to the decor view (1×1 px invisible) so layout completes.
- **EncryptedSharedPreferences for tokens** — not DataStore
- **BASE_URL hardcoded** in `Config.kt` (points to production Worker URL)
- **Photo annotation** — Compose Canvas overlay for real-time drawing on issue photos, platform `expect`/`actual` for flattening strokes onto image bytes (Android: Bitmap+Canvas, iOS: stub)

## PDF Export Architecture

```
User taps "Export PDF" in ReportScreen
  └─> Coroutine launches exportPdf()
      ├─> Collects all image URLs (plan image + issue photos)
      ├─> Fetches each via container.http.get(url).body<ByteArray>()  ← auth'd Ktor client
      ├─> Base64-encodes to "data:image/*;base64,..." data URIs
      ├─> buildReportHtml() embeds data URIs in inline-styled HTML
      └─> exporter(html, jobName, baseUrl) → platform actual

Android (ReportExporter.android.kt):
  └─> WebView.loadDataWithBaseURL(baseUrl, html, ...)
      ├─> settings: JS enabled, MIXED_CONTENT_ALWAYS_ALLOW, LOAD_NO_CACHE
      ├─> @JavascriptInterface onImagesReady() → waits for all <img> complete
      ├─> printManager.print() with createPrintDocumentAdapter()
      └─> Cleanup: detach + destroy after 3s delay

iOS (ReportExporter.ios.kt):
  └─> Stub (no-op)
```

### Key design decisions
- **Base64 data URIs instead of remote URLs**: The WebView is a separate browser context without auth cookies. Remote images hit the Worker's auth gate (401) and Chromium's ORB (ERR_BLOCKED_BY_ORB from null-origin `loadDataWithBaseURL`). Embedding images as base64 eliminates all network/auth/ORB issues — the WebView becomes a pure renderer.
- **JS image-load detection**: `onPageFinished` fires when HTML DOM is parsed, not when images render. Injected JS via `@JavascriptInterface` tracks all `<img>` `.complete` state before calling `printManager.print()`.
- **WebView attached to window**: A programmatically created WebView must be added to the activity's decor view (even at 1×1 px) for Chromium to perform layout and render content.

### Files
| File | Role |
|------|------|
| `ui/screens/ReportScreen.kt` | Report UI, `exportPdf()`, `buildReportHtml()` |
| `ui/ReportExporter.kt` | `expect fun rememberReportExporter()` |
| `androidMain/.../ReportExporter.android.kt` | WebView + PrintManager, JS bridge |
| `iosMain/.../ReportExporter.ios.kt` | Stub |
