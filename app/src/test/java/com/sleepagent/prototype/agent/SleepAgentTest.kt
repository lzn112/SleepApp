package com.sleepagent.prototype.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class SleepAgentTest {
    private fun context(nights: List<AgentNight> = emptyList()) = AgentSleepContext(
        nights, 3, "规律作息", "00:10", "08:00", ZoneId.of("UTC")
    )

    @Test fun emptyDataDoesNotInventMeasurements() = runBlocking {
        val reply = LocalSummaryModel().respond(AgentRequest(
            listOf(AgentMessage(AgentRole.USER, "昨晚睡得怎么样")), context()
        ))
        assertTrue(reply.text.contains("没有已完成"))
        assertTrue(reply.text.contains("排除 3 条模拟记录"))
        assertFalse(reply.text.contains("趋势在慢慢改善"))
        assertNull(reply.plan)
    }

    @Test fun summaryUsesActualValuesAndHandlesMissingData() = runBlocking {
        val reply = LocalSummaryModel().respond(AgentRequest(
            listOf(AgentMessage(AgentRole.USER, "报告")),
            context(listOf(AgentNight("one", 0, 27_000_000, 1_200_000, 2),
                AgentNight("two", 86_400_000, null, null, null)))
        ))
        assertTrue(reply.text.contains("7 小时 30 分钟"))
        assertTrue(reply.text.contains("入睡用时 20 分钟"))
        assertTrue(reply.text.contains("觉醒 2 次"))
        assertTrue(reply.text.contains("睡眠时长暂无摘要"))
    }

    @Test fun planUsesSavedTimesAcrossMidnight() = runBlocking {
        val reply = LocalSummaryModel().respond(AgentRequest(
            listOf(AgentMessage(AgentRole.USER, "今晚计划")), context()
        ))
        assertEquals("00:10", reply.plan!!.bedtime)
        assertEquals("08:00", reply.plan!!.wakeTime)
        assertTrue(reply.plan!!.steps.first().contains("23:40"))
        assertTrue(reply.displayText().contains("尚未应用"))
    }

    @Test fun replacementModelReceivesFreshContextAndBoundedHistory() = runBlocking {
        var loads = 0
        var received: AgentRequest? = null
        val model = object : SleepAgentModel {
            override val statusLabel = "Test"
            override suspend fun respond(request: AgentRequest): AgentReply {
                received = request
                return AgentReply("custom reply")
            }
        }
        val agent = SleepAgent(SleepAgentContextSource { loads++; context() }, model)
        val messages = (1..25).map { AgentMessage(AgentRole.USER, "问题 $it") }
        assertEquals("custom reply", agent.respond(messages).text)
        assertEquals(20, received!!.messages.size)
        assertEquals("问题 25", received!!.messages.last().text)
        agent.respond(messages)
        assertEquals(2, loads)
    }

    @Test fun modelFailurePropagatesForUiRetry() = runBlocking {
        val agent = SleepAgent(SleepAgentContextSource { context() }, object : SleepAgentModel {
            override val statusLabel = "Test"
            override suspend fun respond(request: AgentRequest): AgentReply = error("unavailable")
        })
        try {
            agent.respond(listOf(AgentMessage(AgentRole.USER, "报告")))
            fail("Expected failure")
        } catch (expected: IllegalStateException) {
            assertEquals("unavailable", expected.message)
        }
    }
}
