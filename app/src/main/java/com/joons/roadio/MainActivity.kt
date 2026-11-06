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
import java.time.Instant

private enum class DemoScenario { CHECKING, FAMILY_NEAR, FAMILY_FAR, NOT_SELECTED, EXPIRED, UNAVAILABLE }
private enum class DemoPage { HOME, LIST, DETAIL, PLAYER }

/** DEV-05 stationary sample discovery; no server, live vehicle position or audio playback. */
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
    val sampleTime = remember { Instant.now() }
    val sampleCapsules = remember(sampleTime) { Dev05SampleData.capsules(sampleTime) }
    var scenario by remember { mutableStateOf(DemoScenario.CHECKING) }
    var page by remember { mutableStateOf(DemoPage.HOME) }
    val vehiclePosition = when (scenario) {
        DemoScenario.CHECKING, DemoScenario.UNAVAILABLE -> null
        DemoScenario.FAMILY_FAR -> Dev05SampleData.farPosition
        else -> Dev05SampleData.nearPosition
    }
    val viewerUserId = if (scenario == DemoScenario.NOT_SELECTED) Dev05SampleData.otherUserId else Dev05SampleData.recipientUserId
    val searchTime = if (scenario == DemoScenario.EXPIRED) sampleTime.plusSeconds(8 * 24 * 60 * 60) else sampleTime
    val results = vehiclePosition?.let { SampleCapsuleDiscovery.search(sampleCapsules, it, viewerUserId, searchTime) }.orEmpty()
    val selected = results.firstOrNull()
    val changeScenario: (DemoScenario) -> Unit = { scenario = it; page = DemoPage.HOME }
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
        Text("DEV-05 샘플 캡슐 발견 · 정차 상태", style = MaterialTheme.typography.titleMedium)
        Text("가상 차량 위치 · 사전 준비한 캡슐 · 시연용 계정입니다. 실제 서버 검색과 음성 재생은 연결되지 않았습니다.")
        if (page != DemoPage.HOME) OutlinedButton(onClick = back) { Text("뒤로") }
        when (page) {
            DemoPage.HOME -> {
                Text("내 주변의 목소리", style = MaterialTheme.typography.headlineSmall)
                when (scenario) {
                    DemoScenario.CHECKING -> Text("위치를 확인하는 중입니다.")
                    DemoScenario.UNAVAILABLE -> Text("위치를 사용할 수 없습니다. 위치 권한과 연결 상태를 확인해 주세요.")
                    else -> {
                        Text("가상 위치 기준 · 접근 가능한 샘플 캡슐 ${results.size}개")
                        if (results.isEmpty()) Text("이 위치에는 들을 수 있는 캡슐이 없습니다.")
                        else Button(onClick = { page = DemoPage.LIST }) { Text("캡슐 목록 보기") }
                    }
                }
                Text("DEV-05 검증 조건", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { changeScenario(DemoScenario.FAMILY_NEAR) }) { Text("가족·반경 안") }
                    OutlinedButton(onClick = { changeScenario(DemoScenario.FAMILY_FAR) }) { Text("반경 밖") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { changeScenario(DemoScenario.NOT_SELECTED) }) { Text("대상 아님") }
                    OutlinedButton(onClick = { changeScenario(DemoScenario.EXPIRED) }) { Text("기간 만료") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { changeScenario(DemoScenario.UNAVAILABLE) }) { Text("위치 오류") }
                    OutlinedButton(onClick = { changeScenario(DemoScenario.CHECKING) }) { Text("확인 중") }
                }
                if (scenario == DemoScenario.EXPIRED) Text("만료 조건 확인을 위해 검증 시각만 가상으로 8일 뒤로 옮겼습니다.")
                OutlinedButton(onClick = { context.startActivity(Intent(context, Dev02ProbeActivity::class.java)) }) {
                    Text("DEV-02 기술 진단")
                }
            }
            DemoPage.LIST -> {
                Text("주변 캡슐", style = MaterialTheme.typography.headlineSmall)
                Text("가상 위치 기준 · 접근 가능한 샘플 ${results.size}개")
                results.forEach { capsule ->
                    OutlinedButton(onClick = { page = DemoPage.DETAIL }, modifier = Modifier.fillMaxWidth()) {
                        Text("${capsule.title} · ${capsule.authorDisplayName} · ${capsule.durationMs / 1000}초 · ${capsule.distanceMeters}m")
                    }
                }
                Text("목록을 여는 것만으로 음성이 재생되지 않습니다.")
            }
            DemoPage.DETAIL -> if (selected != null) {
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                Text("${selected.authorDisplayName} · ${selected.durationMs / 1000}초 · ${selected.distanceMeters}m")
                Text("공개 대상: 지정된 가족 · 사전 준비한 샘플")
                Text("재생 여부를 직접 선택해 주세요.")
                Button(onClick = { page = DemoPage.PLAYER }) { Text("재생 화면 열기") }
            }
            DemoPage.PLAYER -> if (selected != null) {
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                Text("재생 준비 · 샘플 화면")
                Text("음성 재생과 일시정지·종료는 DEV-06에서 연결합니다.")
                OutlinedButton(onClick = { page = DemoPage.LIST }) { Text("목록으로") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoadioPreview() { RoadioTheme { RoadioHome() } }
