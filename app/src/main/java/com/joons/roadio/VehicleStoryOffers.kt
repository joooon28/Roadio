package com.joons.roadio

import java.time.Instant

/** Retains one discovered story for this connection until it is answered or expires. */
internal class VehicleStoryOffers {
    private val answered = mutableMapOf<String, Instant>()
    private var pending: SampleCapsuleSummary? = null

    fun update(nearby: List<SampleCapsuleSummary>, now: Instant): SampleCapsuleSummary? {
        answered.values.removeAll { !it.isAfter(now) }
        val current = pending?.takeIf { it.expiresAt.isAfter(now) }
        pending = current ?: nearby.firstOrNull { it.expiresAt.isAfter(now) && it.id !in answered }
        return pending
    }

    fun answer(story: SampleCapsuleSummary) {
        if (pending?.id != story.id) return
        answered[story.id] = story.expiresAt
        pending = null
    }
}
