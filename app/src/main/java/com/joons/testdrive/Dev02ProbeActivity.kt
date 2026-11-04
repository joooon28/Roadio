package com.joons.testdrive

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import com.joons.testdrive.ui.theme.TestDriveTheme
import java.util.Locale

/** Temporary, user-operated probes for DEV-02. A callback result is shown without inferring real-car support. */
class Dev02ProbeActivity : ComponentActivity() {
    private var doorStatus by mutableStateOf("아직 조회하지 않음")
    private var speechStatus by mutableStateOf("아직 시험하지 않음")
    private var vehicle: Vehicle? = null
    private var speechToText: SpeechToText? = null
    private var speechReady = false

    private val speechListener = object : ResultListener {
        override fun onReady() = updateSpeech("음성 서비스 준비 완료")
        override fun onStartedRecognition() = updateSpeech("음성 인식 중")
        override fun onUpdated(stt: String, completed: Boolean) =
            updateSpeech("${if (completed) "최종" else "중간"} 결과: $stt")
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { Log.i("DriveFitDev02", "음성 인식 종료") }
        override fun onError(errorType: ErrorType) = updateSpeech("오류: $errorType")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestDriveTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("DEV-02 Pleos 검증", style = MaterialTheme.typography.headlineMedium)
                    Text("Pleos 에뮬레이터에서만 시험한 결과입니다.")
                    Button(onClick = ::readDoor) { Text("운전석 문 위치 조회") }
                    Text("차량: $doorStatus")
                    Button(onClick = ::startSpeech) { Text("한국어 음성 인식 시작") }
                    Button(onClick = { speechToText?.stop() }) { Text("음성 인식 중지") }
                    Button(onClick = ::sendSampleAudio) { Text("합성 한국어 샘플 인식") }
                    Text("음성: $speechStatus")
                }
            }
        }
        readDoor()
        val action = intent.getStringExtra("dev02_action")
        initializeSpeech(requestAfterInitialization = action == "mic", sampleAfterInitialization = action == "sample")
    }

    private fun readDoor() {
        doorStatus = "조회 중"
        try {
            val sdk = vehicle ?: Vehicle(applicationContext).also {
                it.initialize()
                vehicle = it
            }
            sdk.getDoor().getCurrentDoorPosition(
                area = DoorArea(DoorValue.ROW_1_LEFT),
                onComplete = { position: Int? ->
                    runOnUiThread {
                        doorStatus = "조회 성공: $position"
                        Log.i("DriveFitDev02", doorStatus)
                    }
                },
                onFailed = { error: Exception ->
                    runOnUiThread {
                        doorStatus = "조회 실패: ${error.message ?: error.javaClass.simpleName}"
                        Log.e("DriveFitDev02", doorStatus)
                    }
                }
            )
        } catch (error: Exception) {
            doorStatus = "호출 실패: ${error.message ?: error.javaClass.simpleName}"
            Log.e("DriveFitDev02", doorStatus)
        }
    }

    private fun startSpeech() {
        if (speechReady) {
            requestSpeech()
            return
        }
        initializeSpeech(requestAfterInitialization = true)
    }

    private fun sendSampleAudio() {
        if (speechReady) {
            requestSampleAudio()
            return
        }
        initializeSpeech(sampleAfterInitialization = true)
    }

    private fun initializeSpeech(
        requestAfterInitialization: Boolean = false,
        sampleAfterInitialization: Boolean = false
    ) {
        speechStatus = "SpeechToText 초기화 중"
        try {
            val sdk = speechToText ?: SpeechToText(applicationContext, Mode.ON_DEVICE).also {
                speechToText = it
            }
            sdk.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runOnUiThread {
                        speechReady = true
                        sdk.addListener(speechListener)
                        sdk.setLocale(Locale.KOREA)
                        updateSpeech("초기화 성공: ko-KR, ON_DEVICE")
                        if (requestAfterInitialization) requestSpeech()
                        if (sampleAfterInitialization) requestSampleAudio()
                    }
                }

                override fun onFailure(errorType: ErrorType) = updateSpeech("초기화 실패: $errorType")
            })
        } catch (error: Exception) {
            speechStatus = "호출 실패: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun requestSpeech() {
        speechStatus = "마이크 입력 대기 중"
        try {
            speechToText?.request()
            Log.i("DriveFitDev02", "마이크 인식 요청 전달")
            Handler(Looper.getMainLooper()).postDelayed({ speechToText?.stop() }, 5000L)
        } catch (error: Exception) {
            speechStatus = "요청 실패: ${error.message ?: error.javaClass.simpleName}"
            Log.e("DriveFitDev02", speechStatus)
        }
    }

    private fun requestSampleAudio() {
        try {
            val sample = assets.open("dev02_ko_test.pcm").use { it.readBytes() }
            speechStatus = "합성 한국어 샘플 전송 중"
            Log.i("DriveFitDev02", "합성 한국어 PCM 전송: ${sample.size} bytes")
            speechToText?.sendAudio(sample)
            speechToText?.completeAudioSend()
        } catch (error: Exception) {
            speechStatus = "샘플 전송 실패: ${error.message ?: error.javaClass.simpleName}"
            Log.e("DriveFitDev02", speechStatus)
        }
    }

    private fun updateSpeech(value: String) = runOnUiThread {
        speechStatus = value
        Log.i("DriveFitDev02", value)
    }

    override fun onDestroy() {
        speechToText?.let {
            if (speechReady) it.removeListener(speechListener)
            it.release()
        }
        vehicle?.release()
        super.onDestroy()
    }
}
