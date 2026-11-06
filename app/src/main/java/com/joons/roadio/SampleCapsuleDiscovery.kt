package com.joons.roadio

import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class SamplePosition(val latitude: Double, val longitude: Double)
enum class SampleVisibility { SELF, SELECTED, PUBLIC }
enum class SampleStatus { PUBLISHED, DELETED, REVIEW_PENDING }

data class SampleCapsule(
    val id: String,
    val title: String,
    val authorDisplayName: String,
    val ownerUserId: String,
    val durationMs: Long,
    val position: SamplePosition,
    val radiusMeters: Double,
    val visibility: SampleVisibility,
    val selectedRecipientUserIds: Set<String>,
    val expiresAt: Instant,
    val status: SampleStatus = SampleStatus.PUBLISHED,
)

data class SampleCapsuleSummary(
    val id: String,
    val title: String,
    val authorDisplayName: String,
    val durationMs: Long,
    val visibility: SampleVisibility,
    val expiresAt: Instant,
    val distanceMeters: Int,
    val contentOrigin: String = "PRELOADED_SAMPLE",
)

/** Local DEV-05 simulation of the DEV-03 server search rule, not an authorization boundary. */
object SampleCapsuleDiscovery {
    fun search(
        capsules: List<SampleCapsule>,
        vehiclePosition: SamplePosition,
        viewerUserId: String,
        now: Instant,
    ): List<SampleCapsuleSummary> {
        if (viewerUserId.isBlank()) return emptyList()
        return capsules.asSequence()
            .filter { it.status == SampleStatus.PUBLISHED && now.isBefore(it.expiresAt) }
            .filter {
                when (it.visibility) {
                    SampleVisibility.SELF -> viewerUserId == it.ownerUserId
                    SampleVisibility.SELECTED -> viewerUserId == it.ownerUserId || viewerUserId in it.selectedRecipientUserIds
                    SampleVisibility.PUBLIC -> true
                }
            }
            .map { it to distanceMeters(vehiclePosition, it.position) }
            .filter { (capsule, distance) -> distance <= capsule.radiusMeters }
            .map { (capsule, distance) ->
                SampleCapsuleSummary(
                    id = capsule.id,
                    title = capsule.title,
                    authorDisplayName = capsule.authorDisplayName,
                    durationMs = capsule.durationMs,
                    visibility = capsule.visibility,
                    expiresAt = capsule.expiresAt,
                    distanceMeters = distance.toInt(),
                )
            }
            .sortedWith(compareBy<SampleCapsuleSummary> { it.distanceMeters }.thenBy { it.id })
            .toList()
    }

    fun distanceMeters(a: SamplePosition, b: SamplePosition): Double {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val latitudeA = Math.toRadians(a.latitude)
        val latitudeB = Math.toRadians(b.latitude)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(latitudeA) * cos(latitudeB) * sin(longitudeDelta / 2).pow(2)
        return 2 * earthRadiusMeters * asin(min(1.0, sqrt(haversine)))
    }
}

object Dev05SampleData {
    const val authorUserId = "11111111-1111-4111-8111-111111111111"
    const val recipientUserId = "22222222-2222-4222-8222-222222222222"
    const val otherUserId = "33333333-3333-4333-8333-333333333333"
    const val capsuleId = "44444444-4444-4444-8444-444444444444"
    val capsulePosition = SamplePosition(37.4979, 127.0276)
    val nearPosition = capsulePosition
    val farPosition = SamplePosition(37.5200, 127.0276)

    fun capsules(now: Instant) = listOf(
        SampleCapsule(
            id = capsuleId,
            title = "퇴근길 인사",
            authorDisplayName = "가족",
            ownerUserId = authorUserId,
            durationMs = 12_000,
            position = capsulePosition,
            radiusMeters = 500.0,
            visibility = SampleVisibility.SELECTED,
            selectedRecipientUserIds = setOf(recipientUserId),
            expiresAt = now.plusSeconds(7 * 24 * 60 * 60),
        ),
    )
}
