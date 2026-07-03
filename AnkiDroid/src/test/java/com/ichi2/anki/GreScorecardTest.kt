/*
 * Copyright (c) 2026 Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure host-JVM parser test for the desktop-authoritative `gre_scorecard` (Task 7).
 * No backend needed: the parse contract is pure JSON -> typed model.
 */
class GreScorecardTest {
    @Test
    fun parsesGatedScorecardWithThreeSeparateScoresAndHiddenReadiness() {
        val json =
            """
            {"version":1,"updated_at":"2026-07-03T00:00:00Z","source":"desktop; validity unestablished at n=0",
             "memory":{"estimate":null,"low":null,"high":null,"coverage_pct":0.0},
             "performance":{"estimate":null,"low":null,"high":null,"state":"not_available"},
             "readiness":{"shown":false,"estimate":null,"low":null,"high":null,"width":null,
               "reasons":["<200 graded reviews","no exam attempts yet"],"coverage_pct":0.0,
               "confidence":"low","best_next_topic":"topic::calculus::differential_single","sd_frac":0.5}}
            """.trimIndent()
        val sc = GreScorecard.parse(json)!!
        assertEquals(1, sc.version)
        // The three scores are separate objects (never blended).
        assertEquals("not_available", sc.performance.state)
        // Readiness gated OFF -> no bare number, but the full evidence panel is present.
        assertFalse(sc.readiness.shown)
        assertNull(sc.readiness.estimate)
        assertTrue(sc.readiness.reasons.contains("<200 graded reviews"))
        assertEquals("topic::calculus::differential_single", sc.readiness.bestNextTopic)
        assertEquals(0.0, sc.readiness.coveragePct!!, 1e-9)
    }

    @Test
    fun parsesShownReadinessRange() {
        val json =
            """
            {"version":1,"updated_at":"t","source":"s",
             "memory":{"estimate":0.72,"low":0.65,"high":0.79,"coverage_pct":0.8},
             "performance":{"estimate":0.6,"low":0.5,"high":0.7},
             "readiness":{"shown":true,"estimate":711,"low":678,"high":748,"reasons":[],
               "coverage_pct":0.8,"confidence":"medium","best_next_topic":null}}
            """.trimIndent()
        val sc = GreScorecard.parse(json)!!
        assertTrue(sc.readiness.shown)
        assertEquals(711.0, sc.readiness.estimate!!, 1e-9)
        assertEquals(0.72, sc.memory.estimate!!, 1e-9)
    }

    @Test
    fun blankOrGarbageReturnsNull() {
        assertNull(GreScorecard.parse(""))
        assertNull(GreScorecard.parse("not json at all"))
    }

    @Test
    fun shownReadinessShowsNumberWithFullEvidencePanelNeverBare() {
        val json =
            """
            {"version":1,"updated_at":"t","source":"s",
             "memory":{"estimate":0.72,"low":0.65,"high":0.79,"coverage_pct":0.8},
             "performance":{"estimate":0.6,"low":0.5,"high":0.7},
             "readiness":{"shown":true,"estimate":711,"low":678,"high":748,"reasons":[],
               "coverage_pct":0.82,"confidence":"medium","best_next_topic":"topic::algebra::linear"}}
            """.trimIndent()
        val lines = GreScorecard.parse(json)!!.readinessLines()
        // the number + range...
        assertTrue("shows the score", lines.any { it.contains("711") })
        // ...AND the full evidence panel alongside it (a bare number is an automatic fail)
        assertTrue("confidence shown", lines.any { it.contains("Confidence") })
        assertTrue("coverage shown", lines.any { it.contains("Coverage") })
        assertTrue("best-next shown", lines.any { it.contains("Best next") })
    }

    @Test
    fun gatedReadinessShowsReasonsAndNoBareNumber() {
        val json =
            """
            {"version":1,"updated_at":"t","source":"s","memory":{},"performance":{},
             "readiness":{"shown":false,"estimate":null,"reasons":["<200 graded reviews"],
               "coverage_pct":0.0,"confidence":"low","best_next_topic":"topic::calculus::differential_single"}}
            """.trimIndent()
        val lines = GreScorecard.parse(json)!!.readinessLines()
        assertTrue("states it is gated", lines.any { it.contains("Not shown yet") })
        assertTrue("lists the reason", lines.any { it.contains("<200 graded reviews") })
        assertFalse("no bare numeric score line", lines.any { it.trimStart().matches(Regex("\\d{3}.*")) })
    }
}
