package com.joons.roadio

import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Media library with offline samples and explicitly selected server-authorized audio. */
@androidx.annotation.OptIn(UnstableApi::class)
class RoadioMediaService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val dev02SampleId = "dev02-sample"
    private val rootId = "roadio-root"

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.i("RoadioDev06", "media3State=$playbackState")
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.i("RoadioDev06", "isPlaying=$isPlaying")
                }
            })
        }
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder().setMediaId(rootId).setMediaMetadata(
                        MediaMetadata.Builder().setTitle("Roadio 시연용 샘플").setIsBrowsable(true).setIsPlayable(false).build()
                    ).build(), params
                )
            )

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val children = if (parentId == rootId && page == 0) ImmutableList.of(dev02Sample()) else ImmutableList.of()
                return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                mediaItems.mapNotNull { requested ->
                    when (requested.mediaId) {
                        Dev05SampleData.capsuleId -> capsuleSample()
                        dev02SampleId -> dev02Sample()
                        else -> VehicleSelectedAudio.uri(requested.mediaId)?.let { audioUri ->
                            MediaItem.Builder().setMediaId(requested.mediaId).setUri(audioUri)
                                .setMediaMetadata(MediaMetadata.Builder().setTitle("Roadio 선택 재생")
                                    .setIsBrowsable(false).setIsPlayable(true).build()).build()
                        }
                    }
                }
            )
        }).build()
        Log.i("RoadioDev06", "샘플 MediaLibraryService 시작")
    }

    private fun capsuleSample() = MediaItem.Builder()
        .setMediaId(Dev05SampleData.capsuleId)
        .setUri(Uri.parse("android.resource://$packageName/${R.raw.dev06_capsule_sample_voice}"))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle("퇴근길 인사 · 사전 준비 샘플")
                .setIsBrowsable(false).setIsPlayable(true).build()
        )
        .build()

    private fun dev02Sample() = MediaItem.Builder()
        .setMediaId(dev02SampleId)
        .setUri(Uri.parse("android.resource://$packageName/${R.raw.dev02_sample_voice}"))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle("시연용 합성 음성")
                .setIsBrowsable(false).setIsPlayable(true).build()
        )
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
