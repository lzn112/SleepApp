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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.data.MockDataGenerator
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Section row data ──

private data class SectionRow(
    val label: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val onClick: () -> Unit = {}
)

// ── Editable UI state ──

private data class UserProfileUiState(
    val nickname: String = "用户昵称",
    val gender: String = "未设置",
    val ageRange: String = "未设置",
    val currentGoal: String = "更快入睡"
)

private data class SleepPreferenceUiState(
    val targetSleepHours: Float = 7.5f,
    val defaultBedtime: String = "23:30",
    val defaultWakeTime: String = "07:30",
    val bedtimeReminder: String = "23:00",
    val smartWakeEnabled: Boolean = true,
    val soundAidPreferences: Set<String> = setOf("呼吸放松", "白噪音"),
    val aiCompanionEnabled: Boolean = true
)

// ── Goal options ──

private val goalOptions = listOf("更快入睡", "减少夜醒", "规律作息", "提升恢复感", "减少熬夜", "稳定起床时间")
private val genderOptions = listOf("男", "女", "不想填写")
private val ageRangeOptions = listOf("18-25", "26-35", "36-45", "46+")
private val soundAidOptions = listOf("呼吸放松", "白噪音", "雨声", "海浪", "睡前故事")

// ── Main entry ──

@Composable
fun ProfileScreenContent(
    onHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SleepStorageRepository(context) }
    val generator = remember { MockDataGenerator(repository) }

    var profileState by rememberSaveable { mutableStateOf(UserProfileUiState()) }
    var preferenceState by rememberSaveable { mutableStateOf(SleepPreferenceUiState()) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var showEditPreference by rememberSaveable { mutableStateOf(false) }

    when {
        showEditProfile -> {
            EditProfileSheet(
                profile = profileState,
                onDismiss = { showEditProfile = false },
                onSave = {
                    profileState = it
                    showEditProfile = false
                }
            )
        }

        showEditPreference -> {
            SleepPreferenceEditSheet(
                preference = preferenceState,
                onDismiss = { showEditPreference = false },
                onSave = {
                    preferenceState = it
                    showEditPreference = false
                }
            )
        }

        else -> ProfileMainContent(
            profileState = profileState,
            preferenceState = preferenceState,
            onEditProfile = { showEditProfile = true },
            onEditPreference = { showEditPreference = true },
            onHistoryClick = onHistoryClick,
            scope = scope,
            context = context,
            generator = generator
        )
    }
}

@Composable
private fun ProfileMainContent(
    profileState: UserProfileUiState,
    preferenceState: SleepPreferenceUiState,
    onEditProfile: () -> Unit,
    onEditPreference: () -> Unit,
    onHistoryClick: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    generator: MockDataGenerator
) {
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
            // ── Title ──
            item {
                Text(
                    "我的",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.94f)
                )
            }

            // ── 1. Profile Hero Card ──
            item { ProfileHeroCard(profile = profileState, onEdit = onEditProfile) }

            // ── 2. My Device Card ──
            item { MyDeviceCard() }

            // ── 3. Sleep Preferences ──
            item { SleepPreferenceSection(preference = preferenceState, onEdit = onEditPreference) }

            // ── 4. Data & Reports ──
            item { DataReportSection(onHistoryClick = onHistoryClick) }

            // ── 5. Service Center ──
            item { ServiceSection() }

            // ── 6. Settings & Privacy ──
            item { PrivacySettingSection() }

            // ── 7. Advanced Mode ──
            item { AdvancedModeCard() }

            // ── Generate demo data (hidden at bottom) ──
            item {
                Surface(
                    onClick = {
                        scope.launch {
                            android.widget.Toast.makeText(context, "正在生成演示数据...", android.widget.Toast.LENGTH_SHORT).show()
                            generator.generateLastSevenDays()
                            android.widget.Toast.makeText(context, "演示数据生成成功！", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "生成演示数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.24f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ── 1. Profile Hero Card ──

@Composable
private fun ProfileHeroCard(
    profile: UserProfileUiState,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar + name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF6C8CFF).copy(alpha = 0.18f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF6C8CFF),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        profile.nickname,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.94f)
                    )
                    Text(
                        "已连续记录 12 晚",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                    Text(
                        "当前目标：${profile.currentGoal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6C8CFF).copy(alpha = 0.70f)
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )

            // Edit button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (profile.gender != "未设置") "${profile.gender} · ${profile.ageRange}" else "完善信息，获得更个性化建议",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF6C8CFF).copy(alpha = 0.12f)
                ) {
                    Text(
                        "编辑资料",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6C8CFF),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ── 2. My Device Card ──

@Composable
private fun MyDeviceCard() {
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
            Text(
                "我的设备",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF6C8CFF).copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color(0xFF6C8CFF),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SleepAgent Headband",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Text(
                        "已连接 · 电量 76% · 信号正常",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.48f)
                    )
                }
            }

            Surface(
                onClick = { /* TODO: device management */ },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF6C8CFF).copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "设备管理",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6C8CFF),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ── 3. Sleep Preferences ──

@Composable
private fun SleepPreferenceSection(
    preference: SleepPreferenceUiState,
    onEdit: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "睡眠偏好",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6C8CFF).copy(alpha = 0.10f)
                ) {
                    Text(
                        "编辑",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6C8CFF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            PreferenceRow("目标睡眠时长", "${preference.targetSleepHours} 小时")
            PreferenceRow("默认入睡时间", preference.defaultBedtime)
            PreferenceRow("默认起床时间", preference.defaultWakeTime)
            PreferenceRow("睡前提醒", preference.bedtimeReminder)
            PreferenceRow("智能唤醒", if (preference.smartWakeEnabled) "已开启" else "已关闭")
            PreferenceRow("助眠偏好", preference.soundAidPreferences.joinToString(" + "))
            PreferenceRow("AI 陪伴", if (preference.aiCompanionEnabled) "已开启" else "已关闭")
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.62f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.80f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── 4. Data & Reports ──

@Composable
private fun DataReportSection(onHistoryClick: () -> Unit) {
    SectionCard(
        title = "数据与报告",
        rows = listOf(
            SectionRow("历史睡眠记录", "查看所有睡眠会话与分析报告", Icons.Default.CalendarMonth, onClick = onHistoryClick),
            SectionRow("睡眠趋势报告", "了解你的长期睡眠变化", Icons.Default.MonitorHeart),
            SectionRow("AI 睡眠报告", "智能睡眠质量分析与建议", Icons.Default.SelfImprovement),
            SectionRow("数据导出", "导出你的原始睡眠数据"),
            SectionRow("云同步", "同步数据到云端")
        )
    )
}

// ── 5. Service Center ──

@Composable
private fun ServiceSection() {
    SectionCard(
        title = "服务中心",
        rows = listOf(
            SectionRow("会员权益", "解锁长期趋势和个性化计划", Icons.Default.Diamond),
            SectionRow("帮助反馈", "常见问题与意见反馈"),
            SectionRow("关于 SleepAgent", "版本 1.0 · 你的睡眠伙伴")
        )
    )
}

// ── 6. Settings & Privacy ──

@Composable
private fun PrivacySettingSection() {
    SectionCard(
        title = "设置与隐私",
        rows = listOf(
            SectionRow("通知设置", "管理提醒与通知", Icons.Default.Tune),
            SectionRow("隐私权限", "数据授权与隐私管理"),
            SectionRow("账号与安全", "账号管理与数据保护"),
            SectionRow("用户协议", ""),
            SectionRow("隐私政策", "")
        )
    )
}

// ── Generic Section Card ──

@Composable
private fun SectionCard(
    title: String,
    rows: List<SectionRow>
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            rows.forEachIndexed { index, row ->
                SectionRowItem(row = row)
                if (index != rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.04f))
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRowItem(row: SectionRow) {
    val rowIcon = row.icon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { row.onClick() }
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rowIcon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    rowIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(36.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.82f)
            )
            if (row.subtitle.isNotEmpty()) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── 7. Advanced Mode Card ──

@Composable
private fun AdvancedModeCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "高级模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.38f)
            )
            Text(
                "研究者功能、原始信号和设备调试。普通用户无需开启。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.30f)
            )
            Surface(
                onClick = { /* TODO: enter advanced mode */ },
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Text(
                    "进入高级模式",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.36f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ── Edit Profile Sheet ──

@Composable
private fun EditProfileSheet(
    profile: UserProfileUiState,
    onDismiss: () -> Unit,
    onSave: (UserProfileUiState) -> Unit
) {
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var gender by rememberSaveable { mutableStateOf(profile.gender) }
    var ageRange by rememberSaveable { mutableStateOf(profile.ageRange) }
    var currentGoal by rememberSaveable { mutableStateOf(profile.currentGoal) }

    EditSheetScaffold(
        title = "编辑资料",
        onDismiss = onDismiss,
        onSave = {
            onSave(profile.copy(
                nickname = nickname.trim().ifBlank { profile.nickname },
                gender = gender,
                ageRange = ageRange,
                currentGoal = currentGoal
            ))
        }
    ) {
        GroupLabel("头像 / 昵称")
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            singleLine = true,
            label = { Text("昵称") },
            colors = darkFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        GroupLabel("基础信息")
        ChipGroup("性别", genderOptions, gender) { gender = it }
        ChipGroup("年龄段", ageRangeOptions, ageRange) { ageRange = it }

        GroupLabel("当前睡眠目标")
        ChipGroup(null, goalOptions, currentGoal) { currentGoal = it }
    }
}

// ── Sleep Preference Edit Sheet ──

@Composable
private fun SleepPreferenceEditSheet(
    preference: SleepPreferenceUiState,
    onDismiss: () -> Unit,
    onSave: (SleepPreferenceUiState) -> Unit
) {
    var targetSleepHours by rememberSaveable { mutableStateOf(preference.targetSleepHours) }
    var defaultBedtime by rememberSaveable { mutableStateOf(preference.defaultBedtime) }
    var defaultWakeTime by rememberSaveable { mutableStateOf(preference.defaultWakeTime) }
    var bedtimeReminder by rememberSaveable { mutableStateOf(preference.bedtimeReminder) }
    var smartWakeEnabled by rememberSaveable { mutableStateOf(preference.smartWakeEnabled) }
    var soundAidPreferences by rememberSaveable { mutableStateOf(preference.soundAidPreferences) }
    var aiCompanionEnabled by rememberSaveable { mutableStateOf(preference.aiCompanionEnabled) }

    EditSheetScaffold(
        title = "睡眠偏好",
        onDismiss = onDismiss,
        onSave = {
            onSave(preference.copy(
                targetSleepHours = targetSleepHours,
                defaultBedtime = defaultBedtime,
                defaultWakeTime = defaultWakeTime,
                bedtimeReminder = bedtimeReminder,
                smartWakeEnabled = smartWakeEnabled,
                soundAidPreferences = soundAidPreferences,
                aiCompanionEnabled = aiCompanionEnabled
            ))
        }
    ) {
        // Target sleep hours
        GroupLabel("目标睡眠时长")
        Text(
            "${targetSleepHours} 小时",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6C8CFF)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("5.0", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.30f))
            Slider(
                value = targetSleepHours,
                onValueChange = { targetSleepHours = (it * 2f).roundToInt() / 2f },
                valueRange = 5f..10f,
                steps = 9,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6C8CFF),
                    activeTrackColor = Color(0xFF6C8CFF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.10f)
                )
            )
            Text("10.0", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.30f))
        }

        // Default bedtime
        GroupLabel("默认入睡时间")
        TimeAdjustRow(time = defaultBedtime, onMinus = { defaultBedtime = adjustTimeStr(defaultBedtime, -15) }, onPlus = { defaultBedtime = adjustTimeStr(defaultBedtime, 15) })

        // Default wake time
        GroupLabel("默认起床时间")
        TimeAdjustRow(time = defaultWakeTime, onMinus = { defaultWakeTime = adjustTimeStr(defaultWakeTime, -15) }, onPlus = { defaultWakeTime = adjustTimeStr(defaultWakeTime, 15) })

        // Bedtime reminder
        GroupLabel("睡前提醒")
        ChipGroup(null, listOf("入睡前15分钟", "入睡前30分钟", "入睡前60分钟"), bedtimeReminder) { bedtimeReminder = it }

        // Smart wake
        ToggleSetting("智能唤醒", smartWakeEnabled) { smartWakeEnabled = it }

        // Sound aid preferences (multi-select)
        GroupLabel("助眠偏好")
        MultiChipGroup(options = soundAidOptions, selected = soundAidPreferences) { selected ->
            soundAidPreferences = if (soundAidPreferences.contains(selected)) {
                if (soundAidPreferences.size > 1) soundAidPreferences - selected else soundAidPreferences
            } else {
                soundAidPreferences + selected
            }
        }

        // AI companion
        ToggleSetting("AI 陪伴", aiCompanionEnabled) { aiCompanionEnabled = it }
    }
}

// ── Edit Sheet Shared Components ──

@Composable
private fun EditSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit
) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White.copy(alpha = 0.70f)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.weight(1f)
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
        }

        // Save button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C8CFF),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "保存",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.44f),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ChipGroup(
    title: String?,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.36f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChipGroup(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            Surface(
                onClick = { onToggle(option) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                border = if (isSelected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.62f)
        )
        Surface(
            onClick = { onToggle(!checked) },
            shape = RoundedCornerShape(12.dp),
            color = if (checked) Color(0xFF6C8CFF).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
        ) {
            Text(
                if (checked) "开启" else "关闭",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (checked) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.40f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun TimeAdjustRow(
    time: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onMinus,
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Text(
                "-15",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Text(
            time,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Surface(
            onClick = onPlus,
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Text(
                "+15",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White.copy(alpha = 0.90f),
    unfocusedTextColor = Color.White.copy(alpha = 0.70f),
    focusedLabelColor = Color(0xFF6C8CFF),
    unfocusedLabelColor = Color.White.copy(alpha = 0.40f),
    cursorColor = Color(0xFF6C8CFF),
    focusedBorderColor = Color(0xFF6C8CFF),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
)

private fun adjustTimeStr(time: String, deltaMin: Int): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
    val total = hour * 60 + minute + deltaMin
    val adjusted = (total + 1440) % 1440
    return "%02d:%02d".format(adjusted / 60, adjusted % 60)
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SleepAgentPrototypeTheme {
        ProfileScreenContent()
    }
}
