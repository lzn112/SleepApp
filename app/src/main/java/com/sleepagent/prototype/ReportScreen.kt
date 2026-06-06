package com.sleepagent.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.sleepagent.prototype.data.SleepDataSource
import com.sleepagent.prototype.data.SleepSessionRecord
import com.sleepagent.prototype.data.SleepSessionStatus
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SleepMetric(
    val label: String,
    val value: String,
    val highlight: Color,
)

private data class ReportUiModel(
    val dateText: String,
    val score: Int,
    val sleepAge: Int,
    val efficiencyText: String,
    val sourceBadge: String,
    val overviewNote: String,
    val stages: List<SleepStageVisual>,
    val hourLabels: List<String>,
    val assistantTitle: String,
    val assistantSummary: String,
    val assistantAdvice: String,
    val footerNote: String,
)

private enum class SleepStageVisual(
    val label: String,
    val color: Color,
    val level: Float,
) {
    Deep("深睡", Color(0xFF2FCBBC), 3.2f),
    Light("浅睡", Color(0xFF1758FF), 2.2f),
    Dream("梦", Color(0xFFAAC0FF), 1.6f),
    Awake("觉醒", Color(0xFFE7C57B), 1.0f),
    Unknown("未检测到", Color(0xFFFF6B72), 0.7f),
}

private val reportDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val hourFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun ReportScreenContent() {
    val appContext = LocalContext.current.applicationContext
    val uiModel by produceState(
        initialValue = mockReportUiModel(),
        key1 = appContext,
    ) {
        val repository = SleepStorageRepository(appContext)
        val session = runCatching { repository.listRecentSessions(limit = 1).firstOrNull() }.getOrNull()
        value = session?.let(::buildConnectedReportUiModel) ?: mockReportUiModel()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ReportHeader() }
        item { ReportDateBar(uiModel = uiModel) }
        item { SleepOverviewCard(uiModel = uiModel) }
        item { ReportAssistantCard(uiModel = uiModel) }
    }
}

@Composable
private fun ReportHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                ReportHeaderTab(label = "日报告", selected = true)
                ReportHeaderTab(label = "睡眠趋势", selected = false)
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ReportHeaderTab(label: String, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
        )
        Box(
            modifier = Modifier
                .width(if (selected) 58.dp else 0.dp)
                .height(4.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(999.dp),
                ),
        )
    }
}

@Composable
private fun ReportDateBar(uiModel: ReportUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = uiModel.dateText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "标记你的睡眠",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 18.dp,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun SleepOverviewCard(uiModel: ReportUiModel) {
    val metrics = listOf(
        SleepMetric("睡眠年龄", uiModel.sleepAge.toString(), Color(0xFF7E93FF)),
        SleepMetric("睡眠效率", uiModel.efficiencyText, Color(0xFF36D6BC)),
    )

    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepMiniMetric(
                    label = metrics[0].label,
                    value = metrics[0].value,
                    tint = metrics[0].highlight,
                    modifier = Modifier.weight(1f),
                )
                ScoreRing(
                    score = uiModel.score,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                SleepMiniMetric(
                    label = metrics[1].label,
                    value = metrics[1].value,
                    tint = metrics[1].highlight,
                    modifier = Modifier.weight(1f),
                )
            }

            SleepStageTimelineCard(
                stages = uiModel.stages,
                hourLabels = uiModel.hourLabels,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SourceBadge(text = uiModel.sourceBadge)
                Text(
                    text = uiModel.overviewNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SleepMiniMetric(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.18f),
                modifier = Modifier.padding(start = 4.dp, top = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(11.dp),
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(132.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val diameter = size.minDimension - stroke
            drawArc(
                color = Color(0x243D7CFF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF1E53FF),
                        Color(0xFF2D79FF),
                        Color(0xFF1E53FF),
                    ),
                ),
                startAngle = -90f,
                sweepAngle = (score / 100f) * 320f,
                useCenter = false,
                topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "得分",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SleepStageTimelineCard(
    stages: List<SleepStageVisual>,
    hourLabels: List<String>,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val stageColors = listOf(
        SleepStageVisual.Deep.color,
        SleepStageVisual.Light.color,
        SleepStageVisual.Dream.color,
        SleepStageVisual.Awake.color,
        SleepStageVisual.Unknown.color,
    )
    val columnProvider = ColumnCartesianLayer.ColumnProvider.series(
        stageColors.map { color ->
            rememberLineComponent(
                fill = Fill(color),
                thickness = 12.dp,
            )
        },
    )
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = columnProvider,
            mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
        ),
    )

    LaunchedEffect(modelProducer, stages) {
        val stageSeries = SleepStageVisual.entries.map { targetStage ->
            stages.map { stage ->
                if (stage == targetStage) stage.level else 0f
            }
        }
        modelProducer.runTransaction {
            columnSeries {
                stageSeries.forEach { series(it) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0x141B59FF),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CartesianChartHost(
                chart = chart,
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            hourLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (SleepStageVisual.entries.map { it.label to it.color } + listOf("打鼾" to Color(0xFFA77BFF))).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 10.dp)
                            .background(color = color, shape = RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ReportAssistantCard(uiModel: ReportUiModel) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.26f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF143A9A),
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Text(
                    text = uiModel.assistantTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = uiModel.assistantSummary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            )

            Text(
                text = uiModel.assistantAdvice,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF28355D),
                                Color(0xFF323764),
                            ),
                        ),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp),
                        )
                    }
                    Text(
                        text = uiModel.footerNote,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                    )
                }
            }
        }
    }
}

private fun mockReportUiModel(): ReportUiModel {
    return ReportUiModel(
        dateText = "2026-06-03",
        score = 93,
        sleepAge = 23,
        efficiencyText = "89%",
        sourceBadge = "示例报告",
        overviewNote = "当前没有读取到本地睡眠会话，页面先展示一份 mock 数据，等你完成一次真实采集后会自动替换。",
        stages = listOf(
            SleepStageVisual.Light,
            SleepStageVisual.Light,
            SleepStageVisual.Deep,
            SleepStageVisual.Light,
            SleepStageVisual.Dream,
            SleepStageVisual.Light,
            SleepStageVisual.Deep,
            SleepStageVisual.Dream,
            SleepStageVisual.Light,
            SleepStageVisual.Deep,
            SleepStageVisual.Light,
            SleepStageVisual.Light,
            SleepStageVisual.Awake,
        ),
        hourLabels = listOf("22:58", "0", "1", "2", "3", "4", "5", "6", "7", "07:32"),
        assistantTitle = "Hi，我是 AI 睡眠助手 Luna",
        assistantSummary = "这是一份示例睡眠报告：整体结构偏稳定，前半夜深睡更集中，后半夜浅睡和短时清醒稍多，适合先用于对齐页面样式与信息层级。",
        assistantAdvice = "等真实会话接入后，我会根据最近一次采集结果自动生成更具体的解释，比如时长、效率、数据质量和恢复倾向。",
        footerNote = "当前显示的是 mock 数据，但页面已经准备好连接本地存储；一旦检测到真实 session，会优先展示真实会话信息。",
    )
}

private fun buildConnectedReportUiModel(session: SleepSessionRecord): ReportUiModel {
    val zoneId = ZoneId.systemDefault()
    val startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zoneId)
    val endedAtEpochMs = session.endedAtEpochMs ?: (session.startedAtEpochMs + 8L * 60L * 60L * 1000L)
    val endedAt = Instant.ofEpochMilli(endedAtEpochMs).atZone(zoneId)
    val durationHours = ((endedAtEpochMs - session.startedAtEpochMs).coerceAtLeast(60_000L) / 3_600_000f)
    val durationScore = (97 - abs(durationHours - 7.8f) * 10f).roundToInt().coerceIn(70, 97)
    val packetBonus = (session.packetCount / 1_500L).toInt().coerceIn(0, 6)
    val sourceBonus = if (session.sourceType == SleepDataSource.BLE) 2 else 0
    val statusBonus = if (session.status == SleepSessionStatus.COMPLETED) 2 else -4
    val score = (durationScore + packetBonus + sourceBonus + statusBonus).coerceIn(72, 98)
    val efficiency = (80 + (durationHours * 1.7f).roundToInt() + packetBonus).coerceIn(78, 95)
    val sleepAge = (33 - ((score - 70) / 2.4f)).roundToInt().coerceIn(19, 34)
    val sourceBadge = if (session.sourceType == SleepDataSource.BLE) "真实本地会话" else "本地模拟会话"
    val durationText = String.format("%.1f", durationHours)
    val sourceText = when (session.sourceType) {
        SleepDataSource.BLE -> "BLE 设备"
        SleepDataSource.MOCK -> "Mock 数据源"
        SleepDataSource.IMPORT -> "导入数据"
    }
    val stages = buildStageSequence(durationHours = durationHours, status = session.status)
    val assistantSummary = buildString {
        append("最近一次会话已从本地存储读取成功。")
        append("本次记录开始于 ${startedAt.format(hourFormatter)}，")
        append("预计持续约 $durationText 小时，")
        append("来源是$sourceText，")
        append("当前累计原始包 ${session.packetCount}。")
        if (session.status != SleepSessionStatus.COMPLETED) {
            append("由于这次会话还不是完整结束态，部分指标仍按 mock 逻辑补齐显示。")
        }
    }
    val assistantAdvice = when {
        durationHours < 6.0f -> "这次睡眠时长偏短，后续如果继续接 BLE 真机数据，建议优先关注入睡前拖延和夜间中断。"
        durationHours > 8.8f -> "这次睡眠时长偏长，后续可以结合真实分期结果观察深睡占比，看看是不是恢复期拉长。"
        else -> "这次会话时长落在比较舒适的区间里，接下来最值得补的是 epoch 分期和 nightly summary 入库，这样分期图就能完全变成真实数据。"
    }
    return ReportUiModel(
        dateText = startedAt.format(reportDateFormatter),
        score = score,
        sleepAge = sleepAge,
        efficiencyText = "$efficiency%",
        sourceBadge = sourceBadge,
        overviewNote = "页面已经连接本地睡眠存储。当前分数、效率和分期图是基于最近一次真实 session 生成的 mock 分析结果，等 epoch / nightly summary 入库后会自动替换成完整真实分析。",
        stages = stages,
        hourLabels = buildHourLabels(startedAt, endedAt),
        assistantTitle = "Hi，我已连接最近一次睡眠会话",
        assistantSummary = assistantSummary,
        assistantAdvice = assistantAdvice,
        footerNote = "当前报告已使用真实 session 时间、状态和包数；还没接上的部分是分期明细、summary 和 AI 报告表，所以这些分析层数据现在仍是 mock 推导结果。",
    )
}

private fun buildStageSequence(
    durationHours: Float,
    status: SleepSessionStatus,
): List<SleepStageVisual> {
    val segments = 13
    val awakeTail = if (status == SleepSessionStatus.COMPLETED) 1 else 2
    return List(segments) { index ->
        when {
            index == 0 -> SleepStageVisual.Light
            index < 3 -> if (durationHours >= 6.0f && index % 2 == 0) SleepStageVisual.Deep else SleepStageVisual.Light
            index in 3..5 -> if (index % 2 == 0) SleepStageVisual.Dream else SleepStageVisual.Light
            index in 6..9 -> if (durationHours >= 7.0f && index == 7) SleepStageVisual.Deep else SleepStageVisual.Light
            index >= segments - awakeTail -> SleepStageVisual.Awake
            index == segments - 3 -> SleepStageVisual.Dream
            else -> SleepStageVisual.Light
        }
    }
}

private fun buildHourLabels(
    startedAt: java.time.ZonedDateTime,
    endedAt: java.time.ZonedDateTime,
): List<String> {
    val totalSteps = 8
    val labels = mutableListOf(startedAt.format(hourFormatter))
    repeat(totalSteps) { step ->
        labels += startedAt.plusHours((step + 1).toLong()).format(DateTimeFormatter.ofPattern("H"))
    }
    labels += endedAt.format(hourFormatter)
    return labels
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    SleepAgentPrototypeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161E39)),
        ) {
            ReportScreenContent()
        }
    }
}
