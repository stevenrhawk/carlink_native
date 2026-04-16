package com.carlink.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import com.carlink.BuildConfig

private const val TAG = "CARLINK_BUS_ROUTER"

/**
 * AudioBusRouter - Routes AudioTracks/AudioRecords to GM AAOS hardware buses.
 *
 * PURPOSE:
 * On GM AAOS, the audio HAL exposes named bus devices for different audio contexts.
 * The standard AAOS AudioAttributes-based routing works for most streams, but phone
 * call audio benefits from explicit routing to the telephony bus for proper echo
 * cancellation, exclusive mode, and correct speaker/mic selection.
 *
 * GM AAOS BUS ADDRESSES (from GMCarPlay APK analysis):
 * - "bus4_call_out"     → Telephony output (exclusive mode for calls)
 * - "Call_In_Mic"       → Telephony input (dedicated call microphone)
 * - "bus15_aux_out"     → Auxiliary output
 * - "bus11_cp_alt_out"  → CarPlay alternate audio output
 *
 * USAGE:
 * On non-GM platforms, this class is a no-op — all methods return false and
 * no preferred devices are set, allowing standard AudioAttributes routing.
 *
 * DISCOVERY:
 * Queries AudioManager.getDevices() for devices matching known bus addresses.
 * Device IDs are cached at init time. If a bus is not found (non-GM hardware),
 * routing silently falls back to default.
 */
class AudioBusRouter(context: Context, private val isGmAaos: Boolean) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Cached AudioDeviceInfo for each known bus (null if not found on this hardware)
    private var callOutputDevice: AudioDeviceInfo? = null
    private var callInputDevice: AudioDeviceInfo? = null
    private var auxOutputDevice: AudioDeviceInfo? = null
    private var altOutputDevice: AudioDeviceInfo? = null

    /** True if at least one GM audio bus was discovered. */
    var hasGmBuses: Boolean = false
        private set

    init {
        if (isGmAaos) {
            discoverBusDevices()
        }
    }

    /**
     * Scan all audio devices for GM AAOS bus addresses.
     *
     * Uses AudioManager.getDevices(GET_DEVICES_ALL) to enumerate both input and output
     * devices, then matches by address string. This mirrors the GM APK's setDeviceId()
     * method in CarPlayAudioManager.java.
     */
    private fun discoverBusDevices() {
        // GET_DEVICES_ALL = GET_DEVICES_OUTPUTS | GET_DEVICES_INPUTS = 3
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        if (devices == null || devices.isEmpty()) {
            log("No audio devices found")
            return
        }

        var found = 0
        for (device in devices) {
            val address = device.address
            if (address.isEmpty()) continue
            when (address) {
                BUS_CALL_OUTPUT -> {
                    callOutputDevice = device
                    found++
                    log("Found call output bus: id=${device.id} type=${device.type} address=$address")
                }
                BUS_CALL_INPUT -> {
                    callInputDevice = device
                    found++
                    log("Found call input bus: id=${device.id} type=${device.type} address=$address")
                }
                BUS_AUX_OUTPUT -> {
                    auxOutputDevice = device
                    found++
                    log("Found aux output bus: id=${device.id} type=${device.type} address=$address")
                }
                BUS_ALT_OUTPUT -> {
                    altOutputDevice = device
                    found++
                    log("Found alt output bus: id=${device.id} type=${device.type} address=$address")
                }
            }
            // Early exit if all 4 found
            if (found >= 4) break
        }

        hasGmBuses = found > 0
        log("Bus discovery complete: $found/4 buses found (callOut=${callOutputDevice != null}, " +
                "callIn=${callInputDevice != null}, aux=${auxOutputDevice != null}, alt=${altOutputDevice != null})")

        if (BuildConfig.DEBUG && found > 0) {
            Log.i(TAG, "[BUS] GM AAOS audio buses discovered: $found/4")
        }
    }

    /**
     * Route an AudioTrack to the appropriate GM bus based on stream type.
     *
     * Currently routes:
     * - PHONE_CALL → bus4_call_out (exclusive telephony output)
     * - Other streams use default AudioAttributes-based routing
     *
     * @param track The AudioTrack to route
     * @param streamType AudioStreamType constant
     * @return true if preferred device was set, false if using default routing
     */
    fun routeOutputTrack(track: AudioTrack, streamType: Int): Boolean {
        if (!isGmAaos || !hasGmBuses) return false

        val device = when (streamType) {
            AudioStreamType.PHONE_CALL -> callOutputDevice
            // Media, Nav, Voice use standard AAOS AudioAttributes routing which
            // already maps to the correct CarAudioContext. Explicit bus routing
            // for these streams could conflict with AAOS audio policy.
            else -> null
        }

        if (device != null) {
            val success = track.setPreferredDevice(device)
            log("Route ${streamName(streamType)} output → ${device.address}: ${if (success) "OK" else "FAILED"}")
            return success
        }
        return false
    }

    /**
     * Route an AudioRecord to the appropriate GM bus based on capture purpose.
     *
     * Routes phone call microphone capture to the dedicated Call_In_Mic bus,
     * which provides hardware echo cancellation optimized for in-vehicle acoustics.
     *
     * @param record The AudioRecord to route
     * @param isCallCapture true if this capture is for phone call audio
     * @return true if preferred device was set, false if using default routing
     */
    fun routeInputRecord(record: AudioRecord, isCallCapture: Boolean): Boolean {
        if (!isGmAaos || !hasGmBuses) return false

        if (isCallCapture && callInputDevice != null) {
            val success = record.setPreferredDevice(callInputDevice)
            log("Route call input → ${callInputDevice!!.address}: ${if (success) "OK" else "FAILED"}")
            return success
        }
        return false
    }

    fun getStats(): Map<String, Any> = mapOf(
        "isGmAaos" to isGmAaos,
        "hasGmBuses" to hasGmBuses,
        "callOutputBus" to (callOutputDevice?.address ?: "not found"),
        "callInputBus" to (callInputDevice?.address ?: "not found"),
        "auxOutputBus" to (auxOutputDevice?.address ?: "not found"),
        "altOutputBus" to (altOutputDevice?.address ?: "not found"),
    )

    private fun streamName(streamType: Int): String = when (streamType) {
        AudioStreamType.MEDIA -> "MEDIA"
        AudioStreamType.NAVIGATION -> "NAV"
        AudioStreamType.SIRI -> "VOICE"
        AudioStreamType.PHONE_CALL -> "CALL"
        else -> "UNKNOWN($streamType)"
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[BUS] $msg")
        }
    }

    companion object {
        // GM AAOS bus addresses (from GMCarPlay APK: CarPlayAudioManager.java)
        const val BUS_CALL_OUTPUT = "bus4_call_out"
        const val BUS_CALL_INPUT = "Call_In_Mic"
        const val BUS_AUX_OUTPUT = "bus15_aux_out"
        const val BUS_ALT_OUTPUT = "bus11_cp_alt_out"

        /** No-op router for non-GM platforms. All routing methods return false. */
        fun noOp(context: Context): AudioBusRouter = AudioBusRouter(context, isGmAaos = false)
    }
}
