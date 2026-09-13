package com.cpepanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 仪表盘：圆弧 + 指针 */
@Composable
fun IconGauge(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val sw = w * 0.12f
        val inset = sw / 2f + w * 0.06f
        val d = w - inset * 2f
        drawArc(
            color = tint,
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(d, d),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        val cx = w / 2f
        val cy = w / 2f + w * 0.05f
        val r = d / 2f * 0.60f
        val rad = Math.toRadians(300.0)
        drawLine(
            color = tint,
            start = Offset(cx, cy),
            end = Offset(cx + (r * cos(rad)).toFloat(), cy + (r * sin(rad)).toFloat()),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

/** 折线图 */
@Composable
fun IconChart(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.11f
        val padX = w * 0.10f
        val usable = h * 0.64f
        val base = h * 0.82f
        val path = Path().apply {
            moveTo(padX, base - usable * 0.14f)
            lineTo(w * 0.36f, base - usable * 0.58f)
            lineTo(w * 0.60f, base - usable * 0.32f)
            lineTo(w - padX, base - usable * 0.92f)
        }
        drawPath(
            path, tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            tint.copy(alpha = 0.35f),
            Offset(padX, base), Offset(w - padX, base),
            sw * 0.7f, StrokeCap.Round
        )
    }
}

/** 网络：地球经纬线 */
@Composable
fun IconGlobe(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val sw = w * 0.10f
        val inset = sw / 2f + w * 0.04f
        val d = w - inset * 2f
        val cx = w / 2f
        // 外圈
        drawCircle(
            color = tint,
            radius = d / 2f,
            center = Offset(cx, w / 2f),
            style = Stroke(width = sw)
        )
        // 赤道
        drawLine(
            color = tint,
            start = Offset(inset + sw * 0.6f, w / 2f),
            end = Offset(w - inset - sw * 0.6f, w / 2f),
            strokeWidth = sw * 0.8f,
            cap = StrokeCap.Round
        )
        // 左右经线（用椭圆弧近似球面）
        val half = d * 0.28f
        drawArc(
            color = tint,
            startAngle = -90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - half, inset),
            size = Size(half * 2f, d),
            style = Stroke(width = sw * 0.8f)
        )
        drawArc(
            color = tint,
            startAngle = 90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - half, inset),
            size = Size(half * 2f, d),
            style = Stroke(width = sw * 0.8f)
        )
    }
}

/** 芯片 / 主板 */
@Composable
fun IconChip(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val sw = w * 0.10f
        val l = w * 0.30f
        val r = w * 0.70f
        val t = w * 0.32f
        val b = w * 0.68f
        drawRoundRect(
            color = tint,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
            style = Stroke(width = sw)
        )
        val pin = w * 0.14f
        for (i in 1..2) {
            val y = t + (b - t) * i / 3f
            drawLine(tint, Offset(l - pin, y), Offset(l, y), sw * 0.8f, StrokeCap.Round)
            drawLine(tint, Offset(r, y), Offset(r + pin, y), sw * 0.8f, StrokeCap.Round)
        }
    }
}

/** 短信：对话气泡 + 内容线 */
@Composable
fun IconMessage(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.095f
        val l = w * 0.13f
        val r = w * 0.87f
        val t = h * 0.19f
        val b = h * 0.64f

        // 气泡主体
        drawRoundRect(
            color = tint,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            cornerRadius = CornerRadius(w * 0.17f, w * 0.17f),
            style = Stroke(width = sw)
        )
        // 左下角的小尾巴（实心三角）
        val tail = Path().apply {
            moveTo(l + (r - l) * 0.20f, b - sw * 0.55f)
            lineTo(l + (r - l) * 0.16f, b + h * 0.14f)
            lineTo(l + (r - l) * 0.46f, b - sw * 0.55f)
            close()
        }
        drawPath(tail, tint)

        // 两条内容线
        for (i in 0..1) {
            val y = t + (b - t) * (0.35f + i * 0.31f)
            drawLine(
                color = tint,
                start = Offset(l + (r - l) * 0.24f, y),
                end = Offset(r - (r - l) * (if (i == 0) 0.24f else 0.42f), y),
                strokeWidth = sw * 0.68f,
                cap = StrokeCap.Round
            )
        }
    }
}
