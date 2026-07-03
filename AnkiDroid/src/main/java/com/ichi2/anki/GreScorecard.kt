/*
 * Copyright (c) 2026 Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * The desktop-authoritative three-score card, computed on desktop
 * (`anki/qt/aqt/gre/scoring_adapter.py`) and synced to this device via `col.conf`
 * `gre_scorecard`. AnkiDroid renders it **read-only** — no scoring math on device.
 *
 * Honesty ceilings mirror desktop: the three scores stay **separate** (never
 * blended), and a Readiness **number** is never shown without the full evidence
 * panel — `readinessLines()` always appends coverage / confidence / reasons /
 * best-next topic, whether the score is shown or gated off.
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

    /** Memory slot line: recall fraction as a range, or a "not enough reviews" note. */
    fun memoryLine(): String = fractionRange(memory.estimate, memory.low, memory.high, "Not enough reviews yet.")

    /** Performance slot line: "not available" until the exam/MCQ attempt bank exists. */
    fun performanceLine(): String =
        if (performance.state == "not_available" || performance.estimate == null) {
            "Not available yet (arrives with the exam/MCQ surface)."
        } else {
            fractionRange(performance.estimate, performance.low, performance.high, "\u2014")
        }

    /**
     * All Readiness display lines. **Honesty ceiling:** a Readiness *number* is never
     * shown without the full evidence panel, so the panel fields (reasons / confidence /
     * coverage / best-next topic) are ALWAYS appended — whether the score is shown or
     * gated off. The number + range is the first line only when `shown` and an estimate
     * is present; otherwise the first line states it is gated.
     */
    fun readinessLines(): List<String> {
        val r = readiness
        val out = mutableListOf<String>()
        out +=
            if (r.shown && r.estimate != null) {
                scoreRange(r.estimate, r.low, r.high)
            } else {
                "Not shown yet — needs more evidence:"
            }
        r.reasons.forEach { out += "  \u2022  $it" }
        r.confidence?.let { out += "Confidence: $it" }
        r.coveragePct?.let { out += "Coverage: ${pct(it)}" }
        r.bestNextTopic?.let { out += "Best next topic: ${leaf(it)}" }
        return out
    }

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

private fun fractionRange(
    est: Double?,
    lo: Double?,
    hi: Double?,
    emptyText: String,
): String = if (est == null) emptyText else "${pct(est)}   [${pct(lo ?: est)} – ${pct(hi ?: est)}]"

private fun scoreRange(
    est: Double,
    lo: Double?,
    hi: Double?,
): String = "${est.roundToInt()}   [${(lo ?: est).roundToInt()} – ${(hi ?: est).roundToInt()}]"

private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

private fun leaf(tag: String) = tag.substringAfterLast("::")
