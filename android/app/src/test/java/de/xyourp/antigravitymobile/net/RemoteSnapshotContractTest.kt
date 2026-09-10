package de.xyourp.antigravitymobile.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decodes the desktop's canonical example snapshot, so a field the desktop
 * renames or reshapes fails here instead of silently decoding to its default.
 *
 * The fixture is owned by the OAC plugin. This module keeps a copy in
 * `src/test/resources` so the test also runs where the vault is absent (CI,
 * another machine); [fixtureMatchesVaultCopy] catches the copy going stale.
 */
class RemoteSnapshotContractTest {
    private val fixtureName = "remote-snapshot.v1.json"
    private val vaultFixture = File("C:/XyourP/XyourP-Main-Vault/Machine/Products/obsidian-agent-client/plugin/tests/fixtures/$fixtureName")

    private fun fixtureText(): String {
        val stream = javaClass.classLoader?.getResourceAsStream(fixtureName)
        assertNotNull("test resource $fixtureName is missing", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun snapshot(): RemoteSnapshot = ApiClient.json.decodeFromString(RemoteSnapshot.serializer(), fixtureText())

    private fun RemoteSnapshot.conversation(id: String): RemoteConversation =
        conversations.firstOrNull { it.id == id } ?: throw AssertionError("conversation $id missing from the fixture")

    private fun normalise(text: String) = text.replace("\r\n", "\n").replace("\r", "\n")

    @Test fun fixtureMatchesVaultCopy() {
        // Only on a machine that has the vault checked out; elsewhere the local copy is the contract.
        if (!vaultFixture.isFile) return
        val canonical = normalise(vaultFixture.readText(Charsets.UTF_8))
        val local = normalise(fixtureText())
        assertTrue(
            "Fixture drifted, re-copy it: ${vaultFixture.path} differs from app/src/test/resources/$fixtureName",
            canonical == local
        )
    }

    @Test fun decodesConversationsAndMasterRole() {
        val s = snapshot()
        assertEquals(1, s.protocolVersion)
        assertEquals("conv-master", s.activeConversationId)
        assertEquals(listOf("conv-master", "conv-impl", "conv-review", "conv-solo"), s.conversations.map { it.id })

        val master = s.conversation("conv-master")
        assertTrue(master.isMaster)
        assertEquals("running", master.agentState)
        assertEquals(true, master.open)
        assertTrue(master.busy)
        assertNull(master.masterId)

        val solo = s.conversation("conv-solo")
        assertFalse(solo.isMaster)
        assertEquals("unread", solo.agentState)
        assertEquals(false, solo.open)
        assertNull(solo.plan)
        assertNull(solo.worktree)
    }

    @Test fun decodesMasterPlan() {
        val plan = snapshot().conversation("conv-master").plan
        assertNotNull(plan)
        assertEquals(1789000250000, plan!!.updatedAt)
        assertEquals(listOf("t1", "t2", "t3", "t4"), plan.items.map { it.id })
        assertEquals(listOf("done", "doing", "todo", "blocked"), plan.items.map { it.status })

        val t1 = plan.items[0]
        assertEquals("Implement the tokenizer", t1.title)
        assertEquals("conv-impl", t1.owner)
        assertEquals(listOf("Machine/parser/tokenizer.ts"), t1.files)
        assertEquals(listOf("t1"), plan.items[1].dependsOn)
        assertNull(plan.items[2].owner)
        assertTrue(plan.items[2].dependsOn.isEmpty())
        assertEquals("Needs a fixture corpus", plan.items[3].note)
    }

    @Test fun decodesCrewMembersWorktreeAndHeldFlag() {
        val s = snapshot()
        val impl = s.conversation("conv-impl")
        assertEquals("conv-master", impl.masterId)
        assertEquals("implementer", impl.crewRole)
        assertEquals("You implement the changes the master assigns.", impl.brief)
        assertEquals("approval", impl.agentState)
        assertFalse(impl.held)
        val worktree = impl.worktree
        assertNotNull(worktree)
        assertEquals("oac/implementer", worktree!!.branch)
        assertEquals("main", worktree.base)
        assertEquals("C:/XyourP/parser", worktree.repo)
        assertEquals("C:/XyourP/.oac-worktrees/parser-implementer", worktree.path)
        assertEquals(listOf("Add error recovery"), impl.queued.map { it.text })

        val reviewer = s.conversation("conv-review")
        assertEquals("reviewer", reviewer.crewRole)
        assertEquals("waiting", reviewer.agentState)
        assertTrue(reviewer.held)
        assertFalse(reviewer.busy)

        // Absent on the wire, so the default applies.
        assertFalse(s.conversation("conv-solo").held)
    }

    @Test fun decodesPendingApprovalAndWorkerReport() {
        val s = snapshot()
        val approval = s.conversation("conv-impl").messages.flatMap { it.approvals }.single()
        assertEquals("codex-approval-7", approval.id)
        assertEquals("pending", approval.decision)
        assertEquals("write", approval.kind)
        assertEquals("Machine/parser/grammar.md", approval.path)
        assertTrue(approval.reason.orEmpty().contains("being edited by agent \"Docs\""))

        val streaming = s.conversation("conv-impl").messages.last()
        assertTrue(streaming.streaming)
        assertEquals("apply_patch", streaming.tools.single().name)

        val master = s.conversation("conv-master")
        val report = master.messages.single { it.id == "user-2" }
        assertEquals("user", report.role)
        assertTrue(report.text.startsWith("[OAC worker report]"))
        assertEquals("system", master.messages.single { it.id == "system-1" }.role)
    }

    @Test fun decodesLeasesAndProviderLoad() {
        val s = snapshot()
        assertEquals("ask", s.leaseMode)
        assertEquals(listOf(false, true), s.leases.map { it.reserved })
        val reserved = s.leases[1]
        assertEquals("Machine/parser/README.md", reserved.path)
        assertEquals("conv-review", reserved.holderId)
        assertEquals("Reviewer", reserved.holderTitle)

        assertEquals(setOf("claude", "codex", "antigravity"), s.providerLoad.keys)
        assertEquals(ProviderLoad(running = 3, limit = 3, waiting = 1), s.providerLoad["claude"])
        assertEquals(ProviderLoad(running = 1, limit = 3, waiting = 0), s.providerLoad["codex"])
        assertEquals(0, s.providerLoad["antigravity"]!!.limit)
        assertTrue(s.reviewGate)
        assertNull(s.quota)
    }

    @Test fun decodesCrewTemplatesAndScheduledRuns() {
        val s = snapshot()
        val template = s.crewTemplates.single()
        assertEquals("build-review-test", template.id)
        assertEquals("Build, review, test", template.name)
        assertEquals(listOf("Implementer", "Reviewer"), template.members.map { it.title })
        assertEquals(listOf("codex", "claude"), template.members.map { it.providerId })
        assertEquals(listOf("implementer", "reviewer"), template.members.map { it.crewRole })
        assertNull(template.members[1].brief)

        val run = s.scheduledRuns.single()
        assertEquals("run-1", run.id)
        assertEquals("Nightly dependency audit", run.name)
        assertEquals("22:30", run.time)
        assertEquals("weekdays", run.repeat)
        assertNull(run.date)
        assertEquals("claude", run.providerId)
        assertTrue(run.asMaster)
        assertEquals("research-pair", run.templateId)
        assertTrue(run.enabled)
        assertEquals(1788917400000, run.lastRunAt)
        assertEquals("conv-solo", run.lastConversationId)
    }

    @Test fun olderDesktopWithoutAgentFieldsDecodesWithDefaults() {
        val legacy = """{"protocolVersion":1,"conversations":[{"id":"c1","title":"Chat","providerId":"codex","busy":true}]}"""
        val s = ApiClient.json.decodeFromString(RemoteSnapshot.serializer(), legacy)
        val c = s.conversations.single()
        assertNull(c.agentState)
        assertNull(c.open)
        assertNull(c.plan)
        assertNull(c.worktree)
        assertFalse(c.held)
        assertTrue(s.providerLoad.isEmpty())
        assertFalse(s.reviewGate)
        assertTrue(s.crewTemplates.isEmpty())
        assertTrue(s.scheduledRuns.isEmpty())
        assertTrue(s.leases.isEmpty())
    }
}
