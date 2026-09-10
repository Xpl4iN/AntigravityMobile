package de.xyourp.antigravitymobile.ui.agents

import de.xyourp.antigravitymobile.net.RemoteConversation
import de.xyourp.antigravitymobile.net.RemoteSnapshot
import de.xyourp.antigravitymobile.net.ScheduledRun
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A master and the crew that takes instructions from it. */
internal data class AgentGroup(val master: RemoteConversation, val crew: List<RemoteConversation>)

/**
 * What the Agents screen lists. [legacy] is true when the desktop predates
 * the parallel-agents bridge and sends no agent fields, so only busy chats show.
 */
internal data class AgentBoard(val groups: List<AgentGroup>, val independent: List<RemoteConversation>, val legacy: Boolean) {
    val isEmpty: Boolean get() = groups.isEmpty() && independent.isEmpty()
}

/** Agent states that mean the agent needs attention or has work in flight. */
private val ACTIVE_STATES = setOf("running", "approval", "waiting", "queued")

private fun hasAgentFields(c: RemoteConversation) = c.agentState != null || c.open != null || c.role != null || c.masterId != null

/**
 * True when the desktop speaks the parallel-agents protocol. Any agent field
 * counts, including snapshot-level ones, so a new desktop with no chats yet
 * is not mistaken for an old one.
 */
internal fun supportsAgents(snapshot: RemoteSnapshot): Boolean =
    snapshot.conversations.any(::hasAgentFields) || snapshot.leaseMode != null || snapshot.providerLoad.isNotEmpty() ||
        snapshot.crewTemplates.isNotEmpty() || snapshot.scheduledRuns.isNotEmpty()

private fun isActive(c: RemoteConversation) = c.open == true || c.agentState in ACTIVE_STATES

/**
 * Picks and groups the conversations for the Agents screen. A conversation
 * shows when it is open on the desktop or has work in flight; a master that
 * shows brings its whole crew. An active crew member also brings its master,
 * so it is never listed apart from the crew it belongs to. Snapshot order is
 * kept, so cards do not jump around while agents stream.
 */
internal fun agentBoard(snapshot: RemoteSnapshot): AgentBoard {
    val conversations = snapshot.conversations
    if (!supportsAgents(snapshot)) {
        return AgentBoard(emptyList(), conversations.filter { it.busy }, legacy = true)
    }
    val masters = conversations.filter { it.isMaster }
    val masterIds = masters.map { it.id }.toSet()
    // A master is never listed as another master's crew, so no card appears twice.
    fun crewOf(master: RemoteConversation) = conversations.filter { !it.isMaster && it.masterId == master.id }
    val groups = masters
        .filter { master -> isActive(master) || crewOf(master).any(::isActive) }
        .map { master -> AgentGroup(master, crewOf(master)) }
    val independent = conversations.filter { !it.isMaster && it.masterId !in masterIds && isActive(it) }
    return AgentBoard(groups, independent, legacy = false)
}

/** The plan status a tap moves to: todo, doing, done, then round again. A blocked task restarts at todo. */
internal fun nextPlanStatus(status: String): String = when (status) {
    "todo" -> "doing"
    "doing" -> "done"
    else -> "todo"
}

internal val PLAN_STATUSES = listOf("todo", "doing", "done", "blocked")

private val TIME = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

internal fun isValidTime(value: String) = TIME.matches(value)
internal fun isValidDate(value: String) = DATE.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess

/**
 * When the desktop will next start [run], in the phone's local time, or null
 * when it will not (disabled, malformed, or a one-off whose moment has passed).
 * The desktop decides in its own time zone; the phone assumes they match.
 */
internal fun nextRunAt(run: ScheduledRun, now: LocalDateTime): LocalDateTime? {
    if (!run.enabled) return null
    val match = TIME.matchEntire(run.time) ?: return null
    val time = LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    return when (run.repeat) {
        "once" -> {
            val date = run.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            LocalDateTime.of(date, time).takeIf { it.isAfter(now) }
        }
        "daily" -> LocalDateTime.of(now.toLocalDate(), time).let { if (it.isAfter(now)) it else it.plusDays(1) }
        "weekdays" -> {
            var candidate = LocalDateTime.of(now.toLocalDate(), time)
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
            while (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) candidate = candidate.plusDays(1)
            candidate
        }
        else -> null
    }
}
