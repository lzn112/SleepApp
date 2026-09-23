package com.sleepagent.prototype

import com.sleepagent.prototype.ui.theme.SleepPalette

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme

// ── Tab enum ──

private enum class CommunityTab(val label: String) {
    CheckIn("打卡"),
    Discuss("交流"),
    Goods("好物"),
    Course("课程")
}

// ── Mock data ──

private data class CheckInUser(val name: String, val message: String)
private data class CompanionRoom(val name: String, val count: Int)
private data class CircleTag(val name: String)
private data class DiscussionPost(val title: String, val circle: String, val author: String, val replies: Int, val summary: String)
private data class GoodScenario(val title: String, val items: String, val subtitle: String)
private data class CourseItem(val title: String, val subtitle: String, val actionLabel: String)

private val mockCheckInUsers = listOf(
    CheckInUser("小鹿", "今晚目标 23:30 前睡觉"),
    CheckInUser("Blue", "刚完成 8 分钟呼吸放松"),
    CheckInUser("阿眠", "今天不刷短视频挑战完成"),
    CheckInUser("夜猫改作息中", "Day 4 打卡，比昨天早了 15 分钟"),
    CheckInUser("清晨捕光", "睡前泡了脚，感觉更容易入睡了")
)

private val mockRooms = listOf(
    CompanionRoom("23:30 早睡房", 128),
    CompanionRoom("考研党恢复房", 36),
    CompanionRoom("上班族放松房", 52),
    CompanionRoom("新手爸妈夜醒房", 21)
)

private val mockCircles = listOf(
    CircleTag("入睡困难"), CircleTag("半夜醒来"), CircleTag("熬夜党"),
    CircleTag("学生党"), CircleTag("上班族"), CircleTag("焦虑放松"),
    CircleTag("助眠声音"), CircleTag("早醒")
)

private val mockDiscussions = listOf(
    DiscussionPost("睡前越想睡越睡不着怎么办？", "入睡困难", "晚风", 24, "越是想快点睡着，脑子越清醒，大家都怎么应对的？"),
    DiscussionPost("大家白噪音一般开多久？", "助眠声音", "Blue", 18, "我开一晚上会不会对耳朵不好？"),
    DiscussionPost("半夜醒了要不要看手机？", "半夜醒来", "阿眠", 36, "看了更难睡，不看又无聊..."),
    DiscussionPost("最近把入睡时间从 40 分钟降到 20 分钟了", "入睡困难", "清晨捕光", 42, "分享几个对我有效的小方法。"),
    DiscussionPost("室友打呼噜怎么办？", "学生党", "熬夜冠军", 15, "宿舍里真的很难入睡...")
)

private val mockGoodScenarios = listOf(
    GoodScenario("入睡困难", "白噪音 · 呼吸引导 · 遮光眼罩", "帮你更快进入睡眠状态"),
    GoodScenario("容易被吵醒", "耳塞 · 白噪音 · 门缝隔音条", "减少夜间被外界声音打扰"),
    GoodScenario("睡醒脖子酸", "护颈枕 · 床垫测评 · 睡姿调整", "醒来后身体更轻松"),
    GoodScenario("睡前焦虑", "冥念头带 · 呼吸灯 · 助眠音波", "让大脑慢慢平静下来")
)

private val mockCourses = listOf(
    CourseItem("7 天更快入睡计划", "每天 5 分钟，帮你建立更稳定的入睡节律。", "开始学习"),
    CourseItem("半夜醒来应对课", "学习醒来后如何重新放松入睡，不再盯着天花板。", "开始学习"),
    CourseItem("睡前放松训练", "跟随呼吸和身体扫描，慢慢进入睡眠状态。", "开始练习"),
    CourseItem("睡眠知识入门", "了解你的睡眠周期，学会和自己的身体合作。", "开始阅读")
)

// ── Main entry ──

@Composable
fun DiscoverScreenContent() {
    var selectedTab by rememberSaveable { mutableStateOf(CommunityTab.CheckIn) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SleepPalette.BackgroundTop,
                        SleepPalette.BackgroundMiddle,
                        SleepPalette.BackgroundBottom
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
            // Title
            item {
                Text(
                    "社区",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SleepPalette.Ink
                )
            }

            // Tab bar
            item { CommunityTopTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) }

            // Channel content
            item {
                when (selectedTab) {
                    CommunityTab.CheckIn -> CheckInChannel()
                    CommunityTab.Discuss -> DiscussChannel()
                    CommunityTab.Goods -> GoodsChannel()
                    CommunityTab.Course -> CourseChannel()
                }
            }
        }
    }
}

// ── Tab bar ──

@Composable
private fun CommunityTopTabs(
    selectedTab: CommunityTab,
    onTabSelected: (CommunityTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunityTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Surface(
                onClick = { onTabSelected(tab) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) SleepPalette.Primary else SleepPalette.Card,
                border = if (!isSelected) BorderStroke(1.dp, SleepPalette.Border) else null
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else SleepPalette.Muted,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ── 1. Check-in Channel ──

@Composable
private fun CheckInChannel() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero
        CheckInHero()

        // Today's challenges
        CheckInChallenges()

        // Companion rooms
        CompanionRoomsSection()

        // Live check-ins
        LiveCheckInsSection()
    }
}

@Composable
private fun CheckInHero() {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = SleepPalette.Primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SleepPalette.Primary.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = SleepPalette.Primary.copy(alpha = 0.80f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "已有 128 人准备在 23:30 前睡觉",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SleepPalette.Ink
                )
            }

            Text(
                "今晚一起早睡",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SleepPalette.Ink
            )

            Text(
                "加入今晚打卡，和大家一起慢慢放松，安静地结束这一天。",
                style = MaterialTheme.typography.bodyMedium,
                color = SleepPalette.Muted
            )

            Button(
                onClick = { /* TODO: join check-in */ },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleepPalette.Primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "加入今晚打卡",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CheckInChallenges() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "今日挑战",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val challenges = listOf(
                "23:30 前关灯",
                "睡前 30 分钟不刷手机",
                "呼吸放松 8 分钟",
                "7 天规律作息"
            )
            items(challenges) { challenge ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SleepPalette.Card,
                    border = BorderStroke(1.dp, SleepPalette.Border)
                ) {
                    Text(
                        challenge,
                        style = MaterialTheme.typography.labelLarge,
                        color = SleepPalette.Ink,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionRoomsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "陪伴房间",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        mockRooms.forEach { room ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SleepPalette.Card,
                border = BorderStroke(1.dp, SleepPalette.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = SleepPalette.Subtle,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            room.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SleepPalette.Ink
                        )
                    }
                    Text(
                        "${room.count} 人",
                        style = MaterialTheme.typography.labelMedium,
                        color = SleepPalette.Primary.copy(alpha = 0.70f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCheckInsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "大家正在打卡",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        mockCheckInUsers.forEach { user ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SleepPalette.Card,
                border = BorderStroke(1.dp, SleepPalette.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SleepPalette.Primary.copy(alpha = 0.20f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.name.take(1),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SleepPalette.Primary
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            user.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SleepPalette.Ink
                        )
                        Text(
                            user.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SleepPalette.Muted
                        )
                    }
                }
            }
        }
    }
}

// ── 2. Discuss Channel ──

@Composable
private fun DiscussChannel() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hot circles
        DiscussCircles()

        // Hot discussions
        DiscussHotTopics()
    }
}

@Composable
private fun DiscussCircles() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "热门圈子",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mockCircles) { circle ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SleepPalette.Card,
                    border = BorderStroke(1.dp, SleepPalette.Border)
                ) {
                    Text(
                        circle.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = SleepPalette.Ink,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscussHotTopics() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "热门讨论",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        mockDiscussions.forEach { post ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SleepPalette.Card,
                border = BorderStroke(1.dp, SleepPalette.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        post.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SleepPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        post.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            post.circle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SleepPalette.Primary.copy(alpha = 0.65f)
                        )
                        Text(
                            post.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = SleepPalette.Subtle
                        )
                        Text(
                            "${post.replies} 评论",
                            style = MaterialTheme.typography.labelSmall,
                            color = SleepPalette.Subtle
                        )
                    }
                }
            }
        }
    }
}

// ── 3. Goods Channel ──

@Composable
private fun GoodsChannel() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Scenario cards
        Text(
            "按场景找好物",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SleepPalette.Muted
        )
        mockGoodScenarios.forEach { scenario ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SleepPalette.Card,
                border = BorderStroke(1.dp, SleepPalette.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        scenario.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SleepPalette.Ink
                    )
                    Text(
                        scenario.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.Muted
                    )
                    Text(
                        "推荐：${scenario.items}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SleepPalette.Primary.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { /* TODO */ },
                            shape = RoundedCornerShape(14.dp),
                            color = SleepPalette.Primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "查看推荐",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = SleepPalette.Primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // User experiences
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SleepPalette.Card,
            border = BorderStroke(1.dp, SleepPalette.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "用户真实体验",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SleepPalette.Muted
                )
                Text(
                    "小鹿：用了遮光眼罩一周，入睡快了 10 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepPalette.Muted
                )
                Text(
                    "Blue：白噪音对半夜醒来特别有用",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepPalette.Muted
                )
            }
        }
    }
}

// ── 4. Course Channel ──

@Composable
private fun CourseChannel() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SleepPalette.Primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, SleepPalette.Primary.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = SleepPalette.Primary.copy(alpha = 0.70f),
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "推荐课程",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SleepPalette.Muted
                    )
                    Text(
                        "每天学一点，慢慢改善睡眠。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.Subtle
                    )
                }
            }
        }

        // Course cards
        mockCourses.forEach { course ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SleepPalette.Card,
                border = BorderStroke(1.dp, SleepPalette.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        course.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = SleepPalette.Ink
                    )
                    Text(
                        course.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepPalette.Muted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { /* TODO */ },
                            shape = RoundedCornerShape(14.dp),
                            color = SleepPalette.Primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                course.actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = SleepPalette.Primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    SleepAgentPrototypeTheme {
        DiscoverScreenContent()
    }
}
