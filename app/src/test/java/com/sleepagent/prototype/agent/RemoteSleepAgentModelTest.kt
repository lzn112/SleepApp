package com.sleepagent.prototype.agent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

class RemoteSleepAgentModelTest {
    private class FakeConnection(private val code: Int) : HttpURLConnection(URL("https://example.com/chat/completions")) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getOutputStream() = sent
        override fun getResponseCode() = code
        override fun getInputStream() = """{"choices":[{"message":{"content":"真实接口格式的回复"}}]}""".byteInputStream()
    }

    @Test fun sendsAuthenticatedPostAndClosesConnection() = runBlocking {
        val connection = FakeConnection(200)
        val model = RemoteSleepAgentModel(AgentModelConfig(
            "https://example.com/chat/completions", "model", "test-key")) { connection }
        val reply = model.respond(AgentRequest(listOf(AgentMessage(AgentRole.USER, "你好")),
            AgentSleepContext(emptyList(), 0, "", "23:30", "07:30")))
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.connectTimeout > 0)
        assertTrue(connection.readTimeout > 0)
        assertEquals("model", JSONObject(connection.sent.toString("UTF-8")).getString("model"))
        assertEquals("真实接口格式的回复", reply.text)
        assertTrue(connection.disconnected)
    }

    @Test fun rejectsHttpFailureAndClosesConnection() = runBlocking {
        val connection = FakeConnection(401)
        val model = RemoteSleepAgentModel(AgentModelConfig(
            "https://example.com/chat/completions", "model", "test-key")) { connection }
        try {
            model.respond(AgentRequest(emptyList(), AgentSleepContext(emptyList(), 0, "", "23:30", "07:30")))
            fail("Expected authentication failure")
        } catch (e: AgentServiceException) {
            assertTrue(e.message!!.contains("API Key"))
        }
        assertTrue(connection.disconnected)
    }

    @Test fun validatesEndpointWithoutLeakingKey() {
        val config = AgentModelConfig("https://example.com/v1/chat/completions", "my-model", "private-token")
        config.validate()
        assertFalse(config.toString().contains("private-token"))
        listOf("http://example.com/chat/completions", "https://user:pass@example.com/chat/completions",
            "https://example.com/chat/completions?key=secret", "https://example.com/v1").forEach {
            assertThrows(IllegalArgumentException::class.java) { AgentModelConfig(it, "model", "key").validate() }
        }
    }

    @Test fun encodesEvidenceAndConversationWithoutDeviceOrSessionIdentifiers() {
        val request = AgentRequest(listOf(AgentMessage(AgentRole.USER, "睡得怎样？")),
            AgentSleepContext(listOf(AgentNight("private-session-id", 1234, null, 60000, 2)),
                1, "更快入睡", "23:30", "07:30"))
        val json = JSONObject(RemoteSleepAgentModel.encodeRequest("my-model", request))
        assertEquals("my-model", json.getString("model"))
        assertFalse(json.getBoolean("stream"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val content = messages.getJSONObject(0).getString("content")
        assertTrue(content.contains("\"sleepDurationMs\":null"))
        assertTrue(content.contains("60000"))
        assertFalse(content.contains("private-session-id"))
        assertEquals("睡得怎样？", messages.getJSONObject(1).getString("content"))
    }

    @Test fun parsesRealChatCompletionResponse() {
        val reply = RemoteSleepAgentModel.decodeReply("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"根据记录，入睡用时为 1 分钟。"}}]}""")
        assertEquals("根据记录，入睡用时为 1 分钟。", reply.text)
    }

    @Test fun rejectsEmptyTruncatedOrInvalidResponses() {
        listOf("not json", "{}", """{"choices":[{"message":{"content":null}}]}""",
            """{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""")
            .forEach { body -> assertThrows(AgentServiceException::class.java) { RemoteSleepAgentModel.decodeReply(body) } }
    }

    @Test fun explainsAuthQuotaAndRedirectErrors() {
        assertTrue(RemoteSleepAgentModel.httpError(401).contains("API Key"))
        assertTrue(RemoteSleepAgentModel.httpError(429).contains("额度"))
        assertTrue(RemoteSleepAgentModel.httpError(302).contains("重定向"))
    }

    @Test fun unconfiguredModelDoesNotReturnFakeSuccess() = runBlocking {
        try {
            UnconfiguredSleepAgentModel().respond(AgentRequest(emptyList(),
                AgentSleepContext(emptyList(), 0, "", "23:30", "07:30")))
            fail("Expected configuration error")
        } catch (e: AgentServiceException) {
            assertTrue(e.message!!.contains("配置模型"))
        }
    }
}
