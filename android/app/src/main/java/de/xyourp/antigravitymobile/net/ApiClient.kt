package de.xyourp.antigravitymobile.net

import de.xyourp.antigravitymobile.data.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiClient(private val settingsProvider: () -> ConnectionSettings) {
    internal val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    companion object { val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }; private val JSON = "application/json; charset=utf-8".toMediaType() }
    private fun settings() = settingsProvider()
    internal fun authed(path: String, target: ConnectionSettings = settings()) = Request.Builder().url(target.restUrl(path)).apply { if (target.deviceToken.isNotBlank()) header("x-device-token", target.deviceToken) }
    private suspend fun get(path: String, target: ConnectionSettings = settings()) = withContext(Dispatchers.IO) { http.newCall(authed(path, target).get().build()).execute().use { r -> val body = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IOException(body.ifBlank { "HTTP ${r.code}" }); body } }
    private suspend fun post(path: String, body: String, target: ConnectionSettings = settings()) = withContext(Dispatchers.IO) { http.newCall(authed(path, target).post(body.toRequestBody(JSON)).build()).execute().use { r -> val value = r.body?.string().orEmpty(); if (!r.isSuccessful) throw IOException(value.ifBlank { "HTTP ${r.code}" }); value } }
    suspend fun health(target: ConnectionSettings = settings()): HealthResponse = json.decodeFromString(get("/api/health", target))
    suspend fun status(): RemoteSnapshot = json.decodeFromString(get("/api/status"))
    suspend fun vaultFile(path: String): VaultFileResponse = json.decodeFromString(get("/api/file?path=${URLEncoder.encode(path, Charsets.UTF_8.name())}"))
    suspend fun gitStatus(): GitStatusResponse = json.decodeFromString(get("/api/git"))
    suspend fun gitDiff(path: String? = null): GitDiffResponse = json.decodeFromString(get(if (path.isNullOrBlank()) "/api/git/diff" else "/api/git/diff?path=${URLEncoder.encode(path, Charsets.UTF_8.name())}"))
    suspend fun listFiles(folder: String? = null, query: String? = null): VaultFilesResponse {
        val params = mutableListOf<String>()
        folder?.takeIf { it.isNotBlank() }?.let { params.add("folder=${URLEncoder.encode(it, Charsets.UTF_8.name())}") }
        query?.takeIf { it.isNotBlank() }?.let { params.add("query=${URLEncoder.encode(it, Charsets.UTF_8.name())}") }
        val queryStr = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return json.decodeFromString(get("/api/files$queryStr"))
    }
    suspend fun pair(target: ConnectionSettings, code: String, name: String): PairResponse = json.decodeFromString(post("/api/pair", buildJsonObject { put("code", code); put("name", name) }.toString(), target))
    fun deviceToken() = settings().deviceToken
    fun attachmentUrl(path: String) = settings().restUrl("/api/attachment?path=${URLEncoder.encode(path, Charsets.UTF_8.name())}")

    suspend fun uploadAttachment(name: String, mime: String, bytes: ByteArray): UploadResponse {
        val mediaType = mime.toMediaType()
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name())
        val req = authed("/api/upload")
            .header("x-file-name", encodedName)
            .header("Content-Type", mime)
            .post(bytes.toRequestBody(mediaType))
            .build()
        val responseBody = withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException(body.ifBlank { "HTTP ${r.code}" })
                body
            }
        }
        return json.decodeFromString(responseBody)
    }

    suspend fun command(name: String, conversationId: String? = null, text: String? = null, timing: String? = null, approvalId: String? = null, model: String? = null, effort: String? = null, providerId: String? = null, contextPath: String? = null, attachments: List<ContextReference> = emptyList(), index: Int? = null, extra: JsonObject? = null): ActionResult {
        val body = buildJsonObject {
            // Command-specific fields (role, masterId, enabled, path) that the
            // common parameters do not cover. A JSON null in `extra` is sent as null.
            extra?.forEach { (key, value) -> put(key, value) }
            conversationId?.let { put("conversationId", it) }
            text?.let { put("text", it) }
            timing?.let { put("timing", it) }
            approvalId?.let { put("approvalId", it) }
            model?.let { put("model", it) }
            effort?.let { put("effort", it) }
            providerId?.let { put("providerId", it) }
            index?.let { put("index", it) }
            val contextItems = mutableListOf<kotlinx.serialization.json.JsonObject>()
            contextPath?.takeIf { it.isNotBlank() }?.let { path ->
                contextItems.add(buildJsonObject { put("kind", "note"); put("path", path) })
            }
            attachments.forEach { att ->
                contextItems.add(buildJsonObject {
                    put("kind", att.kind)
                    put("path", att.path)
                    att.name?.let { put("name", it) }
                    att.mime?.let { put("mime", it) }
                })
            }
            if (contextItems.isNotEmpty()) {
                putJsonArray("context") {
                    contextItems.forEach { add(it) }
                }
            }
        }
        return json.decodeFromString(post("/api/command/$name", body.toString()))
    }

    suspend fun draftHandover(conversationId: String?, extraPrompt: String? = null): HandoverResponse {
        val body = buildJsonObject {
            conversationId?.let { put("conversationId", it) }
            extraPrompt?.let { put("extraPrompt", it) }
        }
        return json.decodeFromString(post("/api/handover", body.toString()))
    }

    suspend fun commitChanges(message: String, push: Boolean = false): GitCommitResponse {
        val body = buildJsonObject {
            put("message", message)
            put("push", push)
        }
        return json.decodeFromString(post("/api/git/commit", body.toString()))
    }

    suspend fun pushChanges(): GitPushResponse {
        return json.decodeFromString(post("/api/git/push", "{}"))
    }

    suspend fun generateCommitMessage(conversationId: String?): GitGenerateCommitResponse {
        val body = buildJsonObject {
            conversationId?.let { put("conversationId", it) }
        }
        return json.decodeFromString(post("/api/git/generate-commit", body.toString()))
    }

    suspend fun getQuota(conversationId: String? = null, force: Boolean = false): QuotaResponse {
        val query = buildList {
            conversationId?.let { add("conversationId=$it") }
            if (force) add("force=true")
        }.joinToString("&").let { if (it.isNotEmpty()) "?$it" else "" }
        return json.decodeFromString(get("/api/quota$query"))
    }

    suspend fun refreshQuota(conversationId: String? = null): QuotaResponse {
        val body = buildJsonObject {
            conversationId?.let { put("conversationId", it) }
        }
        return json.decodeFromString(post("/api/quota/refresh", body.toString()))
    }

    suspend fun listSkills(provider: String? = null): SkillsResponse {
        val query = if (!provider.isNullOrBlank()) "?provider=$provider" else ""
        return json.decodeFromString(get("/api/skills$query"))
    }
}
