package com.sleepagent.prototype.agent

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AgentRole { USER, ASSISTANT }
data class AgentMessage(val role: AgentRole, val text: String)

/** Only summary data enters the model boundary: no device IDs or raw EEG. */
data class AgentNight(
    val sessionId: String,
    val startedAtEpochMs: Long,
    val sleepDurationMs: Long?,
    val sleepOnsetLatencyMs: Long?,
    val wakeCount: Int?
)

data class AgentSleepContext(
    val nights: List<AgentNight>,
    val excludedDemoCount: Int,
    val goal: String,
    val bedtime: String,
    val wakeTime: String,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

data class AgentRequest(val messages: List<AgentMessage>, val context: AgentSleepContext)
data class BedtimePlan(val bedtime: String, val wakeTime: String, val steps: List<String>)
data class AgentReply(val text: String, val plan: BedtimePlan? = null) {
    fun displayText(): String = text + (plan?.let {
        "\n\n今晚计划（草案，尚未应用）\n" + it.steps.joinToString("\n") +
            "\n预计上床：${it.bedtime}；起床：${it.wakeTime}。"
    } ?: "")
}

/** Implement this interface to plug in a backend or on-device model. */
interface SleepAgentModel {
    val statusLabel: String
    suspend fun respond(request: AgentRequest): AgentReply
}

fun interface SleepAgentContextSource {
    suspend fun load(): AgentSleepContext
}

class SleepAgent(
    private val source: SleepAgentContextSource,
    val model: SleepAgentModel = LocalSummaryModel()
) {
    suspend fun respond(messages: List<AgentMessage>): AgentReply {
        require(messages.lastOrNull()?.role == AgentRole.USER) { "需要用户问题" }
        require(messages.last().text.isNotBlank()) { "问题不能为空" }
        return model.respond(AgentRequest(messages.takeLast(20), source.load()))
    }
}

/** Useful before a model is configured. Deliberately does not pretend to be an LLM. */
class LocalSummaryModel : SleepAgentModel {
    override val statusLabel = "本地摘要模式 · 未接入大模型"

    override suspend fun respond(request: AgentRequest): AgentReply {
        val context = request.context
        val question = request.messages.last().text
        val wantsPlan = listOf("计划", "今晚", "入睡", "安排").any(question::contains)
        val text = buildString {
            append("当前为本地摘要模式，尚未接入大模型。\n\n")
            if (context.excludedDemoCount > 0) {
                append("已排除 ${context.excludedDemoCount} 条模拟记录。\n")
            }
            if (context.nights.isEmpty()) {
                append("最近查询的记录中没有已完成的非模拟睡眠记录，暂时无法评价你的睡眠或趋势。")
            } else {
                append("以下来自最近 ${context.nights.size} 次已完成的非模拟记录（不一定连续）：\n")
                context.nights.forEach { night ->
                    val date = Instant.ofEpochMilli(night.startedAtEpochMs)
                        .atZone(context.zoneId).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    append("• $date：")
                    append(night.sleepDurationMs?.takeIf { it >= 0 }?.let {
                        "睡眠 ${it / 3_600_000} 小时 ${(it / 60_000) % 60} 分钟"
                    } ?: "睡眠时长暂无摘要")
                    night.sleepOnsetLatencyMs?.takeIf { it >= 0 }?.let {
                        append("，入睡用时 ${it / 60_000} 分钟")
                    }
                    night.wakeCount?.takeIf { it >= 0 }?.let { append("，觉醒 $it 次") }
                    append("\n")
                }
                append("记录摘要不能直接说明睡浅或疲惫的原因；本地模式暂不判断改善趋势。")
            }
            if (wantsPlan) {
                append("\n\n以下草案按你的已保存作息和目标“${context.goal}”整理，并非模型分析结果。")
            } else {
                append("\n\n我目前可以展示睡眠摘要、按已保存作息整理计划。你可以发送“生成今晚计划”；其他开放式问题需要接入模型后回答。")
            }
        }
        return AgentReply(text, if (wantsPlan) createPlan(context) else null)
    }

    private fun createPlan(context: AgentSleepContext): BedtimePlan? {
        val bedtime = runCatching { LocalTime.parse(context.bedtime) }.getOrNull() ?: return null
        val wakeTime = runCatching { LocalTime.parse(context.wakeTime) }.getOrNull() ?: return null
        val format = DateTimeFormatter.ofPattern("HH:mm")
        return BedtimePlan(bedtime.format(format), wakeTime.format(format), listOf(
            "• ${bedtime.minusMinutes(30).format(format)}：结束手头事务，准备睡前环境。",
            "• ${bedtime.minusMinutes(15).format(format)}：安排一段安静的放松时间。",
            "• ${bedtime.format(format)}：按计划准备休息。"
        ))
    }
}
