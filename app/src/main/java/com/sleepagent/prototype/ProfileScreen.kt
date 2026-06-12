package com.sleepagent.prototype

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sleepagent.prototype.data.UserPreferences
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import kotlinx.coroutines.launch

// ── Section row data ──

private data class SectionRow(
    val label: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val onClick: () -> Unit = {}
)

// ── Main entry ──

@Composable
fun ProfileScreenContent(
    onHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SleepStorageRepository(context) }
    val generator = remember { MockDataGenerator(repository) }

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
            item { ProfileHeroCard() }

            // ── 2. My Device Card ──
            item { MyDeviceCard() }

            // ── 3. Sleep Preferences ──
            item { SleepPreferenceSection() }

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
                            Toast.makeText(context, "正在生成演示数据...", Toast.LENGTH_SHORT).show()
                            generator.generateLastSevenDays()
                            Toast.makeText(context, "演示数据生成成功！", Toast.LENGTH_SHORT).show()
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
private fun ProfileHeroCard() {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(UserPreferences.getDisplayName(context)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

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
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            editText = displayName
                            showEditDialog = true
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        displayName,
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
                        "当前目标：更快入睡",
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

            // Improvement stat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "本周入睡时间减少 9 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Surface(
                    onClick = {
                        editText = displayName
                        showEditDialog = true
                    },
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

    // Edit dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    label = { Text("昵称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = editText.trim().ifBlank { return@TextButton }
                    context.getSharedPreferences("sleepagent_user", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("display_name", newName)
                        .apply()
                    displayName = newName
                    showEditDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
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
private fun SleepPreferenceSection() {
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
                "睡眠偏好",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f)
            )
            PreferenceRow("目标睡眠时长", "7.5 小时")
            PreferenceRow("默认起床时间", "07:30")
            PreferenceRow("睡前提醒", "23:00")
            PreferenceRow("智能唤醒", "已开启")
            PreferenceRow("助眠偏好", "呼吸放松 + 白噪音")
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

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SleepAgentPrototypeTheme {
        ProfileScreenContent()
    }
}
