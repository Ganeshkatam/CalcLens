package com.calclens.tracking

import com.calclens.math.MathEngine
import com.calclens.math.MathResult
import com.calclens.vision.RectBounds
import com.calclens.vision.VisionCandidate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-frequency spatial tracker managing persistent mathematical expression objects.
 * Decouples low-frequency visual recognition (~5-15 Hz) from high-frequency spatial tracking (60-120+ Hz).
 */
class SpatialTracker(
    private val iouThreshold: Float = 0.30f,
    private val gracePeriodMs: Long = 800L,
    private val minFramesToDisplay: Int = 2,
    private val smoothingAlphaStationary: Float = 0.40f,
    private val smoothingAlphaDynamic: Float = 0.85f
) {
    private val entities = mutableMapOf<String, TrackedEquation>()

    @Synchronized
    fun getEntities(): List<TrackedEquation> = entities.values.toList()

    /**
     * Applies instantaneous high-frequency motion predicted by IMU/gyroscope sensors.
     * Keeps overlays physically locked to the paper even during rapid camera movement.
     */
    @Synchronized
    fun applyImuMotion(deltaX: Float, deltaY: Float) {
        if (deltaX == 0f && deltaY == 0f) return
        for (entity in entities.values) {
            entity.smoothedBox = entity.smoothedBox.offset(deltaX, deltaY)
            entity.boundingBox = entity.boundingBox.offset(deltaX, deltaY)
        }
    }

    /**
     * Updates tracked entities with new vision candidates from the OCR/reconstruction pipeline.
     */
    @Synchronized
    fun updateWithVisionCandidates(
        candidates: List<VisionCandidate>,
        currentTime: Long = System.currentTimeMillis()
    ): List<TrackedEquation> {
        val matchedIds = mutableSetOf<String>()

        for (candidate in candidates) {
            var bestMatchId: String? = null
            var bestScore = 0f

            for ((id, entity) in entities) {
                if (matchedIds.contains(id)) continue

                val iou = calculateIoU(candidate.boundingBox, entity.boundingBox)
                val textMatches = entity.normalizedText == candidate.normalizedText

                val dx: Float = abs(candidate.boundingBox.centerX - entity.boundingBox.centerX)
                val dy: Float = abs(candidate.boundingBox.centerY - entity.boundingBox.centerY)
                val isSpatiallyClose = dx < 0.18f && dy < 0.14f

                var score = 0f
                if (textMatches && isSpatiallyClose) {
                    score = 1.0f + iou + (0.18f - dx)
                } else if (iou >= iouThreshold) {
                    score = iou + (if (textMatches) 0.5f else 0.0f)
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatchId = id
                }
            }

            if (bestMatchId != null) {
                matchedIds.add(bestMatchId)
                val entity = entities[bestMatchId]!!

                val textMatches = entity.normalizedText == candidate.normalizedText
                if (textMatches) {
                    entity.consecutiveMatches++
                } else {
                    // Only swap text if the candidate has very high confidence
                    if (candidate.confidence >= 0.75f) {
                        entity.consecutiveMatches = 1
                        entity.normalizedText = candidate.normalizedText
                        entity.rawText = candidate.rawText
                        entity.result = null
                        entity.errorMessage = null
                    }
                }

                val dt = max(0.016f, (currentTime - entity.lastSeen) / 1000f)
                entity.velocityX = (candidate.boundingBox.centerX - entity.boundingBox.centerX) / dt
                entity.velocityY = (candidate.boundingBox.centerY - entity.boundingBox.centerY) / dt

                entity.boundingBox = candidate.boundingBox
                entity.confidence = candidate.confidence
                entity.lastSeen = currentTime

                val speed = sqrt(entity.velocityX * entity.velocityX + entity.velocityY * entity.velocityY)
                val alpha = if (speed > 0.35f) smoothingAlphaDynamic else smoothingAlphaStationary

                entity.smoothedBox = RectBounds(
                    alpha * candidate.boundingBox.left + (1f - alpha) * entity.smoothedBox.left,
                    alpha * candidate.boundingBox.top + (1f - alpha) * entity.smoothedBox.top,
                    alpha * candidate.boundingBox.right + (1f - alpha) * entity.smoothedBox.right,
                    alpha * candidate.boundingBox.bottom + (1f - alpha) * entity.smoothedBox.bottom
                )

                // Confirm and calculate expression once stable across multiple frames
                if (entity.consecutiveMatches >= minFramesToDisplay) {
                    if (entity.result == null && entity.errorMessage == null) {
                        when (val mathResult = MathEngine.evaluate(entity.normalizedText)) {
                            is MathResult.Success -> {
                                entity.result = mathResult.formatted
                                entity.status = TrackingStatus.DISPLAYING
                                android.util.Log.d("CalcLens", "Locked '${entity.normalizedText}' = ${mathResult.formatted}")
                            }
                            is MathResult.DivisionByZero -> {
                                entity.errorMessage = "Undefined"
                                entity.status = TrackingStatus.DISPLAYING
                            }
                            is MathResult.Overflow -> {
                                entity.errorMessage = "Overflow"
                                entity.status = TrackingStatus.DISPLAYING
                            }
                            is MathResult.SyntaxError -> {
                                entity.errorMessage = "Invalid"
                                entity.status = TrackingStatus.DISPLAYING
                            }
                        }
                    } else {
                        entity.status = TrackingStatus.DISPLAYING
                    }
                } else {
                    entity.status = TrackingStatus.DETECTED
                }
            } else {
                // New Candidate: require adequate confidence (>= 0.65f)
                if (candidate.confidence >= 0.65f) {
                    val newEntity = TrackedEquation(
                        id = candidate.id,
                        rawText = candidate.rawText,
                        normalizedText = candidate.normalizedText,
                        confidence = candidate.confidence,
                        boundingBox = candidate.boundingBox,
                        smoothedBox = candidate.boundingBox,
                        firstSeen = currentTime,
                        lastSeen = currentTime,
                        consecutiveMatches = 1,
                        status = TrackingStatus.DETECTED
                    )
                    entities[candidate.id] = newEntity
                    matchedIds.add(candidate.id)
                }
            }
        }

        // Handle unmatched entities (temporary occlusion / distance changes / camera motion)
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val (id, entity) = iterator.next()
            if (!matchedIds.contains(id)) {
                val timeSinceSeen = currentTime - entity.lastSeen
                val effectiveGrace = if (entity.status == TrackingStatus.DISPLAYING) 1500L else gracePeriodMs
                if (timeSinceSeen > effectiveGrace) {
                    iterator.remove()
                } else {
                    // Maintain last known position with slight velocity extrapolation
                    val dt = 0.016f
                    entity.smoothedBox = entity.smoothedBox.offset(
                        entity.velocityX * dt * 0.4f,
                        entity.velocityY * dt * 0.4f
                    )
                }
            }
        }

        return entities.values.toList()
    }

    @Synchronized
    fun clear() {
        entities.clear()
    }

    companion object {
        fun calculateIoU(boxA: RectBounds, boxB: RectBounds): Float {
            val left = max(boxA.left, boxB.left)
            val top = max(boxA.top, boxB.top)
            val right = min(boxA.right, boxB.right)
            val bottom = min(boxA.bottom, boxB.bottom)

            val interWidth = max(0f, right - left)
            val interHeight = max(0f, bottom - top)
            val intersection = interWidth * interHeight

            val areaA = boxA.width * boxA.height
            val areaB = boxB.width * boxB.height
            val union = areaA + areaB - intersection

            if (union <= 0f) return 0f
            return intersection / union
        }
    }
}
