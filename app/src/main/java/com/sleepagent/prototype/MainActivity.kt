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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.sleep.SleepScreen
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SleepBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
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
                when (selectedTab) {
                    MainTab.Home -> HomeScreen()
                    MainTab.Dream -> DreamScreen()
                    MainTab.Sleep -> SleepScreen()
                    MainTab.Community -> CommunityScreen()
                    MainTab.Profile -> ProfileScreen()
                }
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    Home("音乐", Icons.Default.Home),
    Dream("报告", Icons.Default.AutoAwesome),
    Sleep("睡眠", Icons.Default.Bedtime),
    Community("发现", Icons.Default.Groups),
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
private fun HomeScreen() {
    ScreenContainer(
        title = "晚安小窝",
        subtitle = "让小宠物陪你一起准备入睡，把晚间计划变得轻一点，也温柔一点。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PetHeroCard(
                petName = "Luna",
                mood = "有点困了",
                message = "我已经把今晚的入睡流程整理好了。我们先慢下来，再让脑波和夜色接管后面的部分。"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "昨夜睡眠",
                    value = "7h 26m",
                    hint = "比均值多 18 分钟",
                    icon = Icons.Default.AccessTime
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "宠物信赖感",
                    value = "92%",
                    hint = "连续 5 晚作息稳定",
                    icon = Icons.Default.Pets
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "唤醒窗口",
                    value = "07:00",
                    hint = "智能轻唤醒",
                    icon = Icons.Default.Notifications
                )
            }

            PetStatusCard(
                petName = "Luna",
                energy = "78%",
                comfort = "很安心",
                habit = "喜欢在 23:15 陪你进入睡眠模式"
            )

            ScreenSection(
                title = "今晚的陪睡任务",
                lines = listOf(
                    "23:00 - Luna 提醒你调暗灯光，放下高刺激内容",
                    "23:15 - 开启睡眠监测，宠物会进入安静陪伴状态",
                    "07:00 - 轻柔唤醒窗口开启，避免突然惊醒"
                )
            )

            ScreenSection(
                title = "Luna 的晚安建议",
                lines = listOf(
                    "今晚的节奏很好，继续保持固定上床时间。",
                    "如果头脑还有点兴奋，先做 3 分钟缓慢呼吸。",
                    "你的深睡比例正在回升，明天大概率会更轻松。"
                )
            )

            ScreenSection(
                title = "恢复状态",
                lines = listOf(
                    "深睡占比：24%",
                    "恢复分数：81/100",
                    "本周压力趋势：整体平稳"
                )
            )
        }
    }
}

@Composable
private fun PetHeroCard(
    petName: String,
    mood: String,
    message: String
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.90f)
        )
    )

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusPill(
                            text = "宠物陪伴模式",
                            background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )

                        Text(
                            text = "$petName 正在陪你准备入睡",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))
                    SleepPetIllustration()
                }

                PetSpeechBubble(
                    text = "“今晚也要稳稳睡着，我会在这里等你。”",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = "当前状态：$mood",
                        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    StatusPill(
                        text = "目标睡眠：8h",
                        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PetStatusCard(
    petName: String,
    energy: String,
    comfort: String,
    habit: String
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "$petName 的陪伴状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "它会根据你的节奏，切换提醒、安静和唤醒陪伴。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "陪伴能量",
                    value = energy,
                    hint = "今晚状态稳定",
                    icon = Icons.Default.CheckCircle
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "安抚感",
                    value = comfort,
                    hint = "适合轻柔入睡",
                    icon = Icons.Default.Bedtime
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "习惯偏好：$habit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PetSpeechBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val bubbleShape = GenericShape { size, _ ->
        val tailWidth = size.width * 0.13f
        val tailHeight = size.height * 0.18f
        addRoundRect(
            roundRect = androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height - tailHeight,
                radiusX = 28f,
                radiusY = 28f
            )
        )
        moveTo(size.width * 0.24f, size.height - tailHeight)
        lineTo(size.width * 0.24f + tailWidth, size.height - tailHeight)
        lineTo(size.width * 0.24f + tailWidth * 0.35f, size.height)
        close()
    }

    Surface(
        shape = bubbleShape,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun SleepPetIllustration() {
    val fur = MaterialTheme.colorScheme.surface
    val innerEar = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
    val faceShadow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val eye = MaterialTheme.colorScheme.onSurface
    val blush = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f)

    Box(
        modifier = Modifier.size(width = 132.dp, height = 150.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .offset(y = 12.dp)
                .size(width = 118.dp, height = 126.dp)
                .background(
                    color = faceShadow.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(44.dp)
                )
        )
        Box(
            modifier = Modifier
                .offset(x = (-26).dp, y = 8.dp)
                .size(34.dp)
                .background(
                    fur,
                    RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 28.dp,
                        bottomEnd = 8.dp,
                        bottomStart = 22.dp
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .background(innerEar, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = 26.dp, y = 8.dp)
                .size(34.dp)
                .background(
                    fur,
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 6.dp,
                        bottomEnd = 22.dp,
                        bottomStart = 8.dp
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .background(innerEar, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .offset(y = 24.dp)
                .size(width = 108.dp, height = 108.dp)
                .background(fur, RoundedCornerShape(40.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(40.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 26.dp, y = (-6).dp)
                    .size(width = 8.dp, height = 12.dp)
                    .background(eye, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-26).dp, y = (-6).dp)
                    .size(width = 8.dp, height = 12.dp)
                    .background(eye, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 12.dp)
                    .size(width = 26.dp, height = 18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .size(8.dp)
                        .background(innerEar, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 18.dp, y = 12.dp)
                    .size(12.dp)
                    .background(blush, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-18).dp, y = 12.dp)
                    .size(12.dp)
                    .background(blush, CircleShape)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-8).dp)
            ) {
                Text(
                    text = "Zz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DreamScreen() {
    ReportScreenContent()
}

@Composable
private fun CommunityScreen() {
    DiscoverScreenContent()
}

@Composable
private fun ProfileScreen() {
    ProfileScreenContent()
}

@Composable
private fun HeroCard(
    eyebrow: String,
    title: String,
    body: String,
    status: String,
    icon: ImageVector
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.94f)
        )
    )

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusPill(
                    text = eyebrow,
                    background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    lineHeight = MaterialTheme.typography.headlineSmall.lineHeight
                )

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.90f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = status,
                        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    hint: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
                MainTab.values().forEach { tab ->
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
    val displayLabel = when (tab) {
        MainTab.Home -> "首页"
        MainTab.Dream -> "报告"
        MainTab.Sleep -> "睡眠"
        MainTab.Community -> "发现"
        MainTab.Profile -> "我的"
    }
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

@Preview(showBackground = true)
@Composable
fun SleepAgentPreview() {
    SleepAgentPrototypeTheme {
        SleepAgentApp()
    }
}
