package com.sleepagent.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import kotlin.math.max
import kotlin.math.min
import com.sleepagent.prototype.data.SleepEpochRecord
import com.sleepagent.prototype.data.SleepNightlySummaryRecord
import com.sleepagent.prototype.data.SleepSessionRecord
import com.sleepagent.prototype.data.SleepStage
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Report UI Model ──

private data class ReportUiModel(
    val sessionId: String?,
    val dateText: String,
    val dayOfWeekText: String,
    val sleepStartText: String,
    val sleepEndText: String,
    val dataQualityLabel: String,
    val score: Int,
    val scoreLabel: String,
    val scoreDescription: String,
    val totalSleepText: String,
    val efficiencyText: String,
    val onsetLatencyText: String,
    val wakeCountText: String,
    val epochs: List<SleepEpochRecord>,
    val deepSleepMs: Long,
    val deepPercent: Int,
    val remSleepMs: Long,
    val remPercent: Int,
    val lightSleepMs: Long,
    val lightPercent: Int,
    val awakeMs: Long,
    val awakePercent: Int,
    val totalDurationMs: Long,
    val wakeAfterSleepOnsetMs: Long,
    val avgSignalQuality: Float?,
    val dataQualityScore: Float?,
    val bedDurationMs: Long
)

private enum class ReportTab(val label: String) {
    Overview("总览"),
    Stage("分期"),
    Movement("体动"),
    SpO2("血氧"),
    HRV("HRV")
}

// ── Health Insight Model ──

private enum class HealthInsightStatus { Good, Normal, Attention }

private data class HealthInsightUi(
    val title: String,
    val valueText: String,
    val statusText: String,
    val status: HealthInsightStatus
)

private fun calculateHealthInsights(model: ReportUiModel): List<HealthInsightUi> {
    val totalSleepMinutes = model.deepSleepMs + model.remSleepMs + model.lightSleepMs + model.awakeMs
    val totalSleepHours = max(totalSleepMinutes / 3_600_000f, 0.5f)
    val isShortSleep = totalSleepHours < 6f
    val isVeryShort = totalSleepHours < 4.5f
    val wasoMinutes = model.wakeAfterSleepOnsetMs / 60_000f
    val eff = model.efficiencyText.replace("%", "").toFloatOrNull() ?: 85f

    fun statusFor(score: Float): HealthInsightStatus = when {
        score >= 80f -> HealthInsightStatus.Good
        score >= 55f -> HealthInsightStatus.Normal
        else -> HealthInsightStatus.Attention
    }
    fun statusText(s: HealthInsightStatus) = when (s) {
        HealthInsightStatus.Good -> "较好"
        HealthInsightStatus.Normal -> "一般"
        HealthInsightStatus.Attention -> "较低"
    }

    // Body repair: primarily deep sleep + efficiency
    val bodyScore = min(100f, (model.deepPercent * 3.5f + eff * 0.3f).coerceIn(0f, 100f))
    // Brain recovery: REM + total duration
    val brainScore = min(100f, (model.remPercent * 3f + totalSleepHours * 6f + eff * 0.2f).coerceIn(0f, 100f))
    // Mood stability: efficiency + low wake count + low latency
    val onsetVal = model.onsetLatencyText.replace("m", "").toFloatOrNull() ?: 30f
    val moodScore = min(100f, (eff * 0.5f + max(0f, 30f - wasoMinutes) * 1.5f + max(0f, 60f - onsetVal) * 0.5f).coerceIn(0f, 100f))
    // Immune: total sleep + deep
    val immuneScore = min(100f, (totalSleepHours * 10f + model.deepPercent * 1.5f + eff * 0.2f).coerceIn(0f, 100f))
    // Skin: sleep duration + deep
    val skinScore = min(100f, (totalSleepHours * 8f + model.deepPercent * 1.5f + eff * 0.2f).coerceIn(0f, 100f))
    // Dark circles: inverse of short sleep + waso
    val circleRisk = max(0f, (if (isVeryShort) 80f else if (isShortSleep) 50f else 15f) + wasoMinutes * 0.4f).coerceIn(0f, 100f)
    // Hair loss risk: similar inverse
    val hairRisk = max(0f, (if (isVeryShort) 70f else if (isShortSleep) 40f else 10f) + wasoMinutes * 0.3f).coerceIn(0f, 100f)
    // Memory/learning: REM + total duration
    val memoryScore = min(100f, (model.remPercent * 3.5f + totalSleepHours * 5f + eff * 0.25f).coerceIn(0f, 100f))

    return listOf(
        HealthInsightUi("黑眼圈风险", "${circleRisk.roundToInt()}%", statusText(statusFor(100f - circleRisk)),
            when { circleRisk < 20 -> HealthInsightStatus.Good; circleRisk < 50 -> HealthInsightStatus.Normal; else -> HealthInsightStatus.Attention }),
        HealthInsightUi("皮肤恢复", "${skinScore.roundToInt()}%", statusText(statusFor(skinScore)), statusFor(skinScore)),
        HealthInsightUi("身体修复", "${bodyScore.roundToInt()}%", statusText(statusFor(bodyScore)), statusFor(bodyScore)),
        HealthInsightUi("大脑恢复", "${brainScore.roundToInt()}%", statusText(statusFor(brainScore)), statusFor(brainScore)),
        HealthInsightUi("情绪稳定", "${moodScore.roundToInt()}%", statusText(statusFor(moodScore)), statusFor(moodScore)),
        HealthInsightUi("免疫恢复", "${immuneScore.roundToInt()}%", statusText(statusFor(immuneScore)), statusFor(immuneScore)),
        HealthInsightUi("记忆与学习", "${memoryScore.roundToInt()}%", statusText(statusFor(memoryScore)), statusFor(memoryScore)),
        HealthInsightUi("脱发风险", "${hairRisk.roundToInt()}%", statusText(statusFor(100f - hairRisk)),
            when { hairRisk < 20 -> HealthInsightStatus.Good; hairRisk < 50 -> HealthInsightStatus.Normal; else -> HealthInsightStatus.Attention }),
    )
}

// ── Main Entry ──

@Composable
fun ReportScreenContent(
    sessionId: String? = null
) {
    val appContext = LocalContext.current.applicationContext

    val uiModel by produceState(
        initialValue = mockReportUiModel(),
        key1 = appContext,
        key2 = sessionId
    ) {
        val repository = SleepStorageRepository(appContext)
        val session = runCatching {
            if (sessionId != null) repository.getSession(sessionId)
            else repository.listRecentSessions(limit = 1).firstOrNull()
        }.getOrNull()

        value = if (session != null) {
            val summary = runCatching { repository.getNightlySummary(session.sessionId) }.getOrNull()
            val epochs = runCatching { repository.listEpochs(session.sessionId) }.getOrDefault(emptyList())
            buildReportUiModel(session, summary, epochs)
        } else {
            mockReportUiModel()
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(ReportTab.Overview) }

    val sessionStartMs = uiModel.epochs.firstOrNull()?.startAtEpochMs ?: 0L
    val sessionEndMs = uiModel.epochs.lastOrNull()?.endAtEpochMs ?: 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF10172A),
                        Color(0xFF0B1020),
                        Color(0xFF070B16)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Report Header (always visible)
            item { ReportHeader(uiModel = uiModel) }

            // 2. Tab Bar (always visible)
            item { ReportTabBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) }

            // 3. Tab content
            when (selectedTab) {
                ReportTab.Overview -> {
                    item { SleepScoreSummaryCard(uiModel = uiModel) }
                    item { CoreMetricsGrid(uiModel = uiModel) }
                    item { SleepHealthAnalysisCard(uiModel = uiModel) }
                    item {
                        SleepStageOverviewCard(
                            epochs = uiModel.epochs,
                            sessionStartMs = sessionStartMs,
                            sessionEndMs = sessionEndMs
                        )
                    }
                    item { DataQualityCard(uiModel = uiModel) }
                    item { DetailedMetricsTable(uiModel = uiModel) }
                }

                ReportTab.Stage -> {
                    item {
                        SleepStageTimelineCard(
                            epochs = uiModel.epochs,
                            sessionStartMs = sessionStartMs,
                            sessionEndMs = sessionEndMs
                        )
                    }
                    item { SleepArchitectureCard(uiModel = uiModel) }
                    item { AwakeAnalysisCard(uiModel = uiModel) }
                }

                ReportTab.Movement -> {
                    item { MovementReportTab(uiModel = uiModel) }
                }

                ReportTab.SpO2 -> {
                    item { SpO2ReportTab() }
                }

                ReportTab.HRV -> {
                    item { HrvReportTab(uiModel = uiModel) }
                }
            }
        }
    }
}

// ── 1. Report Header ──

@Composable
private fun ReportHeader(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "睡眠报告",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${uiModel.dateText} ${uiModel.dayOfWeekText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f)
                )
                Text(
                    "${uiModel.sleepStartText} - ${uiModel.sleepEndText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
            }
            StatusBadge(
                text = uiModel.dataQualityLabel,
                color = if (uiModel.dataQualityLabel == "数据良好") Color(0xFF2FCBBC) else Color.White.copy(alpha = 0.40f)
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Report Tab Bar ──

@Composable
private fun ReportTabBar(
    selectedTab: ReportTab,
    onTabSelected: (ReportTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(remember { androidx.compose.foundation.ScrollState(0) }),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReportTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)) else null,
                modifier = Modifier.clickable { onTabSelected(tab) }
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ── 2. Sleep Score Summary ──

@Composable
private fun SleepScoreSummaryCard(uiModel: ReportUiModel) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Score ring + info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Score ring
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = Color.White.copy(alpha = 0.08f)
                    val arcColor = scoreArcColor(uiModel.score)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = (uiModel.score / 100f) * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiModel.score.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.94f)
                        )
                        Text(
                            "睡眠评分",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                    }
                }

                // Description
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "整体表现：${uiModel.scoreLabel}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scoreArcColor(uiModel.score)
                    )
                    Text(
                        uiModel.scoreDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "总睡眠 ${uiModel.totalSleepText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Text(
                            "效率 ${uiModel.efficiencyText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

private fun scoreArcColor(score: Int): Color = when {
    score >= 85 -> Color(0xFF2FCBBC)
    score >= 70 -> Color(0xFF6C8CFF)
    else -> Color(0xFFFFC857)
}

// ── 3. Core Metrics Grid ──

@Composable
private fun CoreMetricsGrid(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "核心指标",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoreMetricItem("睡眠时长", uiModel.totalSleepText, "实际睡着时间", Modifier.weight(1f))
            CoreMetricItem("睡眠效率", uiModel.efficiencyText, "睡着 / 卧床时间", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoreMetricItem("入睡用时", uiModel.onsetLatencyText, "躺下到入睡", Modifier.weight(1f))
            CoreMetricItem("夜醒次数", uiModel.wakeCountText, "短暂醒来的次数", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CoreMetricItem(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.44f)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.94f)
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.32f)
            )
        }
    }
}

// ── 4. Sleep Health Analysis ──

@Composable
private fun SleepHealthAnalysisCard(uiModel: ReportUiModel) {
    val insights = remember(uiModel) { calculateHealthInsights(uiModel) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "睡眠健康分析",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Text(
            "基于本次睡眠时长、效率和睡眠结构生成，仅供健康管理参考。",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.32f)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            insights.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { insight ->
                        Box(modifier = Modifier.weight(1f)) {
                            HealthInsightItem(insight)
                        }
                    }
                    // If odd count, fill empty space
                    if (row.size < 2) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthInsightItem(insight: HealthInsightUi) {
    val accentColor = when (insight.status) {
        HealthInsightStatus.Good -> Color(0xFF2FCBBC)
        HealthInsightStatus.Normal -> Color(0xFFFFC857)
        HealthInsightStatus.Attention -> Color(0xFFFF9F43)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    insight.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.60f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        insight.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                insight.valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

// ── 5. Sleep Stage Timeline ──

private data class SleepStagePoint(
    val timeMs: Long,
    val stage: SleepStage,
    val value: Float
)

private fun SleepStage.toChartValue(): Float = when (this) {
    SleepStage.AWAKE -> 3f
    SleepStage.REM -> 2f
    SleepStage.LIGHT -> 1f
    SleepStage.DEEP -> 0f
    SleepStage.UNKNOWN -> Float.NaN
}

private val stageColors = mapOf(
    SleepStage.AWAKE to Color(0xFFFF9F43),   // 柔和橙色
    SleepStage.REM to Color(0xFFA29BFE),     // 紫蓝色
    SleepStage.LIGHT to Color(0xFF6C8CFF),   // 蓝色
    SleepStage.DEEP to Color(0xFF2D5BFF)     // 深蓝色
)

private val stageLabels = mapOf(
    SleepStage.AWAKE to "清醒",
    SleepStage.REM to "REM",
    SleepStage.LIGHT to "浅睡",
    SleepStage.DEEP to "深睡"
)

@Composable
private fun SleepStageTimelineCard(
    epochs: List<SleepEpochRecord>,
    sessionStartMs: Long,
    sessionEndMs: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "完整睡眠阶段图",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val validEpochs = epochs.filter { it.stage != SleepStage.UNKNOWN }
            when {
                validEpochs.isEmpty() -> {
                    EmptyStageState(
                        title = "暂无完整睡眠阶段图",
                        subtitle = "本次记录缺少连续睡眠阶段数据，其他指标仍可参考。"
                    )
                }
                validEpochs.size < 5 -> {
                    EmptyStageState(
                        title = "睡眠阶段数据较少",
                        subtitle = "本次图表仅供参考。"
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StageLegend()
                        val points = remember(validEpochs) {
                            buildHypnogramPoints(validEpochs)
                        }
                        SleepStageHypnogramChart(
                            points = points,
                            sessionStartMs = sessionStartMs,
                            sessionEndMs = sessionEndMs,
                            compact = false
                        )
                        Text(
                            "左右拖动查看整晚睡眠阶段变化",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.24f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStageState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.24f),
            modifier = Modifier.size(32.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.44f)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.30f)
        )
    }
}

@Composable
private fun StageLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stageLabels.entries.forEach { (stage, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(stageColors[stage] ?: Color.Gray, RoundedCornerShape(2.dp))
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
            }
        }
    }
}

/**
 * Build hypnogram points with step-line simulation.
 * Each epoch produces two points (start and end) at the same y-value,
 * creating true step lines when connected by Vico's LineCartesianLayer.
 */
private fun buildHypnogramPoints(epochs: List<SleepEpochRecord>): List<SleepStagePoint> {
    val result = mutableListOf<SleepStagePoint>()
    for (epoch in epochs) {
        val value = epoch.stage.toChartValue()
        if (value.isNaN()) continue
        result.add(SleepStagePoint(epoch.startAtEpochMs, epoch.stage, value))
        result.add(SleepStagePoint(epoch.endAtEpochMs, epoch.stage, value))
    }
    return result
}

@Composable
private fun SleepStageHypnogramChart(
    points: List<SleepStagePoint>,
    sessionStartMs: Long,
    sessionEndMs: Long,
    compact: Boolean
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = points.map { (it.timeMs - sessionStartMs).toLong() },
                    y = points.map { it.value }
                )
            }
        }
    }

    val yFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            when (value.toInt()) {
                3 -> "清醒"
                2 -> "REM"
                1 -> "浅睡"
                0 -> "深睡"
                else -> ""
            }
        }
    }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zoneId = remember { ZoneId.systemDefault() }
    val xFormatter = remember(sessionStartMs, zoneId) {
        CartesianValueFormatter { _, value, _ ->
            val absTimeMs = sessionStartMs + value.toLong()
            Instant.ofEpochMilli(absTimeMs).atZone(zoneId).format(timeFmt)
        }
    }

    val lineColor = Color(0xFF6C8CFF)
    val chartHeight = if (compact) 180.dp else 260.dp

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(
                            Fill(lineColor.copy(alpha = 0.85f))
                        ),
                        pointProvider = null,
                        stroke = LineCartesianLayer.LineStroke.Continuous(
                            thickness = 2.5.dp
                        )
                    )
                ),
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = -0.2, maxY = 3.2)
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = yFormatter,
                guideline = null,
                label = null
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = xFormatter,
                guideline = null,
                label = null
            )
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight),
        scrollState = rememberVicoScrollState(scrollEnabled = !compact),
        zoomState = rememberVicoZoomState(zoomEnabled = false)
    )
}

// ── 4b. Sleep Stage Overview Card (simplified for Overview tab) ──

@Composable
private fun SleepStageOverviewCard(
    epochs: List<SleepEpochRecord>,
    sessionStartMs: Long,
    sessionEndMs: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "睡眠阶段概览",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val validEpochs = epochs.filter { it.stage != SleepStage.UNKNOWN }
            if (validEpochs.isEmpty()) {
                EmptyStageState(
                    title = "暂无完整睡眠阶段图",
                    subtitle = "本次记录缺少连续阶段数据，其他指标仍可参考。"
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageLegend()
                    val points = remember(validEpochs) { buildHypnogramPoints(validEpochs) }
                    SleepStageHypnogramChart(
                        points = points,
                        sessionStartMs = sessionStartMs,
                        sessionEndMs = sessionEndMs,
                        compact = true
                    )
                }
            }
        }
    }
}

// ── Movement Tab ──

@Composable
private fun MovementReportTab(@Suppress("UNUSED_PARAMETER") uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "整晚体动分析",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )

        // Empty state - no movement data model available yet
        EmptyStateCard(
            title = "暂无体动数据",
            subtitle = "本次记录未包含连续体动信号。",
            iconTint = Color(0xFFFFC857).copy(alpha = 0.40f)
        )

        // Placeholder structure showing what will be available
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaceholderRow("体动次数", "-- 次")
                PlaceholderRow("明显体动", "-- 次")
                PlaceholderRow("最长安静时段", "--")
                PlaceholderRow("高体动主要发生", "--")
            }
        }

        // Placeholder chart area
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "体动趋势图（数据就绪后可用）",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.25f)
                )
            }
        }

        DisclaimText("体动数据可作为夜间活动水平的参考，受睡姿变化和环境干扰影响。")
    }
}

// ── SpO2 Tab ──

@Composable
private fun SpO2ReportTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "血氧分析",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )

        // Empty state - no SpO2 data model available yet
        EmptyStateCard(
            title = "暂无血氧数据",
            subtitle = "当前设备或本次记录未包含血氧信号。",
            iconTint = Color(0xFFFF6B6B).copy(alpha = 0.40f)
        )

        // Placeholder structure
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaceholderRow("平均血氧", "--%")
                PlaceholderRow("最低血氧", "--%")
                PlaceholderRow("低氧事件", "-- 次")
                PlaceholderRow("低于 90%", "-- 分钟")
            }
        }

        // Placeholder chart area
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "血氧趋势图（数据就绪后可用）",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.25f)
                )
            }
        }

        DisclaimText("本报告仅展示睡眠期间的血氧变化，不能替代医学诊断。")
    }
}

// ── HRV Tab ──

@Composable
private fun HrvReportTab(@Suppress("UNUSED_PARAMETER") uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "HRV 恢复分析",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )

        EmptyStateCard(
            title = "暂无 HRV 数据",
            subtitle = "本次记录未包含连续 HRV 信号。",
            iconTint = Color(0xFFA29BFE).copy(alpha = 0.40f)
        )

        // Placeholder structure
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaceholderRow("平均 HRV", "-- ms")
                PlaceholderRow("夜间趋势", "--")
                PlaceholderRow("恢复状态", "--")
            }
        }

        // Placeholder chart area
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "HRV 趋势图（数据就绪后可用）",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.25f)
                )
            }
        }

        DisclaimText("HRV 可作为身体恢复状态的参考，受压力、运动、饮酒和作息影响。")
    }
}

// ── Shared UI Components ──

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
    iconTint: Color = Color.White.copy(alpha = 0.24f)
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.32f)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.44f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.36f)
        )
    }
}

@Composable
private fun DisclaimText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.28f),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

// ── 5. Sleep Architecture ──

@Composable
private fun SleepArchitectureCard(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "睡眠结构",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArchitectureRow("深睡", formatDuration(uiModel.deepSleepMs), "${uiModel.deepPercent}%", Color(0xFF2D5BFF), uiModel.deepPercent)
                ArchitectureRow("REM", formatDuration(uiModel.remSleepMs), "${uiModel.remPercent}%", Color(0xFFAB7DFF), uiModel.remPercent)
                ArchitectureRow("浅睡", formatDuration(uiModel.lightSleepMs), "${uiModel.lightPercent}%", Color(0xFF6C8CFF), uiModel.lightPercent)
                ArchitectureRow("清醒", formatDuration(uiModel.awakeMs), "${uiModel.awakePercent}%", Color(0xFFFFC857), uiModel.awakePercent)
            }
        }
    }
}

@Composable
private fun ArchitectureRow(
    label: String,
    duration: String,
    percent: String,
    color: Color,
    percentValue: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.width(36.dp)
        )
        Text(
            duration,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.80f),
            modifier = Modifier.width(52.dp)
        )
        Text(
            percent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.width(36.dp)
        )
        LinearProgressIndicator(
            progress = { percentValue / 100f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color.copy(alpha = 0.60f),
            trackColor = Color.White.copy(alpha = 0.06f),
            strokeCap = StrokeCap.Round
        )
    }
}

// ── 6. Awake Analysis ──

@Composable
private fun AwakeAnalysisCard(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "夜间醒来",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val longestAwakeMs = findLongestAwakeSegment(uiModel.epochs)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    Column {
                        Text(
                            "醒来次数",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                        Text(
                            uiModel.wakeCountText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.94f)
                        )
                    }
                    Column {
                        Text(
                            "总清醒时长",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.40f)
                        )
                        Text(
                            formatDuration(uiModel.wakeAfterSleepOnsetMs),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.94f)
                        )
                    }
                    if (longestAwakeMs > 0) {
                        Column {
                            Text(
                                "最长清醒",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.40f)
                            )
                            Text(
                                formatDuration(longestAwakeMs),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.94f)
                            )
                        }
                    }
                }

                Text(
                    if (uiModel.wakeCountText != "0次")
                        "夜间有几次短暂醒来，属于常见波动。"
                    else "这一晚睡眠连续稳定，没有明显夜间醒来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
            }
        }
    }
}

private fun findLongestAwakeSegment(epochs: List<SleepEpochRecord>): Long {
    var longestMs = 0L
    var currentMs = 0L
    for (epoch in epochs) {
        if (epoch.stage == SleepStage.AWAKE) {
            currentMs += (epoch.endAtEpochMs - epoch.startAtEpochMs)
        } else {
            if (currentMs > longestMs) longestMs = currentMs
            currentMs = 0L
        }
    }
    if (currentMs > longestMs) longestMs = currentMs
    return longestMs
}

// ── 7. Data Quality ──

@Composable
private fun DataQualityCard(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "数据质量",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sigQualityText = when {
                    (uiModel.avgSignalQuality ?: 0f) >= 0.85f -> "良好"
                    (uiModel.avgSignalQuality ?: 0f) >= 0.70f -> "一般"
                    uiModel.avgSignalQuality != null -> "需谨慎参考"
                    else -> "--"
                }
                val sigQualityColor = when (sigQualityText) {
                    "良好" -> Color(0xFF2FCBBC)
                    "一般" -> Color(0xFFFFC857)
                    else -> Color(0xFFFF6B6B)
                }
                val dataTrustText = when {
                    (uiModel.dataQualityScore ?: 0f) >= 0.85f -> "较高"
                    (uiModel.dataQualityScore ?: 0f) >= 0.70f -> "一般"
                    uiModel.dataQualityScore != null -> "需谨慎参考"
                    else -> "--"
                }
                val dataTrustColor = when (dataTrustText) {
                    "较高" -> Color(0xFF2FCBBC)
                    "一般" -> Color(0xFFFFC857)
                    else -> Color(0xFFFF6B6B)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    Column {
                        Text("信号质量", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.40f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(sigQualityText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = sigQualityColor)
                            if (uiModel.avgSignalQuality != null) {
                                Text("${(uiModel.avgSignalQuality * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.36f))
                            }
                        }
                    }
                    Column {
                        Text("有效记录", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.40f))
                        Text(formatDuration(uiModel.totalDurationMs), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.80f))
                    }
                    Column {
                        Text("报告可信度", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.40f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(dataTrustText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = dataTrustColor)
                            if (uiModel.dataQualityScore != null) {
                                Text("${(uiModel.dataQualityScore * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.36f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 8. Detailed Metrics Table ──

@Composable
private fun DetailedMetricsTable(uiModel: ReportUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "详细指标",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailRow("卧床时长", formatDuration(uiModel.bedDurationMs))
                DetailRow("总睡眠时长", uiModel.totalSleepText)
                DetailRow("睡眠效率", uiModel.efficiencyText)
                DetailRow("入睡潜伏期", uiModel.onsetLatencyText)
                DetailRow("入睡后清醒", formatDuration(uiModel.wakeAfterSleepOnsetMs))
                DetailRow("夜醒次数", uiModel.wakeCountText)
                DetailRow("深睡时长", formatDuration(uiModel.deepSleepMs))
                DetailRow("REM 时长", formatDuration(uiModel.remSleepMs))
                DetailRow("浅睡时长", formatDuration(uiModel.lightSleepMs))
                DetailRow("清醒时长", formatDuration(uiModel.awakeMs))
                DetDivider()
                DetailRow(
                    "平均信号质量",
                    uiModel.avgSignalQuality?.let { "${(it * 100).roundToInt()}%" } ?: "--"
                )
                DetailRow(
                    "数据质量评分",
                    uiModel.dataQualityScore?.let { "${(it * 100).roundToInt()}%" } ?: "--"
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.56f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.82f)
        )
    }
}

@Composable
private fun DetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.04f))
    )
}

// ── Builder ──

private suspend fun buildReportUiModel(
    session: SleepSessionRecord,
    summary: SleepNightlySummaryRecord?,
    epochs: List<SleepEpochRecord>
): ReportUiModel {
    val zoneId = ZoneId.systemDefault()
    val startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zoneId)
    val endedAtMs = session.endedAtEpochMs ?: session.startedAtEpochMs
    val endedAt = Instant.ofEpochMilli(endedAtMs).atZone(zoneId)

    val dateFmt = DateTimeFormatter.ofPattern("M月d日")
    val dayFmt = DateTimeFormatter.ofPattern("EEEE")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    val dateText = startedAt.format(dateFmt)
    val dayOfWeekText = startedAt.format(dayFmt)
    val sleepStartText = startedAt.format(timeFmt)
    val sleepEndText = endedAt.format(timeFmt)

    val totalDurationMs = endedAtMs - session.startedAtEpochMs

    val score = summary?.let { calculateSleepScore(it) } ?: 72
    val scoreLabel = when {
        score >= 85 -> "优秀"
        score >= 70 -> "良好"
        score >= 55 -> "一般"
        else -> "偏低"
    }
    val scoreDescription = when {
        score >= 85 -> "这一晚的睡眠时长和效率都很优秀。"
        score >= 70 -> "整体睡眠质量良好，各项指标基本正常。"
        score >= 55 -> "睡眠质量一般，个别指标可以关注。"
        else -> "这一晚睡眠需要关注，建议查看详细数据。"
    }

    val efficiency = summary?.sleepEfficiency ?: 0f
    val efficiencyText = if (summary?.sleepEfficiency != null) "${(efficiency * 100).roundToInt()}%" else "--"

    val totalSleepMs = summary?.sleepDurationMs ?: totalDurationMs
    val totalSleepText = formatDuration(totalSleepMs)

    val onsetLatencyText = summary?.sleepOnsetLatencyMs?.let { "${it / 60000}m" } ?: "--"
    val wakeCount = summary?.wakeCount ?: 0
    val wakeCountText = "${wakeCount}次"

    val deepSleepMs = summary?.deepSleepMs ?: 0L
    val remSleepMs = summary?.remSleepMs ?: 0L
    val lightSleepMs = summary?.lightSleepMs ?: 0L
    val awakeMs = summary?.awakeMs ?: 0L

    val totalStagedMs = deepSleepMs + remSleepMs + lightSleepMs + awakeMs
    val baseMs = if (totalStagedMs > 0) totalStagedMs.toFloat() else totalSleepMs.toFloat().coerceAtLeast(1f)

    val deepPercent = if (deepSleepMs > 0) (deepSleepMs.toFloat() / baseMs * 100f).roundToInt() else 0
    val remPercent = if (remSleepMs > 0) (remSleepMs.toFloat() / baseMs * 100f).roundToInt() else 0
    val lightPercent = if (lightSleepMs > 0) (lightSleepMs.toFloat() / baseMs * 100f).roundToInt() else 0
    val awakePercent = if (awakeMs > 0) (awakeMs.toFloat() / baseMs * 100f).roundToInt() else 0

    val wakeAfterSleepOnsetMs = summary?.wakeAfterSleepOnsetMs ?: 0L

    val avgSignalQuality = summary?.avgSignalQuality
    val dataQualityScore = summary?.dataQualityScore

    val dataQualityLabel = when {
        (dataQualityScore ?: 0f) >= 0.80f -> "数据良好"
        (dataQualityScore ?: 0f) >= 0.60f -> "数据一般"
        dataQualityScore != null -> "数据待确认"
        else -> "无质量数据"
    }

    return ReportUiModel(
        sessionId = session.sessionId,
        dateText = dateText,
        dayOfWeekText = dayOfWeekText,
        sleepStartText = sleepStartText,
        sleepEndText = sleepEndText,
        dataQualityLabel = dataQualityLabel,
        score = score,
        scoreLabel = scoreLabel,
        scoreDescription = scoreDescription,
        totalSleepText = totalSleepText,
        efficiencyText = efficiencyText,
        onsetLatencyText = onsetLatencyText,
        wakeCountText = wakeCountText,
        epochs = epochs,
        deepSleepMs = deepSleepMs,
        deepPercent = deepPercent,
        remSleepMs = remSleepMs,
        remPercent = remPercent,
        lightSleepMs = lightSleepMs,
        lightPercent = lightPercent,
        awakeMs = awakeMs,
        awakePercent = awakePercent,
        totalDurationMs = totalDurationMs,
        wakeAfterSleepOnsetMs = wakeAfterSleepOnsetMs,
        avgSignalQuality = avgSignalQuality,
        dataQualityScore = dataQualityScore,
        bedDurationMs = totalDurationMs
    )
}

private fun mockReportUiModel(): ReportUiModel {
    return ReportUiModel(
        sessionId = null,
        dateText = "6月12日",
        dayOfWeekText = "周五",
        sleepStartText = "23:32",
        sleepEndText = "07:08",
        dataQualityLabel = "数据良好",
        score = 82,
        scoreLabel = "良好",
        scoreDescription = "这一晚的睡眠时长和效率都比较稳定。",
        totalSleepText = "7h12m",
        efficiencyText = "86%",
        onsetLatencyText = "18m",
        wakeCountText = "2次",
        epochs = mockEpochs(),
        deepSleepMs = 4860000L,
        deepPercent = 19,
        remSleepMs = 5640000L,
        remPercent = 22,
        lightSleepMs = 15420000L,
        lightPercent = 59,
        awakeMs = 2520000L,
        awakePercent = 0,
        totalDurationMs = 28200000L,
        wakeAfterSleepOnsetMs = 2520000L,
        avgSignalQuality = 0.91f,
        dataQualityScore = 0.88f,
        bedDurationMs = 28200000L
    )
}

private fun mockEpochs(): List<SleepEpochRecord> {
    val baseTime = Instant.parse("2025-06-12T15:32:00Z").toEpochMilli()
    val epochMs = 30000L
    val stages = listOf(
        SleepStage.AWAKE, SleepStage.AWAKE,
        SleepStage.LIGHT, SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP,
        SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.REM, SleepStage.REM, SleepStage.REM,
        SleepStage.LIGHT, SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP,
        SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.REM, SleepStage.REM, SleepStage.REM, SleepStage.REM,
        SleepStage.AWAKE,
        SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.REM, SleepStage.REM,
        SleepStage.LIGHT, SleepStage.LIGHT, SleepStage.LIGHT, SleepStage.LIGHT,
        SleepStage.AWAKE, SleepStage.AWAKE
    )
    return stages.mapIndexed { i, stage ->
        SleepEpochRecord(
            id = (i + 1).toLong(),
            sessionId = "mock",
            epochIndex = i,
            startAtEpochMs = baseTime + i * epochMs,
            endAtEpochMs = baseTime + (i + 1) * epochMs,
            stage = stage,
            confidence = 0.82f,
            avgSignalQuality = 0.91f
        )
    }
}

private fun calculateSleepScore(summary: SleepNightlySummaryRecord): Int {
    val efficiencyScore = ((summary.sleepEfficiency ?: 0f) * 100f).roundToInt()
    val qualityScore = ((summary.dataQualityScore ?: 0.7f) * 100f).roundToInt()
    return (efficiencyScore * 0.7f + qualityScore * 0.3f).roundToInt().coerceIn(0, 100)
}

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--"
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    val remainMinutes = minutes % 60L
    return if (hours > 0) "${hours}h${remainMinutes}m" else "${remainMinutes}m"
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    SleepAgentPrototypeTheme {
        ReportScreenContent()
    }
}
