package com.joons.roadio

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI bridge to the Media3 service through the platform media session. */
class RoadioPlaybackClient(private val context: Context) {
    enum class Status { CONNECTING, READY, BUFFERING, PLAYING, PAUSED, STOPPED, ERROR }

    var status by mutableStateOf(Status.CONNECTING)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun currentPositionMs(): Long {
        val state = controller?.playbackState ?: return 0L
        val elapsed = if (state.state == PlaybackState.STATE_PLAYING) {
            SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        } else 0L
        return (state.position + elapsed * state.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    private var pendingPlayId: String? = null
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            status = when (state?.state) {
                PlaybackState.STATE_PLAYING -> Status.PLAYING
                PlaybackState.STATE_PAUSED -> Status.PAUSED
                PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> Status.BUFFERING
                PlaybackState.STATE_STOPPED -> Status.STOPPED
                PlaybackState.STATE_ERROR -> Status.ERROR
                else -> if (status == Status.STOPPED) Status.STOPPED else Status.READY
            }
            errorMessage = if (status == Status.ERROR) state?.errorMessage?.toString() ?: "음성을 재생할 수 없습니다." else null
            Log.i("RoadioDev06", "controllerState=${state?.state}, status=$status")
        }
        override fun onSessionDestroyed() {
            controller = null
            status = Status.ERROR
            errorMessage = "오디오 서비스 연결이 끊겼습니다. 다시 시도해 주세요."
        }
    }

    fun connect() {
        if (browser != null) return
        status = Status.CONNECTING
        browser = MediaBrowser(
            context,
            ComponentName(context, RoadioMediaService::class.java),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    val connected = browser ?: return
                    controller = MediaController(context, connected.sessionToken).apply {
                        registerCallback(callback)
                    }
                    status = Status.READY
                    errorMessage = null
                    callback.onPlaybackStateChanged(controller?.playbackState)
                    Log.i("RoadioDev06", "미디어 서비스 연결")
                    pendingPlayId?.let { id -> pendingPlayId = null; playSelected(id) }
                }
                override fun onConnectionSuspended() {
                    controller?.unregisterCallback(callback)
                    controller = null
                    status = Status.ERROR
                    errorMessage = "오디오 서비스 연결이 끊겼습니다."
                }
                override fun onConnectionFailed() {
                    browser = null
                    status = Status.ERROR
                    errorMessage = "오디오 서비스에 연결할 수 없습니다."
                }
            },
            null,
        ).also { it.connect() }
    }

    fun playSelected(capsuleId: String) {
        val connected = controller ?: run {
            status = Status.ERROR
            errorMessage = "오디오 서비스 연결 전입니다. 다시 시도해 주세요."
            return
        }
        if (capsuleId != Dev05SampleData.capsuleId && VehicleSelectedAudio.uri(capsuleId) == null) {
            status = Status.ERROR
            errorMessage = "재생할 수 없는 이야기입니다."
            return
        }
        status = Status.BUFFERING
        errorMessage = null
        connected.transportControls.playFromMediaId(capsuleId, null)
        Log.i("RoadioDev06", "사용자 선택 재생 요청: $capsuleId")
    }

    fun retry(capsuleId: String) {
        if (controller != null) playSelected(capsuleId)
        else {
            pendingPlayId = capsuleId
            release()
            connect()
        }
    }

    fun pause() { controller?.transportControls?.pause() }
    fun resume() { controller?.transportControls?.play() }
    fun stop() {
        pendingPlayId = null
        if (status == Status.PLAYING || status == Status.PAUSED || status == Status.BUFFERING) {
            controller?.transportControls?.stop()
            status = Status.STOPPED
        }
    }

    fun release() {
        controller?.unregisterCallback(callback)
        controller = null
        browser?.disconnect()
        browser = null
    }
}
