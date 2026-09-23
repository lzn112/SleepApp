package com.sleepagent.prototype.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class StimulationBundleTest {
    @Test fun `bundle preserves raw data and complete stimulation CSV`() {
        val raw = File.createTempFile("sleep-raw", ".csv")
        try {
            raw.writeText("sequence,eeg\n1,23\n")
            val events = "session_id,event_type\r\ns,ALPHA_PULSE_TRIGGERED\r\ns,SESSION_STOPPED\r\n"
            val output = ByteArrayOutputStream()
            writeSleepSessionBundle(output, "{\"schema_version\":2}", raw, events)
            val entries = mutableMapOf<String, String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            assertEquals(setOf("manifest.json", "raw.csv", "stimulation_events.csv"), entries.keys)
            assertEquals(raw.readText(), entries["raw.csv"])
            assertEquals(events, entries["stimulation_events.csv"])
        } finally { raw.delete() }
    }
}
