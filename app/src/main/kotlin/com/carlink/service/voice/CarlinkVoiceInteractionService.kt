package com.carlink.service.voice

import android.service.voice.VoiceInteractionService

/**
 * Stub VoiceInteractionService — registers the app as a voice assistant on AAOS
 * so the hardware PTT button triggers Siri (via CarPlay) instead of Google Assistant.
 */
class CarlinkVoiceInteractionService : VoiceInteractionService()
