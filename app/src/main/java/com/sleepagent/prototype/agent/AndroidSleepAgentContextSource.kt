package com.sleepagent.prototype.agent

import android.content.Context
import com.sleepagent.prototype.data.SleepDataSource
import com.sleepagent.prototype.data.SleepLocalPreferences
import com.sleepagent.prototype.data.SleepSessionStatus
import com.sleepagent.prototype.data.SleepStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSleepAgentContextSource(context: Context) : SleepAgentContextSource {
    private val applicationContext = context.applicationContext
    private val repository = SleepStorageRepository(applicationContext)

    override suspend fun load(): AgentSleepContext = withContext(Dispatchers.IO) {
        val sessions = repository.listRecentSessions(100)
        val nights = sessions.filter {
            it.sourceType != SleepDataSource.MOCK && it.status == SleepSessionStatus.COMPLETED
        }.sortedByDescending { it.startedAtEpochMs }.take(7).map { session ->
            val summary = repository.getNightlySummary(session.sessionId)
            AgentNight(
                sessionId = session.sessionId,
                startedAtEpochMs = session.startedAtEpochMs,
                sleepDurationMs = summary?.sleepDurationMs,
                sleepOnsetLatencyMs = summary?.sleepOnsetLatencyMs,
                wakeCount = summary?.wakeCount
            )
        }
        val profile = SleepLocalPreferences.loadUserProfile(applicationContext)
        val preferences = SleepLocalPreferences.loadSleepPreference(applicationContext)
        val plan = SleepLocalPreferences.loadSleepPlan(applicationContext)
        AgentSleepContext(
            nights = nights,
            excludedDemoCount = sessions.count { it.sourceType == SleepDataSource.MOCK },
            goal = profile.currentGoal,
            bedtime = plan?.bedtime ?: preferences.defaultBedtime,
            wakeTime = plan?.wakeTime ?: preferences.defaultWakeTime
        )
    }
}
