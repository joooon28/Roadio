package com.joons.roadio

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class VehicleUxRestrictions(context: Context) : AutoCloseable {
    var restricted: Boolean? by mutableStateOf(null)
        private set
    private var manager: CarUxRestrictionsManager? = null
    private var closed = false

    private val car = try {
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
            if (!closed) {
                restricted = null
                manager = null
                if (ready) {
                    try {
                        val service = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                            as? CarUxRestrictionsManager
                        if (service != null) {
                            manager = service
                            service.registerListener { state ->
                                if (!closed && manager === service) {
                                    restricted = state?.isRequiresDistractionOptimization
                                }
                            }
                            restricted = service.currentCarUxRestrictions?.isRequiresDistractionOptimization
                        }
                    } catch (_: Exception) {
                        restricted = null
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }

    override fun close() {
        closed = true
        try {
            manager?.unregisterListener()
        } finally {
            car?.disconnect()
        }
    }
}
