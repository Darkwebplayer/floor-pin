# FloorPin API

Cloudflare Workers backend — Hono + D1 + R2 + Better Auth (Google OAuth, invite-only).

## Develop

```bash
pnpm dev                 # wrangler dev on :8787
pnpm db:migrate          # apply migrations to LOCAL d1
pnpm typecheck
```

`.dev.vars` holds `GOOGLE_CLIENT_ID/SECRET`, `BETTER_AUTH_SECRET`, `BETTER_AUTH_URL`.

### Google OAuth
Register redirect URIs in the Google console:
- `http://localhost:8787/api/auth/callback/google` (dev)
- `https://<prod-domain>/api/auth/callback/google` (prod)

### Bootstrap an admin (invite-only)
Sign-in is rejected unless the email is in `allowed_emails`. Seed yourself first:
```bash
wrangler d1 execute floorpin --local \
  --command "INSERT INTO allowed_emails (email,role) VALUES ('you@x.com','admin')"
```
Then sign in via Google. The `user.create.before` hook assigns the role from the allowlist.

## Deploy

```bash
wrangler secret put BETTER_AUTH_SECRET
wrangler secret put GOOGLE_CLIENT_SECRET    # move out of .dev.vars for prod
pnpm db:migrate:remote                       # apply migrations to remote D1
pnpm deploy
```
Set `BETTER_AUTH_URL` to the prod URL (var) and tighten CORS `origin` in `src/index.ts`.

## API

Auth (Better Auth): `GET|POST /api/auth/**` — Google sign-in, session, sign-out.
Everything below needs a session cookie; admin routes need `role=admin`.

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/api/projects` | list / create |
| GET/PATCH/DELETE | `/api/projects/:id` | |
| GET/POST | `/api/projects/:id/floor-plans` | POST = multipart `file` (image) → R2 |
| GET/DELETE | `/api/floor-plans/:id` | GET includes `locations[]` |
| GET/POST | `/api/floor-plans/:id/locations` | x/y are 0..100 (%) |
| PATCH/DELETE | `/api/locations/:id` | |
| GET/POST | `/api/locations/:id/issues` | |
| GET/PATCH/DELETE | `/api/issues/:id` | GET includes `photos[]`; status→`resolved` sets `resolvedAt` |
| POST | `/api/issues/:id/photos` | multipart `file` (image) → R2 |
| DELETE | `/api/photos/:id` | |
| GET | `/files/*` | serve R2 object by key (auth-gated) |
| GET | `/api/activity` | feed; admin sees all, staff sees own; `?limit&offset` |
| GET | `/api/activity/entity/:type/:id` | per-entity history |
| GET/POST | `/api/admin/allowlist` | invite emails (admin) |
| DELETE | `/api/admin/allowlist/:email` | |
| GET | `/api/admin/users` | Better Auth `listUsers` |
| POST | `/api/admin/users/:id/{role,ban,unban}` | Better Auth admin ops |
| POST | `/api/sync` | bulk offline replay (see below) |

### Offline sync
Client generates UUIDs and keeps its own queue, then POSTs:
```json
{ "ops": [ { "entity": "projects|locations|issues", "op": "create|update|delete",
            "id": "<uuid>", "data": {...}, "updatedAt": 1700000000000 } ] }
```
Creates are insert-or-ignore (safe to replay); updates are last-write-wins on `updatedAt`
(returns `applied` / `stale` / `missing`). Floor plans and photos upload online only.

## Android client

Native apps don't run in a browser, so:
- **CORS** doesn't apply to native HTTP clients (OkHttp/Retrofit) — it's kept only for a future web admin panel.
- **Sessions use Bearer tokens, not cookies.** The `bearer` plugin is enabled. On sign-in, read the token from the `set-auth-token` response header, store it (EncryptedSharedPreferences / Keystore), and send `Authorization: Bearer <token>` on every request. No cookie jar needed.
- The app's deep-link scheme (`floorpin://`) is in `trustedOrigins` — change it to your real scheme.

**Sign-in (recommended: native, no browser redirect):**
1. Android uses **Credential Manager / Google Identity** with `serverClientId = GOOGLE_CLIENT_ID` (the **Web** OAuth client) to get a Google **ID token**.
2. App POSTs it to Better Auth:
   ```
   POST /api/auth/sign-in/social
   { "provider": "google", "idToken": { "token": "<google_id_token>" } }
   ```
3. Better Auth verifies the token server-side, the invite-only hook runs, and the session token comes back in `set-auth-token`.

Google Cloud setup: keep the existing **Web** OAuth client (its ID is the `serverClientId` and the ID-token audience), and additionally register an **Android** OAuth client (package name + signing SHA-1) so Google trusts the app. No redirect URI is needed for the ID-token flow.

## Notes
- `auth.ts` is a per-request factory (Workers bindings are request-scoped). The module-level
  `auth` export exists only for `@better-auth/cli generate`.
- Auth tables are generated into `src/db/auth-schema.ts` — don't hand-edit; rerun `pnpm auth:generate`.
- `session.cookieCache` is disabled (Better Auth #4203 5-min-logout on Workers).
- Known gap: deleting a floor plan leaves its issue-photo R2 objects orphaned (sweep later).