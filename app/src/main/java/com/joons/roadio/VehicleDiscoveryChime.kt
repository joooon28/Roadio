package com.joons.roadio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/** Short, low-volume discovery cues. They identify content type without imitating a vehicle warning. */
object VehicleDiscoveryChime {
    private const val SampleRate = 48_000
    private const val ChannelCount = 2
    private const val Amplitude = 0.18
    private val lock = Any()
    private var current: AudioTrack? = null

    fun play(type: SampleAudioType) {
        val samples = when (type) {
            SampleAudioType.VOICE_MESSAGE -> sequence(
                Note(523.25, 150), Note(0.0, 45), Note(659.25, 190),
            )
            SampleAudioType.LOCAL_DRIVING_TIP -> sequence(
                Note(698.46, 105), Note(0.0, 55), Note(554.37, 160),
            )
        }
        Thread {
            val minimumBufferBytes = AudioTrack.getMinBufferSize(
                SampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferBytes = maxOf(samples.size * Short.SIZE_BYTES, minimumBufferBytes.coerceAtLeast(0))
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
                bufferBytes,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w("RoadioChime", "AudioTrack initialization failed for ${type.name}; minBuffer=$minimumBufferBytes")
                track.release()
                return@Thread
            }
            synchronized(lock) {
                current?.runCatching { stop() }
                current?.release()
                current = track
            }
            track.play()
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            Log.i("RoadioChime", "Playing ${type.name}")
            Thread.sleep(samples.size / ChannelCount * 1_000L / SampleRate + 80L)
            synchronized(lock) {
                if (current === track) {
                    track.release()
                    current = null
                }
            }
        }.start()
    }

    private fun sequence(vararg notes: Note): ShortArray {
        val frameCount = notes.sumOf { it.durationMs * SampleRate / 1_000 }
        val output = ShortArray(frameCount * ChannelCount)
        var cursor = 0
        notes.forEach { note ->
            val count = note.durationMs * SampleRate / 1_000
            val fadeSamples = minOf(count / 2, SampleRate * 25 / 1_000)
            repeat(count) { index ->
                val envelope = when {
                    fadeSamples == 0 -> 1.0
                    index < fadeSamples -> index.toDouble() / fadeSamples
                    index >= count - fadeSamples -> (count - index - 1).coerceAtLeast(0).toDouble() / fadeSamples
                    else -> 1.0
                }
                val wave = if (note.frequencyHz == 0.0) 0.0 else sin(2.0 * PI * note.frequencyHz * index / SampleRate)
                val sample = (wave * envelope * Amplitude * Short.MAX_VALUE).toInt().toShort()
                val frame = (cursor + index) * ChannelCount
                output[frame] = sample
                output[frame + 1] = sample
            }
            cursor += count
        }
        return output
    }

    private data class Note(val frequencyHz: Double, val durationMs: Int)
}
