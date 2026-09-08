package de.xyourp.antigravitymobile.net

import de.xyourp.antigravitymobile.data.ConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
    suspend fun pair(target: ConnectionSettings, code: String, name: String): PairResponse = json.decodeFromString(post("/api/pair", buildJsonObject { put("code", code); put("name", name) }.toString(), target))
    suspend fun command(name: String, conversationId: String? = null, text: String? = null, timing: String? = null, approvalId: String? = null, model: String? = null, effort: String? = null, providerId: String? = null): ActionResult {
        val body = buildJsonObject { conversationId?.let { put("conversationId", it) }; text?.let { put("text", it) }; timing?.let { put("timing", it) }; approvalId?.let { put("approvalId", it) }; model?.let { put("model", it) }; effort?.let { put("effort", it) }; providerId?.let { put("providerId", it) } }
        return json.decodeFromString(post("/api/command/$name", body.toString()))
    }
}
