package com.sleepagent.prototype

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme

// ── Data ──

data class LastNightUi(
    val sessionId: String,
    val score: Int,
    val durationText: String,
    val onsetMin: Int,
    val wakeCount: Int,
    val insight: String,
    val focusMetric: String,
    val focusValue: String
)

data class TrendUi(
    val icon: ImageVector,
    val label: String,
    val before: String,
    val after: String,
    val accentColor: Color
)

data class RecentRecordUi(
    val sessionId: String,
    val dateLabel: String,
    val score: Int,
    val durationText: String
)

data class ImproveUiState(
    val goalTitle: String,
    val goalSubtitle: String,
    val completedDays: Int,
    val totalDays: Int,
    val streakDays: Int,
    val improvementMinutes: Int,
    val weekDayStates: List<DayState>,
    val encouragement: String,
    val lastNight: LastNightUi?,
    val trends: List<TrendUi>,
    val recentRecords: List<RecentRecordUi>
)

enum class DayState { Complete, Incomplete, Today }

fun mockImproveUiState(): ImproveUiState = ImproveUiState(
    goalTitle = "更快入睡",
    goalSubtitle = "本周已坚持 4 天",
    completedDays = 4,
    totalDays = 7,
    streakDays = 3,
    improvementMinutes = 9,
    weekDayStates = listOf(
        DayState.Complete, DayState.Complete, DayState.Complete,
        DayState.Complete, DayState.Today,
        DayState.Incomplete, DayState.Incomplete
    ),
    encouragement = "你正在形成更稳定的入睡节律",
    lastNight = LastNightUi(
        sessionId = "mock_last_night",
        score = 82,
        durationText = "7h12m",
        onsetMin = 18,
        wakeCount = 2,
        insight = "昨晚完成睡前放松后，入睡速度比最近平均快了 6 分钟。",
        focusMetric = "入睡",
        focusValue = "18m"
    ),
    trends = listOf(
        TrendUi(Icons.AutoMirrored.Filled.TrendingDown, "入睡用时", "28m", "21m", Color(0xFF2FCBBC)),
        TrendUi(Icons.AutoMirrored.Filled.TrendingUp, "作息规律", "一般", "良好", Color(0xFF2D79FF)),
        TrendUi(Icons.AutoMirrored.Filled.TrendingDown, "夜醒次数", "3次", "2次", Color(0xFF2FCBBC))
    ),
    recentRecords = listOf(
        RecentRecordUi("mock_1", "昨晚", 82, "7h12m"),
        RecentRecordUi("mock_2", "前天", 76, "6h48m"),
        RecentRecordUi("mock_3", "周一", 79, "7h01m")
    )
)

// ── Screen ──

@Composable
fun ImproveScreen(
    uiState: ImproveUiState = mockImproveUiState(),
    onOpenHistory: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
    onAdjustPlan: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Goal Hero
        item { ImproveGoalHero(uiState) }

        // Visual spacer to emphasize Hero
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 2. Weekly Progress (lightweight)
        item { WeeklyProgressLight(uiState) }

        // 3. Last Night Review
        item {
            LastNightReviewCard(
                lastNight = uiState.lastNight,
                onOpenReport = onOpenReport
            )
        }

        // 4. Trend Change (lightweight strip)
        item { TrendChangeStrip(trends = uiState.trends) }

        // 5. AI Improve Suggestion
        item {
            AiImproveSuggestionCard(onAdjustPlan = onAdjustPlan)
        }

        // 6. History Entry
        item {
            RecentHistorySection(
                records = uiState.recentRecords,
                onOpenReport = onOpenReport,
                onOpenHistory = onOpenHistory
            )
        }
    }
}

// ── 1. Improve Goal Hero ──

@Composable
private fun ImproveGoalHero(state: ImproveUiState) {
    val heroBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF141E3A),
            Color(0xFF1A2D55),
            Color(0xFF1F3A6B)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroBrush)
    ) {
        // ── Decorative layer: moon glow + stars ──
        // Large soft moon glow (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 10.dp)
                .size(64.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8F0FF).copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        // Crescent moon outline
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 22.dp)
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 28.dp)
                .size(26.dp)
                .background(heroBrush, CircleShape) // masks part of the circle to create crescent
        )
        // Small star dots
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp, end = 18.dp)
                .size(4.dp)
                .background(Color(0xFFB8D4FF).copy(alpha = 0.40f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 50.dp)
                .size(3.dp)
                .background(Color(0xFFB8D4FF).copy(alpha = 0.30f), CircleShape)
        )
        // Soft light orb (bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 40.dp)
                .size(48.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5BA0FF).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        // Tiny sparkle bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 16.dp)
                .size(5.dp)
                .background(Color(0xFF8AB4F8).copy(alpha = 0.30f), CircleShape)
        )

        // ── Content layer ──
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top row: label + adjust pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "当前目标",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.70f)
                )
                Surface(
                    onClick = { /* TODO: goal picker */ },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        "调整",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.76f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Goal title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFFB8D4FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    state.goalTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Progress bar
            val progress = state.completedDays.toFloat() / state.totalDays.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF5BA0FF),
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        state.goalSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                    Text(
                        "平均入睡减少 ${state.improvementMinutes} 分钟",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }

            // Encouragement
            Text(
                state.encouragement,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8AB4F8)
            )
        }
    }
}

// ── 2. Weekly Progress (lightweight) ──

private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun WeeklyProgressLight(state: ImproveUiState) {
    val pct = (state.completedDays * 100) / state.totalDays
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本周进度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pct}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    " · ${state.streakDays}天连续",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                )
            }
        }

        // Day dots with labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            state.weekDayStates.forEachIndexed { index, dayState ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                when (dayState) {
                                    DayState.Complete -> MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
                                    DayState.Today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                    DayState.Incomplete -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                },
                                CircleShape
                            )
                    ) {
                        if (dayState == DayState.Today) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.Center)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                    Text(
                        dayLabels[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dayState == DayState.Today)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

// ── 3. Last Night Review ──

@Composable
private fun LastNightReviewCard(
    lastNight: LastNightUi?,
    onOpenReport: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (lastNight == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    "还没有昨晚的复盘数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                )
                Text(
                    "完成一次睡眠后，会在这里对比你的改善目标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "昨晚复盘",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                    )
                }

                // Focus metric — goal-aligned
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Score pill (smaller, not dominant)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${lastNight.score}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Main focus: the goal-related metric
                    Column {
                        Text(
                            lastNight.focusValue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            lastNight.focusMetric,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Secondary metrics
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            lastNight.durationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                        Text(
                            "${lastNight.wakeCount}次夜醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                        )
                    }
                }

                // Insight
                Text(
                    lastNight.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                )

                // Report button
                Surface(
                    onClick = { onOpenReport(lastNight.sessionId) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        "查看详细报告",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ── 4. Trend Change Strip ──

@Composable
private fun TrendChangeStrip(trends: List<TrendUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "趋势变化",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(trends) { trend ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillParentMaxWidth(1f / trends.size.coerceAtLeast(1))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            trend.icon,
                            contentDescription = null,
                            tint = trend.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            trend.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                trend.before,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                            )
                            Text(
                                "→",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
                            )
                            Text(
                                trend.after,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = trend.accentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 5. AI Improve Suggestion ──

@Composable
private fun AiImproveSuggestionCard(onAdjustPlan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF143A9A),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    "AI 改善建议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Strategy text
            Text(
                "你最近的入睡速度已经在悄悄变好了，今晚不需要额外改变，保持稳定最重要。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
            )

            // Light action items
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AiActionItem("23:00 后放下手机，让大脑慢慢降温")
                AiActionItem("睡前做 8 分钟呼吸放松")
                AiActionItem("保持 23:30 前关灯入睡")
            }

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onAdjustPlan,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "调整今晚计划",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Surface(
                    onClick = { /* TODO: 进入 AI 对话页或弹窗 */ },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Text(
                        "问问 AI",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiActionItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                    CircleShape
                )
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
        )
    }
}

// ── 6. Recent History Section ──

@Composable
private fun RecentHistorySection(
    records: List<RecentRecordUi>,
    onOpenReport: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "最近记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
            )
            Surface(
                onClick = onOpenHistory,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            ) {
                Text(
                    "查看全部",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (records.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "完成一次睡眠后，我会在这里展示你的改善变化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    records.forEach { record ->
                        RecentHistoryRow(
                            record = record,
                            onClick = { onOpenReport(record.sessionId) }
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenHistory,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "查看全部记录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentHistoryRow(
    record: RecentRecordUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            record.dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${record.score}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            record.durationText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImproveScreenPreview() {
    SleepAgentPrototypeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF151D38))
        ) {
            ImproveScreen()
        }
    }
}
