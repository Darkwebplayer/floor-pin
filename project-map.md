# FloorPin — Project Map

## Description
Kotlin Multiplatform (Android + iOS) inspection/snagging app. Inspectors upload floor plan images, drop pins to mark issues, attach photos, and export PDF reports. Backend is Cloudflare Workers (Hono + D1 + R2 + Better Auth for Google OAuth, invite-only).

## Data Model (SQLDelight — `floorpin.sq`)

Timestamps are epoch ms (`INTEGER`). IDs are client/server UUIDs (`TEXT`). Write model is split by entity (see **Write paths** below): location/issue writes are offline-first (local + outbox → `POST /api/sync`); project/floor-plan/photo writes are online-only direct REST.

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
  - `ui/screens/viewer/` — floor plan canvas with pan/zoom/pin interaction (ViewerScreen, Inspector, ViewModel). Pins/issue-dots show name labels and are dragged with a local offset that commits **once on release** (one write + one sync, no per-frame stutter). Inspector is a side panel (wide) or bottom sheet (narrow); location/issue detail actions are inline (Add beside tabs, delete icon by the name, Done = header ✕), not a bottom bar.
  - `ui/components/` — reusable: adaptive nav scaffold, hand-ported SVG icons, top bar, widgets, photo annotation marker, fullscreen image lightbox (`ImageLightbox.kt`, + `photoImageUrl()` helper), CRUD controls (`CrudControls.kt`: `ConfirmDialog` for deletes, `CardMenu` 3-dot overflow)
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
- **Write paths differ by entity** — **locations & issues** write local + outbox (offline-capable, LWW via sync). **Projects, floor plans, photos** are **online-only** direct REST (`ProjectRepo`/`FloorPlanRepo` have no outbox; create/update/delete call `ApiService` then cache the response, surfacing `error` on failure). Full CRUD exists everywhere except: floor plans are delete-only (no server PATCH — name set at upload). Deletes go through a shared `ConfirmDialog`. Note: the issue `item` field is client/local-only — it's not in the server's `/api/sync` whitelist or REST body, so edits to it don't round-trip to the backend.
- **Manual DI** — `AppContainer` class, built once per platform; no Hilt/Koin
- **Single `floorpin.sq` file** — all SQLDelight schema + queries in one file (7 tables, full CRUD + upserts)
- **HTML→PDF via WebView** — report export builds HTML in Kotlin, renders in platform WebView, prints via PrintDocumentAdapter. Images are pre-fetched **concurrently** (bounded by a `Semaphore(6)`, on `Dispatchers.Default`) through the auth'd Ktor client and embedded as base64 data URIs (avoids WebView auth/CORS/ORB issues). JS image-load detection via `@JavascriptInterface` waits for all `<img>` elements to settle before printing. WebView is briefly attached to the decor view (1×1 px invisible) so layout completes.
- **Images are WebP, and re-encoded per use** — uploads are downscaled to 1600px longest edge / WebP q78 (`ImageCodec.android.kt`, ~30% smaller than the old JPEG q80). Issue photos are downscaled again to 800px for PDF embedding, since they print into a 180×200px box — embedding the 1600px original was ~8× the pixels needed and could OOM the print WebView. The floor-plan image is embedded as uploaded (it prints near full page width). Data URIs carry a magic-byte-sniffed media type (`sniffImageMime`); the old `image/*` is not a valid MIME type and only worked via Chromium sniffing. Old JPEG/PNG objects in R2 still work everywhere.
- **EncryptedSharedPreferences for tokens** — not DataStore
- **BASE_URL hardcoded** in `Config.kt` (points to production Worker URL)
- **Photo annotation** — Compose Canvas overlay for real-time drawing on issue photos, platform `expect`/`actual` for flattening strokes onto image bytes (Android: Bitmap+Canvas, iOS: stub). Flatten/rotate re-encode at the same WebP quality as the upload, so annotating a photo no longer grows it (it used to re-encode at JPEG q92).

## PDF Export Architecture

```
User taps "Export PDF" in ReportScreen
  └─> Coroutine launches exportPdf()
      ├─> Collects all image URLs (plan image + issue photos)
      ├─> Fetches them in parallel (Semaphore(6), Dispatchers.Default)  ← auth'd Ktor client
      ├─> Downscales issue photos to 800px WebP (plan image kept as-is)
      ├─> Base64-encodes to "data:<sniffed mime>;base64,..." data URIs
      ├─> buildReportHtml() embeds data URIs in inline-styled HTML
      └─> exporter(html, jobName, baseUrl) → platform actual

Android (ReportExporter.android.kt):
  └─> WebView.loadDataWithBaseURL(baseUrl, html, ...)
      ├─> settings: JS enabled, MIXED_CONTENT_ALWAYS_ALLOW, LOAD_NO_CACHE
      ├─> @JavascriptInterface onImagesReady() → waits for all <img> complete
      ├─> printManager.print() with a delegating PrintDocumentAdapter
      └─> Cleanup: detach + destroy in adapter.onFinish() (NOT a timer — a
          fixed delay races the system print dialog; if the user takes too long
          to tap Save the WebView is gone and the save fails)

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
| `ui/screens/ReportScreen.kt` | Report UI, `exportPdf()`, `buildReportHtml()`. Page 1 = summary + plan image with every location pin & issue dot overlaid (positioned by their `x`/`y` percentages, colored by worst status); each location's issues then start on their own page (`page-break-before`). Per-issue block mirrors the Snag Assure layout: `#shortid - title`, status/priority badges, assigned-to, category·type, item, location, created date, `x/y` coords + primary photo top-right, remaining photos in a bordered gallery. Fields absent from our model (due date, cost, creator name) are omitted. |
| `ui/ReportExporter.kt` | `expect fun rememberReportExporter()` |
| `androidMain/.../ui/components/ImageCodec.android.kt` | `compressWebp` / `decodeScaled` / `downscaleImage` — shared by the picker, the annotator, and the report |
| `androidMain/.../ReportExporter.android.kt` | WebView + PrintManager, JS bridge |
| `iosMain/.../ReportExporter.ios.kt` | Stub |
