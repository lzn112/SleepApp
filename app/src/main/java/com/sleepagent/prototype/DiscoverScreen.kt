package com.sleepagent.prototype

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepagent.prototype.ui.theme.SleepAgentPrototypeTheme

private data class DiscoverPost(
    val author: String,
    val badge: String,
    val content: String,
    val accent: Color,
    val imageStyle: DiscoverImageStyle? = null,
    val likes: Int = 0
)

private enum class DiscoverImageStyle {
    Moon,
    Cloud
}

@Composable
fun DiscoverScreenContent() {
    val posts = listOf(
        DiscoverPost(
            author = "眠海散步的人",
            badge = "树洞驻留",
            content = "夜里终于安静下来，白天没说出口的情绪像潮水一样慢慢回来了。不是想要答案，只是想把那些没有落点的心事，先放在这里一会儿。",
            accent = Color(0xFF74A8FF),
            imageStyle = DiscoverImageStyle.Moon
        ),
        DiscoverPost(
            author = "晚风收集站",
            badge = "睡前随想",
            content = "有时候会怀疑，是不是到了某个年龄就一定要交出一份标准答卷。工作、关系、状态，好像都该按部就班。可真正困住人的，往往是那个总在深夜里追问自己的人。",
            accent = Color(0xFF95B2FF),
            imageStyle = DiscoverImageStyle.Cloud,
            likes = 18
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 98.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DiscoverContentTabs() }
            items(posts.size) { index ->
                DiscoverPostCard(post = posts[index])
            }
        }

        FloatingCreateButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 22.dp)
        )
    }
}

@Composable
private fun DiscoverContentTabs() {
    val tabs = listOf("梦话", "树洞", "课程")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == "树洞"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tab,
                    style = if (selected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                )
                Box(
                    modifier = Modifier
                        .width(if (selected) 48.dp else 0.dp)
                        .height(5.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun DiscoverTopSwitcher() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiscoverSwitchButton(label = "商城", selected = false)
                DiscoverSwitchButton(label = "社区", selected = true)
            }
        }
    }
}

@Composable
private fun DiscoverSwitchButton(label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier.clickable { }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f)
            )
        }
    }
}

@Composable
private fun DiscoverCategoryTabs() {
    val tabs = listOf("梦话", "打盹", "树洞", "睡眠")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == "树洞"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tab,
                    style = if (selected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                )
                Box(
                    modifier = Modifier
                        .width(if (selected) 48.dp else 0.dp)
                        .height(5.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun DiscoverPostCard(post: DiscoverPost) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                DiscoverAvatar(accent = post.accent)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = post.author,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = post.badge,
                        style = MaterialTheme.typography.bodyMedium,
                        color = post.accent.copy(alpha = 0.90f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 34.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
            )

            post.imageStyle?.let {
                DiscoverIllustrationCard(style = it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DiscoverPostAction(icon = Icons.Default.Share, label = "分享")
                DiscoverPostAction(icon = Icons.AutoMirrored.Filled.Chat, label = "评论")
                DiscoverPostAction(
                    icon = Icons.Default.ThumbUpOffAlt,
                    label = if (post.likes > 0) "点赞 ${post.likes}" else "点赞"
                )
            }
        }
    }
}

@Composable
private fun DiscoverAvatar(accent: Color) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = 0.55f))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "夜",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun DiscoverIllustrationCard(style: DiscoverImageStyle) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth(0.56f)
            .height(210.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = when (style) {
                            DiscoverImageStyle.Moon -> listOf(Color(0xFF284563), Color(0xFF192842))
                            DiscoverImageStyle.Cloud -> listOf(Color(0xFF2B365D), Color(0xFF202844))
                        }
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            when (style) {
                DiscoverImageStyle.Moon -> drawMoonIllustration(size)
                DiscoverImageStyle.Cloud -> drawCloudIllustration(size)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoonIllustration(size: Size) {
    drawCircle(
        color = Color(0x335ED5FF),
        radius = size.minDimension * 0.36f,
        center = Offset(size.width * 0.33f, size.height * 0.38f)
    )
    drawCircle(
        color = Color(0xFFF7F4FB),
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.33f, size.height * 0.38f)
    )
    drawCircle(
        color = Color(0xFF24334D),
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.37f, size.height * 0.36f)
    )
    drawRoundRect(
        color = Color(0xFF93C4A9),
        topLeft = Offset(size.width * 0.08f, size.height * 0.56f),
        size = Size(size.width * 0.66f, size.height * 0.28f),
        cornerRadius = CornerRadius(22f, 22f)
    )
    drawCircle(
        color = Color(0xFFF9F1F4),
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.34f, size.height * 0.56f)
    )
    drawCircle(
        color = Color(0xFFF7A5C2),
        radius = size.minDimension * 0.038f,
        center = Offset(size.width * 0.28f, size.height * 0.57f)
    )
    drawCircle(
        color = Color(0xFFF7A5C2),
        radius = size.minDimension * 0.038f,
        center = Offset(size.width * 0.40f, size.height * 0.57f)
    )
    drawRoundRect(
        color = Color(0xFF1E2943),
        topLeft = Offset(size.width * 0.60f, size.height * 0.18f),
        size = Size(size.width * 0.18f, size.height * 0.14f),
        cornerRadius = CornerRadius(12f, 12f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudIllustration(size: Size) {
    drawCircle(
        color = Color(0xFFB5C7FF),
        radius = size.minDimension * 0.14f,
        center = Offset(size.width * 0.28f, size.height * 0.40f)
    )
    drawCircle(
        color = Color(0xFFD7E0FF),
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.40f, size.height * 0.36f)
    )
    drawCircle(
        color = Color(0xFFC7D7FF),
        radius = size.minDimension * 0.10f,
        center = Offset(size.width * 0.52f, size.height * 0.42f)
    )
    drawRoundRect(
        color = Color(0xFFC9D6FF),
        topLeft = Offset(size.width * 0.18f, size.height * 0.44f),
        size = Size(size.width * 0.42f, size.height * 0.16f),
        cornerRadius = CornerRadius(28f, 28f)
    )
    drawRoundRect(
        color = Color(0xFF86A7FF),
        topLeft = Offset(size.width * 0.16f, size.height * 0.66f),
        size = Size(size.width * 0.62f, size.height * 0.08f),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawCircle(
        color = Color(0x55FFFFFF),
        radius = size.minDimension * 0.016f,
        center = Offset(size.width * 0.74f, size.height * 0.22f)
    )
    drawCircle(
        color = Color(0x66FFFFFF),
        radius = size.minDimension * 0.022f,
        center = Offset(size.width * 0.80f, size.height * 0.28f)
    )
}

@Composable
private fun DiscoverPostAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier.clickable { }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
        )
    }
}

@Composable
private fun FloatingCreateButton(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 20.dp,
        modifier = modifier.size(74.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    SleepAgentPrototypeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            DiscoverScreenContent()
        }
    }
}
