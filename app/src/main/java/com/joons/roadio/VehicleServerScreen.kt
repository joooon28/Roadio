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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** SDK location can discover a story. Only an explicit user choice can start audio. */
@Composable
internal fun VehicleServerScreen(
    activity: ComponentActivity,
    location: VehicleDiscoveryController,
    playback: RoadioPlaybackClient,
    uxRestrictions: VehicleUxRestrictions,
    onOpenSample: () -> Unit,
) {
    val api = remember { VehicleCapsuleApi(activity) }
    val filmingToken = BuildConfig.FILMING_VEHICLE_TOKEN
    val filmingBuild = filmingToken.isNotEmpty()
    var serverUrl by remember { mutableStateOf("https://roadio-production.up.railway.app") }
    var token by remember { mutableStateOf(filmingToken) }
    var requestedInitialLocation by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf(if (filmingBuild) "" else "37.4979") }
    var longitude by remember { mutableStateOf(if (filmingBuild) "" else "127.0276") }
    var results by remember { mutableStateOf<List<SampleCapsuleSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<SampleCapsuleSummary?>(null) }
    var selectedFromOffer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var hasResults by remember { mutableStateOf(false) }
    val offers = remember(serverUrl, token, location.source) { VehicleStoryOffers() }
    var offer by remember(offers) { mutableStateOf<SampleCapsuleSummary?>(null) }
    var autoSearchFailed by remember(offers) { mutableStateOf(false) }
    var screenActive by remember { mutableStateOf(true) }
    val source = location.source
    val sdkLocation = source == "PLEOS_EMULATOR" || source == "PLEOS_DEVICE"
    val autoDiscovery = sdkLocation || (filmingBuild && source == "DEMO_MANUAL")

    DisposableEffect(Unit) {
        onDispose { screenActive = false }
    }

    LaunchedEffect(uxRestrictions.restricted) {
        if (filmingBuild && uxRestrictions.restricted == false && !requestedInitialLocation) {
            requestedInitialLocation = true
            location.requestLocation()
        }
    }

    LaunchedEffect(offers, source) {
        if (!autoDiscovery || token.isBlank()) return@LaunchedEffect
        val url = serverUrl
        val account = token
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastSearched: SamplePosition? = null
            while (isActive) {
                offer = offers.update(emptyList(), Instant.now())
                val position = location.position
                if (position != null && position != lastSearched && location.source == source) {
                    try {
                        val nearby = withContext(Dispatchers.IO) { api.search(url, account, position, source) }
                        if (serverUrl == url && token == account && location.source == source) {
                            offer = offers.update(nearby, Instant.now())
                            lastSearched = position
                            autoSearchFailed = false
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        autoSearchFailed = true
                    }
                }
                delay(2_000)
            }
        }
    }

    LaunchedEffect(location.position, location.source) {
        results = emptyList()
        searched = false
        hasResults = false
        if (!selectedFromOffer && playback.status !in setOf(RoadioPlaybackClient.Status.PLAYING, RoadioPlaybackClient.Status.PAUSED, RoadioPlaybackClient.Status.BUFFERING)) selected = null
    }

    LaunchedEffect(serverUrl, token) {
        results = emptyList()
        selected = null
        selectedFromOffer = false
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
        if (busy || uxRestrictions.restricted != false) return
        playback.stop(); VehicleSelectedAudio.clear()
        selected = null; selectedFromOffer = false; busy = true; error = null
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
        if (busy || uxRestrictions.restricted != false) return
        val requestedUrl = serverUrl
        val requestedToken = token
        playback.stop()
        busy = true; error = null
        Thread {
            try {
                val audio = api.fetchSelectedAudio(requestedUrl, requestedToken, capsule.id)
                activity.runOnUiThread {
                    busy = false
                    if (screenActive && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        uxRestrictions.restricted == false && serverUrl == requestedUrl && token == requestedToken
                    ) {
                        VehicleSelectedAudio.register(capsule.id, audio)
                        playback.playSelected(capsule.id)
                    } else {
                        audio.delete()
                        if (screenActive && serverUrl == requestedUrl && token == requestedToken) {
                            error = "화면 조작이 가능해지면 다시 재생해 주세요."
                        }
                    }
                }
            } catch (failure: Exception) {
                activity.runOnUiThread { busy = false; error = failure.message ?: "음성 재생 실패" }
            }
        }.start()
    }

    if (uxRestrictions.restricted != false) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Roadio", style = MaterialTheme.typography.headlineLarge)
            Text(if (uxRestrictions.restricted == true) "주행 제한 중에는 화면을 조작할 수 없어요."
                else "차량의 화면 조작 가능 상태를 확인하고 있어요.")
        }
        return
    }

    val status = playback.status
    val canOffer = !busy && status !in setOf(RoadioPlaybackClient.Status.CONNECTING,
        RoadioPlaybackClient.Status.PLAYING, RoadioPlaybackClient.Status.PAUSED, RoadioPlaybackClient.Status.BUFFERING)
    offer?.takeIf { autoDiscovery && canOffer }?.let { story ->
        LaunchedEffect(story.id) {
            VehicleDiscoveryChime.play(story.audioType)
        }
        fun skip() {
            offers.answer(story)
            offer = null
        }
        VehicleStoryDiscoveredScreen(
            title = story.title,
            audioTypeLabel = story.audioType.label,
            onListen = {
                if (uxRestrictions.restricted == false && story.expiresAt.isAfter(Instant.now())) {
                    skip()
                    selected = story
                    selectedFromOffer = true
                    play(story)
                }
            },
            onSkip = { if (uxRestrictions.restricted == false) skip() },
        )
        return
    }

    selected?.takeIf {
        selectedFromOffer && (
            busy || status in setOf(
                RoadioPlaybackClient.Status.BUFFERING,
                RoadioPlaybackClient.Status.PLAYING,
                RoadioPlaybackClient.Status.PAUSED,
                RoadioPlaybackClient.Status.STOPPED,
            )
        )
    }?.let { story ->
        var positionMs by remember(story.id) { mutableStateOf(0L) }
        LaunchedEffect(story.id, status) {
            while (isActive) {
                positionMs = playback.currentPositionMs().coerceAtMost(story.durationMs)
                delay(250)
            }
        }
        VehicleStoryPlayingScreen(
            title = story.title,
            positionMs = positionMs,
            durationMs = story.durationMs,
            showPlayIcon = status != RoadioPlaybackClient.Status.PLAYING,
            controlsEnabled = status == RoadioPlaybackClient.Status.PLAYING ||
                status == RoadioPlaybackClient.Status.PAUSED ||
                status == RoadioPlaybackClient.Status.STOPPED,
            onTogglePlayback = {
                if (status == RoadioPlaybackClient.Status.PLAYING) playback.pause()
                else if (status == RoadioPlaybackClient.Status.PAUSED) playback.resume()
                else if (status == RoadioPlaybackClient.Status.STOPPED) playback.playSelected(story.id)
            },
            onSeek = { requestedPositionMs ->
                if (status == RoadioPlaybackClient.Status.STOPPED) {
                    playback.playSelected(story.id, requestedPositionMs)
                } else {
                    playback.seekTo(requestedPositionMs)
                }
            },
        )
        return
    }

    if (filmingBuild && selected == null) {
        VehicleNearbySearchingScreen()
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Roadio", style = MaterialTheme.typography.headlineLarge)
        Text("차량 주변 이야기 · 서버 연결 시연", style = MaterialTheme.typography.titleMedium)
        Text("주변 이야기를 발견해도 듣기를 선택할 때만 재생합니다.")
        if (autoDiscovery && token.isNotBlank()) {
            Text(when {
                autoSearchFailed -> "자동 발견 연결을 확인하고 있어요."
                sdkLocation -> "Pleos 위치로 주변 이야기를 자동 확인하고 있어요."
                else -> "입력한 가상 위치로 주변 이야기를 자동 확인하고 있어요."
            })
        }
        HorizontalDivider()
        if (!filmingBuild) OutlinedCard(Modifier.fillMaxWidth()) {
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
                Text(if (filmingBuild) "가상 위치 시연 · 적용하면 자동으로 주변 이야기를 확인해요."
                    else "가상 위치 시연용 · 실제 연동과 구분")
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
                        Text("${capsule.audioType.label} · ${capsule.authorDisplayName} · ${capsule.distanceMeters}m · ${capsule.durationMs / 1000}초")
                        Text("콘텐츠 출처: ${if (capsule.contentOrigin == "MOBILE_RECORDING") "모바일 녹음" else "사전 준비한 시연용 음성"}")
                        OutlinedButton(onClick = { playback.stop(); selected = capsule; selectedFromOffer = false }) { Text("정보 보기") }
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
