package com.joons.roadio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStoryOffersTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val story = SampleCapsuleSummary(
        id = "family-story", title = "아빠에게", authorDisplayName = "가족",
        durationMs = 4_000, visibility = SampleVisibility.SELECTED,
        expiresAt = now.plusSeconds(60), distanceMeters = 40,
    )

    @Test
    fun keepsAnAuthorizedDiscoveryAfterLeavingTheRadius() {
        val offers = VehicleStoryOffers()
        assertNull(offers.update(emptyList(), now))
        assertEquals(story, offers.update(listOf(story), now.plusSeconds(2)))
        assertEquals(story, offers.update(emptyList(), now.plusSeconds(5)))
    }

    @Test
    fun answeringSuppressesRepeatOffersButAllowsAnotherStory() {
        val offers = VehicleStoryOffers()
        val next = story.copy(id = "another-story")
        offers.update(listOf(story), now)
        offers.answer(story)
        assertEquals(next, offers.update(listOf(story, next), now.plusSeconds(2)))
    }

    @Test
    fun neverOffersOrKeepsAnExpiredStory() {
        val offers = VehicleStoryOffers()
        offers.update(listOf(story), now)
        assertNull(offers.update(listOf(story), story.expiresAt))
    }
}
