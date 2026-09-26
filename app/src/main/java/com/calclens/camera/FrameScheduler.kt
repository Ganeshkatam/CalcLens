package com.calclens.camera

class FrameScheduler(
    val ocrIntervalMs: Long = 250L,
    val trackingIntervalMs: Long = 16L
) {
    private var lastOcrTimestamp: Long = 0L
    private var isOcrProcessing: Boolean = false

    fun shouldDispatchOcr(now: Long = System.currentTimeMillis()): Boolean {
        if (isOcrProcessing) return false
        return (now - lastOcrTimestamp) >= ocrIntervalMs
    }

    fun markOcrStarted(now: Long = System.currentTimeMillis()) {
        isOcrProcessing = true
        lastOcrTimestamp = now
    }

    fun markOcrCompleted() {
        isOcrProcessing = false
    }
}
