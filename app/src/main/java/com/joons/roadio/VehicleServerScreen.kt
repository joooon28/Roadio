package com.joons.roadio

import androidx.activity.ComponentActivity
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Server results are displayed only after an explicit search. No proximity auto-play. */
@Composable
fun VehicleServerScreen(
    activity: ComponentActivity,
    location: VehicleDiscoveryController,
    playback: RoadioPlaybackClient,
    onOpenSample: () -> Unit,
) {
    val api = remember { VehicleCapsuleApi(activity) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8787") }
    var token by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("37.4979") }
    var longitude by remember { mutableStateOf("127.0276") }
    var results by remember { mutableStateOf<List<SampleCapsuleSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<SampleCapsuleSummary?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var hasResults by remember { mutableStateOf(false) }

    LaunchedEffect(location.position, location.source) {
        results = emptyList()
        searched = false
        hasResults = false
        if (playback.status !in setOf(RoadioPlaybackClient.Status.PLAYING, RoadioPlaybackClient.Status.PAUSED, RoadioPlaybackClient.Status.BUFFERING)) selected = null
    }

    LaunchedEffect(serverUrl, token) {
        results = emptyList()
        selected = null
        searched = false
        hasResults = false
        playback.stop()
        VehicleSelectedAudio.clear()
    }

    fun search() {
        val current = location.position
        val source = location.source
        val requestedUrl = serverUrl
        val requestedToken = token
        if (current == null || source == null) { error = "먼저 위치를 확인해 주세요."; return }
        if (busy) return
        playback.stop(); VehicleSelectedAudio.clear()
        selected = null; busy = true; error = null
        Thread {
            try {
                val found = api.search(requestedUrl, requestedToken, current, source)
                activity.runOnUiThread {
                    if (location.position == current && location.source == source && serverUrl == requestedUrl && token == requestedToken) {
                        results = found; searched = true; hasResults = found.isNotEmpty()
                    } else error = "위치가 변경됐습니다. 다시 검색해 주세요."
                    busy = false
                }
            } catch (failure: Exception) {
                activity.runOnUiThread { results = emptyList(); searched = false; busy = false; error = failure.message ?: "서버 검색 실패" }
            }
        }.start()
    }

    fun play(capsule: SampleCapsuleSummary) {
        if (busy) return
        val requestedUrl = serverUrl
        val requestedToken = token
        playback.stop()
        busy = true; error = null
        Thread {
            try {
                api.fetchSelectedAudio(requestedUrl, requestedToken, capsule.id)
                activity.runOnUiThread {
                    busy = false
                    if (serverUrl == requestedUrl && token == requestedToken) playback.playSelected(capsule.id)
                    else VehicleSelectedAudio.clear()
                }
            } catch (failure: Exception) {
                activity.runOnUiThread { busy = false; error = failure.message ?: "음성 재생 실패" }
            }
        }.start()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Roadio", style = MaterialTheme.typography.headlineLarge)
        Text("차량 주변 이야기 · 서버 연결 시연", style = MaterialTheme.typography.titleMedium)
        Text("정차 상태에서만 조작해 주세요. 주변 진입으로 자동 재생하지 않습니다.")
        HorizontalDivider()
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("개발 서버 연결", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(serverUrl, { serverUrl = it }, label = { Text("서버 주소") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("차량 시연 계정 토큰") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("토큰은 이 화면의 메모리에만 보관합니다. 공개 시연 화면에 노출하지 마세요.")
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("위치 출처: ${location.sourceLabel}", style = MaterialTheme.typography.titleMedium)
                Text(location.message)
                location.position?.let { Text("위도 ${it.latitude}, 경도 ${it.longitude}") }
                Button(onClick = { location.requestLocation() }) { Text("Pleos 위치 확인") }
                HorizontalDivider()
                Text("가상 위치 시연용 · 실제 연동과 구분")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text("위도") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(longitude, { longitude = it }, label = { Text("경도") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedButton(onClick = { location.setDemoPosition(latitude, longitude) }) { Text("가상 위치 적용") }
            }
        }
        Button(onClick = { search() }, enabled = !busy && location.position != null && token.isNotBlank()) { Text(if (busy) "확인 중" else "주변 이야기 검색") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (searched) {
            Text("${location.sourceLabel} · 접근 가능한 이야기 ${results.size}개", style = MaterialTheme.typography.titleMedium)
            if (!hasResults) Text("이 위치에는 들을 수 있는 이야기가 없습니다.")
            results.forEach { capsule ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(capsule.title, style = MaterialTheme.typography.titleMedium)
                        Text("${capsule.authorDisplayName} · ${capsule.distanceMeters}m · ${capsule.durationMs / 1000}초")
                        Text("콘텐츠 출처: ${if (capsule.contentOrigin == "MOBILE_RECORDING") "모바일 녹음" else "사전 준비한 시연용 음성"}")
                        OutlinedButton(onClick = { playback.stop(); selected = capsule }) { Text("정보 보기") }
                    }
                }
            }
        }
        selected?.let { capsule ->
            HorizontalDivider()
            Text("선택한 이야기: ${capsule.title}", style = MaterialTheme.typography.titleMedium)
            Text("재생을 누르면 서버가 현재 권한·만료·삭제 상태를 다시 확인합니다.")
            val status = playback.status
            Text("재생 상태: $status")
            playback.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (status) {
                RoadioPlaybackClient.Status.PLAYING -> Button(onClick = { playback.pause() }) { Text("일시정지") }
                RoadioPlaybackClient.Status.PAUSED -> Button(onClick = { playback.resume() }) { Text("이어서 재생") }
                else -> Button(onClick = { play(capsule) }, enabled = !busy && status != RoadioPlaybackClient.Status.CONNECTING) { Text(if (busy) "음성 확인 중" else "선택 재생") }
            }
            if (status == RoadioPlaybackClient.Status.PLAYING || status == RoadioPlaybackClient.Status.PAUSED || status == RoadioPlaybackClient.Status.BUFFERING) {
                OutlinedButton(onClick = { playback.stop(); VehicleSelectedAudio.clear() }) { Text("재생 종료") }
            }
        }
        HorizontalDivider()
        OutlinedButton(onClick = onOpenSample) { Text("오프라인 샘플 화면") }
    }
}
