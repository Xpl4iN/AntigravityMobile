package de.xyourp.antigravitymobile.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class HealthResponse(val ok: Boolean = false, val protocolVersion: Int = 0, val pairedDevices: Int = 0)
@Serializable data class PairResponse(val ok: Boolean = false, val token: String? = null, val error: String? = null)
@Serializable data class ActionResult(val ok: Boolean = false, val error: String? = null)
@Serializable data class VaultFileResponse(val path: String, val name: String, val content: String)
@Serializable data class HostPowerStatus(val hasBattery: Boolean = false, val percent: Int? = null, val powerSource: String = "unknown", val chargingState: String = "unknown", val observedAtMs: Long = 0, val detail: String? = null) {
    fun available(connected: Boolean, now: Long = System.currentTimeMillis()) = connected && observedAtMs > 0 && now - observedAtMs <= 90_000 && (hasBattery || powerSource == "ac")
}
@Serializable data class RemoteTheme(val tokens: Map<String, String> = emptyMap(), val observedAtMs: Long = 0)
@Serializable data class ContextReference(val kind: String, val path: String, val name: String? = null, val mime: String? = null, val text: String? = null)
@Serializable data class ToolCard(val id: String, val name: String, val description: String? = null, val state: String = "running", val input: JsonElement? = null, val output: JsonElement? = null, val updates: List<String> = emptyList())
@Serializable data class ApprovalCard(val id: String, val path: String = "", val diff: String = "", val decision: String = "pending")
@Serializable data class SubAgentActivity(val id: String, val kind: String, val title: String, val detail: String? = null, val state: String? = null, val timestamp: Long? = null)
@Serializable data class SubAgentCard(val id: String, val parentId: String? = null, val name: String = "Sub-agent", val task: String? = null, val state: String = "running", val detail: String? = null, val result: String? = null, val error: String? = null, val model: String? = null, val tokens: Int? = null, val toolCalls: Int? = null, val timeline: List<SubAgentActivity> = emptyList(), val startedAt: Long = 0, val endedAt: Long? = null)
@Serializable data class DisplayMessage(val id: String, val role: String, val text: String = "", val createdAt: Long = 0, val tools: List<ToolCard> = emptyList(), val approvals: List<ApprovalCard> = emptyList(), val subAgents: List<SubAgentCard> = emptyList(), val context: List<ContextReference> = emptyList(), val streaming: Boolean = false, val error: String? = null)
@Serializable data class RemoteConversation(val id: String, val title: String, val providerId: String, val model: String? = null, val effort: String? = null, val busy: Boolean = false, val updatedAt: Long = 0, val messages: List<DisplayMessage> = emptyList())
@Serializable data class RemoteModelOption(val id: String, val label: String, val description: String? = null, val efforts: List<String> = emptyList())
@Serializable data class RemoteProviderCatalog(val id: String, val label: String, val models: List<RemoteModelOption> = emptyList())
@Serializable data class RemoteSnapshot(val protocolVersion: Int = 0, val activeConversationId: String? = null, val conversations: List<RemoteConversation> = emptyList(), val providers: List<RemoteProviderCatalog> = emptyList(), val theme: RemoteTheme = RemoteTheme(), val power: HostPowerStatus = HostPowerStatus())
@Serializable data class RemoteFrame(val id: Long, val kind: String, val conversationId: String? = null, val payload: JsonElement)
@Serializable data class AgentEvent(val type: String, val text: String? = null, val message: String? = null, val id: String? = null, val name: String? = null, val description: String? = null, val input: JsonElement? = null, val output: JsonElement? = null, val success: Boolean? = null, val path: String? = null, val diff: String? = null, val reason: String? = null, val paths: List<String> = emptyList(), val state: String? = null, val task: String? = null, val detail: String? = null, val result: String? = null, val error: String? = null, val model: String? = null, val tokens: Int? = null, val toolCalls: Int? = null, val timeline: List<SubAgentActivity> = emptyList(), val startedAt: Long? = null, val endedAt: Long? = null)
