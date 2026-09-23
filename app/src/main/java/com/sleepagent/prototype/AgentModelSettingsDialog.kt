package com.sleepagent.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sleepagent.prototype.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentModelSettingsDialog(
    initial: AgentModelConfig?,
    store: AgentModelConfigStore,
    onSaved: (AgentModelConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    var endpoint by remember { mutableStateOf(initial?.endpoint ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    // Never put the API key in saved instance state.
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun config() = AgentModelConfig(endpoint.trim(), model.trim(), apiKey.trim()).also { it.validate() }
    fun perform(test: Boolean) {
        if (busy) return
        val candidate = try { config() } catch (e: IllegalArgumentException) {
            status = e.message
            return
        }
        busy = true
        status = if (test) "正在测试连接…" else "正在保存…"
        scope.launch {
            try {
                if (test) {
                    RemoteSleepAgentModel(candidate).respond(AgentRequest(
                        listOf(AgentMessage(AgentRole.USER, "请回复：连接成功")),
                        AgentSleepContext(emptyList(), 0, "连接测试", "23:30", "07:30")
                    ))
                    status = "连接成功，模型已返回文本。点击保存后生效。"
                } else {
                    withContext(Dispatchers.IO) { store.save(candidate) }
                    onSaved(candidate)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = (e as? AgentServiceException)?.message ?: "操作失败，请检查配置或稍后重试。"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("配置模型") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("支持 Chat Completions 兼容接口和自建后端。保存后，提问会将近期睡眠摘要、作息和对话发送到你配置的服务。",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(endpoint, { endpoint = it; status = null },
                    label = { Text("完整接口地址（HTTPS）") },
                    placeholder = { Text("https://你的服务/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                OutlinedTextField(model, { model = it; status = null },
                    label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, enabled = !busy)
                OutlinedTextField(apiKey, { apiKey = it; status = null },
                    label = { Text("API Key / 访问令牌") },
                    supportingText = { Text("无需认证的自建后端可留空；密钥在本机加密保存。") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                TextButton(onClick = { perform(test = true) }, enabled = !busy) { Text("测试连接") }
                Text("测试仅发送测试消息，不读取睡眠记录；可能产生少量模型调用费用。",
                    style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (initial != null) {
                    TextButton(onClick = {
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { store.clear() }
                                onSaved(null)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                status = "清除失败，请重试。"
                            } finally { busy = false }
                        }
                    }, enabled = !busy) { Text("清除配置") }
                }
            }
        },
        confirmButton = { Button(onClick = { perform(test = false) }, enabled = !busy) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}
