package com.joons.roadio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val PlayingReferenceWidth = 1697f
private const val PlayingReferenceHeight = 1184f
private const val PlayingGlowSize = 620f
private const val PlayingProgressSize = 400f
private const val PlayingSymbolSize = 96f

private val PlayingPrimary50 = Color(0xFFF0F5FE)
private val PlayingPrimary100 = Color(0xFFDFE9FC)
private val PlayingPrimary600 = Color(0xFF2356C4)
private val PlayingNeutral50 = Color(0xFFF7F8F9)

@Composable
internal fun VehicleStoryPlayingScreen(
    title: String,
    positionMs: Long,
    durationMs: Long,
    isPaused: Boolean,
    onTogglePlayback: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayingNeutral50)
            .border(1.dp, Color(0x33131417)),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth.value / PlayingReferenceWidth, maxHeight.value / PlayingReferenceHeight)
        val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

        Canvas(Modifier.size((PlayingGlowSize * scale).dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF91B2EF).copy(alpha = .98f),
                        .24f to Color(0xFFBED2F8).copy(alpha = .92f),
                        .48f to PlayingPrimary100.copy(alpha = .76f),
                        .66f to PlayingPrimary50.copy(alpha = .46f),
                        .82f to PlayingNeutral50.copy(alpha = .16f),
                        1f to PlayingNeutral50.copy(alpha = 0f),
                    ),
                    radius = size.minDimension / 2f,
                ),
            )
        }

        Canvas(Modifier.size((PlayingProgressSize * scale).dp)) {
            val strokeWidth = 10f * scale
            val inset = strokeWidth / 2f
            val arcSize = size.minDimension - strokeWidth
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color.White.copy(alpha = .82f),
                startAngle = 180f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            val sweep = -180f * progress
            drawArc(
                color = PlayingPrimary600,
                startAngle = 180f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians((180f + sweep).toDouble())
            val radius = arcSize / 2f
            drawCircle(
                color = PlayingPrimary600,
                radius = 17.8526f * scale,
                center = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat(),
                ),
            )
        }

        Image(
            painter = painterResource(R.drawable.roadio_vehicle_search_symbol),
            contentDescription = null,
            modifier = Modifier
                .size((PlayingSymbolSize * scale).dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Multiply
                },
            contentScale = ContentScale.Fit,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (200f * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.roadio_vehicle_discovery_globe),
                contentDescription = "전체 공개 이야기",
                modifier = Modifier
                    .size((60f * scale).dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        blendMode = BlendMode.Multiply
                    },
                contentScale = ContentScale.Fit,
            )
            Text(
                text = title,
                modifier = Modifier.offset(y = (16f * scale).dp),
                color = PlayingPrimary600,
                fontSize = (60f * scale).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (84f * scale).sp,
                letterSpacing = (-1.5f * scale).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (841f * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size((120f * scale).dp)
                    .clickable(onClick = onTogglePlayback),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        if (isPaused) R.drawable.roadio_vehicle_playing_play
                        else R.drawable.roadio_vehicle_playing_pause,
                    ),
                    contentDescription = if (isPaused) "재생" else "일시정지",
                    modifier = if (isPaused) {
                        Modifier.size(
                            width = (83.299f * scale).dp,
                            height = (82.0375f * scale).dp,
                        )
                    } else {
                        Modifier.size(
                            width = (60f * scale).dp,
                            height = (82f * scale).dp,
                        )
                    },
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = "${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs)}",
                modifier = Modifier.offset(y = (20f * scale).dp),
                color = PlayingPrimary600,
                fontSize = (48f * scale).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = (67.2f * scale).sp,
                letterSpacing = (-1.2f * scale).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
