package com.sleepagent.prototype

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SingleBed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.sleepagent.prototype.data.SleepNightlySummaryRecord
import com.sleepagent.prototype.data.SleepSessionRecord
import com.sleepagent.prototype.data.SleepSessionStatus
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class SleepSessionWithSummary(
    val session: SleepSessionRecord,
    val summary: SleepNightlySummaryRecord?
)

@Composable
fun SleepHistoryScreen(
    onBackClick: () -> Unit = {},
    onSessionClick: (String) -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext
    var selectedFilter by remember { mutableStateOf<SleepSessionStatus?>(null) }

    val allSessionsWithSummaries by produceState(
        initialValue = emptyList<SleepSessionWithSummary>(),
        key1 = appContext
    ) {
        val repository = SleepStorageRepository(appContext)
        val sessions = runCatching {
            repository.listRecentSessions(limit = 100)
        }.getOrDefault(emptyList())

        value = sessions.map { session ->
            SleepSessionWithSummary(
                session = session,
                summary = repository.getNightlySummary(session.sessionId)
            )
        }
    }

    val filteredSessions = if (selectedFilter == null) {
        allSessionsWithSummaries
    } else {
        allSessionsWithSummaries.filter { it.session.status == selectedFilter }
    }

    val groupedSessions = filteredSessions.groupBy {
        Instant.ofEpochMilli(it.session.startedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF151D38)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HistoryTopBar(onBackClick = onBackClick)
        }

        item {
            OverviewCard(allSessionsWithSummaries.take(7))
        }

        item {
            FilterTabs(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )
        }

        if (filteredSessions.isEmpty()) {
            item {
                SleepHistoryEmpty()
            }
        } else {
            groupedSessions.forEach { (date, sessions) ->
                item {
                    DateHeader(date)
                }
                items(sessions) { sessionWithSummary ->
                    SleepHistoryRow(
                        item = sessionWithSummary,
                        onClick = { onSessionClick(sessionWithSummary.session.sessionId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "返回",
            tint = Color.White,
            modifier = Modifier
                .size(32.dp)
                .clickable { onBackClick() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "历史记录",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "日历",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "筛选",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun OverviewCard(recentItems: List<SleepSessionWithSummary>) {
    val completedSessions = recentItems.filter { it.session.status == SleepSessionStatus.COMPLETED }
    val abortedCount = recentItems.count { it.session.status == SleepSessionStatus.ABORTED }

    val avgDurationMs = if (completedSessions.isNotEmpty()) {
        completedSessions.mapNotNull { it.summary?.sleepDurationMs }.average().toLong()
    } else 0L

    val avgEfficiency = if (completedSessions.isNotEmpty()) {
        completedSessions.mapNotNull { it.summary?.sleepEfficiency }.average().toFloat()
    } else 0f

    val hours = avgDurationMs / 3_600_000
    val minutes = (avgDurationMs % 3_600_000) / 60_000

    val chartItems = recentItems.take(7).reversed()
    val modelProducer = remember { CartesianChartModelProducer() }

    val dateRangeText = if (chartItems.isNotEmpty()) {
        val formatter = DateTimeFormatter.ofPattern("M月d日")
        val start = Instant.ofEpochMilli(chartItems.first().session.startedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val end = Instant.ofEpochMilli(chartItems.last().session.startedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        "${start.format(formatter)} - ${end.format(formatter)}"
    } else ""

    androidx.compose.runtime.LaunchedEffect(chartItems) {
        if (chartItems.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(chartItems.map { item ->
                        val durationMs = item.summary?.sleepDurationMs
                            ?: item.session.endedAtEpochMs?.let { it - item.session.startedAtEpochMs }
                            ?: 0L
                        durationMs / 3_600_000f
                    })
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E2746),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "近 7 晚睡眠概览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (dateRangeText.isNotEmpty()) {
                        Text(
                            text = dateRangeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                if (chartItems.isNotEmpty()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.single(Fill(Color(0xFF2FCBBC))),
                                        areaFill = LineCartesianLayer.AreaFill.single(Fill(Color(0x202FCBBC))),
                                        pointConnector = LineCartesianLayer.PointConnector.cubic()
                                    )
                                )
                            ),
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewItem("平均睡眠时长", "${hours}h ${minutes}m", Modifier.weight(1f))
                    OverviewItem("平均睡眠效率", "${(avgEfficiency * 100).toInt()}%", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewItem("记录晚数", "${completedSessions.size} 晚", Modifier.weight(1f))
                    OverviewItem("中断次数", "$abortedCount 次", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun FilterTabs(
    selectedFilter: SleepSessionStatus?,
    onFilterSelected: (SleepSessionStatus?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            label = "全部",
            isSelected = selectedFilter == null,
            onClick = { onFilterSelected(null) }
        )
        FilterChip(
            label = "已完成",
            isSelected = selectedFilter == SleepSessionStatus.COMPLETED,
            onClick = { onFilterSelected(SleepSessionStatus.COMPLETED) }
        )
        FilterChip(
            label = "采集中",
            isSelected = selectedFilter == SleepSessionStatus.RECORDING,
            onClick = { onFilterSelected(SleepSessionStatus.RECORDING) }
        )
        FilterChip(
            label = "中断",
            isSelected = selectedFilter == SleepSessionStatus.ABORTED,
            onClick = { onFilterSelected(SleepSessionStatus.ABORTED) }
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF2E3A59) else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val text = when (date) {
        today -> "今天"
        yesterday -> "昨天"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("M月d日")
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
            "${date.format(formatter)} $dayOfWeek"
        }
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SleepHistoryRow(
    item: SleepSessionWithSummary,
    onClick: () -> Unit
) {
    val session = item.session
    val summary = item.summary
    
    val zoneId = ZoneId.systemDefault()
    val startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zoneId)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTimeText = startedAt.format(timeFormatter)
    
    val endTimeText = session.endedAtEpochMs?.let {
        Instant.ofEpochMilli(it).atZone(zoneId).format(timeFormatter)
    } ?: "--:--"

    val durationText = if (session.endedAtEpochMs != null) {
        val minutes = ((session.endedAtEpochMs - session.startedAtEpochMs) / 60_000L).coerceAtLeast(0L)
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        "进行中"
    }

    val themeColor = when (session.status) {
        SleepSessionStatus.COMPLETED -> Color(0xFF2FCBBC)
        SleepSessionStatus.RECORDING -> Color(0xFFFF6B72)
        SleepSessionStatus.ABORTED -> Color(0xFFE7C57B)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E2746),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Icon Box
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = themeColor.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SingleBed,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$startTimeText - $endTimeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                StatusTag(session.status)
            }

            if (session.status == SleepSessionStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val score = (summary?.sleepEfficiency?.times(100))?.toInt() ?: "--"
                    val quality = when {
                        (summary?.dataQualityScore ?: 0f) > 0.8f -> "良好"
                        (summary?.dataQualityScore ?: 0f) > 0.5f -> "一般"
                        else -> "较差"
                    }
                    
                    Text(
                        text = "睡眠评分",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2FCBBC)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "数据质量",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = quality,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2FCBBC)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (session.status == SleepSessionStatus.ABORTED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "采集时间过短，未生成报告",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTag(status: SleepSessionStatus) {
    val (text, color) = when (status) {
        SleepSessionStatus.COMPLETED -> "已完成" to Color(0xFF2FCBBC)
        SleepSessionStatus.RECORDING -> "采集中" to Color(0xFFFF6B72)
        SleepSessionStatus.ABORTED -> "已中断" to Color(0xFFE7C57B)
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun SleepHistoryEmpty() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "还没有本地睡眠记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = "完成一次睡眠采集后，记录会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.52f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SleepHistoryScreenPreview() {
    SleepAgentPrototypeTheme {
        SleepHistoryScreen()
    }
}
