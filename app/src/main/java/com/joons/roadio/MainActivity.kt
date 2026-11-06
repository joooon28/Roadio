package com.joons.roadio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joons.roadio.ui.theme.RoadioTheme

private enum class DemoState { CHECKING, AVAILABLE, EMPTY, UNAVAILABLE }
private enum class DemoPage { HOME, LIST, DETAIL, PLAYER }

/** DEV-04 stationary wireframe. The capsule and location here are fictional UI samples. */
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
    var state by remember { mutableStateOf(DemoState.CHECKING) }
    var page by remember { mutableStateOf(DemoPage.HOME) }
    val back: () -> Unit = {
        page = when (page) {
            DemoPage.HOME -> DemoPage.HOME
            DemoPage.LIST -> DemoPage.HOME
            DemoPage.DETAIL -> DemoPage.LIST
            DemoPage.PLAYER -> DemoPage.DETAIL
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Roadio", style = MaterialTheme.typography.headlineLarge)
        Text("DEV-04 화면 흐름 · 정차 상태 와이어프레임", style = MaterialTheme.typography.titleMedium)
        Text("가상 위치·예시 캡슐입니다. 실제 주변 검색과 음성 재생은 연결되지 않았습니다.")
        if (page != DemoPage.HOME) OutlinedButton(onClick = back) { Text("뒤로") }
        when (page) {
            DemoPage.HOME -> {
                Text("내 주변의 목소리", style = MaterialTheme.typography.headlineSmall)
                when (state) {
                    DemoState.CHECKING -> Text("위치를 확인하는 중입니다.")
                    DemoState.AVAILABLE -> {
                        Text("가상 위치 · 주변 캡슐 1개")
                        Button(onClick = { page = DemoPage.LIST }) { Text("캡슐 목록 보기") }
                    }
                    DemoState.EMPTY -> Text("이 위치에는 들을 수 있는 캡슐이 없습니다.")
                    DemoState.UNAVAILABLE -> Text("위치를 사용할 수 없습니다. 위치 권한과 연결 상태를 확인해 주세요.")
                }
                Text("화면 상태 확인", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { state = DemoState.AVAILABLE }) { Text("있음") }
                    OutlinedButton(onClick = { state = DemoState.EMPTY }) { Text("없음") }
                    OutlinedButton(onClick = { state = DemoState.UNAVAILABLE }) { Text("오류") }
                }
                OutlinedButton(onClick = { state = DemoState.CHECKING }) { Text("확인 중") }
                OutlinedButton(onClick = { context.startActivity(Intent(context, Dev02ProbeActivity::class.java)) }) {
                    Text("DEV-02 기술 진단")
                }
            }
            DemoPage.LIST -> {
                Text("주변 캡슐", style = MaterialTheme.typography.headlineSmall)
                Text("가상 위치 기준 · 접근 가능한 예시 1개")
                OutlinedButton(onClick = { page = DemoPage.DETAIL }, modifier = Modifier.fillMaxWidth()) {
                    Text("퇴근길 인사 · 가족 · 약 20초 · 320m")
                }
                Text("목록을 여는 것만으로 음성이 재생되지 않습니다.")
            }
            DemoPage.DETAIL -> {
                Text("퇴근길 인사", style = MaterialTheme.typography.headlineSmall)
                Text("가족 · 약 20초 · 320m")
                Text("공개 대상: 지정된 가족 · 예시 캡슐")
                Text("재생 여부를 직접 선택해 주세요.")
                Button(onClick = { page = DemoPage.PLAYER }) { Text("재생 화면 열기") }
            }
            DemoPage.PLAYER -> {
                Text("퇴근길 인사", style = MaterialTheme.typography.headlineSmall)
                Text("재생 준비 · 예시 화면")
                Text("음성 재생과 일시정지·종료는 DEV-06에서 연결합니다.")
                OutlinedButton(onClick = { page = DemoPage.LIST }) { Text("목록으로") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoadioPreview() { RoadioTheme { RoadioHome() } }
