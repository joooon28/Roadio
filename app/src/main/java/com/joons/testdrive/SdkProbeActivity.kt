package com.joons.testdrive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener

/** 읽기 전용 연결 진단. 경로 설정, 위치 조회, 음성 재생은 실행하지 않는다. */
class SdkProbeActivity : ComponentActivity() {
    private var lines by mutableStateOf("확인 중…")
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null

    private fun report(message: String) {
        Log.i("DriveFitSdkProbe", message)
        runOnUiThread { lines += "\n$message" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Column(Modifier.fillMaxSize().padding(24.dp)) { Text("SDK 연결 진단\n$lines") } }
        report("FusedLocation: Maven 의존성 1.1.2 해석 실패로 실행 미검증")
        try {
            tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)
            tts!!.initialize(object : OnInitializeListener {
                override fun onSuccess() { report("TextToSpeech ON_DEVICE 초기화 성공") }
                override fun onFailure(errorType: ErrorType) { report("TextToSpeech 초기화 실패: $errorType") }
            })
        } catch (e: Exception) { report("TextToSpeech 실패: ${e.javaClass.simpleName}: ${e.message}") }
        try {
            navi = NaviHelper(applicationContext)
            navi!!.initialize(
                onConnected = { report("NaviHelper 서비스 연결 성공") },
                onFailed = { error -> report("NaviHelper 연결 실패: ${error.javaClass.simpleName}: ${error.message}") },
            )
            report("NaviHelper initialize 호출 완료")
        } catch (e: Exception) { report("NaviHelper 실패: ${e.javaClass.simpleName}: ${e.message}") }
    }

    override fun onDestroy() {
        try { navi?.release() } catch (_: Exception) {}
        try { tts?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
