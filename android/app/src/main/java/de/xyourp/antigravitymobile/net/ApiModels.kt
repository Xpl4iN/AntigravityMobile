package de.xyourp.antigravitymobile.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class HealthResponse(val ok: Boolean = false, val protocolVersion: Int = 0, val pairedDevices: Int = 0)
@Serializable data class PairResponse(val ok: Boolean = false, val token: String? = null, val error: String? = null)
@Serializable data class ActionResult(val ok: Boolean = false, val error: String? = null)
@Serializable data class VaultFileResponse(val path: String, val name: String, val content: String)
@Serializable data class UploadResponse(val ok: Boolean = false, val reference: ContextReference? = null, val error: String? = null)
@Serializable data class HostPowerStatus(val hasBattery: Boolean = false, val percent: Int? = null, val powerSource: String = "unknown", val chargingState: String = "unknown", val observedAtMs: Long = 0, val detail: String? = null, val secondsRemaining: Long? = null) {
    fun available(connected: Boolean, now: Long = System.currentTimeMillis()) = connected && observedAtMs > 0 && now - observedAtMs <= 90_000 && (hasBattery || powerSource == "ac")
}
@Serializable data class RemoteTheme(val tokens: Map<String, String> = emptyMap(), val observedAtMs: Long = 0)
@Serializable data class ContextReference(val kind: String, val path: String, val name: String? = null, val mime: String? = null, val text: String? = null)
@Serializable data class ToolCard(val id: String, val name: String, val description: String? = null, val state: String = "running", val input: JsonElement? = null, val output: JsonElement? = null, val updates: List<String> = emptyList())
@Serializable data class ApprovalCard(val id: String, val path: String = "", val diff: String = "", val decision: String = "pending", val kind: String = "write", val reason: String? = null, val paths: List<String> = emptyList())
@Serializable data class SubAgentActivity(val id: String, val kind: String, val title: String, val detail: String? = null, val state: String? = null, val timestamp: Long? = null)
@Serializable data class SubAgentCard(val id: String, val parentId: String? = null, val name: String = "Sub-agent", val task: String? = null, val state: String = "running", val detail: String? = null, val result: String? = null, val error: String? = null, val model: String? = null, val tokens: Int? = null, val toolCalls: Int? = null, val timeline: List<SubAgentActivity> = emptyList(), val mutatedPaths: List<String> = emptyList(), val workspace: String? = null, val transcriptUri: String? = null, val startedAt: Long = 0, val endedAt: Long? = null)
@Serializable data class DisplayMessage(val id: String, val role: String, val text: String = "", val createdAt: Long = 0, val tools: List<ToolCard> = emptyList(), val approvals: List<ApprovalCard> = emptyList(), val subAgents: List<SubAgentCard> = emptyList(), val context: List<ContextReference> = emptyList(), val streaming: Boolean = false, val error: String? = null)
@Serializable data class QueuedItem(val id: String? = null, val text: String = "", val context: List<ContextReference> = emptyList(), val createdAt: Long = 0)
@Serializable data class RemoteConversation(val id: String, val title: String, val providerId: String, val model: String? = null, val effort: String? = null, val busy: Boolean = false, val updatedAt: Long = 0, val messages: List<DisplayMessage> = emptyList(), val queued: List<QueuedItem> = emptyList(), val pendingSteer: JsonElement? = null,
    /** "master" for a conversation that directs a crew (OAC ADR 0012). */
    val role: String? = null,
    /** The master this conversation takes instructions from. */
    val masterId: String? = null,
    /** approval, running, waiting, error, queued, unread or idle; null from an older desktop. */
    val agentState: String? = null,
    /** True while the conversation is open in the desktop tab strip. */
    val open: Boolean? = null,
    /** The part a crew member plays for its master, e.g. "implementer" or "reviewer". */
    val crewRole: String? = null,
    /** Standing instructions the master gave this crew member. */
    val brief: String? = null,
    /** A master's task board; null for every other conversation. */
    val plan: MasterPlan? = null,
    /** The isolated git worktree this agent works in, if it has one. */
    val worktree: AgentWorktree? = null,
    /** True while a send waits for a free slot under its provider's concurrency limit. */
    val held: Boolean = false) {
    val isMaster: Boolean get() = role == "master"
}
/** One task on a master's plan board. `owner` is the id of the crew conversation working on it. */
@Serializable data class PlanItem(val id: String, val title: String = "", val status: String = "todo", val owner: String? = null, val dependsOn: List<String> = emptyList(), val files: List<String> = emptyList(), val note: String? = null)
@Serializable data class MasterPlan(val updatedAt: Long = 0, val items: List<PlanItem> = emptyList())
@Serializable data class AgentWorktree(val repo: String = "", val path: String = "", val branch: String = "", val base: String? = null, val createdAt: Long = 0)
/** Concurrency of one provider. `limit` 0 means unlimited; `waiting` counts held sends. */
@Serializable data class ProviderLoad(val running: Int = 0, val limit: Int = 0, val waiting: Int = 0)
@Serializable data class CrewTemplateMember(val title: String = "", val providerId: String? = null, val crewRole: String? = null, val brief: String? = null)
@Serializable data class CrewTemplate(val id: String, val name: String = "", val members: List<CrewTemplateMember> = emptyList())
/**
 * A prompt the desktop starts on its own. `time` is local "HH:MM"; `repeat` is
 * once, daily or weekdays, and `date` ("YYYY-MM-DD") only applies to once.
 */
@Serializable data class ScheduledRun(val id: String, val name: String = "", val prompt: String = "", val time: String = "", val repeat: String = "once", val date: String? = null, val providerId: String = "", val asMaster: Boolean = false, val templateId: String? = null, val enabled: Boolean = true, val lastRunAt: Long? = null, val lastConversationId: String? = null)
/**
 * A file a running desktop agent is editing; other agents are asked or refused before writing it.
 * `reserved` marks a claim made before the first write (a crew member's planned files).
 */
@Serializable data class RemoteFileLease(val path: String, val holderId: String, val holderTitle: String = "", val acquiredAt: Long = 0, val reserved: Boolean = false)
@Serializable data class RemoteModelOption(val id: String, val label: String, val description: String? = null, val efforts: List<String> = emptyList())
@Serializable data class RemoteProviderCatalog(val id: String, val label: String, val models: List<RemoteModelOption> = emptyList())
@Serializable data class QuotaSnapshot(val providerId: String = "", val modelId: String? = null, val primaryRemainingPercent: Int = 100, val fiveHourRemainingPercent: Int? = null, val fiveHourReset: String? = null, val weeklyRemainingPercent: Int? = null, val weeklyReset: String? = null, val contextTokens: Int? = null, val contextLimit: Int? = null, val contextRemainingPercent: Int? = null, val creditsBalanceUsd: Double? = null, val source: String = "unknown", val updatedAt: Long = 0, val awaitingNativeReading: Boolean = false)
@Serializable data class QuotaResponse(val ok: Boolean = false, val quota: QuotaSnapshot? = null, val error: String? = null)
@Serializable data class RemoteSnapshot(val protocolVersion: Int = 0, val activeConversationId: String? = null, val conversations: List<RemoteConversation> = emptyList(), val providers: List<RemoteProviderCatalog> = emptyList(), val theme: RemoteTheme = RemoteTheme(), val power: HostPowerStatus = HostPowerStatus(), val quota: QuotaSnapshot? = null, val leases: List<RemoteFileLease> = emptyList(), val leaseMode: String? = null,
    /** Keyed by provider id. */
    val providerLoad: Map<String, ProviderLoad> = emptyMap(),
    /** True when a reviewer checks crew work before the master accepts it. */
    val reviewGate: Boolean = false,
    val crewTemplates: List<CrewTemplate> = emptyList(),
    val scheduledRuns: List<ScheduledRun> = emptyList())
@Serializable data class RemoteFrame(val id: Long, val kind: String, val conversationId: String? = null, val payload: JsonElement)
@Serializable data class AgentEvent(val type: String, val text: String? = null, val message: String? = null, val id: String? = null, val name: String? = null, val description: String? = null, val input: JsonElement? = null, val output: JsonElement? = null, val success: Boolean? = null, val kind: String? = null, val path: String? = null, val diff: String? = null, val reason: String? = null, val paths: List<String> = emptyList(), val state: String? = null, val task: String? = null, val detail: String? = null, val result: String? = null, val error: String? = null, val model: String? = null, val tokens: Int? = null, val toolCalls: Int? = null, val timeline: List<SubAgentActivity> = emptyList(), val mutatedPaths: List<String> = emptyList(), val workspace: String? = null, val transcriptUri: String? = null, val startedAt: Long? = null, val endedAt: Long? = null)
@Serializable data class SkillEntry(val name: String, val description: String = "", val source: String = "")
@Serializable data class SkillsResponse(val ok: Boolean = false, val skills: List<SkillEntry> = emptyList(), val error: String? = null)

@Serializable data class GitStatusEntry(val path: String, val status: String = "modified")
@Serializable data class GitStatusDetails(val isAvailable: Boolean = false, val branch: String? = null, val upstream: String? = null, val aheadCount: Int = 0, val behindCount: Int = 0, val isCommitted: Boolean = true, val isPushed: Boolean = true, val dirtyPaths: List<String> = emptyList(), val entries: List<GitStatusEntry> = emptyList())
@Serializable data class GitStatusResponse(val ok: Boolean = false, val status: GitStatusDetails? = null, val error: String? = null)
@Serializable data class GitDiffResponse(val ok: Boolean = false, val diff: String = "", val error: String? = null)

@Serializable data class VaultFolderEntry(val name: String, val path: String)
@Serializable data class VaultFileEntry(val name: String, val path: String, val size: Long = 0, val mtime: Long = 0, val extension: String = "")
@Serializable data class VaultFilesResponse(val ok: Boolean = false, val folder: String = "", val parent: String? = null, val folders: List<VaultFolderEntry> = emptyList(), val files: List<VaultFileEntry> = emptyList(), val error: String? = null)

@Serializable data class HandoverRequest(val conversationId: String? = null, val extraPrompt: String? = null)
@Serializable data class HandoverResponse(val ok: Boolean = false, val path: String? = null, val filename: String? = null, val title: String? = null, val content: String? = null, val error: String? = null)

@Serializable data class GitCommitRequest(val message: String, val push: Boolean = false)
@Serializable data class GitCommitResponse(val ok: Boolean = false, val count: Int? = null, val error: String? = null)
@Serializable data class GitPushResponse(val ok: Boolean = false, val error: String? = null)
@Serializable data class GitGenerateCommitRequest(val conversationId: String? = null)
@Serializable data class GitGenerateCommitResponse(val ok: Boolean = false, val message: String? = null, val error: String? = null)
