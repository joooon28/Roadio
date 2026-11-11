package com.joons.roadio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private const val DiscoveryReferenceWidth = 1697f
private const val DiscoveryReferenceHeight = 1184f
private const val DiscoveryGlowSize = 620f
private const val DiscoverySymbolSize = 137f

private val DiscoveryPrimary50 = Color(0xFFF0F5FE)
private val DiscoveryPrimary100 = Color(0xFFDFE9FC)
private val DiscoveryPrimary600 = Color(0xFF2356C4)
private val DiscoveryNeutral50 = Color(0xFFF7F8F9)

@Composable
internal fun VehicleStoryDiscoveredScreen(
    title: String,
    onListen: () -> Unit,
    onSkip: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0x33131417))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to DiscoveryPrimary100,
                        .3f to DiscoveryNeutral50,
                        .7f to DiscoveryPrimary50,
                        1f to DiscoveryNeutral50,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth.value / DiscoveryReferenceWidth, maxHeight.value / DiscoveryReferenceHeight)
        val glowSize = (DiscoveryGlowSize * scale).dp
        val symbolSize = (DiscoverySymbolSize * scale).dp

        Canvas(Modifier.size(glowSize)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to DiscoveryPrimary100.copy(alpha = .96f),
                        .35f to DiscoveryPrimary100.copy(alpha = .88f),
                        .58f to DiscoveryPrimary100.copy(alpha = .62f),
                        .74f to DiscoveryPrimary50.copy(alpha = .36f),
                        .88f to DiscoveryNeutral50.copy(alpha = .14f),
                        1f to DiscoveryNeutral50.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
            )
        }

        Image(
            painter = painterResource(R.drawable.roadio_vehicle_search_symbol),
            contentDescription = null,
            modifier = Modifier
                .size(symbolSize)
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
            verticalArrangement = Arrangement.spacedBy((16f * scale).dp),
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
                color = DiscoveryPrimary600,
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
                .align(Alignment.BottomCenter)
                .offset(y = (-80f * scale).dp),
            verticalArrangement = Arrangement.spacedBy((20f * scale).dp),
        ) {
            DiscoveryButton(
                label = "이야기 듣기",
                filled = true,
                scale = scale,
                onClick = onListen,
            )
            DiscoveryButton(
                label = "건너뛰기",
                filled = false,
                scale = scale,
                onClick = onSkip,
            )
        }
    }
}

@Composable
private fun DiscoveryButton(label: String, filled: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape((16f * scale).dp)
    Box(
        modifier = Modifier
            .size(width = (800f * scale).dp, height = (104f * scale).dp)
            .background(if (filled) DiscoveryPrimary600 else Color.Transparent, shape)
            .then(if (filled) Modifier else Modifier.border((4f * scale).dp, DiscoveryPrimary600, shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((20f * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (filled) {
                Image(
                    painter = painterResource(R.drawable.roadio_vehicle_discovery_play),
                    contentDescription = null,
                    modifier = Modifier.size((48f * scale).dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = label,
                color = if (filled) DiscoveryNeutral50 else DiscoveryPrimary600,
                fontSize = (40f * scale).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = (56f * scale).sp,
                letterSpacing = (-1f * scale).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
