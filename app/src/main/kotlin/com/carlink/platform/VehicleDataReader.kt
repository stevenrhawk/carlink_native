package com.carlink.platform

import androidx.car.app.hardware.common.CarValue

data class PropertyResult(
    val name: String,
    val status: Status,
    val value: String? = null,
) {
    enum class Status {
        OK,
        UNAVAILABLE,
        UNIMPLEMENTED,
        ERROR,
        PENDING,
    }

    val displayValue: String
        get() = when (status) {
            Status.OK -> value ?: "null"
            Status.UNAVAILABLE -> "Not available"
            Status.UNIMPLEMENTED -> "Not supported"
            Status.ERROR -> "Error"
            Status.PENDING -> "Waiting..."
        }
}

object VehicleDataFormatter {

    fun format(data: VehicleData): List<PropertyResult> = buildList {
        // Vehicle Info
        add(carValueResult("Make", data.manufacturer))
        add(carValueResult("Model", data.modelName))
        add(carValueResult("Model Year", data.modelYear) { it.toString() })
        add(carValueResult("Fuel Types", data.fuelTypes) { types ->
            types.joinToString { fuelTypeName(it) }
        })
        add(carValueResult("EV Connector Types", data.evConnectorTypes) { types ->
            if (types.isEmpty()) "None" else types.joinToString { evConnectorName(it) }
        })

        // Speed
        add(carValueResult("Vehicle Speed", data.displaySpeed) { speed ->
            val mph = speed * 2.23694f
            val kmh = speed * 3.6f
            "%.1f m/s (%.0f mph / %.0f km/h)".format(speed, mph, kmh)
        })

        // Mileage
        add(carValueResult("Odometer", data.odometerMeters) { meters ->
            val km = meters / 1000f
            val mi = meters * 0.000621371f
            "%.0f km (%.0f mi)".format(km, mi)
        })

        // Energy
        add(carValueResult("Fuel Level", data.fuelPercent) { "%.1f%%".format(it) })
        add(carValueResult("Battery Level", data.batteryPercent) { "%.1f%%".format(it) })
        add(carValueResult("Range Remaining", data.rangeRemainingMeters) { meters ->
            val km = meters / 1000f
            val mi = meters * 0.000621371f
            "%.1f km (%.1f mi)".format(km, mi)
        })
        add(carValueResult("Low Energy", data.energyIsLow) { if (it) "Yes" else "No" })

        // EV Status
        add(carValueResult("EV Charge Status", data.evChargeStatus) { chargeStatusName(it) })

        // Toll
        add(carValueResult("Toll Card", data.tollCardStatus) { tollCardName(it) })
    }

    private fun <T> carValueResult(
        name: String,
        carValue: CarValue<T>?,
        formatter: ((T) -> String)? = null,
    ): PropertyResult {
        if (carValue == null) {
            return PropertyResult(name, PropertyResult.Status.PENDING)
        }
        return when (carValue.status) {
            CarValue.STATUS_SUCCESS -> {
                val v = carValue.value
                val formatted = if (v != null && formatter != null) {
                    formatter(v)
                } else {
                    v?.toString() ?: "null"
                }
                PropertyResult(name, PropertyResult.Status.OK, formatted)
            }
            CarValue.STATUS_UNIMPLEMENTED -> PropertyResult(name, PropertyResult.Status.UNIMPLEMENTED)
            CarValue.STATUS_UNAVAILABLE -> PropertyResult(name, PropertyResult.Status.UNAVAILABLE)
            else -> PropertyResult(name, PropertyResult.Status.ERROR)
        }
    }

    private fun fuelTypeName(type: Int): String = when (type) {
        1 -> "Unleaded"
        2 -> "Leaded"
        3 -> "Diesel (1)"
        4 -> "Diesel (2)"
        5 -> "Biodiesel"
        6 -> "E85"
        7 -> "LPG"
        8 -> "CNG"
        9 -> "LNG"
        10 -> "Electric"
        11 -> "Hydrogen"
        12 -> "Other"
        else -> "Unknown($type)"
    }

    private fun evConnectorName(type: Int): String = when (type) {
        1 -> "J1772"
        2 -> "Mennekes (Type 2)"
        3 -> "CHAdeMO"
        4 -> "CCS Combo 1"
        5 -> "CCS Combo 2"
        6 -> "Tesla Roadster"
        7 -> "Tesla HPWC"
        8 -> "Tesla Supercharger"
        9 -> "GBT"
        10 -> "GBT DC"
        11 -> "NACS"
        101 -> "Other"
        else -> "Unknown($type)"
    }

    private fun chargeStatusName(status: Int): String = when (status) {
        1 -> "Charging"
        2 -> "Fully Charged"
        3 -> "Not Charging"
        4 -> "Error"
        else -> "Unknown($status)"
    }

    private fun tollCardName(status: Int): String = when (status) {
        1 -> "Valid"
        2 -> "Not Inserted"
        3 -> "Invalid"
        else -> "Unknown($status)"
    }
}
