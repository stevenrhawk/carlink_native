package com.carlink.service.voice

import android.content.Intent
import android.speech.RecognitionService

/**
 * Stub RecognitionService required by the VoiceInteractionService manifest declaration.
 * All methods are no-ops — actual speech recognition is handled by Siri via CarPlay.
 */
class CarlinkRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {}
    override fun onStopListening(callback: Callback?) {}
    override fun onCancel(callback: Callback?) {}
}
