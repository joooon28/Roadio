package com.joons.roadio

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private const val ReferenceWidth = 1697f
private const val ReferenceHeight = 1184f
private const val WaveSize = 1184f
private const val GlowSize = 453f
private const val SymbolSize = 137f
private const val RippleDurationMillis = 4_200

private val Primary50 = Color(0xFFF0F5FE)
private val Primary100 = Color(0xFFDFE9FC)
private val Primary600 = Color(0xFF2356C4)
private val Neutral50 = Color(0xFFF7F8F9)

@Composable
internal fun VehicleNearbySearchingScreen() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .border(1.dp, Color(0x33131417))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Primary100,
                        .3f to Neutral50,
                        .7f to Primary50,
                        1f to Primary100,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth.value / ReferenceWidth, maxHeight.value / ReferenceHeight)
        val waveSize = (WaveSize * scale).dp
        val glowSize = (GlowSize * scale).dp
        val symbolSize = (SymbolSize * scale).dp

        VehicleSearchWave(
            size = waveSize,
            phaseMillis = 0,
        )
        VehicleSearchWave(
            size = waveSize,
            phaseMillis = RippleDurationMillis / 2,
        )

        Canvas(Modifier.size(glowSize)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Primary100,
                        .3f to Primary100,
                        .6f to Primary100,
                        .79808f to Primary50,
                        1f to Neutral50.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
            )
        }

        Image(
            painter = painterResource(R.drawable.roadio_vehicle_search_symbol),
            contentDescription = "Roadio가 주변 이야기를 찾는 중",
            modifier = Modifier
                .size(symbolSize)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Multiply
                },
            contentScale = ContentScale.Fit,
        )

        Text(
            text = "이야기 찾는 중...",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-100f * scale).dp),
            color = Primary600,
            fontSize = (48f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = (67.2f * scale).sp,
            letterSpacing = (-1.2f * scale).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VehicleSearchWave(size: Dp, phaseMillis: Int) {
    val transition = rememberInfiniteTransition(label = "vehicle-search-wave-$phaseMillis")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = RippleDurationMillis
                0f at 0
                .2f at 700 using FastOutSlowInEasing
                .72f at 2_900
                1f at RippleDurationMillis
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(phaseMillis, StartOffsetType.FastForward),
        ),
        label = "vehicle-search-wave-progress-$phaseMillis",
    )
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val alpha = if (!animationsEnabled) .24f else when {
        progress < .14f -> progress / .14f * .74f
        progress < .78f -> .74f - ((progress - .14f) / .64f * .42f)
        else -> .32f * (1f - (progress - .78f) / .22f)
    }
    val scale = if (animationsEnabled) .42f + progress * 1.58f else 1.45f

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha.coerceIn(0f, 1f)
            },
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    .5f to Color.Transparent,
                    .58f to Primary100.copy(alpha = .1f),
                    .66f to Color(0xFF608CE1).copy(alpha = .34f),
                    .74f to Color(0xFF91B2EF).copy(alpha = .2f),
                    .88f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = center,
                radius = this.size.minDimension / 2f,
            ),
        )
    }
}
