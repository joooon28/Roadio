package com.joons.roadio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SampleCapsuleDiscoveryTest {
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val sample = Dev05SampleData.capsules(now).single()

    private fun search(
        position: SamplePosition = Dev05SampleData.nearPosition,
        viewerId: String = Dev05SampleData.recipientUserId,
        at: Instant = now,
        capsules: List<SampleCapsule> = listOf(sample),
    ) = SampleCapsuleDiscovery.search(capsules, position, viewerId, at)

    @Test fun selectedRecipientInsideRadiusCanDiscoverSample() {
        val result = search()
        assertEquals(listOf(Dev05SampleData.capsuleId), result.map { it.id })
        assertEquals(0, result.single().distanceMeters)
        assertEquals("PRELOADED_SAMPLE", result.single().contentOrigin)
    }

    @Test fun outsideRadiusIsExcluded() {
        assertTrue(search(position = Dev05SampleData.farPosition).isEmpty())
    }

    @Test fun unselectedViewerIsExcludedEvenInsideRadius() {
        assertTrue(search(viewerId = Dev05SampleData.otherUserId).isEmpty())
        assertTrue(search(viewerId = "").isEmpty())
    }

    @Test fun capsuleExpiresAtBoundaryAndDeletedOrPendingCapsulesAreExcluded() {
        assertTrue(search(at = sample.expiresAt).isEmpty())
        assertTrue(search(capsules = listOf(sample.copy(status = SampleStatus.DELETED))).isEmpty())
        assertTrue(search(capsules = listOf(sample.copy(status = SampleStatus.REVIEW_PENDING))).isEmpty())
    }

    @Test fun selfAndPublicVisibilityFollowContract() {
        val self = sample.copy(visibility = SampleVisibility.SELF, selectedRecipientUserIds = emptySet())
        assertTrue(search(capsules = listOf(self)).isEmpty())
        assertEquals(1, search(viewerId = Dev05SampleData.authorUserId, capsules = listOf(self)).size)
        val public = sample.copy(visibility = SampleVisibility.PUBLIC, selectedRecipientUserIds = emptySet())
        assertEquals(1, search(viewerId = Dev05SampleData.otherUserId, capsules = listOf(public)).size)
    }

    @Test fun equalRadiusBoundaryIsIncluded() {
        val meters = SampleCapsuleDiscovery.distanceMeters(Dev05SampleData.nearPosition, Dev05SampleData.farPosition)
        assertEquals(1, search(position = Dev05SampleData.farPosition, capsules = listOf(sample.copy(radiusMeters = meters))).size)
    }
}
