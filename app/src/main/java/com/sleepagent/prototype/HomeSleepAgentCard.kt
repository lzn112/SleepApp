package com.sleepagent.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepagent.prototype.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SleepInk = Color(0xFF262342)
private val SleepPurple = Color(0xFF7664D9)
private val SleepMuted = Color(0xFF9390A9)

@Composable
fun HomeSleepAgentCard(
    model: SleepAgentModel? = null,
    onConfigureModel: () -> Unit = {},
    onSleep: () -> Unit = {},
    onReports: () -> Unit = {},
    onCommunity: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onProfile: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val agent = remember(context, model) {
        SleepAgent(AndroidSleepAgentContextSource(context), model ?: UnconfiguredSleepAgentModel())
    }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf<List<AgentMessage>>(emptyList()) }
    var question by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun ask(prompt: String) {
        val text = prompt.trim()
        if (loading || text.isEmpty()) return
        keyboard?.hide()
        question = text
        reply = null
        error = null
        loading = true
        scope.launch {
            try {
                val request = history + AgentMessage(AgentRole.USER, text)
                val answer = agent.respond(request).displayText()
                history = (request + AgentMessage(AgentRole.ASSISTANT, answer)).takeLast(20)
                reply = answer
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = (e as? AgentServiceException)?.message ?: "暂时无法完成请求，请重试。"
            } finally { loading = false }
        }
    }
    LaunchedEffect(question, reply, error) {
        if (question != null) listState.animateScrollToItem(2)
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = SleepPurple, onPrimary = Color.White, onSurface = SleepInk,
        surface = Color(0xFFF8F7FF), onSurfaceVariant = SleepMuted
    )) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0xFFE9E5FF), Color(0xFFF1EDFA), Color(0xFFE9E8FC)
            ))).statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { keyboard?.hide(); onOpenDrawer() }) {
                        Icon(Icons.Default.Menu, "打开侧边栏")
                    }
                    Text("眠伴", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Surface(onClick = onConfigureModel, shape = CircleShape, color = Color.White.copy(alpha = .65f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(17.dp), tint = SleepPurple)
                            Text("智能体", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    IconButton(onClick = onConfigureModel) { Icon(Icons.Default.Tune, "配置模型", tint = SleepInk) }
                }
    
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().heightIn(min = 104.dp), verticalAlignment = Alignment.CenterVertically) {
                            MoonCompanion(Modifier.size(104.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("嗨，见到你真好", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF423578))
                                Text("今晚，也陪你睡个好觉", fontSize = 10.sp, color = SleepMuted)
                                Surface(onClick = { ask("帮我看看最近的睡眠记录") }, enabled = !loading,
                                    color = Color.White.copy(alpha = .62f), shape = CircleShape) {
                                    Text("和我聊聊  ✦", Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        fontSize = 10.sp, color = SleepPurple)
                                }
                            }
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .28f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .9f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(4.dp, 17.dp).background(SleepPurple, CircleShape))
                                    Text("我的睡眠", Modifier.padding(start = 8.dp).weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Surface(onClick = onReports, shape = CircleShape, color = Color.White.copy(alpha = .85f)) {
                                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("睡眠数据", fontSize = 10.sp, color = SleepMuted)
                                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(17.dp), tint = SleepMuted)
                                        }
                                    }
                                }
                                Surface(onClick = { ask("生成今晚睡前计划") }, enabled = !loading,
                                    shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = .86f)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(Modifier.size(42.dp).background(Color(0xFFEFEAFF), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.NightsStay, null, tint = SleepPurple)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("定制今晚的好眠计划", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Text("跟着自己的节奏，慢慢放松", fontSize = 10.sp, color = SleepMuted)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = SleepPurple)
                                    }
                                }
                                SleepQuestion("最近的睡眠怎么样？", !loading) { ask("请根据最近的睡眠记录总结我的睡眠情况") }
                                SleepQuestion("怎样安排今晚的睡前时间？", !loading) { ask("请结合已保存作息生成今晚睡前计划") }
                            }
                        }
                    }
                    item {
                        if (question != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE0DAFA), modifier = Modifier.align(Alignment.End)) {
                                    Text(question.orEmpty(), Modifier.padding(16.dp), fontSize = 10.sp)
                                }
                                Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = .92f)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("眠伴", color = SleepPurple, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        if (loading) {
                                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SleepPurple)
                                            Text("正在查看记录，整理回复…", fontSize = 10.sp, color = SleepMuted)
                                        }
                                        error?.let {
                                            Text(it, fontSize = 10.sp)
                                            Row {
                                                TextButton(onClick = { question?.let { ask(it) } }) { Text("重试") }
                                                TextButton(onClick = onConfigureModel) { Text("配置模型") }
                                            }
                                        }
                                        reply?.let { Text(it, fontSize = 10.sp, lineHeight = 16.sp) }
                                    }
                                }
                            }
                        } else {
                            Text(if (agent.model is UnconfiguredSleepAgentModel) "首次使用，点右上角配置你的模型" else agent.model.statusLabel,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                color = SleepMuted, fontSize = 10.sp)
                        }
                    }
                }
    
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeQuickPill("睡眠摘要", Icons.Default.AutoAwesome, !loading) { ask("查看近期睡眠记录") }
                        HomeQuickPill("报告解读", Icons.Default.Description) { onReports() }
                        HomeQuickPill("开始睡眠", Icons.Default.NightsStay) { onSleep() }
                    }
                    Surface(shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.padding(horizontal = 10.dp).size(23.dp), tint = SleepPurple)
                            BasicTextField(value = input, onValueChange = { input = it },
                                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                                textStyle = TextStyle(color = SleepInk, fontSize = 10.sp),
                                cursorBrush = SolidColor(SleepPurple), maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (!loading && input.isNotBlank()) { ask(input); input = "" }
                                }),
                                decorationBox = { inner ->
                                    Box { if (input.isEmpty()) Text("想聊聊你的睡眠吗？", color = SleepMuted, fontSize = 10.sp); inner() }
                                })
                            IconButton(onClick = { ask(input); input = "" }, enabled = !loading && input.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (!loading && input.isNotBlank()) SleepPurple else Color(0xFFC6C1DB))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun DrawerDestination(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 10.sp) },
        icon = { Icon(icon, null, Modifier.size(22.dp)) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFFE3DCF9),
            selectedIconColor = SleepPurple,
            selectedTextColor = SleepPurple,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = SleepMuted,
            unselectedTextColor = SleepInk
        )
    )
}

@Composable
private fun SleepQuestion(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = .90f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).background(Brush.linearGradient(listOf(Color(0xFFAB91F3), SleepPurple)), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("#", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(text, Modifier.weight(1f), fontSize = 10.sp)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = Color(0xFFC8C4D4))
        }
    }
}

@Composable
private fun HomeQuickPill(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.White.copy(alpha = .65f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = SleepInk)
            Text(text, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MoonCompanion(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawOval(Color(0xFFCEC5F0).copy(alpha = .35f), Offset(s * .13f, s * .82f), Size(s * .7f, s * .09f))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .7f), Color.Transparent)), s * .5f, Offset(s * .48f, s * .5f))
        drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF8DA), Color(0xFFF2D294))), s * .32f, Offset(s * .46f, s * .46f))
        drawCircle(Color(0xFFECE7FC), s * .26f, Offset(s * .62f, s * .32f))
        drawArc(SleepInk, 15f, 150f, false, Offset(s * .24f, s * .48f), Size(s * .09f, s * .06f), style = Stroke(s * .014f))
        drawArc(SleepInk, 15f, 150f, false, Offset(s * .39f, s * .57f), Size(s * .09f, s * .06f), style = Stroke(s * .014f))
        drawCircle(Color(0xFFE9A8AC).copy(alpha = .65f), s * .045f, Offset(s * .23f, s * .60f))
        val star = Offset(s * .79f, s * .64f)
        drawLine(SleepPurple, star - Offset(s * .06f, 0f), star + Offset(s * .06f, 0f), s * .022f)
        drawLine(SleepPurple, star - Offset(0f, s * .06f), star + Offset(0f, s * .06f), s * .022f)
        drawCircle(Color.White, s * .022f, Offset(s * .19f, s * .19f))
        drawCircle(SleepPurple.copy(alpha = .4f), s * .017f, Offset(s * .80f, s * .25f))
    }
}
