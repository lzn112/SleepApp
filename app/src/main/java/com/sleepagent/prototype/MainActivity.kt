package com.sleepagent.prototype

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.data.SleepNightlySummaryRecord
import com.sleepagent.prototype.data.SleepSessionRecord
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.data.UserPreferences
import com.sleepagent.prototype.sleep.SleepScreen
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import java.time.LocalTime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            SleepAgentPrototypeTheme {
                SleepAgentApp()
            }
        }
    }
}

@Composable
fun SleepAgentApp() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var historyScreenVisible by remember { mutableStateOf(false) }
    var reportSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var aiChatVisible by remember { mutableStateOf(false) }
    var aiChatInitialPrompt by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!historyScreenVisible && reportSessionId == null) {
                SleepBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepBackgroundBrush())
        ) {
            SleepAppDecorations()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    historyScreenVisible -> {
                        SleepHistoryScreen(
                            onBackClick = { historyScreenVisible = false },
                            onSessionClick = { sessionId ->
                                reportSessionId = sessionId
                                historyScreenVisible = false
                            }
                        )
                    }
                    reportSessionId != null -> {
                        ReportScreenContent(sessionId = reportSessionId)
                        ReportOverlayBackButton {
                            reportSessionId = null
                        }
                    }
                    else -> {
                        when (selectedTab) {
                            MainTab.Home -> HomeScreen(
                                onNavigateToSleep = { selectedTab = MainTab.Sleep },
                                onNavigateToImprove = { selectedTab = MainTab.Improve },
                                onNavigateToCommunity = { selectedTab = MainTab.Community },
                                onAskAi = {
                                    aiChatInitialPrompt = "帮我看看今天最需要注意什么"
                                    aiChatVisible = true
                                }
                            )
                            MainTab.Improve -> ImproveScreen(
                                onOpenHistory = {
                                    historyScreenVisible = true
                                },
                                onOpenReport = { sessionId ->
                                    reportSessionId = sessionId
                                },
                                onAdjustPlan = {
                                    aiChatInitialPrompt = "帮我调整今晚睡前计划"
                                    aiChatVisible = true
                                }
                            )
                            MainTab.Sleep -> SleepScreen()
                            MainTab.Community -> CommunityScreen()
                            MainTab.Profile -> ProfileScreen(
                                onHistoryClick = { historyScreenVisible = true }
                            )
                        }
                    }
                }

                // AI Chat overlay (above all content)
                if (aiChatVisible) {
                    SleepAgentChatScreen(
                        onBack = {
                            aiChatVisible = false
                            aiChatInitialPrompt = null
                        },
                        initialPrompt = aiChatInitialPrompt
                    )
                }
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    Home("首页", Icons.Default.Home),
    Improve("改善", Icons.Default.AutoAwesome),
    Sleep("睡觉", Icons.Default.Bedtime),
    Community("社区", Icons.Default.Groups),
    Profile("我的", Icons.Default.Person)
}

@Composable
private fun SleepBackgroundBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val tertiary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    return Brush.verticalGradient(
        colors = listOf(
            primary,
            tertiary,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    )
}

@Composable
private fun SleepAppDecorations() {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val tertiary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f)
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = 40.dp, y = (-28).dp)
                .size(180.dp)
                .background(primary, CircleShape)
                .align(Alignment.TopStart)
        )
        Box(
            modifier = Modifier
                .offset(x = (-24).dp, y = 68.dp)
                .size(120.dp)
                .background(tertiary, CircleShape)
                .align(Alignment.TopEnd)
        )
        Box(
            modifier = Modifier
                .offset(x = 24.dp, y = 20.dp)
                .size(220.dp)
                .background(surface, CircleShape)
                .align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun HomeScreen(
    onNavigateToSleep: () -> Unit = {},
    onNavigateToImprove: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    onAskAi: () -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext
    val displayName = UserPreferences.getDisplayName(appContext)
    val now = LocalTime.now()
    val greetingText = when {
        now.hour in 6..11 -> "早上好，$displayName"
        now.hour in 12..17 -> "下午好，$displayName"
        now.hour in 18..22 -> "晚上好，$displayName"
        else -> "夜深了，$displayName"
    }
    val greetingHint = when {
        now.hour in 6..11 -> "昨晚睡得比前几天更稳定"
        now.hour in 12..17 -> "今天状态还好吗？"
        now.hour in 18..22 -> "准备进入今晚的睡眠节奏"
        else -> "已经很晚了，先慢慢放松下来"
    }
    val statusText = when {
        now.hour in 6..11 -> "恢复良好"
        now.hour in 12..17 -> "精力稳定"
        now.hour in 18..22 -> "适合放松"
        else -> "需要休息"
    }

    val latestSummary by produceState<SleepNightlySummaryRecord?>(null, appContext) {
        val repository = SleepStorageRepository(appContext)
        val session = runCatching {
            repository.listRecentSessions(limit = 1).firstOrNull()
        }.getOrNull()
        value = if (session != null) {
            runCatching { repository.getNightlySummary(session.sessionId) }.getOrNull()
        } else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeHeroCard(
                greetingText = greetingText,
                greetingHint = greetingHint,
                statusText = statusText,
                summary = latestSummary,
                onStartPlan = onNavigateToSleep
            )
        }

        item {
            TonightTimelineCard(onExecute = onNavigateToSleep)
        }

        item {
            SleepOverviewStrip(onClick = onNavigateToImprove)
        }

        item {
            QuickActionChips(
                onAskAi = onAskAi,
                onViewProgress = onNavigateToImprove,
                onCommunity = onNavigateToCommunity
            )
        }

        item {
            DeviceStatusBar()
        }
    }
}

@Composable
private fun CommunityScreen() {
    DiscoverScreenContent()
}

@Composable
private fun ProfileScreen(
    onHistoryClick: () -> Unit = {}
) {
    ProfileScreenContent(onHistoryClick = onHistoryClick)
}

@Composable
private fun ReportOverlayBackButton(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            modifier = Modifier
                .size(40.dp)
                .clickable { onBackClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun SleepBottomNavigation(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    StandardBottomNavItem(
                        tab = tab,
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StandardBottomNavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSleepTab = tab == MainTab.Sleep
    val displayLabel = tab.label
    val selectedContainer = if (isSleepTab) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val selectedContent = if (isSleepTab) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            selectedContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            selectedContent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconSize = when {
                selected && isSleepTab -> 24.dp
                selected -> 22.dp
                else -> 22.dp
            }
            Icon(
                imageVector = tab.icon,
                contentDescription = displayLabel,
                modifier = Modifier.size(iconSize),
                tint = if (selected) {
                    if (isSleepTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    selectedContent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (selected) {
                    if (isSleepTab) FontWeight.SemiBold else FontWeight.Medium
                } else {
                    FontWeight.Normal
                }
            )
        }
    }
}

@Composable
fun ScreenContainer(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            content()
        }
    }
}

@Composable
fun ScreenSection(
    title: String,
    lines: List<String>
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            lines.forEach { line ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ── Home Hero Card ──

@Composable
private fun HomeHeroCard(
    greetingText: String,
    greetingHint: String,
    statusText: String,
    summary: SleepNightlySummaryRecord?,
    onStartPlan: () -> Unit
) {
    val backgroundBrush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.84f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f)
        )
    )

    val score = if (summary != null) calculateHomeScore(summary) else null
    val durationText = formatHomeDuration(summary?.sleepDurationMs)
    val onsetMs = summary?.sleepOnsetLatencyMs ?: 0L
    val onsetMin = if (onsetMs > 0L) onsetMs / 60_000L else 0L
    val wakeCount = summary?.wakeCount ?: 0
    val aiText = homeAiBriefing(summary)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top: status pill + greeting
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = "昨晚睡眠",
                        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        greetingText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        greetingHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }

                // Score + metrics
                if (score != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Score circle
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.30f),
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
                                        )
                                    ),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "${score}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    "分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                )
                            }
                        }

                        // Metrics
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            HeroMetric(value = durationText, label = "时长")
                            HeroMetric(value = "${onsetMin}m", label = "入睡")
                            HeroMetric(value = "${wakeCount}次", label = "夜醒")
                        }
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                        )
                )

                // AI briefing
                if (summary != null) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "AI 睡眠管家",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.90f)
                            )
                            Text(
                                aiText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Default.SmartToy,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            aiText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                        )
                    }
                }

                // Status text + CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                    )
                    Surface(
                        onClick = onStartPlan,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "开始今晚计划",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
        )
    }
}

// ── Tonight Timeline Card ──

@Composable
private fun TonightTimelineCard(onExecute: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "今晚计划",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    onClick = onExecute,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "去执行",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TimelineStep("22:50", "放下手机", isActive = true, isLast = false)
                TimelineStep("23:00", "呼吸放松 8 分钟", isActive = false, isLast = false)
                TimelineStep("23:20", "佩戴头环", isActive = false, isLast = false)
                TimelineStep("23:30", "开始睡觉", isActive = false, isLast = true)
            }
        }
    }
}

@Composable
private fun TimelineStep(
    time: String,
    task: String,
    isActive: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Dot with connecting line
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .offset(y = 4.dp)
                    .size(if (isActive) 10.dp else 8.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        CircleShape
                    )
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Task label
        Text(
            task,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
        )
    }
}

// ── Sleep Overview Strip ──

@Composable
private fun SleepOverviewStrip(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "睡眠概览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "查看改善",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewPill(label = "本周平均", value = "7h03m")
                OverviewPill(label = "规律性", value = "良好")
                OverviewPill(label = "连续改善", value = "4天")
            }
        }
    }
}

@Composable
private fun OverviewPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
        )
    }
}

// ── Quick Actions ──

@Composable
private fun QuickActionChips(
    onAskAi: () -> Unit,
    onViewProgress: () -> Unit,
    onCommunity: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompactActionChip(
            icon = Icons.Default.SmartToy,
            label = "问问AI",
            onClick = onAskAi,
            modifier = Modifier.weight(1f)
        )
        CompactActionChip(
            icon = Icons.Default.SelfImprovement,
            label = "看改善",
            onClick = onViewProgress,
            modifier = Modifier.weight(1f)
        )
        CompactActionChip(
            icon = Icons.Default.Groups,
            label = "去打卡",
            onClick = onCommunity,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f)
            )
        }
    }
}

// ── Device Status Bar ──

@Composable
private fun DeviceStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "头环未连接",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f),
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Status Pill (shared) ──

@Composable
private fun StatusPill(
    text: String,
    background: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

// ── Shared helpers ──

private fun calculateHomeScore(summary: SleepNightlySummaryRecord): Int {
    val e = ((summary.sleepEfficiency ?: 0f) * 100f).roundToInt()
    val q = ((summary.dataQualityScore ?: 0.7f) * 100f).roundToInt()
    val deepBonus = if ((summary.deepSleepMs ?: 0L) >= 60L * 60L * 1000L) 6 else 0
    val wakePenalty = (summary.wakeCount ?: 0).coerceAtMost(10)
    return ((e * 0.65f + q * 0.35f).roundToInt() + deepBonus - wakePenalty).coerceIn(0, 100)
}

private fun formatHomeDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--"
    val m = ms / 60_000L
    return "${m / 60}h${m % 60}m"
}

private fun homeAiBriefing(summary: SleepNightlySummaryRecord?): String {
    if (summary == null) return "还没有昨晚的睡眠数据，完成一次完整睡眠后我会为你分析。"
    val deepMs = summary.deepSleepMs ?: 0L
    val wakeCount = summary.wakeCount ?: 0
    val efficiency = summary.sleepEfficiency ?: 0f
    return when {
        efficiency < 0.7f -> "睡眠效率偏低，可能是入睡时间较长或夜间中断较多。今晚提前 20 分钟放松。"
        wakeCount >= 3 -> "后半夜睡眠偏浅，醒了 ${wakeCount} 次。今晚减少睡前饮水和压力刺激。"
        deepMs < 60L * 60L * 1000L -> "深睡偏短，身体修复可能不够充分。今晚继续保持 23:30 前关灯。"
        else -> "整体睡得不错，睡眠结构比较完整。继续保持固定上床时间，让身体记住这个节律。"
    }
}

@Preview(showBackground = true)
@Composable
fun SleepAgentPreview() {
    SleepAgentPrototypeTheme {
        SleepAgentApp()
    }
}
