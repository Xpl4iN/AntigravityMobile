package de.xyourp.antigravitymobile.net

import de.xyourp.antigravitymobile.data.ConnectionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class LiveSocket(private val scope: CoroutineScope, private val api: ApiClient) {
    val connected = MutableStateFlow(false)
    val frames = MutableSharedFlow<RemoteFrame>(extraBufferCapacity = 128)
    private var source: EventSource? = null
    private var settings: ConnectionSettings? = null
    private var generation = 0
    private var retryMs = 1_000L
    var appForeground: Boolean = true
    fun connect(value: ConnectionSettings) { settings = value; generation += 1; source?.cancel(); connected.value = false; if (value.isConfigured && value.deviceToken.isNotBlank()) open(generation) }
    fun reconnectNow() { settings?.let(::connect) }
    private fun open(expected: Int) {
        val target = settings ?: return
        source = EventSources.createFactory(api.http).newEventSource(api.authed("/api/events", target).get().build(), object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) { connected.value = true; retryMs = 1_000 }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) { runCatching { ApiClient.json.decodeFromString<RemoteFrame>(data) }.getOrNull()?.let { frames.tryEmit(it) } }
            override fun onClosed(eventSource: EventSource) = failed(expected)
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) = failed(expected)
        })
    }
    private fun failed(expected: Int) { if (expected != generation) return; connected.value = false; scope.launch { delay(retryMs); retryMs = (retryMs * 2).coerceAtMost(30_000); if (expected == generation) open(expected) } }
}
