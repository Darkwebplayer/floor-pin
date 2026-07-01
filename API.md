# FloorPin API Reference

Base URL: `https://<your-worker-domain>` (dev: `http://localhost:8787`)

## Conventions

- **Auth:** every `/api/*` route except `/api/auth/**` requires a session. Native
  clients send `Authorization: Bearer <token>` (token from the `set-auth-token`
  header on sign-in). Web clients may use the session cookie instead.
- **Roles:** `admin` or `staff`. Routes under `/api/admin/*` require `admin`.
- **IDs:** server generates a UUID when the body omits `id`. Clients MAY supply
  their own `id` (UUID) on create so offline records stay idempotent.
- **Timestamps:** JSON ISO‑8601 strings (e.g. `2026-06-28T00:09:11.434Z`),
  stored as epoch‑ms. `created_at`/`updated_at` set automatically.
- **Content type:** JSON unless marked `multipart/form-data`.
- **Images:** an object's `imageKey` is fetched at `GET /files/{imageKey}`.

### Error shape

```json
{ "error": "human readable message" }
```

| Status | Meaning |
|---|---|
| 400 | Validation failed (missing/invalid field) |
| 401 | No / invalid session |
| 403 | Authenticated but not allowed (banned, or non‑admin on admin route) |
| 404 | Resource not found |

Better Auth endpoints (`/api/auth/**`) return their own error envelope.

---

## Data models

**Project**
```json
{ "id": "string", "name": "string", "description": "string|null",
  "createdBy": "userId", "createdAt": "iso", "updatedAt": "iso" }
```

**FloorPlan**
```json
{ "id": "string", "projectId": "string", "name": "string",
  "imageKey": "string", "width": "number|null", "height": "number|null",
  "createdBy": "userId", "createdAt": "iso", "updatedAt": "iso" }
```

**Location** — `x`/`y` are percentages (0–100) of the floor‑plan image.
```json
{ "id": "string", "floorPlanId": "string", "label": "string|null",
  "x": 25.5, "y": 60, "createdBy": "userId",
  "createdAt": "iso", "updatedAt": "iso" }
```

**Issue** — `status`: `open|in_progress|resolved|closed` · `priority`: `low|medium|high|critical`
```json
{ "id": "string", "locationId": "string", "title": "string",
  "description": "string|null", "status": "open", "priority": "medium",
  "type": "string|null", "category": "string|null",
  "assignedTo": "userId|null", "createdBy": "userId",
  "resolvedAt": "iso|null", "createdAt": "iso", "updatedAt": "iso" }
```

**IssuePhoto**
```json
{ "id": "string", "issueId": "string", "imageKey": "string",
  "caption": "string|null", "createdBy": "userId", "createdAt": "iso" }
```

**ActivityLog** — `meta` is an action‑specific JSON blob (or null).
```json
{ "id": "string", "userId": "userId|null", "action": "issue.status_changed",
  "entityType": "issue", "entityId": "string", "meta": { "from": "open", "to": "resolved" },
  "createdAt": "iso" }
```

**AllowedEmail**
```json
{ "email": "string", "role": "staff", "invitedBy": "userId|null", "createdAt": "iso" }
```

> Note: **create** (`POST`) responses echo the fields that were inserted (no
> timestamps); **GET/PATCH** responses return the full persisted row including
> `createdAt`/`updatedAt`.

---

## Auth — `/api/auth/**` (Better Auth)

Handled by Better Auth. Key endpoints for the Android client:

### `POST /api/auth/sign-in/social` — native Google sign‑in
```json
{ "provider": "google", "idToken": { "token": "<google_id_token>" } }
```
On success the session token is returned in the `set-auth-token` response header.
Rejected with 403 if the email is not on the allowlist (invite‑only).

### `GET /api/auth/get-session`
Returns `{ session, user }` or `null`. `user` includes `role`, `banned`.

### `POST /api/auth/sign-out`
Invalidates the current session.

---

## Projects — requires session

### `GET /api/projects`
List all projects. → `200` `Project[]`

### `POST /api/projects`
```json
{ "name": "HQ Building", "description": "Main office", "id": "optional-uuid" }
```
`name` required. → `201` `Project`

### `GET /api/projects/:id`
→ `200` `Project` · `404` if missing

### `PATCH /api/projects/:id`
```json
{ "name": "New name", "description": "..." }
```
At least one field. → `200` `Project` · `404`

### `DELETE /api/projects/:id`
Cascades to floor plans → locations → issues → photos. → `200` `{ "ok": true }` · `404`

---

## Floor plans — requires session

### `GET /api/projects/:projectId/floor-plans`
→ `200` `FloorPlan[]`

### `POST /api/projects/:projectId/floor-plans` — `multipart/form-data`
| field | required | notes |
|---|---|---|
| `file` | yes | image (`image/*`) |
| `name` | no | defaults to filename |
| `width` | no | natural px |
| `height` | no | natural px |

→ `201` `FloorPlan` · `400` if `file` missing or not an image

### `GET /api/floor-plans/:id`
Floor plan **with its markers**. → `200`
```json
{ "id": "...", "projectId": "...", "name": "...", "imageKey": "...",
  "width": null, "height": null, "createdBy": "...",
  "createdAt": "iso", "updatedAt": "iso",
  "locations": [ /* Location[] */ ] }
```
`404` if missing.

### `DELETE /api/floor-plans/:id`
Deletes the plan + its R2 image and cascades children. → `200` `{ "ok": true }` · `404`

---

## Locations — requires session

### `GET /api/floor-plans/:floorPlanId/locations`
→ `200` `Location[]`

### `POST /api/floor-plans/:floorPlanId/locations`
```json
{ "x": 25.5, "y": 60, "label": "Lobby", "id": "optional-uuid" }
```
`x` and `y` required, numbers 0–100. → `201` `Location` · `400`

### `PATCH /api/locations/:id`
```json
{ "label": "Front desk", "x": 30, "y": 55 }
```
Any subset; `x`/`y` validated 0–100. → `200` `Location` · `400` · `404`

### `DELETE /api/locations/:id`
→ `200` `{ "ok": true }` · `404`

---

## Issues — requires session

### `GET /api/locations/:locationId/issues`
→ `200` `Issue[]`

### `POST /api/locations/:locationId/issues`
```json
{ "title": "Cracked tile", "description": "...", "priority": "high",
  "status": "open", "type": "...", "category": "...",
  "assignedTo": "userId", "id": "optional-uuid" }
```
`title` required; `status`/`priority` validated against enums. → `201` `Issue` · `400`

### `GET /api/issues/:id`
Issue **with photos**. → `200`
```json
{ "...Issue fields...": "", "photos": [ /* IssuePhoto[] */ ] }
```
`404` if missing.

### `PATCH /api/issues/:id`
```json
{ "status": "resolved", "priority": "critical", "title": "...",
  "description": "...", "type": "...", "category": "...", "assignedTo": "userId" }
```
Any subset. Setting `status` to `resolved` stamps `resolvedAt`; any other status
clears it. A status change logs `issue.status_changed` with `{ from, to }`.
→ `200` `Issue` · `400` · `404`

### `DELETE /api/issues/:id`
→ `200` `{ "ok": true }` · `404`

---

## Photos — requires session

### `POST /api/issues/:issueId/photos` — `multipart/form-data`
| field | required | notes |
|---|---|---|
| `file` | yes | image (`image/*`) |
| `caption` | no | |

→ `201` `IssuePhoto` · `400`

### `DELETE /api/photos/:id`
Deletes the row + R2 object. → `200` `{ "ok": true }` · `404`

---

## Files

### `GET /files/*` — requires session
Streams an R2 object by key (the `imageKey` of a floor plan or photo), e.g.
`GET /files/floor-plans/<projectId>/<id>`. → `200` image bytes
(`Content-Type` from upload, `Cache-Control: private, max-age=3600`) · `404`

---

## Activity — requires session

### `GET /api/activity?limit=50&offset=0`
Reverse‑chronological feed. Admins see all; staff see only their own actions.
`limit` max 200 (default 50). → `200` `ActivityLog[]`

### `GET /api/activity/entity/:type/:id`
Full history for one entity, e.g. `/api/activity/entity/issue/<issueId>`.
→ `200` `ActivityLog[]`

**Logged actions:** `project.create|update|delete`, `floor_plan.upload|delete`,
`location.create|move|update|delete`, `issue.create|update|status_changed|delete`,
`photo.attach|delete`, `user.invited|uninvited|role_changed|banned|unbanned`.

---

## Admin — requires `admin` role

### `GET /api/admin/allowlist`
→ `200` `AllowedEmail[]`

### `POST /api/admin/allowlist`
```json
{ "email": "person@example.com", "role": "staff" }
```
`email` required (lowercased); `role` is `admin` or `staff` (default `staff`).
Upserts. → `201` `{ "email": "...", "role": "...", "invitedBy": "..." }`

### `DELETE /api/admin/allowlist/:email`
→ `200` `{ "ok": true }` · `404`

### `GET /api/admin/users?limit=100&offset=0`
Pass‑through to Better Auth `listUsers`. → `200`
```json
{ "users": [ { "id": "...", "email": "...", "name": "...", "role": "...",
              "banned": false, "createdAt": "iso" } ],
  "total": 1, "limit": 100, "offset": 0 }
```

### `POST /api/admin/users/:id/role`
```json
{ "role": "admin" }
```
→ `200` updated user

### `POST /api/admin/users/:id/ban`
```json
{ "reason": "optional", "expiresIn": 604800 }
```
`expiresIn` = seconds until the ban lifts (omit for permanent). Revokes the
user's sessions and blocks future sign‑in. → `200`

### `POST /api/admin/users/:id/unban`
→ `200`

---

## Offline sync — requires session

### `POST /api/sync`
Bulk‑apply a client's queued offline edits. The client owns its queue and
generates UUIDs; the server accepts replays without duplicating.

```json
{ "ops": [
  { "entity": "projects", "op": "create", "id": "uuid",
    "data": { "name": "Synced" }, "updatedAt": 1700000001000 },
  { "entity": "issues", "op": "update", "id": "uuid",
    "data": { "status": "resolved" }, "updatedAt": 1700000002000 },
  { "entity": "locations", "op": "delete", "id": "uuid" }
] }
```

- `entity`: `projects` | `locations` | `issues` (floor plans & photos upload online only).
- `op`: `create` | `update` | `delete`.
- `data`: whitelisted fields per entity:
  - projects: `name`, `description`
  - locations: `label`, `x`, `y`, `floorPlanId`
  - issues: `title`, `description`, `status`, `priority`, `type`, `category`, `assignedTo`, `locationId`
- `updatedAt`: client epoch‑ms, used for last‑write‑wins on `update`.

Ops apply in `updatedAt` order. **Response:**
```json
{ "serverTime": 1700000003000,
  "results": [ { "id": "uuid", "status": "applied" } ] }
```

| `status` | meaning |
|---|---|
| `applied` | create inserted (or ignored if already present), or update/delete done |
| `stale` | update skipped — server already has a newer version |
| `missing` | update/delete target doesn't exist |
| `rejected` | unknown entity/op or missing id |