package com.joons.roadio

import ai.pleos.playground.location.fused.FusedLocation
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.pm.PackageManager
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joons.roadio.ui.theme.RoadioTheme

/** 기술 확인용 화면. 위치 결과와 재생 콜백을 기록하며 캡슐 검색은 수행하지 않는다. */
class Dev02ProbeActivity : ComponentActivity() {
    private val locationPermission = "pleos.car.permission.FUSED_LOCATION"
    private var reportText by mutableStateOf("DEV-02 진단 시작")
    private var uxRestricted by mutableStateOf(false)
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var fused: FusedLocation? = null
    private var locationRegistered = false
    private var locationInitialized = false
    private var proximityRequestPending = false
    private var proximityAlertId: String? = null
    private var lastCoordinates: String? = null
    private var player: MediaPlayer? = null
    private var mediaBrowser: MediaBrowser? = null
    private var systemController: MediaController? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val coordinates = if (location == null) "null" else "lat=${location.latitude}, lon=${location.longitude}"
            if (coordinates != lastCoordinates) { lastCoordinates = coordinates; report("위치 콜백: $coordinates") }
        }
        override fun onError(exception: Exception) {
            report("위치 오류: ${exception.javaClass.simpleName}: ${exception.message}")
        }
    }

    private val proximityListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) { proximityRequestPending = false; proximityAlertId = alertId; report("반경 알림 등록: $alertId") }
        override fun onTransition(event: ProximityAlertEvent) { report("반경 알림 전환: ${event.transition}") }
        override fun onError(exception: Exception) { proximityRequestPending = false; report("반경 알림 오류: ${exception.javaClass.simpleName}: ${exception.message}") }
    }

    private fun report(message: String) {
        Log.i("RoadioDev02", message)
        runOnUiThread { reportText += "\n$message" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        setContent {
            RoadioTheme {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Roadio DEV-02 위치·오디오 검증")
                    Text(if (uxRestricted) "주행 규제 활성화: 진단 조작 제한" else "주행 규제 비활성화: 정차 진단")
                    if (!uxRestricted) {
                    Button(onClick = { probeLocation() }) { Text("위치 SDK 확인") }
                    Button(onClick = { registerTestProximity() }) { Text("강남역 500m 반경 알림 등록") }
                    Button(onClick = { playFromMediaLibrary() }) { Text("차량 미디어 서비스로 샘플 선택 재생") }
                    Button(onClick = { systemController?.transportControls?.pause(); report("미디어 세션 일시정지 요청") }) { Text("미디어 세션 일시정지") }
                    Button(onClick = { systemController?.transportControls?.stop(); report("미디어 세션 종료 요청") }) { Text("미디어 세션 종료") }
                    Button(onClick = { playSample() }) { Text("샘플 음성 선택 재생") }
                    Button(onClick = { pauseSample() }) { Text("일시정지") }
                    Button(onClick = { stopSample() }) { Text("종료") }
                    }
                    Text(reportText)
                }
            }
        }
        probeLocation()
        connectMediaBrowser()
        observeUxRestrictions()
    }

    private fun observeUxRestrictions() {
        try {
            car = Car.createCar(this)
            uxManager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            val manager = uxManager ?: run { report("CarUxRestrictionsManager 사용 불가"); return }
            updateUxRestrictions(manager.currentCarUxRestrictions)
            manager.registerListener { restrictions -> updateUxRestrictions(restrictions) }
            report("CarUxRestrictions 리스너 등록")
        } catch (error: Exception) { report("CarUxRestrictions 오류: ${error.javaClass.simpleName}: ${error.message}") }
    }

    private fun updateUxRestrictions(restrictions: CarUxRestrictions?) {
        if (restrictions == null) { report("CarUxRestrictions 상태 없음"); return }
        val active = restrictions.isRequiresDistractionOptimization
        uxRestricted = active
        report("CarUxRestrictions: requiresDO=$active, active=${restrictions.activeRestrictions}")
    }

    private fun connectMediaBrowser() {
        mediaBrowser = MediaBrowser(this, ComponentName(this, RoadioMediaService::class.java), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val browser = mediaBrowser ?: return
                systemController = MediaController(this@Dev02ProbeActivity, browser.sessionToken)
                report("플랫폼 MediaBrowser 연결; root=${browser.root}")
                browser.subscribe(browser.root, object : MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                        report("미디어 라이브러리 항목 ${children.size}개: ${children.joinToString { it.mediaId ?: "unknown" }}")
                    }
                })
            }
            override fun onConnectionFailed() { report("플랫폼 MediaBrowser 연결 실패") }
        }, null)
        mediaBrowser?.connect()
    }

    private fun playFromMediaLibrary() {
        val controller = systemController
        if (controller == null) { report("미디어 서비스 연결 전"); return }
        controller.transportControls.playFromMediaId("dev02-sample", null)
        report("사용자 선택: 미디어 세션 playFromMediaId 요청")
    }

    private fun probeLocation() {
        try {
            if (fused == null) fused = FusedLocation(applicationContext)
            val supported = fused!!.isSupported()
            report("FusedLocation 1.1.3 isSupported=$supported")
            if (!supported) return
            if (checkSelfPermission(locationPermission) != PackageManager.PERMISSION_GRANTED) {
                report("FUSED_LOCATION 권한 요청")
                requestPermissions(arrayOf(locationPermission), 201)
                return
            }
            if (!locationInitialized) {
                fused!!.initialize()
                locationInitialized = true
                report("FusedLocation initialize 호출 완료; 권한=허용")
            }
            fused!!.getLastKnownLocation(locationListener)
            if (!locationRegistered) {
                fused!!.registerFusedLocationCallback(locationListener)
                locationRegistered = true
                report("위치 업데이트 등록 완료")
            }
        } catch (error: Exception) { report("위치 SDK 예외: ${error.javaClass.simpleName}: ${error.message}") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            report("FUSED_LOCATION 권한 결과=" + if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "허용" else "거부")
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) probeLocation()
        }
    }

    private fun registerTestProximity() {
        if (proximityAlertId != null || proximityRequestPending) return
        if (!locationInitialized) { report("위치 SDK 초기화 후 반경 알림을 등록할 수 있습니다"); return }
        try {
            proximityRequestPending = true
            val request = ProximityAlertRequest(37.4979, 127.0276, 500f, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
            fused?.addProximityAlert(request, proximityListener)
            report("강남역 500m 반경 알림 요청")
        } catch (error: Exception) { proximityRequestPending = false; report("반경 알림 예외: ${error.javaClass.simpleName}: ${error.message}") }
    }

    private fun playSample() {
        if (player?.isPlaying == true) return
        try {
            if (focusRequest == null) {
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { change ->
                        report("오디오 포커스 변경=$change")
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pauseSample()
                    }.build()
            }
            val focus = audioManager.requestAudioFocus(focusRequest!!)
            report("오디오 포커스 요청 결과=$focus")
            if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            if (player == null) {
                player = MediaPlayer.create(this, R.raw.dev02_sample_voice)
                player?.setOnCompletionListener { report("샘플 음성 재생 완료"); stopSample() }
            }
            player?.start()
            report("사용자 선택으로 샘플 음성 재생 시작")
        } catch (error: Exception) { report("재생 예외: ${error.javaClass.simpleName}: ${error.message}") }
    }

    private fun pauseSample() {
        if (player?.isPlaying == true) { player?.pause(); report("샘플 음성 일시정지") }
    }

    private fun stopSample() {
        player?.let { if (it.isPlaying) it.stop(); it.release() }
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        report("샘플 음성 종료·포커스 해제")
    }

    override fun onDestroy() {
        proximityAlertId?.let { try { fused?.removeProximityAlert(it) } catch (_: Exception) {} }
        if (locationRegistered) try { fused?.unregisterFusedLocationCallback(locationListener) } catch (_: Exception) {}
        try { fused?.release() } catch (_: Exception) {}
        try { uxManager?.unregisterListener() } catch (_: Exception) {}
        car?.disconnect()
        systemController = null
        mediaBrowser?.disconnect()
        mediaBrowser = null
        stopSample()
        super.onDestroy()
    }
}
