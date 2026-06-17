package com.sleepagent.prototype.report

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepagent.prototype.data.SleepEpochRecord
import com.sleepagent.prototype.data.SleepStage
import com.sleepagent.prototype.data.SoundInterventionEventEntity
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Data models ──

data class SleepStageSegment(
    val stage: SleepStage,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val startEpochIndex: Int,
    val endEpochIndex: Int
)

data class StageSummary(
    val stage: SleepStage,
    val label: String,
    val durationMs: Long,
    val percent: Float
)

enum class SleepStageDisplayMode {
    SIMPLE,
    DETAILED
}

enum class ChartRenderStage {
    W, REM, N1, N2, N3, NO_DATA
}

data class SleepStageColors(
    val wColor: Color = Color(0xFFFF9F43),       // 珊瑚橙红
    val remColor: Color = Color(0xFF56CCF2),     // 明亮青蓝
    val n1Color: Color = Color(0xFFA0D2F0),      // 淡天蓝
    val n2Color: Color = Color(0xFF6C8CFF),      // 中蓝紫
    val n3Color: Color = Color(0xFF2D5BFF),      // 深靛蓝
    val noDataColor: Color = Color(0xFF3A3A4A),  // 灰色
    val wColorDark: Color = Color(0xFFFF7B3D),
    val remColorDark: Color = Color(0xFF48B8D0),
    val n1ColorDark: Color = Color(0xFF7CB8E0),
    val n2ColorDark: Color = Color(0xFF5B7BEE),
    val n3ColorDark: Color = Color(0xFF1E3FCC),
    val noDataColorDark: Color = Color(0xFF2A2A3A),
    val lightStageColor: Color = Color(0xFF8499E0),
    val deepStageColor: Color = Color(0xFF2D5BFF),
    val lightStageColorDark: Color = Color(0xFF6B80D0),
    val deepStageColorDark: Color = Color(0xFF1E3FCC),
) {
    fun colorFor(render: ChartRenderStage, isDark: Boolean = true): Color = when (render) {
        ChartRenderStage.W -> if (isDark) wColorDark else wColor
        ChartRenderStage.REM -> if (isDark) remColorDark else remColor
        ChartRenderStage.N1 -> if (isDark) n1ColorDark else n1Color
        ChartRenderStage.N2 -> if (isDark) n2ColorDark else n2Color
        ChartRenderStage.N3 -> if (isDark) n3ColorDark else n3Color
        ChartRenderStage.NO_DATA -> if (isDark) noDataColorDark else noDataColor
    }

    fun colorForSimple(label: String, isDark: Boolean = true): Color = when (label) {
        "清醒" -> if (isDark) wColorDark else wColor
        "REM" -> if (isDark) remColorDark else remColor
        "浅睡" -> if (isDark) lightStageColorDark else lightStageColor
        "深睡" -> if (isDark) deepStageColorDark else deepStageColor
        else -> if (isDark) noDataColorDark else noDataColor
    }
}

// ── Segment builder ──

fun buildSleepSegments(epochs: List<SleepEpochRecord>): List<SleepStageSegment> {
    if (epochs.isEmpty()) return emptyList()

    // 1. Sort by epoch start time ascending
    val sorted = epochs.sortedBy { it.startAtEpochMs }

    // 2. Merge consecutive same-stage epochs
    val segments = mutableListOf<SleepStageSegment>()
    var currentStage = sorted.first().stage
    var segmentStart = sorted.first().startAtEpochMs
    var segmentEnd = sorted.first().endAtEpochMs
    var startIdx = sorted.first().epochIndex
    var endIdx = sorted.first().epochIndex

    for (i in 1 until sorted.size) {
        val epoch = sorted[i]
        val gap = epoch.startAtEpochMs - segmentEnd

        // If same stage and no gap (>60s), extend segment
        if (epoch.stage == currentStage && gap <= 60_000L) {
            segmentEnd = epoch.endAtEpochMs
            endIdx = epoch.epochIndex
        } else {
            // Close previous segment
            segments.add(SleepStageSegment(
                stage = currentStage,
                startTimeMillis = segmentStart,
                endTimeMillis = segmentEnd,
                startEpochIndex = startIdx,
                endEpochIndex = endIdx
            ))

            // Insert NO_DATA gap if there's a significant time jump
            if (gap > 60_000L && gap < 3_600_000L) {
                segments.add(SleepStageSegment(
                    stage = SleepStage.UNKNOWN,
                    startTimeMillis = segmentEnd,
                    endTimeMillis = epoch.startAtEpochMs,
                    startEpochIndex = -1,
                    endEpochIndex = -1
                ))
            }

            // Start new segment
            currentStage = epoch.stage
            segmentStart = epoch.startAtEpochMs
            segmentEnd = epoch.endAtEpochMs
            startIdx = epoch.epochIndex
            endIdx = epoch.epochIndex
        }
    }

    // Close final segment
    segments.add(SleepStageSegment(
        stage = currentStage,
        startTimeMillis = segmentStart,
        endTimeMillis = segmentEnd,
        startEpochIndex = startIdx,
        endEpochIndex = endIdx
    ))

    return segments
}

fun mapToRenderStage(
    stage: SleepStage,
    displayMode: SleepStageDisplayMode
): ChartRenderStage? = when (stage) {
    SleepStage.AWAKE -> ChartRenderStage.W
    SleepStage.REM -> ChartRenderStage.REM
    SleepStage.DEEP -> ChartRenderStage.N3
    SleepStage.LIGHT -> when (displayMode) {
        SleepStageDisplayMode.SIMPLE -> null // Will be mapped to N1/N2 later; but we treat LIGHT as N2
        SleepStageDisplayMode.DETAILED -> ChartRenderStage.N2
    }
    SleepStage.UNKNOWN -> ChartRenderStage.NO_DATA
}

/** Map LIGHT stage to N1 or N2 based on position heuristic (first half = N1, second half = N2) */
fun mapLightToDetail(
    segment: SleepStageSegment,
    sessionStartMs: Long,
    sessionDurationMs: Long
): ChartRenderStage {
    val relativePos = (segment.startTimeMillis - sessionStartMs).toFloat() / sessionDurationMs.coerceAtLeast(1L).toFloat()
    return if (relativePos < 0.5f) ChartRenderStage.N1 else ChartRenderStage.N2
}

fun mapToSummaryStage(
    stage: SleepStage,
    displayMode: SleepStageDisplayMode
): String? = when (stage) {
    SleepStage.AWAKE -> "清醒"
    SleepStage.REM -> "REM"
    SleepStage.DEEP -> "深睡"
    SleepStage.LIGHT -> when (displayMode) {
        SleepStageDisplayMode.DETAILED -> null // LIGHT is split; handled externally
        SleepStageDisplayMode.SIMPLE -> "浅睡"
    }
    SleepStage.UNKNOWN -> null
}

fun buildStageSummaries(
    epochs: List<SleepEpochRecord>,
    displayMode: SleepStageDisplayMode
): List<StageSummary> {
    val epochMs = 30_000L
    val awakeEpochs = epochs.filter { it.stage == SleepStage.AWAKE }
    val remEpochs = epochs.filter { it.stage == SleepStage.REM }
    val deepEpochs = epochs.filter { it.stage == SleepStage.DEEP }
    val lightEpochs = epochs.filter { it.stage == SleepStage.LIGHT }

    val inBedMs = if (epochs.isNotEmpty()) {
        epochs.last().endAtEpochMs - epochs.first().startAtEpochMs
    } else 0L

    if (displayMode == SleepStageDisplayMode.DETAILED) {
        val sessionStart = epochs.firstOrNull()?.startAtEpochMs ?: 0L
        val duration = inBedMs
        val n1Epochs = lightEpochs.filter {
            val relativePos = (it.startAtEpochMs - sessionStart).toFloat() / duration.coerceAtLeast(1L).toFloat()
            relativePos < 0.5f
        }
        val n2Epochs = lightEpochs.filter {
            val relativePos = (it.startAtEpochMs - sessionStart).toFloat() / duration.coerceAtLeast(1L).toFloat()
            relativePos >= 0.5f
        }
        return listOf(
            StageSummary(SleepStage.AWAKE, "清醒", awakeEpochs.size * epochMs,
                if (inBedMs > 0) (awakeEpochs.size * epochMs).toFloat() / inBedMs else 0f),
            StageSummary(SleepStage.REM, "REM", remEpochs.size * epochMs,
                if (inBedMs > 0) (remEpochs.size * epochMs).toFloat() / inBedMs else 0f),
            StageSummary(SleepStage.LIGHT, "N1", n1Epochs.size * epochMs,
                if (inBedMs > 0) (n1Epochs.size * epochMs).toFloat() / inBedMs else 0f),
            StageSummary(SleepStage.LIGHT, "N2", n2Epochs.size * epochMs,
                if (inBedMs > 0) (n2Epochs.size * epochMs).toFloat() / inBedMs else 0f),
            StageSummary(SleepStage.DEEP, "深睡", deepEpochs.size * epochMs,
                if (inBedMs > 0) (deepEpochs.size * epochMs).toFloat() / inBedMs else 0f),
        )
    } else {
        val totalSleepMs = remEpochs.size * epochMs + deepEpochs.size * epochMs + lightEpochs.size * epochMs
        return listOf(
            StageSummary(SleepStage.AWAKE, "清醒", awakeEpochs.size * epochMs,
                if (inBedMs > 0) (awakeEpochs.size * epochMs).toFloat() / inBedMs else 0f),
            StageSummary(SleepStage.REM, "REM", remEpochs.size * epochMs,
                if (totalSleepMs > 0) (remEpochs.size * epochMs).toFloat() / totalSleepMs else 0f),
            StageSummary(SleepStage.LIGHT, "浅睡", lightEpochs.size * epochMs,
                if (totalSleepMs > 0) (lightEpochs.size * epochMs).toFloat() / totalSleepMs else 0f),
            StageSummary(SleepStage.DEEP, "深睡", deepEpochs.size * epochMs,
                if (totalSleepMs > 0) (deepEpochs.size * epochMs).toFloat() / totalSleepMs else 0f),
        )
    }
}

fun formatDurationShort(ms: Long): String {
    val totalMinutes = (ms / 60_000L).toInt()
    if (totalMinutes < 60) return "${totalMinutes}分"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) "${hours}时" else "${hours}时${minutes}分"
}

// ── Chart drawing ──

@SuppressLint("ReturnFromAwakeLabel")
@Composable
fun SleepStageChartCard(
    epochs: List<SleepEpochRecord>,
    sessionStartMs: Long,
    sessionEndMs: Long,
    totalSleepMs: Long,
    efficiencyPercent: Int,
    interventionEvents: List<SoundInterventionEventEntity> = emptyList(),
    showInterventions: Boolean = false,
    onShowInterventionsToggle: (Boolean) -> Unit = {},
    onFullScreenClick: () -> Unit = {}
) {
    var displayMode by remember { mutableStateOf(SleepStageDisplayMode.DETAILED) }
    var selectedStage by remember { mutableStateOf<SleepStage?>(null) }
    var highlightedLabel by remember { mutableStateOf<String?>(null) }

    val segments by remember(epochs, displayMode) {
        derivedStateOf { buildSleepSegments(epochs) }
    }
    val sessionDurationMs = sessionEndMs - sessionStartMs
    val summaries by remember(epochs, displayMode) {
        derivedStateOf { buildStageSummaries(epochs, displayMode) }
    }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "睡眠阶段",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.90f))
                )
                val startFmt = formatHhmm(sessionStartMs)
                val endFmt = formatHhmm(sessionEndMs)
                Text(
                    "$startFmt—$endFmt  ·  总睡眠 ${formatDurationShort(totalSleepMs)}  ·  效率 $efficiencyPercent%",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                )
            }
            Surface(
                onClick = onFullScreenClick,
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    "全屏",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.50f)),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ── Mode toggle ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.05f),
            ) {
                Row {
                    ModeToggleChip("详细", displayMode == SleepStageDisplayMode.DETAILED) {
                        displayMode = SleepStageDisplayMode.DETAILED
                    }
                    ModeToggleChip("简洁", displayMode == SleepStageDisplayMode.SIMPLE) {
                        displayMode = SleepStageDisplayMode.SIMPLE
                    }
                }
            }
        }

        // ── Chart ──
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.04f),
            modifier = Modifier.fillMaxWidth()
        ) {
            SleepStageCanvasChart(
                segments = segments,
                displayMode = displayMode,
                sessionStartMs = sessionStartMs,
                sessionEndMs = sessionEndMs,
                selectedStage = selectedStage,
                highlightedLabel = highlightedLabel,
                showInterventions = showInterventions,
                interventionEvents = if (showInterventions) interventionEvents else emptyList(),
                onSegmentTap = { seg -> selectedStage = seg.stage },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }

        // ── Stage summaries ──
        StageSummaryRow(
            summaries = summaries,
            displayMode = displayMode,
            highlightedLabel = highlightedLabel,
            onLabelClick = { label ->
                highlightedLabel = if (highlightedLabel == label) null else label
            }
        )

        // ── Intervention toggle ──
        if (interventionEvents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowInterventionsToggle(!showInterventions) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    if (showInterventions) "▪ 隐藏干预标记" else "▪ 显示干预标记",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun ModeToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.20f) else Color.Transparent
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.40f)
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun SleepStageCanvasChart(
    segments: List<SleepStageSegment>,
    displayMode: SleepStageDisplayMode,
    sessionStartMs: Long,
    sessionEndMs: Long,
    selectedStage: SleepStage?,
    highlightedLabel: String?,
    showInterventions: Boolean,
    interventionEvents: List<SoundInterventionEventEntity>,
    onSegmentTap: (SleepStageSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = remember { SleepStageColors() }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val durationMs = (sessionEndMs - sessionStartMs).coerceAtLeast(1L)
    val densityDpToPx = remember(density) { density.density } // captured for pointerInput lambdas

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var tooltipInfo by remember { mutableStateOf<SegmentTooltip?>(null) }
    var isLongPressActive by remember { mutableStateOf(false) }

    // Y-axis labels and positions
    val yLabels = if (displayMode == SleepStageDisplayMode.DETAILED) {
        listOf("清醒" to ChartRenderStage.W, "REM" to ChartRenderStage.REM,
            "N1" to ChartRenderStage.N1, "N2" to ChartRenderStage.N2,
            "N3" to ChartRenderStage.N3)
    } else {
        listOf("清醒" to ChartRenderStage.W, "REM" to ChartRenderStage.REM,
            "浅睡" to ChartRenderStage.N2, "深睡" to ChartRenderStage.N3)
    }

    val leftPaddingPx = with(density) { 40.dp.toPx() }
    val rightPaddingPx = with(density) { 16.dp.toPx() }
    val topPaddingPx = with(density) { 8.dp.toPx() }
    val bottomPaddingPx = with(density) { 24.dp.toPx() }

    // Chart content calculation
    val chartContentWidth = ((durationMs.toFloat() / 1000f) / 40f).coerceAtLeast(1f)
        .times(densityDpToPx) * scale

    val minScale = 1f
    val maxScale = (durationMs.toFloat() / (15 * 60 * 1000f)).coerceAtMost(30f)
    val clampedOffsetX = when {
        chartContentWidth <= 0f -> 0f
        else -> offsetX.coerceIn(-chartContentWidth * 0.1f, chartContentWidth * 0.1f)
    }

    val yPositions = remember { mutableListOf<Float>() }
    val segmentRects = remember { mutableListOf<SegmentRect>() }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(segments) {
                    detectTapGestures(
                        onTap = { offset ->
                            // Find tapped segment
                            val chartW = size.width - leftPaddingPx - rightPaddingPx
                            val bandH = (size.height - topPaddingPx - bottomPaddingPx) / yLabels.size.coerceAtLeast(1)
                            val tapTimeMs = sessionStartMs + ((offset.x - leftPaddingPx - clampedOffsetX) / chartContentWidth * durationMs).toLong()
                            val segment = segments.find { tapTimeMs in it.startTimeMillis..it.endTimeMillis }
                            if (segment != null) {
                                onSegmentTap(segment)
                            }
                        },
                        onLongPress = { offset ->
                            isLongPressActive = true
                            val tapTimeMs = sessionStartMs + ((offset.x - leftPaddingPx - clampedOffsetX) / chartContentWidth * durationMs).toLong()
                            val segment = segments.find { tapTimeMs in it.startTimeMillis..it.endTimeMillis }
                            if (segment != null) {
                                tooltipInfo = SegmentTooltip(
                                    segment = segment,
                                    stageLabel = segment.stage.name,
                                    startTime = formatHhmm(segment.startTimeMillis),
                                    endTime = formatHhmm(segment.endTimeMillis),
                                    duration = formatDurationShort(segment.endTimeMillis - segment.startTimeMillis)
                                )
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = newScale
                        // Recalculate content width for clamping
                        val newChartW = ((durationMs.toFloat() / 1000f) / 40f)
                            .coerceAtLeast(1f)
                            .times(densityDpToPx) * newScale
                        offsetX = (clampedOffsetX + pan.x).coerceIn(
                            -newChartW * 0.1f, newChartW * 0.1f
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { tooltipInfo = null; isLongPressActive = false }
                    ) { _, dragAmount ->
                        val newChartW = ((durationMs.toFloat() / 1000f) / 40f)
                            .coerceAtLeast(1f)
                            .times(densityDpToPx) * scale
                        offsetX = (clampedOffsetX + dragAmount).coerceIn(
                            -newChartW * 0.1f, newChartW * 0.1f
                        )
                    }
                }
        ) {
            yPositions.clear()
            segmentRects.clear()

            val chartW = size.width - leftPaddingPx - rightPaddingPx
            val chartH = size.height - topPaddingPx - bottomPaddingPx
            val bandH = chartH / yLabels.size.coerceAtLeast(1)
            val segmentH = bandH * 0.70f
            val bandCenterOffsets = yLabels.indices.map { topPaddingPx + it * bandH + bandH / 2f }

            // Draw horizontal guide lines (very subtle)
            for (i in 0 until yLabels.size) {
                val y = topPaddingPx + i * bandH + bandH / 2f
                drawLine(
                    Color.White.copy(alpha = 0.06f),
                    Offset(leftPaddingPx - 4f, y),
                    Offset(size.width - rightPaddingPx + 4f, y),
                    strokeWidth = 0.5f
                )
            }

            // Draw Y-axis labels
            for ((idx, pair) in yLabels.withIndex()) {
                val y = bandCenterOffsets[idx]
                val textResult = textMeasurer.measure(
                    pair.first,
                    style = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
                )
                drawText(
                    textResult,
                    topLeft = Offset(leftPaddingPx - textResult.size.width - 8f,
                        y - textResult.size.height / 2f)
                )
            }

            // Draw segments
            val blockRadius = with(density) { 5.dp.toPx() }
            for (seg in segments) {
                val renderStage = mapToRenderStage(seg.stage, displayMode)
                if (renderStage == null) continue

                // For DETAILED mode, split LIGHT into N1/N2
                val finalRenderStages = if (displayMode == SleepStageDisplayMode.DETAILED &&
                    renderStage == ChartRenderStage.N2) {
                    listOf(mapLightToDetail(seg, sessionStartMs, durationMs))
                } else {
                    listOf(renderStage)
                }

                for (rstage in finalRenderStages) {
                    val yIdx = yLabels.indexOfFirst { it.second == rstage }
                    if (yIdx < 0) continue
                    val y = bandCenterOffsets[yIdx]

                    val startRatio = ((seg.startTimeMillis - sessionStartMs).toFloat() / durationMs)
                    val endRatio = ((seg.endTimeMillis - sessionStartMs).toFloat() / durationMs)
                    val xStart = leftPaddingPx + clampedOffsetX + startRatio * chartContentWidth
                    val xEnd = leftPaddingPx + clampedOffsetX + endRatio * chartContentWidth

                    // Clamp to visible area
                    if (xEnd < leftPaddingPx - 20f || xStart > size.width - rightPaddingPx + 20f) continue

                    val drawX = xStart.coerceAtLeast(leftPaddingPx - 4f)
                    val drawW = (xEnd - drawX).coerceAtLeast(1.5f)
                    val drawY = y - segmentH / 2f

                    val isHighlighted = seg.stage == selectedStage ||
                        (highlightedLabel != null && stageMatchesLabel(seg.stage, rstage, highlightedLabel, displayMode))
                    val alpha = if (highlightedLabel != null && !isHighlighted) 0.2f else 1f
                    val stageColor = colors.colorFor(rstage, true).copy(alpha = alpha)

                    drawRoundRect(
                        color = stageColor,
                        topLeft = Offset(drawX, drawY),
                        size = Size(drawW, segmentH),
                        cornerRadius = CornerRadius(blockRadius.coerceAtMost(drawW / 2f), blockRadius.coerceAtMost(drawW / 2f))
                    )

                    // Thin connector to next segment
                    segmentRects.add(SegmentRect(seg, rstage, Rect(drawX, drawY, drawX + drawW, drawY + segmentH)))

                    // Highlight border if selected
                    if (isHighlighted) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.30f),
                            topLeft = Offset(drawX, drawY),
                            size = Size(drawW, segmentH),
                            cornerRadius = CornerRadius(blockRadius.coerceAtMost(drawW / 2f), blockRadius.coerceAtMost(drawW / 2f)),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // Draw intervention markers
            if (showInterventions && interventionEvents.isNotEmpty()) {
                val markerY = topPaddingPx - 2f
                val markerTrackH = 10f
                for (event in interventionEvents) {
                    val eventTimeMs = event.timestampMillis
                    val ratio = ((eventTimeMs - sessionStartMs).toFloat() / durationMs).coerceIn(0f, 1f)
                    val x = leftPaddingPx + clampedOffsetX + ratio * chartContentWidth
                    if (x < leftPaddingPx || x > size.width - rightPaddingPx) continue

                    val markerColor = when {
                        event.eventType.contains("ALPHA") -> Color(0xFF8AB4F8)
                        event.eventType.contains("N3") -> Color(0xFF7B8CDE)
                        event.eventType.contains("WAKE") || event.eventType.contains("ALARM") -> Color(0xFFFFD166)
                        else -> Color(0xFFAAAAAA)
                    }
                    drawCircle(markerColor.copy(alpha = 0.7f), 3f, Offset(x, markerY + markerTrackH / 2f))
                }
            }

            // Draw X-axis time ticks
            val tickCount = 5
            for (i in 0 until tickCount) {
                val ratio = i.toFloat() / (tickCount - 1).coerceAtLeast(1)
                val tickMs = sessionStartMs + (ratio * durationMs).toLong()
                val x = leftPaddingPx + clampedOffsetX + ratio * chartContentWidth
                if (x < leftPaddingPx - 10f || x > size.width - rightPaddingPx + 10f) continue

                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(x, size.height - bottomPaddingPx),
                    Offset(x, size.height - bottomPaddingPx + 6f),
                    strokeWidth = 1f
                )
                val tickText = textMeasurer.measure(
                    formatHhmm(tickMs),
                    style = TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f))
                )
                drawText(
                    tickText,
                    topLeft = Offset(x - tickText.size.width / 2f, size.height - bottomPaddingPx + 8f)
                )
            }
        }

        // Tooltip overlay
        tooltipInfo?.let { tip ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2A2A3E),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${tip.startTime}—${tip.endTime}", style = TextStyle(fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)))
                    Text(tip.stageLabel, style = TextStyle(fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White))
                    Text("持续 ${tip.duration}", style = TextStyle(fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

private data class SegmentRect(
    val segment: SleepStageSegment,
    val renderStage: ChartRenderStage,
    val rect: Rect
)

private data class SegmentTooltip(
    val segment: SleepStageSegment,
    val stageLabel: String,
    val startTime: String,
    val endTime: String,
    val duration: String
)

private fun stageMatchesLabel(
    stage: SleepStage,
    renderStage: ChartRenderStage,
    label: String,
    mode: SleepStageDisplayMode
): Boolean = when {
    label == "清醒" -> stage == SleepStage.AWAKE
    label == "REM" -> stage == SleepStage.REM
    label == "深睡" || label == "N3" -> stage == SleepStage.DEEP
    label == "浅睡" && mode == SleepStageDisplayMode.SIMPLE -> stage == SleepStage.LIGHT
    label == "N1" && mode == SleepStageDisplayMode.DETAILED -> stage == SleepStage.LIGHT
    label == "N2" && mode == SleepStageDisplayMode.DETAILED -> stage == SleepStage.LIGHT
    else -> false
}

@Composable
fun StageSummaryRow(
    summaries: List<StageSummary>,
    displayMode: SleepStageDisplayMode,
    highlightedLabel: String?,
    onLabelClick: (String) -> Unit
) {
    val colors = remember { SleepStageColors() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        summaries.forEach { summary ->
            val label = getSummaryLabel(summary, displayMode)
            val isHighlighted = highlightedLabel == label
            val stageColor = if (displayMode == SleepStageDisplayMode.DETAILED) {
                val renderStage = summaryStageToRender(summary.stage, label, displayMode)
                colors.colorFor(renderStage, true)
            } else {
                colors.colorForSimple(label, true)
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isHighlighted) stageColor.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onLabelClick(label) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(stageColor.copy(alpha = if (isHighlighted) 1f else 0.7f), RoundedCornerShape(2.dp))
                    )
                    Text(label, style = TextStyle(fontSize = 12.sp,
                        fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color.White.copy(alpha = if (isHighlighted) 0.90f else 0.60f)))
                    Text(formatDurationShort(summary.durationMs), style = TextStyle(fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (isHighlighted) 0.80f else 0.45f)))
                }
            }
        }
    }
}

private fun getSummaryLabel(summary: StageSummary, mode: SleepStageDisplayMode): String = when {
    summary.label == "N1" -> "N1"
    summary.label == "N2" -> "N2"
    summary.stage == SleepStage.AWAKE -> "清醒"
    summary.stage == SleepStage.REM -> "REM"
    summary.stage == SleepStage.DEEP -> if (mode == SleepStageDisplayMode.SIMPLE) "深睡" else "N3"
    summary.stage == SleepStage.LIGHT -> if (mode == SleepStageDisplayMode.SIMPLE) "浅睡" else summary.label
    else -> summary.label
}

private fun summaryStageToRender(stage: SleepStage, label: String, mode: SleepStageDisplayMode): ChartRenderStage = when {
    stage == SleepStage.AWAKE -> ChartRenderStage.W
    stage == SleepStage.REM -> ChartRenderStage.REM
    stage == SleepStage.DEEP -> ChartRenderStage.N3
    stage == SleepStage.LIGHT && label == "N1" -> ChartRenderStage.N1
    stage == SleepStage.LIGHT && label == "N2" -> ChartRenderStage.N2
    stage == SleepStage.LIGHT && mode == SleepStageDisplayMode.SIMPLE -> ChartRenderStage.N2
    else -> ChartRenderStage.NO_DATA
}

fun formatHhmm(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val zone = java.time.ZoneId.systemDefault()
    val dt = instant.atZone(zone)
    return "%02d:%02d".format(dt.hour, dt.minute)
}
