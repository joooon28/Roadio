package com.joons.roadio.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class MobileActivity : ComponentActivity() {
    private enum class Stage { EMPTY, RECORDING, PREVIEW, PLAYING }
    private var stage by mutableStateOf(Stage.EMPTY)
    private var message by mutableStateOf<String?>(null)
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var draft: File? = null
    private var startedAt = 0L

    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message = "마이크 권한이 없어 녹음할 수 없습니다. 설정에서 권한을 허용하거나 다시 시도해 주세요."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Roadio 모바일", style = MaterialTheme.typography.headlineMedium)
                    Text("음성 캡슐 만들기", style = MaterialTheme.typography.titleLarge)
                    Text("1 녹음 · 미리 듣기   →   2 위치 · 공개 설정   →   3 등록")
                    HorizontalDivider()
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("01 · 목소리 녹음", style = MaterialTheme.typography.titleMedium)
                            Text("휴대폰 안에만 임시 저장됩니다. 서버나 차량에는 아직 전송되지 않습니다.")
                            HorizontalDivider()
                            Text(when (stage) {
                                Stage.EMPTY -> "녹음 전"
                                Stage.RECORDING -> "녹음 중"
                                Stage.PREVIEW -> "미리 듣기 준비"
                                Stage.PLAYING -> "미리 듣는 중"
                            }, style = MaterialTheme.typography.titleLarge)
                            message?.let { Text(it) }
                            when (stage) {
                                Stage.EMPTY -> Button(onClick = ::checkPermissionAndRecord) { Text("녹음 시작") }
                                Stage.RECORDING -> {
                                    Button(onClick = ::finishRecording) { Text("녹음 종료") }
                                    OutlinedButton(onClick = ::discardDraft) { Text("취소") }
                                }
                                Stage.PREVIEW -> {
                                    Button(onClick = ::playPreview) { Text("미리 듣기") }
                                    OutlinedButton(onClick = ::checkPermissionAndRecord) { Text("다시 녹음") }
                                    OutlinedButton(onClick = ::discardDraft) { Text("녹음 삭제") }
                                }
                                Stage.PLAYING -> {
                                    Button(onClick = ::stopPreview) { Text("재생 중지") }
                                    OutlinedButton(onClick = ::discardDraft) { Text("녹음 삭제") }
                                }
                            }
                        }
                    }
                    Text("현재는 녹음과 미리 듣기만 체험할 수 있습니다. 위치·공개 설정과 등록은 준비 중입니다.")
                }
            }
        }
    }

    private fun checkPermissionAndRecord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        stopPreview()
        draft?.delete()
        draft = File(cacheDir, "roadio-draft-${System.currentTimeMillis()}.m4a")
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(draft!!.absolutePath)
                prepare()
                start()
            }
            startedAt = System.currentTimeMillis()
            stage = Stage.RECORDING
            message = null
        } catch (error: Exception) {
            recorder?.release()
            recorder = null
            draft?.delete()
            draft = null
            stage = Stage.EMPTY
            message = "녹음을 시작할 수 없습니다. 마이크 상태를 확인하고 다시 시도해 주세요."
        }
    }

    private fun finishRecording() {
        if (System.currentTimeMillis() - startedAt < 1000L) {
            message = "녹음이 너무 짧습니다. 1초 이상 녹음해 주세요."
            return
        }
        try {
            recorder?.stop()
            stage = Stage.PREVIEW
            message = "녹음을 마쳤습니다. 미리 들어보거나 다시 녹음할 수 있습니다."
        } catch (error: RuntimeException) {
            draft?.delete()
            draft = null
            stage = Stage.EMPTY
            message = "녹음 파일을 만들지 못했습니다. 다시 시도해 주세요."
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    private fun playPreview() {
        val file = draft?.takeIf { it.exists() && it.length() > 0L } ?: run {
            message = "녹음 파일을 찾을 수 없습니다. 다시 녹음해 주세요."
            stage = Stage.EMPTY
            return
        }
        stopPreview()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
            stage = Stage.PLAYING
            message = null
        } catch (error: Exception) {
            player?.release()
            player = null
            stage = Stage.PREVIEW
            message = "음성을 재생할 수 없습니다. 다시 시도해 주세요."
        }
    }

    private fun stopPreview() {
        player?.release()
        player = null
        if (stage == Stage.PLAYING) stage = Stage.PREVIEW
    }

    private fun discardDraft() {
        stopPreview()
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        draft?.delete()
        draft = null
        stage = Stage.EMPTY
        message = "임시 녹음을 삭제했습니다."
    }

    override fun onDestroy() {
        stopPreview()
        recorder?.let { runCatching { it.stop() }; it.release() }
        draft?.delete()
        super.onDestroy()
    }
}
