package com.carlink.util

/**
 * No-op stub for audio debug logging.
 * All methods are intentionally empty — enable logging by adding implementations as needed.
 */
object AudioDebugLogger {
    fun logNavPatternCheck(tag: String, result: Boolean, detail: String) {}
    fun logNavBufferFlush(reason: String, discardedMs: Int) {}
    fun logUsbReceive(size: Int, audioType: Int, decodeType: Int) {}
    fun logUsbFiltered(audioType: Int, count: Long) {}
    fun logNavEndMarker(timeSinceStart: Long, bufferLevelMs: Int) {}
    fun logNavZeroFlush(count: Int, bufferLevelMs: Int) {}
    fun logNavWarmupSkip(timeSinceStart: Long, reason: String) {}
    fun logNavBufferWrite(bytesWritten: Int, fillLevelMs: Int, timeSinceStart: Long) {}
    fun logNavPromptEnd(playDuration: Long, bytesRead: Long, underruns: Int) {}
    fun logStreamStop(stream: String, playDuration: Long, packets: Long) {}
    fun logStreamStart(stream: String, sampleRate: Int, channelCount: Int, bufferMs: Int) {}
    fun logNavPromptStart(sampleRate: Int, channelCount: Int, bufferMs: Int) {}
    fun logNavPrefillComplete(fillMs: Int, waitTimeMs: Long) {}
    fun logNavTrackWrite(written: Int, fillLevelMs: Int) {}
    fun logTrackUnderrun(stream: String, underruns: Int) {}
    fun logPerfSummary(
        mediaFillMs: Int, navFillMs: Int, voiceFillMs: Int, callFillMs: Int,
        mediaUnderruns: Int, navUnderruns: Int, voiceUnderruns: Int, callUnderruns: Int,
    ) {}
}
