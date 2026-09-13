package com.cpepanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.PanelHi
import com.cpepanel.ui.theme.Stroke
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu

// ───────────────────────────── 卡片 ─────────────────────────────

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = Cyan,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Stroke, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun KV(label: String, value: String, valueColor: Color = TextHi, mono: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = value.ifBlank { "—" },
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f)
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String = "",
    color: Color = Cyan,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanelHi)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextLo, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value.ifBlank { "—" },
                color = color,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = TextMu, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ───────────────────────────── 图形 ─────────────────────────────

@Composable
fun RadialGauge(
    ratio: Float,
    color: Color,
    big: String,
    bigUnit: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.072f
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = Stroke, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = color, startAngle = 135f, sweepAngle = 270f * ratio.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    big, color = TextHi, fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                if (bigUnit.isNotEmpty()) {
                    Text(
                        bigUnit, color = TextMu, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 7.dp, start = 2.dp)
                    )
                }
            }
            Text(caption, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 信号曲线。
 *
 * 关于 Y 轴：早期实现直接用 (max-min) 作跨度，当样本全部相等时（信号静止不动，
 * 例如 RSRP 恒为 -71 dBm）跨度退化成兜底值 1，归一化结果恒为 0，曲线被死死按在
 * 图表底部边缘。
 *
 * 现在改为「围绕中值对称展开」，并由 [minSpan] 兜住最小跨度：
 *   - 波动正常时按 raw × 1.36 展开，上下各留约 13% 余量；
 *   - 波动很小时至少撑开到 minSpan（RSRP 传 10、SINR 传 6），
 *     这样恒定值落在正中，且曲线高度仍能反映信号的绝对强弱。
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    smooth: Boolean = true,
    minSpan: Float = 0f
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0..3) {
            val gy = h * i / 3f
            drawLine(Stroke.copy(alpha = 0.55f), Offset(0f, gy), Offset(w, gy), 1f)
        }
        if (values.size < 2) return@Canvas

        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 1f
        val raw = maxV - minV
        val need = maxOf(raw * 1.36f, minSpan, 0.002f)
        val center = (minV + maxV) / 2f
        val lo = center - need / 2f
        val hi = center + need / 2f
        val span = (hi - lo).takeIf { it > 1e-6f } ?: 1f

        val topPad = h * 0.10f
        val usable = h * 0.80f
        val stepX = w / (values.size - 1).toFloat()

        val pts = ArrayList<Offset>(values.size)
        values.forEachIndexed { i, v ->
            val nx = ((v - lo) / span).coerceIn(0f, 1f)
            pts.add(Offset(i * stepX, h - topPad - nx * usable))
        }

        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        if (smooth && pts.size > 2) {
            // Catmull-Rom 转三次贝塞尔；张力取 1/7 而非 1/6，弱化过冲
            val t = 1f / 7f
            for (i in 0 until pts.size - 1) {
                val p0 = pts[if (i - 1 >= 0) i - 1 else i]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[if (i + 2 < pts.size) i + 2 else i + 1]
                path.cubicTo(
                    p1.x + (p2.x - p0.x) * t, p1.y + (p2.y - p0.y) * t,
                    p2.x - (p3.x - p1.x) * t, p2.y - (p3.y - p1.y) * t,
                    p2.x, p2.y
                )
            }
        } else {
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        }

        // 曲线下方的柔和渐变填充
        val area = Path().apply {
            addPath(path)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)),
                startY = topPad, endY = h
            )
        )

        drawPath(
            path, color,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 末端采样点高亮，指示"现在"
        drawCircle(color.copy(alpha = 0.22f), radius = 9f, center = pts.last())
        drawCircle(color, radius = 3.5f, center = pts.last())
    }
}

@Composable
fun SignalBars(bars: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 5
        val gap = size.width * 0.09f
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = size.height * (0.30f + 0.175f * i)
            drawRoundRect(
                color = if (i < bars) color else Stroke,
                topLeft = Offset(i * (bw + gap), size.height - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw * 0.45f, bw * 0.45f)
            )
        }
    }
}

// ───────────────────────────── 小组件 ─────────────────────────────

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Composable
fun HintBox(text: String, color: Color = TextLo) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelHi.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/** 细长进度条，用于流量套餐用量等比例展示 */
@Composable
fun ProgressBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val r = ratio.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(Stroke)
    ) {
        Box(
            Modifier
                .fillMaxWidth(r)
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.75f), color)
                    )
                )
        )
    }
}

@Composable
fun RowOfMetrics(vararg tiles: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.forEach { (label, value) ->
            MetricTile(label = label, value = value, modifier = Modifier.weight(1f))
        }
    }
}
