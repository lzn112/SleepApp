package com.sleepagent.prototype.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InterventionEventWriterTest {
    private fun event(type: String) = SoundInterventionEventEntity(
        sessionId = "s", timestampMillis = 1L, elapsedRealtimeNanos = 2L,
        interventionType = "ALPHA", eventType = type)

    @Test fun `flush waits for ordered persistence including final stop`() = runBlocking {
        val saved = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val writer = InterventionEventWriter(this, { gate.await(); saved += it.eventType }, { throw it })
        writer.append(event("pulse")); writer.append(event("stop"))
        val flush = async { writer.flush() }
        yield()
        assertFalse(flush.isCompleted)
        gate.complete(Unit)
        flush.await()
        assertEquals(listOf("pulse", "stop"), saved)
        writer.close()
    }
    @Test fun `write failure is surfaced at export barrier`() = runBlocking {
        var notified = false
        val writer = InterventionEventWriter(this, { error("disk full") }, { notified = true })
        writer.append(event("pulse"))
        val result = runCatching { writer.flush() }
        assertTrue(result.isFailure)
        assertTrue(notified)
        assertEquals("disk full", result.exceptionOrNull()?.message)
        writer.close()
    }
    @Test fun `CSV preserves quoted reasons multiline values and nulls`() {
        assertEquals("s,\"error, \"\"audio\"\"\nretry\",\r\n",
            InterventionCsv.row(listOf("s", "error, \"audio\"\nretry", null)))
    }
}
