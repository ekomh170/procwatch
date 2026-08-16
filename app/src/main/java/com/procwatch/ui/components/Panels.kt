package com.procwatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.procwatch.ui.theme.DataStyle
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.Panel
import kotlin.math.ceil

/**
 * The signature element: a segmented load meter, drawn like the LED ladder on a rack unit
 * rather than a smooth progress bar. Discrete cells make a 68% reading legible at a glance
 * and stop the eye from reading small changes as motion.
 */
@Composable
fun SegmentMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 28,
    activeColor: Color = Panel.Signal,
    height: androidx.compose.ui.unit.Dp = 14.dp
) {
    val safe = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val gap = 2.dp.toPx()
        val cellWidth = (size.width - gap * (segments - 1)) / segments
        val lit = ceil(safe * segments).toInt()
        for (i in 0 until segments) {
            val x = i * (cellWidth + gap)
            drawRect(
                color = if (i < lit) activeColor else Panel.OutlineSoft,
                topLeft = Offset(x, 0f),
                size = Size(cellWidth, size.height)
            )
        }
    }
}

/** A titled panel. Every card in the app is one of these — no loose floating content. */
@Composable
fun PanelCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Panel.Surface, RoundedCornerShape(Panel.Radius))
            .border(1.dp, Panel.Outline, RoundedCornerShape(Panel.Radius))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = EyebrowStyle,
                color = Panel.TextFaint,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Label on the left, monospaced value on the right. The workhorse row of the whole app. */
@Composable
fun ReadoutRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Panel.TextPrimary
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DataStyle, color = Panel.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = DataStyle, color = valueColor)
    }
}

/** Bucket / status chip. Filled dot plus text, so it reads without relying on colour alone. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Panel.SurfaceRaised, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(5.dp))
        Text(text, style = EyebrowStyle, color = Panel.TextSecondary)
    }
}

/** Vertical status rail drawn at the leading edge of a list row. */
@Composable
fun StatusRail(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Panel.RailWidth)
            .background(color)
    )
}
