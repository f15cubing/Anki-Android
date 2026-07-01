/*
 * Copyright (c) 2026 Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki.libanki.stats

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.ext.addNote
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Test

/**
 * Proves the W1 mastery-query RPC is reachable from Kotlin through our locally-built
 * rsdroid backend (which bundles f15cubing/anki@ea3acae). Runs on the host JVM via
 * the rsdroid-testing native lib, exercising the real compiled rslib — not a mock.
 */
class MasteryQueryTest : InMemoryAnkiTest() {
    @Test
    fun masteryQuery_is_reachable_and_rolls_up_hierarchically() {
        val leafTag = "topic::calculus::integral_single"
        val basic = col.notetypes.byName("Basic")!!
        repeat(2) { i ->
            val note = col.newNote(basic)
            note.setItem("Front", "front$i")
            note.setItem("Back", "back$i")
            note.addTag(leafTag)
            col.addNote(note)
        }

        val rows = col.masteryQuery(listOf(leafTag, "topic::calculus"))

        // one row per requested topic, in request order
        assertThat(rows.size, equalTo(2))
        val leaf = rows.first { it.topic == leafTag }
        val bucket = rows.first { it.topic == "topic::calculus" }
        assertThat(leaf.totalCards, greaterThanOrEqualTo(2))
        // the parent bucket tag matches the leaf's cards hierarchically (::*)
        assertThat(bucket.totalCards, greaterThanOrEqualTo(leaf.totalCards))
    }
}
