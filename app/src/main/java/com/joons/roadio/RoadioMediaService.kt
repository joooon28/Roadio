package com.joons.roadio

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** DEV-02 sample library; replace with authorized capsule results in later stages. */
@androidx.annotation.OptIn(UnstableApi::class)
class RoadioMediaService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val sampleId = "dev02-sample"
    private val rootId = "roadio-root"

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder().setMediaId(rootId).setMediaMetadata(
                        MediaMetadata.Builder().setTitle("Roadio 샘플").setIsBrowsable(true).setIsPlayable(false).build()
                    ).build(), params
                )
            )

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val children = if (parentId == rootId && page == 0) ImmutableList.of(sample()) else ImmutableList.of()
                return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                mediaItems.map { if (it.mediaId == sampleId) sample() else it }
            )
        }).build()
        Log.i("RoadioDev02", "Media3 MediaLibraryService 시작")
    }

    private fun sample() = MediaItem.Builder()
        .setMediaId(sampleId)
        .setUri(Uri.parse("android.resource://$packageName/${R.raw.dev02_sample_voice}"))
        .setMediaMetadata(MediaMetadata.Builder().setTitle("시연용 합성 음성").setIsBrowsable(false).setIsPlayable(true).build())
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
