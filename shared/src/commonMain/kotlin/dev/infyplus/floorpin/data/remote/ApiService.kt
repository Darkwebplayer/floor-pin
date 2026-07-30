package dev.infyplus.floorpin.data.remote

import dev.infyplus.floorpin.Config
import dev.infyplus.floorpin.data.auth.TokenStore
import dev.infyplus.floorpin.data.db.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val BASE = Config.BASE_URL

/**
 * Media type read from the leading magic bytes rather than the filename. The encoder picks the
 * format (and can fall back), so the extension is a hint, not a fact — and an upload labelled with
 * the wrong type is stored that way in R2 and served back wrong forever.
 */
internal fun sniffImageMime(b: ByteArray): String = when {
    b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
    b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "image/png"
    b.size >= 12 && b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte() -> "image/webp"
    b.size >= 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "image/gif"
    else -> when (b.ftypBrand()) {
        "avif", "avis" -> "image/avif"
        "heic", "heix", "hevc", "heim", "heis", "mif1", "msf1" -> "image/heic"
        else -> "image/jpeg"
    }
}

/** ISO-BMFF brand from a `....ftyp<brand>` header — the container behind HEIC and AVIF. The picker
 *  hands back the untouched original whenever it cannot re-encode, and on an iPhone-sourced photo
 *  that original is HEIC; labelling it `image/jpeg` would store it mislabelled in R2 for good. */
private fun ByteArray.ftypBrand(): String? {
    if (size < 12) return null
    if (this[4] != 'f'.code.toByte() || this[5] != 't'.code.toByte() ||
        this[6] != 'y'.code.toByte() || this[7] != 'p'.code.toByte()
    ) return null
    return decodeToString(8, 12)
}

fun createHttpClient(tokens: TokenStore): HttpClient = HttpClient {
    install(ContentNegotiation) { json(AppJson) }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest {
        tokens.token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}

@Serializable
private data class SessionDto(val user: UserDto? = null)

@Serializable
private data class ListUsersResponse(val users: List<UserDto> = emptyList())

/**
 * Thin wrapper over the FloorPin REST API.
 *
 * Ktor's `expectSuccess` is deliberately left off: [SyncEngine] reads the status of `/api/sync` to
 * decide between retrying and dead-lettering, and [signInSocial] reports its own rejection message.
 * The cost is that a failed request otherwise falls straight through to `.body<T>()` and surfaces
 * as a `NoTransformationFoundException` naming the DTO, hiding whatever the server actually said —
 * so the calls that decode a body go through [bodyOrThrow].
 */
class ApiService(private val client: HttpClient) {

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        if (!status.isSuccess()) {
            val detail = runCatching { bodyAsText() }.getOrDefault("").trim().take(300)
            error("Server returned ${status.value}${if (detail.isEmpty()) "" else ": $detail"}")
        }
        return body()
    }

    // ── auth ──
    /** Exchanges a Google ID token for a session bearer token (set-auth-token header). */
    suspend fun signInSocial(idToken: String): String {
        val resp: HttpResponse = client.post("$BASE/api/auth/sign-in/social") {
            contentType(ContentType.Application.Json)
            setBody(SocialSignInRequest("google", IdTokenWrapper(idToken)))
        }
        if (!resp.status.isSuccess()) error("Sign-in rejected by server (${resp.status})")
        return resp.headers["set-auth-token"]
            ?: error("Signed in but server returned no auth token")
    }

    suspend fun currentUser(): UserDto? =
        client.get("$BASE/api/auth/get-session").body<SessionDto>().user

    suspend fun signOut() { client.post("$BASE/api/auth/sign-out") }

    // ── projects ──
    suspend fun projects(): List<ProjectDto> = client.get("$BASE/api/projects").body()
    suspend fun createProject(name: String, description: String?): ProjectDto =
        client.post("$BASE/api/projects") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", name); if (description != null) put("description", description) })
        }.body()
    suspend fun updateProject(id: String, name: String?, description: String?): ProjectDto =
        client.patch("$BASE/api/projects/$id") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                if (name != null) put("name", name)
                if (description != null) put("description", description)
            })
        }.body()
    suspend fun deleteProject(id: String) { client.delete("$BASE/api/projects/$id") }

    // ── floor plans ──
    suspend fun floorPlans(projectId: String): List<FloorPlanDto> =
        client.get("$BASE/api/projects/$projectId/floor-plans").body()
    suspend fun floorPlan(id: String): FloorPlanDto =
        client.get("$BASE/api/floor-plans/$id").body()
    suspend fun deleteFloorPlan(id: String) { client.delete("$BASE/api/floor-plans/$id") }
    suspend fun uploadFloorPlan(projectId: String, name: String, bytes: ByteArray, filename: String): FloorPlanDto =
        client.post("$BASE/api/projects/$projectId/floor-plans") {
            setBody(multipart(bytes, filename, extra = mapOf("name" to name)))
        }.bodyOrThrow()

    // ── locations / issues (reads; writes flow through /api/sync) ──
    suspend fun issues(locationId: String): List<IssueDto> =
        client.get("$BASE/api/locations/$locationId/issues").body()
    suspend fun issue(id: String): IssueDto = client.get("$BASE/api/issues/$id").body()

    suspend fun uploadPhoto(issueId: String, bytes: ByteArray, filename: String): PhotoDto =
        client.post("$BASE/api/issues/$issueId/photos") {
            setBody(multipart(bytes, filename))
        }.bodyOrThrow()
    suspend fun deletePhoto(id: String) { client.delete("$BASE/api/photos/$id") }

    // ── activity ──
    suspend fun activity(limit: Int = 50, offset: Int = 0): List<ActivityLogDto> =
        client.get("$BASE/api/activity?limit=$limit&offset=$offset").body()
    suspend fun entityActivity(type: String, id: String): List<ActivityLogDto> =
        client.get("$BASE/api/activity/entity/$type/$id").body()

    // ── sync ──
    // Returns the HTTP status only — per-op results (applied/stale/missing) are all terminal, so the
    // engine dequeues on 2xx regardless. A non-2xx status tells the engine the server rejected the batch.
    suspend fun sync(ops: List<SyncOp>): HttpStatusCode =
        client.post("$BASE/api/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequest(ops))
        }.status

    // ── admin ──
    suspend fun users(): List<UserDto> = client.get("$BASE/api/admin/users").body<ListUsersResponse>().users
    suspend fun setRole(userId: String, role: String) {
        client.post("$BASE/api/admin/users/$userId/role") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("role", role) })
        }
    }
    suspend fun ban(userId: String) { client.post("$BASE/api/admin/users/$userId/ban") }
    suspend fun unban(userId: String) { client.post("$BASE/api/admin/users/$userId/unban") }
    suspend fun allowlist(): List<AllowlistEntry> = client.get("$BASE/api/admin/allowlist").body()
    suspend fun addAllowlist(email: String, role: String) {
        client.post("$BASE/api/admin/allowlist") {
            contentType(ContentType.Application.Json)
            setBody(AllowlistEntry(email, role))
        }
    }
    suspend fun removeAllowlist(email: String) { client.delete("$BASE/api/admin/allowlist/$email") }

    // Build parts by hand: Ktor's formData{} DSL injects its own `form-data; name=…`
    // header AND keeps ours, producing a duplicate Content-Disposition that Cloudflare's
    // FormData parser can't read. Constructing PartData directly = exactly one per part.
    private fun multipart(bytes: ByteArray, filename: String, extra: Map<String, String> = emptyMap()): MultiPartFormDataContent {
        println("FloorPin upload → field=file filename=\"$filename\" type=${sniffImageMime(bytes)} bytes=${bytes.size} extra=$extra")
        val parts = buildList {
            extra.forEach { (k, v) ->
                add(PartData.FormItem(v, {}, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"$k\"")
                }))
            }
            add(PartData.BinaryChannelItem({ ByteReadChannel(bytes) }, Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
                append(HttpHeaders.ContentType, sniffImageMime(bytes))
            }))
        }
        return MultiPartFormDataContent(parts)
    }
}
