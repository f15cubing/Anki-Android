/*
 * Copyright (c) 2026 Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The desktop-authoritative three-score card, computed on desktop
 * (`anki/qt/aqt/gre/scoring_adapter.py`) and synced to this device via `col.conf`
 * `gre_scorecard`. AnkiDroid renders it **read-only** — no scoring math on device.
 *
 * Honesty ceilings mirror desktop: the three scores stay **separate** (never
 * blended), and Readiness is shown as a range only when `shown == true`; when gated
 * off it carries `reasons` + the evidence panel (coverage / confidence /
 * best-next topic) but **never a bare number**.
 */
@Serializable
data class GreScorecard(
    val version: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    val source: String = "",
    val memory: Score = Score(),
    val performance: Score = Score(),
    val readiness: Readiness = Readiness(),
) {
    @Serializable
    data class Score(
        val estimate: Double? = null,
        val low: Double? = null,
        val high: Double? = null,
        @SerialName("coverage_pct") val coveragePct: Double? = null,
        val state: String? = null,
    )

    @Serializable
    data class Readiness(
        val shown: Boolean = false,
        val estimate: Double? = null,
        val low: Double? = null,
        val high: Double? = null,
        val reasons: List<String> = emptyList(),
        @SerialName("coverage_pct") val coveragePct: Double? = null,
        val confidence: String? = null,
        @SerialName("best_next_topic") val bestNextTopic: String? = null,
    )

    companion object {
        const val CONFIG_KEY = "gre_scorecard"

        private val json = Json { ignoreUnknownKeys = true }

        /** Parse the synced JSON; returns null for blank/invalid input (render nothing). */
        fun parse(raw: String): GreScorecard? =
            if (raw.isBlank()) {
                null
            } else {
                try {
                    json.decodeFromString<GreScorecard>(raw)
                } catch (ex: SerializationException) {
                    null
                } catch (ex: IllegalArgumentException) {
                    null
                }
            }
    }
}
