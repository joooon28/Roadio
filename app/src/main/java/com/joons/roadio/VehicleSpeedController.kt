package com.joons.roadio

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Reads Android Automotive vehicle speed. PERF_VEHICLE_SPEED is metres per second. */
internal class VehicleSpeedController(context: Context) : AutoCloseable {
    var speedKph: Double? by mutableStateOf(null)
        private set
    var status: String by mutableStateOf("차속 연결 중")
        private set

    private var manager: CarPropertyManager? = null
    private var closed = false
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            update(value.value as? Float)
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) status = "차속을 확인할 수 없음"
        }
    }

    private val car = try {
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
            if (closed) return@createCar
            if (!ready) {
                speedKph = null
                status = "차속 서비스 연결 대기"
                return@createCar
            }
            try {
                val service = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                manager = service
                if (service == null) {
                    status = "차속 서비스를 사용할 수 없음"
                    return@createCar
                }
                update(service.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0))
                service.registerCallback(
                    callback,
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_UI,
                )
            } catch (error: Exception) {
                speedKph = null
                status = "차속 권한 또는 지원 상태 확인 필요"
                Log.w("RoadioSpeed", "Vehicle speed unavailable", error)
            }
        }
    } catch (error: Exception) {
        status = "차속 서비스를 사용할 수 없음"
        Log.w("RoadioSpeed", "Car service unavailable", error)
        null
    } catch (error: LinkageError) {
        status = "차속 API를 사용할 수 없음"
        Log.w("RoadioSpeed", "Car property API unavailable", error)
        null
    }

    private fun update(metersPerSecond: Float?) {
        val value = metersPerSecond?.takeIf { it.isFinite() } ?: return
        speedKph = abs(value.toDouble()) * 3.6
        status = "차속 ${speedKph!!.toInt()}km/h"
        Log.i("RoadioSpeed", "speedKph=${"%.1f".format(speedKph)}")
    }

    fun discoveryBand(): Int = when (val speed = speedKph) {
        null -> 1
        in 0.0..<20.0 -> 0
        in 20.0..<60.0 -> 1
        else -> 2
    }

    override fun close() {
        closed = true
        try {
            manager?.unregisterCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED)
        } catch (_: Exception) {
        } finally {
            car?.disconnect()
        }
    }
}
