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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
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

/** DEV-06 stationary sample discovery and selected playback; no server or live vehicle position. */
class MainActivity : ComponentActivity() {
    private lateinit var playbackClient: RoadioPlaybackClient
    private lateinit var vehicleLocation: VehicleDiscoveryController
    private lateinit var vehicleSpeed: VehicleSpeedController
    private lateinit var uxRestrictions: VehicleUxRestrictions
    private var showOfflineSample by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("dev02_probe", false)) {
            startActivity(Intent(this, Dev02ProbeActivity::class.java))
            finish()
            return
        }
        playbackClient = RoadioPlaybackClient(this)
        vehicleLocation = VehicleDiscoveryController(this)
        vehicleSpeed = VehicleSpeedController(this)
        uxRestrictions = VehicleUxRestrictions(this)
        playbackClient.connect()
        setContent {
            RoadioTheme {
                if (showOfflineSample && uxRestrictions.restricted == false) RoadioHome(playbackClient, onBackServer = { playbackClient.stop(); showOfflineSample = false })
                else VehicleServerScreen(this, vehicleLocation, vehicleSpeed, playbackClient, uxRestrictions, onOpenSample = {
                    playbackClient.stop(); VehicleSelectedAudio.clear(); showOfflineSample = true
                })
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::vehicleLocation.isInitialized) vehicleLocation.onPermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        if (::playbackClient.isInitialized) playbackClient.release()
        if (::vehicleLocation.isInitialized) vehicleLocation.release()
        if (::vehicleSpeed.isInitialized) vehicleSpeed.close()
        if (::uxRestrictions.isInitialized) uxRestrictions.close()
        VehicleSelectedAudio.clear()
        super.onDestroy()
    }
}

@Composable
fun RoadioHome(playbackClient: RoadioPlaybackClient? = null, onBackServer: (() -> Unit)? = null) {
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
    val changeScenario: (DemoScenario) -> Unit = { playbackClient?.stop(); scenario = it; page = DemoPage.HOME }
    val back: () -> Unit = {
        if (page == DemoPage.PLAYER) playbackClient?.stop()
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
        if (onBackServer != null) OutlinedButton(onClick = onBackServer) { Text("서버 연결 화면") }
        Text("차량에서 발견한 목소리 · 정차 시연", style = MaterialTheme.typography.titleMedium)
        Text("가상 차량 위치 · 사전 준비한 음성 · 시연용 계정 / 서버 검색 미연결")
        HorizontalDivider()
        if (page != DemoPage.HOME) OutlinedButton(onClick = back) { Text("뒤로") }
        when (page) {
            DemoPage.HOME -> {
                Text("내 주변의 목소리", style = MaterialTheme.typography.headlineSmall)
                WireSection("현재 위치 · 주변 캡슐") {
                    when (scenario) {
                        DemoScenario.CHECKING -> Text("위치를 확인하는 중입니다.")
                        DemoScenario.UNAVAILABLE -> Text("위치를 사용할 수 없습니다. 위치 권한과 연결 상태를 확인해 주세요.")
                        else -> {
                            Text("가상 위치 기준 · 접근 가능한 샘플 캡슐 ${results.size}개")
                            if (results.isEmpty()) Text("이 위치에는 들을 수 있는 캡슐이 없습니다.")
                            else Button(onClick = { page = DemoPage.LIST }) { Text("캡슐 목록 보기") }
                        }
                    }
                }
                WireSection("시연 조건 변경 · 개발 확인용") {
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
                    if (scenario == DemoScenario.EXPIRED) Text("검증 시각만 가상으로 8일 뒤로 옮겼습니다.")
                    OutlinedButton(onClick = { context.startActivity(Intent(context, Dev02ProbeActivity::class.java)) }) {
                        Text("DEV-02 기술 진단")
                    }
                }
            }
            DemoPage.LIST -> {
                Text("주변 캡슐", style = MaterialTheme.typography.headlineSmall)
                Text("가상 위치 기준 · 접근 가능한 샘플 ${results.size}개")
                results.forEach { capsule ->
                    WireSection(capsule.title) {
                        Text("${capsule.authorDisplayName} · ${capsule.durationMs / 1000}초 · ${capsule.distanceMeters}m")
                        Text("가족에게 공개 · 사전 준비한 음성")
                        Button(onClick = { page = DemoPage.DETAIL }) { Text("캡슐 정보 보기") }
                    }
                }
                Text("목록을 여는 것만으로 음성이 재생되지 않습니다.")
            }
            DemoPage.DETAIL -> if (selected != null) {
                Text("캡슐 정보", style = MaterialTheme.typography.headlineSmall)
                WireSection(selected.title) {
                    Text("작성자 ${selected.authorDisplayName}")
                    Text("길이 ${selected.durationMs / 1000}초 · 거리 ${selected.distanceMeters}m")
                    Text("공개 대상: 지정된 가족")
                    Text("사전 준비한 샘플 음성")
                }
                Text("재생 여부를 직접 선택해 주세요.")
                Button(onClick = { page = DemoPage.PLAYER }) { Text("재생 화면 열기") }
            }
            DemoPage.PLAYER -> if (selected != null) {
                Text("선택 재생", style = MaterialTheme.typography.headlineSmall)
                WireSection(selected.title) {
                    Text("${selected.authorDisplayName} · 지정된 가족 · 약 ${selected.durationMs / 1000}초")
                    Text("재생은 아래 버튼을 직접 눌렀을 때만 시작합니다.")
                    val status = playbackClient?.status ?: RoadioPlaybackClient.Status.CONNECTING
                    Text(when (status) {
                        RoadioPlaybackClient.Status.CONNECTING -> "오디오 서비스 연결 중"
                        RoadioPlaybackClient.Status.READY -> "재생 준비"
                        RoadioPlaybackClient.Status.BUFFERING -> "음성 준비 중"
                        RoadioPlaybackClient.Status.PLAYING -> "재생 중"
                        RoadioPlaybackClient.Status.PAUSED -> "일시정지"
                        RoadioPlaybackClient.Status.STOPPED -> "재생 종료"
                        RoadioPlaybackClient.Status.ERROR -> "재생 오류"
                    }, style = MaterialTheme.typography.titleMedium)
                    playbackClient?.errorMessage?.let { Text(it) }
                    when (status) {
                        RoadioPlaybackClient.Status.PLAYING ->
                            Button(onClick = { playbackClient?.pause() }) { Text("일시정지") }
                        RoadioPlaybackClient.Status.PAUSED ->
                            Button(onClick = { playbackClient?.resume() }) { Text("이어서 재생") }
                        RoadioPlaybackClient.Status.CONNECTING ->
                            Button(onClick = {}, enabled = false) { Text("연결 중") }
                        RoadioPlaybackClient.Status.ERROR ->
                            Button(onClick = { playbackClient?.retry(selected.id) }) { Text("다시 시도") }
                        else ->
                            Button(onClick = { playbackClient?.playSelected(selected.id) }) { Text("재생") }
                    }
                    if (status == RoadioPlaybackClient.Status.PLAYING || status == RoadioPlaybackClient.Status.PAUSED || status == RoadioPlaybackClient.Status.BUFFERING) {
                        OutlinedButton(onClick = { playbackClient?.stop() }) { Text("종료") }
                    }
                }
                OutlinedButton(onClick = { playbackClient?.stop(); page = DemoPage.LIST }) { Text("목록으로") }
                OutlinedButton(onClick = { playbackClient?.playSelected("unknown-sample") }) { Text("DEV-06 오류 시연") }
            }
        }
    }
}

@Composable
private fun WireSection(title: String, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoadioPreview() { RoadioTheme { RoadioHome() } }
