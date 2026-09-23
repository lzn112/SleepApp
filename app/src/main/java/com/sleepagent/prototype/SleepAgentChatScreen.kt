package com.sleepagent.prototype

import com.sleepagent.prototype.ui.theme.SleepPalette

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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme
import com.sleepagent.prototype.agent.AgentMessage
import com.sleepagent.prototype.agent.AgentRole
import com.sleepagent.prototype.agent.AndroidSleepAgentContextSource
import com.sleepagent.prototype.agent.AgentServiceException
import com.sleepagent.prototype.agent.UnconfiguredSleepAgentModel
import com.sleepagent.prototype.agent.SleepAgent
import com.sleepagent.prototype.agent.SleepAgentModel
import kotlinx.coroutines.CancellationException
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
    initialPrompt: String? = null,
    model: SleepAgentModel? = null,
    onConfigureModel: () -> Unit = {}
) {
    val messages = remember { mutableStateListOf<ChatMessageUi>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val agent = remember(context, model) {
        SleepAgent(AndroidSleepAgentContextSource(context), model ?: UnconfiguredSleepAgentModel())
    }
    var isLoading by remember { mutableStateOf(false) }
    var failedPrompt by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf("") }

    fun sendMessage(text: String, retry: Boolean = false) {
        if (isLoading || text.isBlank()) return
        if (!retry) messages.add(ChatMessageUi(role = ChatRole.User, text = text))
        failedPrompt = null
        isLoading = true
        scope.launch {
            try {
                // The introduction is presentation copy, not model conversation history.
                val history = messages.drop(1).map {
                    AgentMessage(if (it.role == ChatRole.User) AgentRole.USER else AgentRole.ASSISTANT, it.text)
                }
                val reply = agent.respond(history)
                messages.add(ChatMessageUi(role = ChatRole.Agent, text = reply.displayText()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errorText = (e as? AgentServiceException)?.message ?: "暂时无法完成请求，请重试。"
                failedPrompt = text
            } finally {
                isLoading = false
            }
        }
    }

    // Add intro message from agent
    LaunchedEffect(Unit) {
        messages.add(
            ChatMessageUi(
                role = ChatRole.Agent,
                text = "我可以读取近期睡眠摘要，帮你整理今晚计划。${agent.model.statusLabel}。"
            )
        )
        if (!initialPrompt.isNullOrBlank()) {
            sendMessage(initialPrompt)
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size, isLoading, failedPrompt) {
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
                        SleepPalette.BackgroundTop,
                        SleepPalette.BackgroundMiddle,
                        SleepPalette.BackgroundBottom
                    )
                )
            )
    ) {
        // ── Top Bar ──
        ChatTopBar(onBack = onBack)
        TextButton(onClick = onConfigureModel, enabled = !isLoading) { Text("配置模型") }
        Text(
            agent.model.statusLabel,
            color = SleepPalette.Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

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
                            sendMessage(question)
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

            if (isLoading) {
                item { AgentMessageBubble(text = "正在读取睡眠记录并整理回复…") }
            }
            failedPrompt?.let { prompt ->
                item {
                    Surface(
                        onClick = { sendMessage(prompt, retry = true) },
                        color = SleepPalette.Card,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("$errorText\n点击重试", color = Color.White,
                            modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // ── Input Bar ──
        ChatInputBar(
            inputText = inputText,
            enabled = !isLoading,
            onInputTextChange = { inputText = it },
            onSend = {
                val text = inputText.trim()
                if (text.isBlank() || isLoading) return@ChatInputBar
                inputText = ""
                sendMessage(text)
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
                        .background(SleepPalette.Primary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = SleepPalette.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "睡眠管家",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SleepPalette.Ink
                )
            }

            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = SleepPalette.Card
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = SleepPalette.Muted,
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
        color = SleepPalette.Card,
        border = BorderStroke(1.dp, SleepPalette.Border),
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
                    .background(SleepPalette.Primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = SleepPalette.Primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "AI 睡眠管家",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SleepPalette.Ink
                )
                Text(
                    "查看实际记录，整理睡前计划草案。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepPalette.Muted
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
        "查看近期睡眠记录",
        "查看入睡用时",
        "生成今晚睡前计划",
        "查看夜间觉醒次数"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "试试这些",
            style = MaterialTheme.typography.labelSmall,
            color = SleepPalette.Subtle
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            questions.forEach { question ->
                Surface(
                    onClick = { onQuestionClick(question) },
                    shape = RoundedCornerShape(16.dp),
                    color = SleepPalette.Primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SleepPalette.Primary.copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        question,
                        style = MaterialTheme.typography.labelSmall,
                        color = SleepPalette.Primary.copy(alpha = 0.72f),
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
            color = SleepPalette.Card,
            border = BorderStroke(1.dp, SleepPalette.Border)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = SleepPalette.Ink,
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
            color = SleepPalette.Primary.copy(alpha = 0.20f),
            border = BorderStroke(1.dp, SleepPalette.Primary.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = SleepPalette.Ink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

// ── Input Bar ──

@Composable
private fun ChatInputBar(
    inputText: String,
    enabled: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = SleepPalette.BackgroundMiddle.copy(alpha = 0.95f),
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
                        color = SleepPalette.Subtle
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SleepPalette.Card,
                    unfocusedBorderColor = SleepPalette.Card,
                    focusedTextColor = SleepPalette.Ink,
                    unfocusedTextColor = SleepPalette.Ink,
                    cursorColor = SleepPalette.Primary,
                    focusedContainerColor = SleepPalette.Card,
                    unfocusedContainerColor = SleepPalette.Card
                )
            )

            Surface(
                onClick = onSend,
                enabled = enabled && inputText.isNotBlank(),
                shape = CircleShape,
                color = if (enabled && inputText.isNotBlank()) SleepPalette.Primary else SleepPalette.Card,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (enabled && inputText.isNotBlank()) Color.White else SleepPalette.Subtle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
