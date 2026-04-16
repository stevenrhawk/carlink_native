package com.carlink.platform

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.carlink.BuildConfig

private const val TAG = "CARLINK_VEHICLE"

/**
 * VehicleDataReader - Reads vehicle properties from AAOS CarPropertyManager.
 *
 * PURPOSE:
 * Diagnostic utility that probes which vehicle properties are readable on GM AAOS.
 * This helps determine what data could potentially be forwarded to CarPlay via the
 * Carlinkit dongle (e.g., vehicle speed for Maps, fuel level for dashboard).
 *
 * GM AAOS ACCESS:
 * CarPropertyManager is a system API. Third-party apps may have limited access
 * depending on which permissions GM grants. This reader attempts to read each
 * property and reports the result (value, permission denied, or unavailable).
 *
 * PROPERTY CATEGORIES (from VehiclePropertyIds):
 * - Info: Make, model, year, VIN
 * - Powertrain: Speed, gear, RPM, fuel level, EV battery
 * - Environment: Outside temperature, cabin temperature
 * - HVAC: Fan speed, temperature setting
 * - Sensors: Tire pressure, odometer
 *
 * THREAD SAFETY:
 * All reads are synchronous. Call readAll() from a background thread or coroutine.
 * The Car connection is created and destroyed per read session.
 */
class VehicleDataReader(private val context: Context) {

    /**
     * Result of attempting to read a single vehicle property.
     */
    data class PropertyResult(
        val name: String,
        val propertyId: Int,
        val status: Status,
        val value: String? = null,
        val unit: String? = null,
        val error: String? = null,
    ) {
        enum class Status {
            OK,              // Successfully read
            PERMISSION_DENIED, // SecurityException — need system permission
            UNAVAILABLE,     // Property not supported on this vehicle
            ERROR,           // Other error
            CAR_NOT_CONNECTED, // Car service not available
        }

        val displayValue: String
            get() = when (status) {
                Status.OK -> if (unit != null) "$value $unit" else value ?: "null"
                Status.PERMISSION_DENIED -> "Permission denied"
                Status.UNAVAILABLE -> "Not available"
                Status.ERROR -> "Error: ${error ?: "unknown"}"
                Status.CAR_NOT_CONNECTED -> "Car service unavailable"
            }
    }

    /**
     * Definition of a vehicle property to probe.
     */
    private data class PropertyDef(
        val name: String,
        val id: Int,
        val unit: String? = null,
        val areaId: Int = 0, // VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
        val formatter: ((Any?) -> String)? = null,
    )

    /**
     * Vehicle properties to probe, organized by category.
     * These cover the most useful data points for CarPlay integration.
     */
    private val properties = listOf(
        // Vehicle Info
        PropertyDef("Make", VehiclePropertyIds.INFO_MAKE),
        PropertyDef("Model", VehiclePropertyIds.INFO_MODEL),
        PropertyDef("Model Year", VehiclePropertyIds.INFO_MODEL_YEAR),
        PropertyDef("VIN", VehiclePropertyIds.INFO_VIN),
        PropertyDef("Fuel Type", VehiclePropertyIds.INFO_FUEL_TYPE) { v ->
            when (v) {
                is IntArray -> v.joinToString { fuelTypeName(it) }
                is Int -> fuelTypeName(v)
                else -> v.toString()
            }
        },
        PropertyDef("EV Connector Types", VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE) { v ->
            when (v) {
                is IntArray -> v.joinToString()
                else -> v.toString()
            }
        },

        // Powertrain / Dynamics
        PropertyDef("Vehicle Speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, "m/s") { v ->
            val ms = (v as? Float) ?: return@PropertyDef v.toString()
            val mph = ms * 2.23694f
            val kmh = ms * 3.6f
            "%.1f m/s (%.0f mph / %.0f km/h)".format(ms, mph, kmh)
        },
        PropertyDef("Odometer", VehiclePropertyIds.PERF_ODOMETER, "km"),
        PropertyDef("Current Gear", VehiclePropertyIds.GEAR_SELECTION) { v ->
            gearName((v as? Int) ?: return@PropertyDef v.toString())
        },
        PropertyDef("Engine RPM", VehiclePropertyIds.ENGINE_RPM, "RPM"),
        PropertyDef("Fuel Level", VehiclePropertyIds.FUEL_LEVEL, "mL"),
        PropertyDef("Fuel Range", VehiclePropertyIds.RANGE_REMAINING, "m") { v ->
            val m = (v as? Float) ?: return@PropertyDef v.toString()
            val mi = m * 0.000621371f
            val km = m / 1000f
            "%.0f m (%.1f mi / %.1f km)".format(m, mi, km)
        },
        PropertyDef("EV Battery Level", VehiclePropertyIds.EV_BATTERY_LEVEL, "Wh"),
        PropertyDef("EV Battery Range", VehiclePropertyIds.RANGE_REMAINING, "m"),

        // Environment
        PropertyDef("Outside Temperature", VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, "°C") { v ->
            val c = (v as? Float) ?: return@PropertyDef v.toString()
            val f = c * 9f / 5f + 32f
            "%.1f°C (%.1f°F)".format(c, f)
        },

        // Sensors
        PropertyDef("Tire Pressure (FL)", VehiclePropertyIds.TIRE_PRESSURE, "kPa"),
        PropertyDef("Parking Brake", VehiclePropertyIds.PARKING_BRAKE_ON),
        PropertyDef("Ignition State", VehiclePropertyIds.IGNITION_STATE) { v ->
            ignitionName((v as? Int) ?: return@PropertyDef v.toString())
        },
        PropertyDef("Night Mode", VehiclePropertyIds.NIGHT_MODE),
        PropertyDef("Turn Signal", VehiclePropertyIds.TURN_SIGNAL_STATE) { v ->
            turnSignalName((v as? Int) ?: return@PropertyDef v.toString())
        },

        // Doors / Lights
        PropertyDef("Headlights State", VehiclePropertyIds.HEADLIGHTS_STATE) { v ->
            lightName((v as? Int) ?: return@PropertyDef v.toString())
        },
    )

    /**
     * Attempt to connect to Car service and read all properties.
     * Returns results for every property — successes, failures, and permission denials.
     */
    fun readAll(): List<PropertyResult> {
        val car: Car?
        try {
            car = Car.createCar(context)
        } catch (e: Exception) {
            log("Failed to connect to Car service: ${e.message}")
            return properties.map { prop ->
                PropertyResult(
                    name = prop.name,
                    propertyId = prop.id,
                    status = PropertyResult.Status.CAR_NOT_CONNECTED,
                    error = e.message,
                )
            }
        }

        if (car == null) {
            log("Car.createCar() returned null")
            return properties.map { prop ->
                PropertyResult(
                    name = prop.name,
                    propertyId = prop.id,
                    status = PropertyResult.Status.CAR_NOT_CONNECTED,
                )
            }
        }

        val propertyManager: CarPropertyManager
        try {
            propertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        } catch (e: Exception) {
            log("Failed to get CarPropertyManager: ${e.message}")
            car.disconnect()
            return properties.map { prop ->
                PropertyResult(
                    name = prop.name,
                    propertyId = prop.id,
                    status = PropertyResult.Status.CAR_NOT_CONNECTED,
                    error = "CarPropertyManager: ${e.message}",
                )
            }
        }

        val results = properties.map { prop -> readProperty(propertyManager, prop) }

        val okCount = results.count { it.status == PropertyResult.Status.OK }
        val deniedCount = results.count { it.status == PropertyResult.Status.PERMISSION_DENIED }
        val unavailCount = results.count { it.status == PropertyResult.Status.UNAVAILABLE }
        log("Read ${results.size} properties: $okCount OK, $deniedCount denied, $unavailCount unavailable")

        car.disconnect()
        return results
    }

    private fun readProperty(
        manager: CarPropertyManager,
        prop: PropertyDef,
    ): PropertyResult {
        return try {
            val value: CarPropertyValue<*> = manager.getProperty<Any>(prop.id, prop.areaId)
            val rawValue = value.value
            val formatted = if (prop.formatter != null) {
                prop.formatter.invoke(rawValue)
            } else {
                rawValue?.toString() ?: "null"
            }
            log("  ${prop.name} (0x${prop.id.toString(16)}): $formatted")
            PropertyResult(
                name = prop.name,
                propertyId = prop.id,
                status = PropertyResult.Status.OK,
                value = formatted,
                unit = if (prop.formatter != null) null else prop.unit,
            )
        } catch (e: SecurityException) {
            log("  ${prop.name} (0x${prop.id.toString(16)}): PERMISSION DENIED")
            PropertyResult(
                name = prop.name,
                propertyId = prop.id,
                status = PropertyResult.Status.PERMISSION_DENIED,
                error = e.message,
            )
        } catch (e: IllegalArgumentException) {
            // Property not supported on this vehicle/hardware
            log("  ${prop.name} (0x${prop.id.toString(16)}): UNAVAILABLE")
            PropertyResult(
                name = prop.name,
                propertyId = prop.id,
                status = PropertyResult.Status.UNAVAILABLE,
                error = e.message,
            )
        } catch (e: Exception) {
            log("  ${prop.name} (0x${prop.id.toString(16)}): ERROR ${e.javaClass.simpleName}: ${e.message}")
            PropertyResult(
                name = prop.name,
                propertyId = prop.id,
                status = PropertyResult.Status.ERROR,
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    // ===== Display name helpers =====

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
        else -> "Unknown($type)"
    }

    private fun gearName(gear: Int): String = when (gear) {
        0 -> "Unknown"
        1 -> "Neutral"
        2 -> "Reverse"
        4 -> "Park"
        8 -> "Drive"
        16 -> "1st"
        32 -> "2nd"
        64 -> "3rd"
        128 -> "4th"
        256 -> "5th"
        512 -> "6th"
        1024 -> "7th"
        2048 -> "8th"
        4096 -> "9th"
        else -> "Gear($gear)"
    }

    private fun ignitionName(state: Int): String = when (state) {
        0 -> "Undefined"
        1 -> "Lock"
        2 -> "Off"
        3 -> "ACC"
        4 -> "On"
        5 -> "Start"
        else -> "Unknown($state)"
    }

    private fun turnSignalName(state: Int): String = when (state) {
        0 -> "None"
        1 -> "Right"
        2 -> "Left"
        else -> "Unknown($state)"
    }

    private fun lightName(state: Int): String = when (state) {
        0 -> "Off"
        1 -> "On"
        2 -> "Daytime Running"
        else -> "Unknown($state)"
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[VEHICLE] $msg")
        }
    }
}
