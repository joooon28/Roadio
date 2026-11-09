package com.joons.roadio

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** AVD SDK locations and typed demo coordinates are labelled separately in the UI. */
class VehicleDiscoveryController(private val activity: ComponentActivity) {
    private val permission = "pleos.car.permission.FUSED_LOCATION"
    private var fused: FusedLocation? = null
    private var initialized = false
    private var registered = false
    var position by mutableStateOf<SamplePosition?>(null)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf("위치를 확인해 주세요.")
        private set
    val sourceLabel: String get() = when (source) {
        "PLEOS_DEVICE" -> "Pleos 차량 위치"
        "PLEOS_EMULATOR" -> "Pleos 에뮬레이터 위치 · 시연 환경"
        "DEMO_MANUAL" -> "직접 입력한 가상 위치 · 시연 전용"
        else -> "위치 미확인"
    }

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            activity.runOnUiThread {
                position = SamplePosition(location.latitude, location.longitude)
                source = if (isEmulator()) "PLEOS_EMULATOR" else "PLEOS_DEVICE"
                message = "위치를 확인했습니다. 주변 이야기를 검색해 주세요."
            }
        }
        override fun onError(exception: Exception) {
            activity.runOnUiThread { message = "위치 오류: ${exception.message ?: exception.javaClass.simpleName}" }
        }
    }

    fun requestLocation() {
        try {
            if (fused == null) fused = FusedLocation(activity.applicationContext)
            if (fused?.isSupported() != true) {
                message = "이 환경에서는 Pleos 위치 SDK를 사용할 수 없습니다."
                return
            }
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                message = "위치 권한을 요청합니다."
                activity.requestPermissions(arrayOf(permission), 211)
                return
            }
            if (!initialized) { fused!!.initialize(); initialized = true }
            message = "Pleos 위치를 확인하는 중입니다."
            fused!!.getLastKnownLocation(listener)
            if (!registered) { fused!!.registerFusedLocationCallback(listener); registered = true }
        } catch (error: Exception) {
            message = "위치를 사용할 수 없습니다: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun onPermissionResult(requestCode: Int, results: IntArray) {
        if (requestCode != 211) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestLocation()
        else message = "위치 권한이 거부됐습니다. 설정에서 권한을 확인해 주세요."
    }

    fun setDemoPosition(latitude: String, longitude: String) {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            message = "유효한 위도·경도를 입력해 주세요."
            return
        }
        if (registered) try { fused?.unregisterFusedLocationCallback(listener) } catch (_: Exception) {}
        registered = false
        position = SamplePosition(lat, lon)
        source = "DEMO_MANUAL"
        message = "가상 위치가 설정됐습니다. 실제 차량 위치가 아닙니다."
    }

    fun release() {
        if (registered) try { fused?.unregisterFusedLocationCallback(listener) } catch (_: Exception) {}
        registered = false
    }

    private fun isEmulator() = Build.FINGERPRINT.contains("generic", true) ||
        Build.PRODUCT.contains("sdk", true) || Build.MODEL.contains("emulator", true)
}
