package de.xyourp.antigravitymobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.xyourp.antigravitymobile.data.AppRepository
import de.xyourp.antigravitymobile.net.AgentEvent
import de.xyourp.antigravitymobile.net.ApiClient
import de.xyourp.antigravitymobile.net.ApprovalCard
import de.xyourp.antigravitymobile.net.DisplayMessage
import de.xyourp.antigravitymobile.net.HostPowerStatus
import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.RemoteSnapshot
import de.xyourp.antigravitymobile.net.RemoteTheme
import de.xyourp.antigravitymobile.net.SubAgentCard
import de.xyourp.antigravitymobile.net.ToolCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class ChatState(val snapshot: RemoteSnapshot = RemoteSnapshot(), val loading: Boolean = true, val error: String? = null, val openFile: de.xyourp.antigravitymobile.net.VaultFileResponse? = null, val fileLoading: Boolean = false)

class ChatViewModel(private val repo: AppRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutable.asStateFlow()
    init {
        refresh()
        viewModelScope.launch { repo.socket.frames.collect { frame ->
            when (frame.kind) {
                "snapshot" -> mutable.value = ChatState(ApiClient.json.decodeFromJsonElement(frame.payload), false)
                "power" -> mutable.value = mutable.value.copy(snapshot = mutable.value.snapshot.copy(power = ApiClient.json.decodeFromJsonElement<HostPowerStatus>(frame.payload)))
                "theme" -> mutable.value = mutable.value.copy(snapshot = mutable.value.snapshot.copy(theme = ApiClient.json.decodeFromJsonElement<RemoteTheme>(frame.payload)))
                "turn" -> apply(frame.conversationId, ApiClient.json.decodeFromJsonElement(frame.payload))
            }
        } }
    }
    fun refresh() = viewModelScope.launch { runCatching { repo.api.status() }.onSuccess { mutable.value = ChatState(it, false) }.onFailure { mutable.value = mutable.value.copy(loading = false, error = it.message) } }
    fun send(text: String, contextPath: String?) = command("send", text = text, contextPath = contextPath)
    fun steer(text: String, immediate: Boolean, contextPath: String?) = command("steer", text = text, timing = if (immediate) "immediate" else "after-current-tool", contextPath = contextPath)
    fun cancel() = command("cancel")
    fun switch(id: String) = command("switch-conversation", id)
    fun create(provider: String, model: String?, effort: String?) = command("new", providerId = provider, model = model, effort = effort)
    fun switchModel(provider: String, model: String, effort: String?) = command("switch-model", model = model, effort = effort, providerId = provider)
    fun approve(id: String, approved: Boolean) = command(if (approved) "approve" else "reject", approvalId = id)
    fun openFile(path: String) = viewModelScope.launch { mutable.value = mutable.value.copy(fileLoading = true); runCatching { repo.api.vaultFile(path) }.onSuccess { mutable.value = mutable.value.copy(openFile = it, fileLoading = false) }.onFailure { mutable.value = mutable.value.copy(fileLoading = false, error = it.message) } }
    fun closeFile() { mutable.value = mutable.value.copy(openFile = null) }
    private fun command(name: String, id: String? = active()?.id, text: String? = null, timing: String? = null, approvalId: String? = null, providerId: String? = null, model: String? = null, effort: String? = null, contextPath: String? = null) = viewModelScope.launch {
        runCatching { repo.api.command(name, id, text, timing, approvalId, model, effort, providerId, contextPath) }.onFailure { mutable.value = mutable.value.copy(error = it.message) }
        if (name in setOf("switch-conversation", "new", "switch-model", "approve", "reject")) refresh()
    }
    private fun active() = mutable.value.snapshot.conversations.firstOrNull { it.id == mutable.value.snapshot.activeConversationId } ?: mutable.value.snapshot.conversations.firstOrNull()
    private fun apply(conversationId: String?, event: AgentEvent) {
        if (conversationId == null) return
        val snapshot = mutable.value.snapshot
        val conversations = snapshot.conversations.map { conversation -> if (conversation.id != conversationId) conversation else reduce(conversation, event) }
        mutable.value = mutable.value.copy(snapshot = snapshot.copy(conversations = conversations), error = event.message.takeIf { event.type == "error" })
    }
    private fun reduce(c: RemoteConversation, e: AgentEvent): RemoteConversation {
        val messages = c.messages.toMutableList()
        fun assistant(): Int { val found = messages.indexOfLast { it.role == "assistant" && it.streaming }; if (found >= 0) return found; messages += DisplayMessage("remote-${System.currentTimeMillis()}", "assistant", streaming = true); return messages.lastIndex }
        val index = assistant(); var message = messages[index]
        when (e.type) {
            "assistant-delta" -> message = message.copy(text = message.text + e.text.orEmpty())
            "assistant-message" -> message = message.copy(text = message.text + e.text.orEmpty())
            "tool-start" -> message = message.copy(tools = message.tools + ToolCard(e.id.orEmpty(), e.name.orEmpty(), e.description, input = e.input))
            "tool-update" -> message = message.copy(tools = message.tools.map { if (it.id == e.id) it.copy(updates = it.updates + e.text.orEmpty()) else it })
            "tool-end" -> message = message.copy(tools = message.tools.map { if (it.id == e.id) it.copy(state = if (e.success == true) "success" else "error", output = e.output) else it })
            "approval-request" -> message = message.copy(approvals = message.approvals + ApprovalCard(e.id.orEmpty(), e.path.orEmpty(), e.diff.orEmpty()))
            "subagent-update" -> { val old = message.subAgents.firstOrNull { it.id == e.id }; val card = SubAgentCard(e.id.orEmpty(), parentId = old?.parentId, name = e.name ?: old?.name ?: "Sub-agent", task = e.task ?: old?.task, state = e.state ?: old?.state ?: "running", detail = e.detail ?: old?.detail, result = e.result ?: old?.result, error = e.error ?: old?.error, model = e.model ?: old?.model, tokens = e.tokens ?: old?.tokens, toolCalls = e.toolCalls ?: old?.toolCalls, timeline = if (e.timeline.isNotEmpty()) e.timeline else old?.timeline.orEmpty(), startedAt = e.startedAt ?: old?.startedAt ?: System.currentTimeMillis(), endedAt = e.endedAt ?: old?.endedAt); message = message.copy(subAgents = message.subAgents.filterNot { it.id == card.id } + card) }
            "turn-complete", "error" -> message = message.copy(streaming = false, error = e.message)
        }
        messages[index] = message
        return c.copy(messages = messages, busy = e.type != "turn-complete" && e.type != "error")
    }
}
