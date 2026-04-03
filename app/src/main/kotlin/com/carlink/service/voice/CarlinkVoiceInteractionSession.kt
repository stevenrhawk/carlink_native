package com.carlink.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.carlink.logging.logInfo

/**
 * When the AAOS hardware PTT button is pressed, onShow() fires.
 * We broadcast TRIGGER_SIRI so CarlinkManager can forward the Siri
 * activation command to the CarPlay adapter, then immediately finish().
 */
class CarlinkVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags)
        logInfo("[VIS] onShow called - hardware PTT detected", tag = "VIS")
        val intent = Intent(ACTION_TRIGGER_SIRI).setPackage(context.packageName)
        context.sendBroadcast(intent)
        logInfo("[VIS] Broadcast Sent: $ACTION_TRIGGER_SIRI", tag = "VIS")
        finish()
    }

    override fun onHide() {
        super.onHide()
        logInfo("[VIS] onHide called", tag = "VIS")
    }

    companion object {
        const val ACTION_TRIGGER_SIRI = "com.carlink.action.TRIGGER_SIRI"
    }
}
