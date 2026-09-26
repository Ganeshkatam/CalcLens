package com.calclens.tracking

import android.graphics.RectF
import com.calclens.math.MathEngine
import com.calclens.math.MathResult
import com.calclens.vision.VisionCandidate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SpatialTracker(
    private val iouThreshold: Float = 0.35f,
    private val gracePeriodMs: Long = 500L,
    private val minFramesToDisplay: Int = 2,
    private val smoothingAlphaStationary: Float = 0.30f,
    private val smoothingAlphaDynamic: Float = 0.85f
) {
    private val entities = mutableMapOf<String, TrackedEquation>()

    fun getEntities(): List<TrackedEquation> = entities.values.toList()

    fun updateWithVisionCandidates(
        candidates: List<VisionCandidate>,
        currentTime: Long = System.currentTimeMillis()
    ): List<TrackedEquation> {
        val matchedIds = mutableSetOf<String>()

        for (candidate in candidates) {
            var bestMatchId: String? = null
            var highestIoU = 0f

            for ((id, entity) in entities) {
                val iou = calculateIoU(candidate.boundingBox, entity.boundingBox)
                if (iou > highestIoU && iou >= iouThreshold) {
                    highestIoU = iou
                    bestMatchId = id
                }
            }

            if (bestMatchId != null && !matchedIds.contains(bestMatchId)) {
                matchedIds.add(bestMatchId)
                val entity = entities[bestMatchId]!!

                val textMatches = entity.normalizedText == candidate.normalizedText
                if (textMatches) {
                    entity.consecutiveMatches++
                } else {
                    entity.consecutiveMatches = 1
                    entity.normalizedText = candidate.normalizedText
                    entity.rawText = candidate.rawText
                    entity.result = null
                    entity.errorMessage = null
                }

                val dt = max(0.016f, (currentTime - entity.lastSeen) / 1000f)
                entity.velocityX = (candidate.boundingBox.centerX() - entity.boundingBox.centerX()) / dt
                entity.velocityY = (candidate.boundingBox.centerY() - entity.boundingBox.centerY()) / dt

                entity.boundingBox = candidate.boundingBox
                entity.confidence = candidate.confidence
                entity.lastSeen = currentTime

                val speed = sqrt(entity.velocityX * entity.velocityX + entity.velocityY * entity.velocityY)
                val alpha = if (speed > 0.4f) smoothingAlphaDynamic else smoothingAlphaStationary

                entity.smoothedBox = RectF(
                    alpha * candidate.boundingBox.left + (1f - alpha) * entity.smoothedBox.left,
                    alpha * candidate.boundingBox.top + (1 - alpha) * entity.smoothedBox.top,
                    alpha * candidate.boundingBox.right + (1 - alpha) * entity.smoothedBox.right,
                    alpha * candidate.boundingBox.bottom + (1 - alpha) * entity.smoothedBox.bottom
                )

                if (entity.consecutiveMatches >= minFramesToDisplay) {
                    if (entity.result == null && entity.errorMessage == null) {
                        when (val mathResult = MathEngine.evaluate(entity.normalizedText)) {
                            is MathResult.Success -> {
                                entity.result = mathResult.formatted
                                entity.status = TrackingStatus.DISPLAYING
                                android.util.Log.d("CalcLens", "Evaluated '${entity.normalizedText}' = ${mathResult.formatted} (matches=${entity.consecutiveMatches})")
                            }
                            is MathResult.DivisionByZero -> {
                                entity.errorMessage = "Undefined"
                                entity.status = TrackingStatus.DISPLAYING
                                android.util.Log.d("CalcLens", "Evaluated '${entity.normalizedText}' = DivisionByZero")
                            }
                            is MathResult.Overflow -> {
                                entity.errorMessage = "Overflow"
                                entity.status = TrackingStatus.DISPLAYING
                                android.util.Log.d("CalcLens", "Evaluated '${entity.normalizedText}' = Overflow")
                            }
                            is MathResult.SyntaxError -> {
                                entity.errorMessage = "Invalid"
                                entity.status = TrackingStatus.DISPLAYING
                                android.util.Log.d("CalcLens", "Evaluated '${entity.normalizedText}' = SyntaxError: ${mathResult.message}")
                            }
                        }
                    } else {
                        entity.status = TrackingStatus.TRACKING
                    }
                } else {
                    entity.status = TrackingStatus.DETECTED
                    android.util.Log.d("CalcLens", "Candidate '${entity.normalizedText}' accumulating matches: ${entity.consecutiveMatches}/$minFramesToDisplay")
                }
            } else {
                // New Candidate
                val newEntity = TrackedEquation(
                    id = candidate.id,
                    rawText = candidate.rawText,
                    normalizedText = candidate.normalizedText,
                    confidence = candidate.confidence,
                    boundingBox = candidate.boundingBox,
                    smoothedBox = RectF(candidate.boundingBox),
                    firstSeen = currentTime,
                    lastSeen = currentTime,
                    consecutiveMatches = 1,
                    status = TrackingStatus.DETECTED
                )
                entities[candidate.id] = newEntity
                matchedIds.add(candidate.id)
            }
        }

        // Handle unmatched entities (temporary occlusion / out of frame)
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val (id, entity) = iterator.next()
            if (!matchedIds.contains(id)) {
                val timeSinceSeen = currentTime - entity.lastSeen
                if (timeSinceSeen > gracePeriodMs) {
                    iterator.remove()
                } else {
                    entity.status = TrackingStatus.LOST
                    val dt = 0.016f
                    entity.smoothedBox.offset(entity.velocityX * dt * 0.5f, entity.velocityY * dt * 0.5f)
                }
            }
        }

        return entities.values.toList()
    }

    fun clear() {
        entities.clear()
    }

    companion object {
        fun calculateIoU(boxA: RectF, boxB: RectF): Float {
            val left = max(boxA.left, boxB.left)
            val top = max(boxA.top, boxB.top)
            val right = min(boxA.right, boxB.right)
            val bottom = min(boxA.bottom, boxB.bottom)

            val interWidth = max(0f, right - left)
            val interHeight = max(0f, bottom - top)
            val intersection = interWidth * interHeight

            val areaA = boxA.width() * boxA.height()
            val areaB = boxB.width() * boxB.height()
            val union = areaA + areaB - intersection

            if (union <= 0f) return 0f
            return intersection / union
        }
    }
}
