package com.joons.roadio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joons.roadio.ui.theme.RoadioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoadioTheme { RoadioHome() } }
    }
}

@Composable
fun RoadioHome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Roadio", style = MaterialTheme.typography.headlineLarge)
        Text("길 위에 목소리를 남기면, 그곳을 지나는 차 안에서 다시 만나는 위치 기반 음성 커뮤니티.")
        Text("모바일: 음성 캡슐 녹음·위치·공개 범위 설정")
        Text("차량: 주변 캡슐 확인·선택 재생")
        Text("현재 단계: 기획 및 기술 검증")
        Text("위치 연동은 검증 전입니다. 시연에는 가상 위치와 사전 녹음 샘플을 구분해 사용합니다.")
    }
}

@Preview(showBackground = true)
@Composable
fun RoadioPreview() { RoadioTheme { RoadioHome() } }
