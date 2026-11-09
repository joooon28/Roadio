package com.joons.roadio

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** DEV-11 local development API. Never put a bearer token in an URL or source file. */
class VehicleCapsuleApi(private val context: Context) {
    fun search(baseUrl: String, token: String, position: SamplePosition, source: String): List<SampleCapsuleSummary> {
        val body = JSONObject().put("position", JSONObject().put("latitude", position.latitude).put("longitude", position.longitude))
            .put("locationSource", source).put("limit", 20).toString()
        val connection = open(baseUrl, token, "/v1/capsules/nearby/search", "POST")
        return try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checkResponse(connection)
            val array = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("capsules")
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SampleCapsuleSummary(
                    id = item.getString("id"), title = item.getString("title"),
                    authorDisplayName = item.getString("authorDisplayName"), durationMs = item.getLong("durationMs"),
                    visibility = SampleVisibility.valueOf(item.getString("visibility")),
                    expiresAt = Instant.parse(item.getString("expiresAt")),
                    distanceMeters = item.getInt("distanceMeters"), contentOrigin = item.getString("contentOrigin"),
                )
            }
        } finally { connection.disconnect() }
    }

    /** The server rechecks access on this request. Playback only begins after a user tap and successful download. */
    fun fetchSelectedAudio(baseUrl: String, token: String, capsuleId: String): Uri {
        require(Regex("[0-9a-f-]{36}").matches(capsuleId))
        val connection = open(baseUrl, token, "/v1/capsules/$capsuleId/audio", "GET")
        return try {
            checkResponse(connection)
            val type = connection.contentType.substringBefore(';').lowercase()
            val extension = when (type) {
                "audio/wav", "audio/x-wav" -> ".wav"
                "audio/mpeg" -> ".mp3"
                "audio/mp4" -> ".m4a"
                "audio/aac" -> ".aac"
                "audio/webm" -> ".webm"
                "audio/ogg" -> ".ogg"
                else -> throw IllegalStateException("지원하지 않는 음성 형식입니다.")
            }
            val file = File.createTempFile("roadio-selected-", extension, context.cacheDir)
            try {
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                if (file.length() == 0L || file.length() > 10L * 1024 * 1024) throw IllegalStateException("음성 파일 크기를 확인해 주세요.")
                VehicleSelectedAudio.register(capsuleId, file)
                Uri.fromFile(file)
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        } finally { connection.disconnect() }
    }

    private fun open(baseUrl: String, token: String, path: String, method: String): HttpURLConnection {
        require(token.isNotBlank()) { "차량 시연 계정 토큰을 입력해 주세요." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https" || base.host in setOf("127.0.0.1", "localhost", "10.0.2.2")) {
            "원격 서버는 HTTPS 주소를 사용해 주세요."
        }
        return (URL(base, path.removePrefix("/")).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json, audio/*")
            instanceFollowRedirects = false
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) {
            val message = when (connection.responseCode) {
                401 -> "인증에 실패했습니다. 차량 시연 계정 토큰을 확인해 주세요."
                404 -> "이 이야기는 삭제·만료됐거나 접근할 수 없습니다. 목록을 새로고침해 주세요."
                else -> "서버 요청에 실패했습니다 (${connection.responseCode})."
            }
            throw IllegalStateException(message)
        }
    }
}

/** Temporary local files stay outside the media browser catalogue and are removed when replaced. */
object VehicleSelectedAudio {
    private val files = mutableMapOf<String, File>()
    @Synchronized fun register(id: String, file: File) {
        files.values.forEach { it.delete() }
        files.clear()
        files[id] = file
    }
    @Synchronized fun uri(id: String): Uri? = files[id]?.takeIf { it.exists() }?.let(Uri::fromFile)
    @Synchronized fun clear() {
        files.values.forEach { it.delete() }
        files.clear()
    }
}
