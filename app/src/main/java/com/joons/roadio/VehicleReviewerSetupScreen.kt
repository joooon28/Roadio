package com.joons.roadio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private const val SetupReferenceWidth = 1697f
private const val SetupReferenceHeight = 1184f

private val SetupPrimary600 = Color(0xFF2356C4)
private val SetupPrimary100 = Color(0xFFDFE9FC)
private val SetupPrimary50 = Color(0xFFF0F5FE)
private val SetupNeutral900 = Color(0xFF131417)
private val SetupNeutral600 = Color(0xFF667085)
private val SetupNeutral200 = Color(0xFFE5E7EB)
private val SetupNeutral50 = Color(0xFFF7F8F9)
private val SetupSuccess = Color(0xFF21845A)

@Composable
internal fun VehicleReviewerSetupScreen(
    token: String,
    onTokenChange: (String) -> Unit,
    locationLabel: String,
    locationMessage: String,
    locationReady: Boolean,
    onCheckLocation: () -> Unit,
    onStart: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SetupPrimary100, SetupNeutral50, SetupPrimary50),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth.value / SetupReferenceWidth, maxHeight.value / SetupReferenceHeight)
        val cardShape = RoundedCornerShape((28f * scale).dp)

        Column(
            modifier = Modifier.width((980f * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((22f * scale).dp),
        ) {
            Box(
                modifier = Modifier
                    .background(SetupPrimary100, RoundedCornerShape(999.dp))
                    .padding(horizontal = (26f * scale).dp, vertical = (10f * scale).dp),
            ) {
                Text(
                    text = "심사용 설정",
                    color = SetupPrimary600,
                    fontSize = (24f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Roadio 연결 준비",
                color = SetupNeutral900,
                fontSize = (52f * scale).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (68f * scale).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "차량 연결 코드와 Pleos 위치를 확인해 주세요.",
                color = SetupNeutral600,
                fontSize = (26f * scale).sp,
                lineHeight = (38f * scale).sp,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = .92f), cardShape)
                    .border((1f * scale).dp, SetupNeutral200, cardShape)
                    .padding((42f * scale).dp),
                verticalArrangement = Arrangement.spacedBy((24f * scale).dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((14f * scale).dp),
                ) {
                    Box(
                        Modifier
                            .size((13f * scale).dp)
                            .background(SetupSuccess, RoundedCornerShape(999.dp)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy((3f * scale).dp)) {
                        Text(
                            text = "시연 서버 준비됨",
                            color = SetupNeutral900,
                            fontSize = (23f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "roadio-production.up.railway.app",
                            color = SetupNeutral600,
                            fontSize = (19f * scale).sp,
                        )
                    }
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("차량 연결 코드") },
                    placeholder = { Text("심사용 차량 연결 코드") },
                    supportingText = {
                        Text(
                            text = if (token.isNotBlank()) {
                                "심사용 연결 코드가 자동으로 입력되었습니다."
                            } else {
                                "연결 코드가 없습니다."
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape((16f * scale).dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = (23f * scale).sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = SetupPrimary600,
                        unfocusedIndicatorColor = SetupNeutral200,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SetupNeutral50, RoundedCornerShape((16f * scale).dp))
                        .padding((22f * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy((5f * scale).dp),
                    ) {
                        Text(
                            text = if (locationReady) "Pleos 위치 확인 완료" else "Pleos 위치 확인 필요",
                            color = if (locationReady) SetupSuccess else SetupNeutral900,
                            fontSize = (23f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (locationReady) locationLabel else locationMessage,
                            color = SetupNeutral600,
                            fontSize = (19f * scale).sp,
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = onCheckLocation,
                        shape = RoundedCornerShape((14f * scale).dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SetupPrimary600),
                    ) {
                        Text(
                            text = if (locationReady) "다시 확인" else "위치 확인",
                            modifier = Modifier.padding(
                                horizontal = (14f * scale).dp,
                                vertical = (7f * scale).dp,
                            ),
                            fontSize = (21f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Button(
                    onClick = onStart,
                    enabled = token.isNotBlank() && locationReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape((16f * scale).dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SetupPrimary600,
                        contentColor = Color.White,
                        disabledContainerColor = SetupNeutral200,
                        disabledContentColor = SetupNeutral600,
                    ),
                ) {
                    Text(
                        text = "시연 시작",
                        modifier = Modifier.padding(vertical = (14f * scale).dp),
                        fontSize = (25f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = "심사용 연결 코드는 심사 종료 후 폐기됩니다.",
                color = SetupNeutral600,
                fontSize = (18f * scale).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
