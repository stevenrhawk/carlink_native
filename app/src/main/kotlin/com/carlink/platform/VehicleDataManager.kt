package com.carlink.platform

import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import com.carlink.logging.Logger
import com.carlink.logging.logError
import com.carlink.logging.logInfo
import com.carlink.logging.logWarn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

data class VehicleData(
    val status: ConnectionStatus = ConnectionStatus.WAITING_FOR_SERVICE,
    val errorMessage: String? = null,
    val lastUpdated: Long = 0L,

    // Model info (one-shot fetch)
    val manufacturer: CarValue<String>? = null,
    val modelName: CarValue<String>? = null,
    val modelYear: CarValue<Int>? = null,

    // Energy profile (one-shot fetch)
    val fuelTypes: CarValue<List<@JvmWildcard Int>>? = null,
    val evConnectorTypes: CarValue<List<@JvmWildcard Int>>? = null,

    // Speed (continuous listener)
    val displaySpeed: CarValue<Float>? = null,
    val rawSpeed: CarValue<Float>? = null,

    // Mileage (continuous listener)
    val odometerMeters: CarValue<Float>? = null,

    // Energy level (continuous listener)
    val batteryPercent: CarValue<Float>? = null,
    val fuelPercent: CarValue<Float>? = null,
    val rangeRemainingMeters: CarValue<Float>? = null,
    val energyIsLow: CarValue<Boolean>? = null,

    // EV status (continuous listener)
    val evChargeStatus: CarValue<Int>? = null,

    // Toll card (continuous listener)
    val tollCardStatus: CarValue<Int>? = null,
) {
    enum class ConnectionStatus {
        WAITING_FOR_SERVICE,
        CONNECTED,
        ERROR,
    }
}

object VehicleDataManager {
    private val _state = MutableStateFlow(VehicleData())
    val state: StateFlow<VehicleData> = _state.asStateFlow()

    private var carInfo: CarInfo? = null
    private var executor: Executor? = null

    private var speedListener: OnCarDataAvailableListener<Speed>? = null
    private var mileageListener: OnCarDataAvailableListener<Mileage>? = null
    private var energyLevelListener: OnCarDataAvailableListener<EnergyLevel>? = null
    private var evStatusListener: OnCarDataAvailableListener<EvStatus>? = null
    private var tollListener: OnCarDataAvailableListener<TollCard>? = null

    fun startCollecting(carContext: CarContext) {
        try {
            val hardwareManager = carContext.getCarService(CarHardwareManager::class.java)
            carInfo = hardwareManager.carInfo
            executor = carContext.mainExecutor
            logInfo("[VEHICLE] CarHardwareManager obtained, starting data collection", tag = Logger.Tags.PLATFORM)
        } catch (e: Exception) {
            logError("[VEHICLE] Failed to get CarHardwareManager: ${e.message}", tag = Logger.Tags.PLATFORM, throwable = e)
            _state.value = VehicleData(
                status = VehicleData.ConnectionStatus.ERROR,
                errorMessage = e.message,
            )
            return
        }

        _state.value = _state.value.copy(status = VehicleData.ConnectionStatus.CONNECTED)

        fetchStaticData()
        registerContinuousListeners()
    }

    fun stopCollecting() {
        removeListeners()
        carInfo = null
        executor = null
        _state.value = VehicleData()
        logInfo("[VEHICLE] Stopped data collection", tag = Logger.Tags.PLATFORM)
    }

    fun requestRefresh() {
        if (carInfo == null) {
            logWarn("[VEHICLE] Cannot refresh — CarInfo not available", tag = Logger.Tags.PLATFORM)
            return
        }
        fetchStaticData()
    }

    private fun fetchStaticData() {
        val info = carInfo ?: return
        val exec = executor ?: return

        try {
            info.fetchModel(exec) { model: Model ->
                logInfo("[VEHICLE] Model: ${model.manufacturer}, ${model.name}, ${model.year}", tag = Logger.Tags.PLATFORM)
                _state.value = _state.value.copy(
                    manufacturer = model.manufacturer,
                    modelName = model.name,
                    modelYear = model.year,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            logWarn("[VEHICLE] fetchModel failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        try {
            info.fetchEnergyProfile(exec) { profile: EnergyProfile ->
                logInfo("[VEHICLE] EnergyProfile: fuelTypes=${profile.fuelTypes}, evConnectors=${profile.evConnectorTypes}", tag = Logger.Tags.PLATFORM)
                _state.value = _state.value.copy(
                    fuelTypes = profile.fuelTypes,
                    evConnectorTypes = profile.evConnectorTypes,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            logWarn("[VEHICLE] fetchEnergyProfile failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }
    }

    private fun registerContinuousListeners() {
        val info = carInfo ?: return
        val exec = executor ?: return

        try {
            speedListener = OnCarDataAvailableListener<Speed> { speed ->
                _state.value = _state.value.copy(
                    displaySpeed = speed.displaySpeed,
                    rawSpeed = speed.rawSpeed,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            info.addSpeedListener(exec, speedListener!!)
        } catch (e: Exception) {
            logWarn("[VEHICLE] addSpeedListener failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        try {
            mileageListener = OnCarDataAvailableListener<Mileage> { mileage ->
                _state.value = _state.value.copy(
                    odometerMeters = mileage.odometerMeters,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            info.addMileageListener(exec, mileageListener!!)
        } catch (e: Exception) {
            logWarn("[VEHICLE] addMileageListener failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        try {
            energyLevelListener = OnCarDataAvailableListener<EnergyLevel> { energy ->
                _state.value = _state.value.copy(
                    batteryPercent = energy.batteryPercent,
                    fuelPercent = energy.fuelPercent,
                    rangeRemainingMeters = energy.rangeRemainingMeters,
                    energyIsLow = energy.energyIsLow,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            info.addEnergyLevelListener(exec, energyLevelListener!!)
        } catch (e: Exception) {
            logWarn("[VEHICLE] addEnergyLevelListener failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        try {
            evStatusListener = OnCarDataAvailableListener<EvStatus> { evStatus ->
                _state.value = _state.value.copy(
                    evChargeStatus = evStatus.chargeStatus,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            info.addEvStatusListener(exec, evStatusListener!!)
        } catch (e: Exception) {
            logWarn("[VEHICLE] addEvStatusListener failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        try {
            tollListener = OnCarDataAvailableListener<TollCard> { toll ->
                _state.value = _state.value.copy(
                    tollCardStatus = toll.cardState,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            info.addTollListener(exec, tollListener!!)
        } catch (e: Exception) {
            logWarn("[VEHICLE] addTollListener failed: ${e.message}", tag = Logger.Tags.PLATFORM)
        }

        logInfo("[VEHICLE] Continuous listeners registered", tag = Logger.Tags.PLATFORM)
    }

    private fun removeListeners() {
        val info = carInfo ?: return
        try {
            speedListener?.let { info.removeSpeedListener(it) }
            mileageListener?.let { info.removeMileageListener(it) }
            energyLevelListener?.let { info.removeEnergyLevelListener(it) }
            evStatusListener?.let { info.removeEvStatusListener(it) }
            tollListener?.let { info.removeTollListener(it) }
        } catch (e: Exception) {
            logWarn("[VEHICLE] Error removing listeners: ${e.message}", tag = Logger.Tags.PLATFORM)
        }
        speedListener = null
        mileageListener = null
        energyLevelListener = null
        evStatusListener = null
        tollListener = null
    }
}
