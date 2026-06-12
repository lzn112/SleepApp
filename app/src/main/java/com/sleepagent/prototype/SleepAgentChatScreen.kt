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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ── Data ──

private enum class ChatRole { User, Agent }

private data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String
)

// ── Main entry ──

@Composable
fun SleepAgentChatScreen(
    onBack: () -> Unit,
    initialPrompt: String? = null
) {
    val messages = remember { mutableStateListOf<ChatMessageUi>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Add intro message from agent
    LaunchedEffect(Unit) {
        messages.add(
            ChatMessageUi(
                role = ChatRole.Agent,
                text = "我会根据你的睡眠记录，帮你分析原因，并安排今晚计划。"
            )
        )
    }

    // Handle initial prompt
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            messages.add(ChatMessageUi(role = ChatRole.User, text = initialPrompt))
            messages.add(
                ChatMessageUi(
                    role = ChatRole.Agent,
                    text = generateMockAgentReply(initialPrompt)
                )
            )
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
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
        // ── Top Bar ──
        ChatTopBar(onBack = onBack)

        // ── Messages ──
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Agent intro card (only show when no conversation yet)
            if (messages.size <= 1) {
                item { AgentIntroCard() }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    QuickQuestionSection(
                        onQuestionClick = { question ->
                            messages.add(ChatMessageUi(role = ChatRole.User, text = question))
                            scope.launch {
                                delay(400)
                                messages.add(
                                    ChatMessageUi(
                                        role = ChatRole.Agent,
                                        text = generateMockAgentReply(question)
                                    )
                                )
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            items(messages, key = { it.id }) { message ->
                when (message.role) {
                    ChatRole.User -> UserMessageBubble(text = message.text)
                    ChatRole.Agent -> AgentMessageBubble(text = message.text)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // ── Input Bar ──
        ChatInputBar(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            onSend = {
                val text = inputText.trim()
                if (text.isBlank()) return@ChatInputBar
                messages.add(ChatMessageUi(role = ChatRole.User, text = text))
                inputText = ""
                scope.launch {
                    delay(400)
                    messages.add(
                        ChatMessageUi(
                            role = ChatRole.Agent,
                            text = generateMockAgentReply(text)
                        )
                    )
                }
            }
        )
    }
}

// ── Top Bar ──

@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF6C8CFF).copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF6C8CFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "睡眠管家",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.94f)
                )
            }

            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(alpha = 0.60f),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

// ── Agent Intro Card ──

@Composable
private fun AgentIntroCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF6C8CFF).copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF6C8CFF),
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "AI 睡眠管家",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    "分析睡眠数据，调整睡前计划，陪你慢慢改善。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }
    }
}

// ── Quick Questions ──

@Composable
private fun QuickQuestionSection(
    onQuestionClick: (String) -> Unit
) {
    val questions = listOf(
        "昨晚为什么睡得浅？",
        "今晚怎么更快入睡？",
        "帮我调整睡前计划",
        "最近有没有变好？"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "试试这些",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.35f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            questions.forEach { question ->
                Surface(
                    onClick = { onQuestionClick(question) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF6C8CFF).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        question,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6C8CFF).copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

// ── Chat Bubbles ──

@Composable
private fun AgentMessageBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = Color(0xFF6C8CFF).copy(alpha = 0.20f),
            border = BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

// ── Input Bar ──

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = Color(0xFF0B1020).copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "问问睡眠管家...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.30f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.12f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = Color.White.copy(alpha = 0.88f),
                    unfocusedTextColor = Color.White.copy(alpha = 0.80f),
                    cursorColor = Color(0xFF6C8CFF),
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                )
            )

            Surface(
                onClick = onSend,
                shape = CircleShape,
                color = if (inputText.isNotBlank()) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.36f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Mock Reply Logic ──

private fun generateMockAgentReply(question: String): String {
    return when {
        question.contains("浅") -> "昨晚后半夜浅睡偏多，可能和睡前刺激、作息波动或夜间短醒有关。偶尔一晚睡得浅不用太担心，今晚建议提前 20 分钟进入放松流程。"
        question.contains("入睡") -> "今晚可以先做 8 分钟呼吸放松，并在 23:00 后减少手机使用。目标不是强迫自己马上睡着，而是让身体慢慢进入睡眠状态。"
        question.contains("计划") -> "我建议今晚保持当前睡前计划：23:00 放下手机，23:10 呼吸放松，23:30 前关灯。先稳定执行，不要增加太多任务，稳定比完美更重要。"
        question.contains("变好") -> "最近你的入睡时间有下降趋势，说明睡前放松可能在起作用。继续保持 3 到 5 晚，让身体适应这个节奏，我会持续帮你关注变化。"
        question.contains("困") || question.contains("累") -> "昨晚睡得还可以，但后半夜有几段浅睡增多，可能影响了恢复质量。今晚不用刻意早睡，继续保持睡前放松流程就好。偶尔的疲惫是正常的，不用太焦虑。"
        question.contains("醒") -> "半夜醒来其实很常见，不用强迫自己立刻睡着。可以试试：不要看手机，用腹式呼吸慢慢放松，如果 15 分钟还睡不着，起来坐一会儿再躺下。重要的是别给半夜醒来贴上焦虑的标签。"
        question.contains("报告") -> "我看了你最近的睡眠数据，整体趋势在慢慢改善。入睡时间有下降，但后半夜的浅睡还比较零散。今晚的重点是稳定睡前放松流程，其他不用着急。"
        else -> "我会结合你的睡眠记录和改善目标来帮你分析。你可以问问我有关入睡、睡眠质量、睡前计划或者长期改善的问题。"
    }
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun SleepAgentChatScreenPreview() {
    SleepAgentPrototypeTheme {
        SleepAgentChatScreen(onBack = {})
    }
}
