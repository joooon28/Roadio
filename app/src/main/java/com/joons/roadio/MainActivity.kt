package com.joons.roadio

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joons.roadio.ui.theme.RoadioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("dev02_probe", false)) {
            startActivity(Intent(this, Dev02ProbeActivity::class.java))
            finish()
            return
        }
        setContent { RoadioTheme { RoadioHome() } }
    }
}

@Composable
fun RoadioHome() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Roadio", style = MaterialTheme.typography.headlineLarge)
        Text("길 위에 목소리를 남기면, 그곳을 지나는 차 안에서 다시 만나는 위치 기반 음성 커뮤니티.")
        Text("모바일: 음성 캡슐 녹음·위치·공개 범위 설정")
        Text("차량: 주변 캡슐 확인·선택 재생")
        Text("현재 단계: 기획 및 기술 검증")
        Text("주변 음성 캡슐 탐색 기능은 준비 중입니다. 시연용 위치와 음성은 실제 사용자 콘텐츠와 구분합니다.")
        OutlinedButton(onClick = { context.startActivity(Intent(context, Dev02ProbeActivity::class.java)) }) { Text("DEV-02 기술 검증") }
    }
}

@Preview(showBackground = true)
@Composable
fun RoadioPreview() { RoadioTheme { RoadioHome() } }
