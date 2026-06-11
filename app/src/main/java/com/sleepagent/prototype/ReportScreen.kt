package com.sleepagent.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.sleepagent.prototype.data.SleepNightlySummaryRecord
import com.sleepagent.prototype.data.SleepSessionRecord
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val time: String,
    val suggestions: List<AdviceItem> = emptyList()
)

private enum class MessageSender {
    AI, User
}

private data class AdviceItem(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val iconTint: Color
)

private data class ReportUiModel(
    val sessionId: String?,
    val dateText: String,
    val score: Int,
    val efficiencyText: String,
    val totalSleepText: String,
    val onsetLatencyText: String,
    val wakeCountText: String,
    val summaryTag: String,
    val chatMessages: List<ChatMessage>,
    val trendScores: List<Int> = emptyList()
)

@Composable
fun ReportScreenContent(
    sessionId: String? = null
) {
    val appContext = LocalContext.current.applicationContext
    val listState = rememberLazyListState()
    var activeCardTab by remember { mutableStateOf(0) } // 0 for Summary, 1 for Trend
    
    val uiModel by produceState(
        initialValue = mockChatUiModel(),
        key1 = appContext,
        key2 = sessionId,
    ) {
        val repository = SleepStorageRepository(appContext)

        val session = runCatching {
            if (sessionId != null) {
                repository.getSession(sessionId)
            } else {
                repository.listRecentSessions(limit = 1).firstOrNull()
            }
        }.getOrNull()

        value = if (session != null) {
            val summary = runCatching {
                repository.getNightlySummary(session.sessionId)
            }.getOrNull()

            buildRealChatUiModel(session, summary, repository)
        } else {
            mockChatUiModel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .background(Color(0xFF0F172A))
    ) {
        ChatHeader(dateText = uiModel.dateText)

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardTabSelector(
                        selectedTabIndex = activeCardTab,
                        onTabSelected = { activeCardTab = it }
                    )

                    if (activeCardTab == 0) {
                        SleepSummaryCard(uiModel = uiModel)
                    } else if (uiModel.trendScores.size >= 2) {
                        SleepTrendCard(scores = uiModel.trendScores)
                    } else {
                        // Fallback if trend is empty
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无趋势数据", color = Color.White.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            items(uiModel.chatMessages) { message ->
                ChatMessageItem(message = message)
            }
            
            item {
                QuickQuestionBar()
            }
        }

        ChatInputBar()
    }
}

@Composable
private fun CardTabSelector(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(
            text = "睡眠摘要",
            isSelected = selectedTabIndex == 0,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(0) }
        )
        TabItem(
            text = "趋势分析",
            isSelected = selectedTabIndex == 1,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
    }
}

@Composable
private fun TabItem(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF334155) else Color.Transparent,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ChatHeader(dateText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI 睡眠助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "基于 $dateText 的睡眠报告",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.clickable { /* TODO: Open History */ }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "历史",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SleepSummaryCard(uiModel: ReportUiModel) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "昨晚睡眠摘要",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                SummaryTag(text = uiModel.summaryTag)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Ring (Simplified)
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.1f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFF10B981),
                            startAngle = -90f,
                            sweepAngle = (uiModel.score / 100f) * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiModel.score.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Metrics Grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row {
                        SummaryMetric(
                            label = "总睡眠时长",
                            value = uiModel.totalSleepText,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetric(
                            label = "睡眠效率",
                            value = uiModel.efficiencyText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row {
                        SummaryMetric(
                            label = "入睡时长",
                            value = uiModel.onsetLatencyText,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetric(
                            label = "夜间醒来",
                            value = uiModel.wakeCountText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "查看完整报告 >",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF10B981),
                modifier = Modifier.align(Alignment.End).clickable { /* TODO */ }
            )
        }
    }
}

@Composable
private fun SleepTrendCard(scores: List<Int>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    androidx.compose.runtime.LaunchedEffect(scores) {
        modelProducer.runTransaction {
            lineSeries { series(scores) }
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "近期得分趋势",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(Color(0xFF38BDF8))),
                                    areaFill = LineCartesianLayer.AreaFill.single(Fill(Color(0x2038BDF8))),
                                    pointConnector = LineCartesianLayer.PointConnector.cubic()
                                )
                            )
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SummaryTag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF10B981).copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10B981)
        )
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isAi = message.sender == MessageSender.AI
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        if (isAi) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isAi) Alignment.Start else Alignment.End
        ) {
            if (isAi) {
                Text(
                    text = "AI 助手",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isAi) 4.dp else 20.dp,
                    topEnd = if (isAi) 20.dp else 4.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isAi) Color(0xFF1E293B) else Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp
                    )
                    
                    if (message.suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        message.suggestions.forEach { advice ->
                            AdviceRow(advice)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "生成今晚计划 >",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF10B981),
                                modifier = Modifier.clickable {  }
                            )
                            Text(
                                text = "查看更多建议",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.clickable {  }
                            )
                        }
                    }
                }
            }
            
            Text(
                text = message.time,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!isAi) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdviceRow(item: AdviceItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = item.iconTint.copy(alpha = 0.2f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = item.desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QuickQuestionBar() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "你可以问我",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.4f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip("为什么入睡慢？", Icons.Default.Info)
            QuickChip("今晚怎么改善？", Icons.Default.AutoAwesome)
        }
    }
}

@Composable
private fun QuickChip(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.clickable { }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ChatInputBar() {
    Surface(
        color = Color(0xFF0F172A),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "询问 AI 睡眠助手...",
                        color = Color.White.copy(alpha = 0.3f)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF38BDF8)
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            )

            Surface(
                shape = CircleShape,
                color = Color(0xFF2563EB),
                modifier = Modifier.size(48.dp).clickable { }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun mockChatUiModel(): ReportUiModel {
    return ReportUiModel(
        sessionId = null,
        dateText = "6月10日",
        score = 82,
        efficiencyText = "84%",
        totalSleepText = "7h 28m",
        onsetLatencyText = "18m",
        wakeCountText = "2次",
        summaryTag = "良好",
        trendScores = listOf(72, 78, 75, 85, 80, 82),
        chatMessages = listOf(
            ChatMessage(
                sender = MessageSender.AI,
                text = "昨晚整体睡得不错，深睡较充足，REM 结构完整，但入睡花了 18 分钟，后半夜出现了 2 次短暂觉醒。\n\n我建议今晚重点关注「缩短入睡时间」，试着放松身心，帮你更快进入睡眠状态。",
                time = "09:30"
            ),
            ChatMessage(
                sender = MessageSender.User,
                text = "今晚怎么改善？",
                time = "09:31"
            ),
            ChatMessage(
                sender = MessageSender.AI,
                text = "结合你的数据，我为你准备了今晚改善建议：",
                time = "09:31",
                suggestions = listOf(
                    AdviceItem("睡前 30 分钟减少屏幕刺激", "降低大脑活跃度，更容易入睡", Icons.Default.AutoAwesome, Color(0xFFEAB308)),
                    AdviceItem("做 5 分钟放松呼吸练习", "降低紧张感，帮助更快进入睡眠", Icons.Default.Person, Color(0xFF38BDF8)),
                    AdviceItem("可以开启助眠声音 15 分钟", "营造放松氛围，减少外界干扰", Icons.Default.Notifications, Color(0xFF818CF8))
                )
            )
        )
    )
}

private suspend fun buildRealChatUiModel(
    session: SleepSessionRecord,
    summary: SleepNightlySummaryRecord?,
    repository: SleepStorageRepository
): ReportUiModel {
    val zoneId = ZoneId.systemDefault()
    val startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zoneId)
    val dateText = startedAt.format(DateTimeFormatter.ofPattern("M月d日"))

    val score = summary?.let { calculateSleepScore(it) } ?: 72
    val efficiencyText = summary?.sleepEfficiency?.let { "${(it * 100).roundToInt()}%" } ?: "--"
    
    val totalSleepMs = summary?.sleepDurationMs ?: (session.endedAtEpochMs?.let { it - session.startedAtEpochMs } ?: 0L)
    val totalSleepText = formatDuration(totalSleepMs)
    
    val onsetLatencyText = summary?.sleepOnsetLatencyMs?.let { "${it / 60000}m" } ?: "--"
    val wakeCountText = summary?.wakeCount?.let { "${it}次" } ?: "0次"
    
    val summaryTag = when {
        score >= 85 -> "优秀"
        score >= 75 -> "良好"
        else -> "一般"
    }

    // Fetch historical scores for the trend card
    val recentSessions = repository.listRecentSessions(limit = 7)
    val trendScores = recentSessions.reversed().map { s ->
        val sSummary = repository.getNightlySummary(s.sessionId)
        sSummary?.let { calculateSleepScore(it) } ?: 70
    }

    val aiIntroText = if (summary != null) {
        val wakeCount = summary.wakeCount ?: 0
        "昨晚你总共睡了 $totalSleepText，睡眠效率为 $efficiencyText。${if (wakeCount > 1) "夜间有几次轻微波动" else "睡眠过程非常平稳"}。\n\n我建议你今晚关注环境噪音，确保深度睡眠不被打扰。"
    } else {
        "昨晚的睡眠数据正在同步中。根据初步记录，你的睡眠时长达到了 $totalSleepText，建议稍后查看完整分析报告。"
    }

    return ReportUiModel(
        sessionId = session.sessionId,
        dateText = dateText,
        score = score,
        efficiencyText = efficiencyText,
        totalSleepText = totalSleepText,
        onsetLatencyText = onsetLatencyText,
        wakeCountText = wakeCountText,
        summaryTag = summaryTag,
        trendScores = trendScores,
        chatMessages = listOf(
            ChatMessage(
                sender = MessageSender.AI,
                text = aiIntroText,
                time = "08:30"
            )
        )
    )
}

private fun calculateSleepScore(summary: SleepNightlySummaryRecord): Int {
    val efficiencyScore = ((summary.sleepEfficiency ?: 0f) * 100f).roundToInt()
    val qualityScore = ((summary.dataQualityScore ?: 0.7f) * 100f).roundToInt()
    return (efficiencyScore * 0.7f + qualityScore * 0.3f).roundToInt().coerceIn(0, 100)
}

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "--"
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    val remainMinutes = minutes % 60L
    return if (hours > 0) "${hours}h ${remainMinutes}m" else "${minutes}m"
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
    SleepAgentPrototypeTheme {
        ReportScreenContent()
    }
}
