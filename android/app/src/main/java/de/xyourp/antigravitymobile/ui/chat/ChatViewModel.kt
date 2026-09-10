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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

data class ChatState(
    val snapshot: RemoteSnapshot = RemoteSnapshot(),
    val loading: Boolean = true,
    val error: String? = null,
    val isOfflineCached: Boolean = false,
    val openFile: de.xyourp.antigravitymobile.net.VaultFileResponse? = null,
    val fileHistory: List<de.xyourp.antigravitymobile.net.VaultFileResponse> = emptyList(),
    val fileLoading: Boolean = false,
    val isDraftingHandover: Boolean = false,
    val feedbackMessage: String? = null,
    val gitStatus: de.xyourp.antigravitymobile.net.GitStatusDetails? = null,
    val isCommitting: Boolean = false,
    val commitMessage: String = "",
    val showCommitSheet: Boolean = false,
    val showSubAgentSheet: Boolean = false,
    val showQuotaSheet: Boolean = false,
    val showMoreOptionsSheet: Boolean = false,
    val isRefreshingQuota: Boolean = false,
    val isGeneratingCommitMessage: Boolean = false,
    val skills: List<de.xyourp.antigravitymobile.net.SkillEntry> = emptyList(),
    val composerText: String = ""
)

/** Events that change the streaming assistant message; everything else waits for a snapshot. */
private val MESSAGE_EVENTS = setOf("assistant-delta", "assistant-message", "tool-start", "tool-update", "tool-end", "approval-request", "subagent-update", "turn-complete", "error")

class ChatViewModel(val repo: AppRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            repo.cacheStore?.loadSnapshot()?.let { cached ->
                if (mutable.value.snapshot.conversations.isEmpty()) {
                    mutable.value = mutable.value.copy(snapshot = cached, loading = false, isOfflineCached = true)
                }
            }
        }
        refresh()
        viewModelScope.launch { repo.socket.frames.collect { frame ->
            when (frame.kind) {
                "snapshot" -> {
                    val s = ApiClient.json.decodeFromJsonElement<RemoteSnapshot>(frame.payload)
                    mutable.value = mutable.value.copy(snapshot = s, loading = false, isOfflineCached = false)
                    repo.cacheStore?.saveSnapshot(s)
                }
                "power" -> mutable.value = mutable.value.copy(snapshot = mutable.value.snapshot.copy(power = ApiClient.json.decodeFromJsonElement<HostPowerStatus>(frame.payload)))
                "theme" -> mutable.value = mutable.value.copy(snapshot = mutable.value.snapshot.copy(theme = ApiClient.json.decodeFromJsonElement<RemoteTheme>(frame.payload)))
                "turn" -> apply(frame.conversationId, ApiClient.json.decodeFromJsonElement(frame.payload))
            }
        } }
    }
    fun refresh() = viewModelScope.launch {
        runCatching { repo.api.status() }
            .onSuccess {
                mutable.value = mutable.value.copy(snapshot = it, loading = false, isOfflineCached = false)
                repo.cacheStore?.saveSnapshot(it)
                refreshSkills(it.conversations.firstOrNull { c -> c.id == it.activeConversationId }?.providerId)
            }
            .onFailure {
                mutable.value = mutable.value.copy(loading = false, error = it.message, isOfflineCached = mutable.value.snapshot.conversations.isNotEmpty())
            }
    }
    val deviceToken: String get() = repo.api.deviceToken()
    fun attachmentUrl(path: String): String = repo.api.attachmentUrl(path)

    suspend fun uploadAttachment(name: String, mime: String, bytes: ByteArray): de.xyourp.antigravitymobile.net.ContextReference? {
        return runCatching { repo.api.uploadAttachment(name, mime, bytes) }.getOrNull()?.reference
    }

    fun send(text: String, contextPath: String?, attachments: List<de.xyourp.antigravitymobile.net.ContextReference> = emptyList()) = 
        command("send", text = text, contextPath = contextPath, attachments = attachments)
    fun queue(text: String, contextPath: String?, attachments: List<de.xyourp.antigravitymobile.net.ContextReference> = emptyList()) = 
        command("queue", text = text, contextPath = contextPath, attachments = attachments)
    fun dequeue(index: Int) = command("dequeue", index = index)
    fun pushQueue(index: Int) = command("push-queue", index = index)
    fun promoteQueue(index: Int) = command("promote-queue", index = index)
    fun clearQueue() = command("clear-queue")
    fun startQueue() = command("start-queue")
    fun steer(text: String, immediate: Boolean, contextPath: String?, attachments: List<de.xyourp.antigravitymobile.net.ContextReference> = emptyList()) = 
        command("steer", text = text, timing = if (immediate) "immediate" else "after-current-tool", contextPath = contextPath, attachments = attachments)
    fun cancel() = command("cancel")
    fun switch(id: String) = command("switch-conversation", id)
    fun create(provider: String, model: String?, effort: String?) = command("new", providerId = provider, model = model, effort = effort)
    fun switchModel(provider: String, model: String, effort: String?) = command("switch-model", model = model, effort = effort, providerId = provider)
    fun approve(id: String, approved: Boolean) = command(if (approved) "approve" else "reject", approvalId = id)
    fun openFile(path: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(fileLoading = true)
        runCatching { repo.api.vaultFile(path) }
            .onSuccess { newFile ->
                val current = mutable.value.openFile
                val newHistory = if (current != null && current.path != newFile.path) {
                    mutable.value.fileHistory + current
                } else {
                    mutable.value.fileHistory
                }
                mutable.value = mutable.value.copy(openFile = newFile, fileHistory = newHistory, fileLoading = false)
            }
            .onFailure { mutable.value = mutable.value.copy(fileLoading = false, error = it.message) }
    }
    fun closeFile() {
        val history = mutable.value.fileHistory
        if (history.isNotEmpty()) {
            val previous = history.last()
            mutable.value = mutable.value.copy(openFile = previous, fileHistory = history.dropLast(1))
        } else {
            mutable.value = mutable.value.copy(openFile = null, fileHistory = emptyList())
        }
    }
    fun dismissFileViewer() {
        mutable.value = mutable.value.copy(openFile = null, fileHistory = emptyList())
    }

    fun draftHandover(extraPrompt: String? = null) = viewModelScope.launch {
        val activeConv = active()
        if (activeConv == null) {
            mutable.value = mutable.value.copy(feedbackMessage = "No active chat to draft handover from")
            return@launch
        }
        mutable.value = mutable.value.copy(isDraftingHandover = true, feedbackMessage = "Drafting Tier-A handover in vault...")
        runCatching { repo.api.draftHandover(activeConv.id, extraPrompt) }
            .onSuccess { res ->
                mutable.value = mutable.value.copy(isDraftingHandover = false)
                if (res.ok && res.path != null) {
                    mutable.value = mutable.value.copy(feedbackMessage = "Handover created: ${res.filename}")
                    openFile(res.path)
                } else {
                    mutable.value = mutable.value.copy(feedbackMessage = res.error ?: "Could not draft handover")
                }
            }
            .onFailure {
                mutable.value = mutable.value.copy(isDraftingHandover = false, feedbackMessage = it.message ?: "Failed to draft handover")
            }
    }

    fun openCommitSheet() = viewModelScope.launch {
        mutable.value = mutable.value.copy(showCommitSheet = true)
        refreshGitStatus()
        if (mutable.value.commitMessage.isBlank()) {
            generateCommitMessage()
        }
    }

    fun generateCommitMessage() = viewModelScope.launch {
        mutable.value = mutable.value.copy(isGeneratingCommitMessage = true)
        runCatching { repo.api.generateCommitMessage(active()?.id) }
            .onSuccess { resp ->
                mutable.value = mutable.value.copy(isGeneratingCommitMessage = false)
                if (resp.ok && !resp.message.isNullOrBlank()) {
                    mutable.value = mutable.value.copy(commitMessage = resp.message)
                } else {
                    mutable.value = mutable.value.copy(feedbackMessage = resp.error ?: "Could not generate commit message")
                }
            }
            .onFailure {
                mutable.value = mutable.value.copy(isGeneratingCommitMessage = false, feedbackMessage = it.message ?: "Failed to generate commit message")
            }
    }

    fun closeCommitSheet() {
        mutable.value = mutable.value.copy(showCommitSheet = false)
    }

    fun updateCommitMessage(msg: String) {
        mutable.value = mutable.value.copy(commitMessage = msg)
    }

    fun commitAndPush(push: Boolean) = viewModelScope.launch {
        val msg = mutable.value.commitMessage.trim()
        if (msg.isBlank()) {
            mutable.value = mutable.value.copy(feedbackMessage = "Please enter a commit message")
            return@launch
        }
        mutable.value = mutable.value.copy(isCommitting = true)
        runCatching { repo.api.commitChanges(msg, push) }
            .onSuccess { res ->
                mutable.value = mutable.value.copy(isCommitting = false, showCommitSheet = false)
                if (res.ok) {
                    mutable.value = mutable.value.copy(feedbackMessage = if (push) "Committed and pushed successfully!" else "Committed successfully!")
                    refreshGitStatus()
                } else {
                    mutable.value = mutable.value.copy(feedbackMessage = res.error ?: "Commit operation failed")
                }
            }
            .onFailure {
                mutable.value = mutable.value.copy(isCommitting = false, feedbackMessage = it.message ?: "Commit failed")
            }
    }

    fun refreshGitStatus() = viewModelScope.launch {
        runCatching { repo.api.gitStatus() }.onSuccess { resp ->
            if (resp.ok) mutable.value = mutable.value.copy(gitStatus = resp.status)
        }
    }

    fun setSubAgentSheet(show: Boolean) {
        mutable.value = mutable.value.copy(showSubAgentSheet = show)
    }

    fun setQuotaSheet(show: Boolean) {
        mutable.value = mutable.value.copy(showQuotaSheet = show)
        if (show && mutable.value.snapshot.quota == null) {
            refreshQuota(false)
        }
    }

    fun setMoreOptionsSheet(show: Boolean) {
        mutable.value = mutable.value.copy(showMoreOptionsSheet = show)
    }

    fun refreshQuota(force: Boolean = true) = viewModelScope.launch {
        mutable.value = mutable.value.copy(isRefreshingQuota = true)
        val convId = state.value.snapshot.activeConversationId
        runCatching {
            if (force) repo.api.refreshQuota(convId) else repo.api.getQuota(convId, false)
        }.onSuccess { resp ->
            resp.quota?.let { q ->
                mutable.value = mutable.value.copy(
                    snapshot = mutable.value.snapshot.copy(quota = q),
                    isRefreshingQuota = false
                )
            } ?: run {
                mutable.value = mutable.value.copy(isRefreshingQuota = false)
            }
        }.onFailure {
            mutable.value = mutable.value.copy(isRefreshingQuota = false)
        }
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        repo.api.command("delete-conversation", conversationId = id)
        refresh()
    }

    fun clearConversation(id: String) = viewModelScope.launch {
        repo.api.command("clear-conversation", conversationId = id)
        refresh()
    }

    fun renameConversation(id: String, newTitle: String) = viewModelScope.launch {
        repo.api.command("rename-conversation", conversationId = id, text = newTitle)
        refresh()
    }

    fun clearFeedback() {
        mutable.value = mutable.value.copy(feedbackMessage = null)
    }

    fun generateTranscriptMarkdown(): String {
        val conv = active() ?: return ""
        val sb = StringBuilder()
        sb.append("# ${conv.title}\n\n")
        sb.append("**Provider**: ${conv.providerId} | **Model**: ${conv.model ?: "default"}\n\n---\n\n")
        for (m in conv.messages) {
            val role = if (m.role == "user") "### User" else "### Assistant"
            sb.append("$role\n\n${m.text}\n\n")
        }
        return sb.toString()
    }

    fun switchModel(providerId: String? = null, model: String? = null, effort: String? = null) {
        command("switch-model", providerId = providerId, model = model, effort = effort)
    }

    fun command(
        name: String,
        id: String? = active()?.id,
        text: String? = null,
        timing: String? = null,
        approvalId: String? = null,
        providerId: String? = null,
        model: String? = null,
        effort: String? = null,
        contextPath: String? = null,
        attachments: List<de.xyourp.antigravitymobile.net.ContextReference> = emptyList(),
        index: Int? = null,
        extra: JsonObject? = null
    ) = viewModelScope.launch {
        runCatching { repo.api.command(name, id, text, timing, approvalId, model, effort, providerId, contextPath, attachments, index, extra) }.onFailure { mutable.value = mutable.value.copy(error = it.message) }
        if (name in setOf("switch-conversation", "new", "switch-model", "approve", "reject", "queue", "dequeue", "push-queue", "promote-queue", "clear-queue", "start-queue", "delete-conversation", "clear-conversation", "rename-conversation", "set-master", "assign-master", "release-lease", "close-agent")) refresh()
    }

    // --- parallel agents (OAC ADR 0012) ---

    /** Starts a chat on the desktop; `master` makes it a master agent, `masterId` puts it in a crew. */
    fun createAgent(provider: String, model: String?, effort: String?, master: Boolean, masterId: String? = null) =
        command("new", providerId = provider, model = model, effort = effort, extra = buildJsonObject {
            if (master) put("role", "master")
            masterId?.let { put("masterId", it) }
        })
    fun setMaster(id: String, enabled: Boolean) = command("set-master", id, extra = buildJsonObject { put("enabled", enabled) })
    fun assignMaster(id: String, masterId: String?) = command("assign-master", id, extra = buildJsonObject { put("masterId", masterId?.let { JsonPrimitive(it) } ?: JsonNull) })
    fun releaseLease(path: String) = command("release-lease", null, extra = buildJsonObject { put("path", path) })
    fun closeAgent(id: String) = command("close-agent", id)
    fun setComposerText(text: String) {
        mutable.value = mutable.value.copy(composerText = text)
    }

    fun clearComposerText() {
        mutable.value = mutable.value.copy(composerText = "")
    }

    fun refreshSkills(provider: String? = null) = viewModelScope.launch {
        runCatching { repo.api.listSkills(provider ?: active()?.providerId) }
            .onSuccess { res ->
                if (res.ok) mutable.value = mutable.value.copy(skills = res.skills)
            }
    }

    fun active() = mutable.value.snapshot.conversations.firstOrNull { it.id == mutable.value.snapshot.activeConversationId } ?: mutable.value.snapshot.conversations.firstOrNull()
    private fun apply(conversationId: String?, event: AgentEvent) {
        if (conversationId == null) return
        val snapshot = mutable.value.snapshot
        val conversations = snapshot.conversations.map { conversation -> if (conversation.id != conversationId) conversation else reduce(conversation, event) }
        mutable.value = mutable.value.copy(snapshot = snapshot.copy(conversations = conversations), error = event.message.takeIf { event.type == "error" })
        if (event.type == "turn-complete") {
            viewModelScope.launch { repo.cacheStore?.saveSnapshot(mutable.value.snapshot) }
        }
    }
    private fun reduce(c: RemoteConversation, e: AgentEvent): RemoteConversation {
        // Status, queue and transcript notices belong to the conversation, not to
        // a message. Folding them in would open an empty "Agent" bubble and mark
        // an idle chat busy until the next snapshot corrects it.
        if (e.type !in MESSAGE_EVENTS) return c
        val messages = c.messages.toMutableList()
        fun assistant(): Int { val found = messages.indexOfLast { it.role == "assistant" && it.streaming }; if (found >= 0) return found; messages += DisplayMessage("remote-${System.currentTimeMillis()}", "assistant", streaming = true); return messages.lastIndex }
        val index = assistant(); var message = messages[index]
        when (e.type) {
            "assistant-delta" -> message = message.copy(text = message.text + e.text.orEmpty())
            "assistant-message" -> message = message.copy(text = message.text + e.text.orEmpty())
            "tool-start" -> message = message.copy(tools = message.tools + ToolCard(e.id.orEmpty(), e.name.orEmpty(), e.description, input = e.input))
            "tool-update" -> message = message.copy(tools = message.tools.map { if (it.id == e.id) it.copy(updates = it.updates + e.text.orEmpty()) else it })
            "tool-end" -> message = message.copy(tools = message.tools.map { if (it.id == e.id) it.copy(state = if (e.success == true) "success" else "error", output = e.output) else it })
            "approval-request" -> message = message.copy(approvals = message.approvals + ApprovalCard(
                id = e.id.orEmpty(),
                path = e.path.orEmpty(),
                diff = e.diff.orEmpty(),
                kind = e.kind ?: "write",
                reason = e.reason,
                paths = e.paths
            ))
            "subagent-update" -> {
                val old = message.subAgents.firstOrNull { it.id == e.id }
                val card = SubAgentCard(
                    id = e.id.orEmpty(),
                    parentId = old?.parentId,
                    name = e.name ?: old?.name ?: "Sub-agent",
                    task = e.task ?: old?.task,
                    state = e.state ?: old?.state ?: "running",
                    detail = e.detail ?: old?.detail,
                    result = e.result ?: old?.result,
                    error = e.error ?: old?.error,
                    model = e.model ?: old?.model,
                    tokens = e.tokens ?: old?.tokens,
                    toolCalls = e.toolCalls ?: old?.toolCalls,
                    timeline = if (e.timeline.isNotEmpty()) e.timeline else old?.timeline.orEmpty(),
                    mutatedPaths = if (e.mutatedPaths.isNotEmpty()) e.mutatedPaths else old?.mutatedPaths.orEmpty(),
                    workspace = e.workspace ?: old?.workspace,
                    transcriptUri = e.transcriptUri ?: old?.transcriptUri,
                    startedAt = e.startedAt ?: old?.startedAt ?: System.currentTimeMillis(),
                    endedAt = e.endedAt ?: old?.endedAt
                )
                message = message.copy(subAgents = message.subAgents.filterNot { it.id == card.id } + card)
            }
            "turn-complete", "error" -> message = message.copy(streaming = false, error = e.message)
        }
        messages[index] = message
        return c.copy(messages = messages, busy = e.type != "turn-complete" && e.type != "error")
    }
}
