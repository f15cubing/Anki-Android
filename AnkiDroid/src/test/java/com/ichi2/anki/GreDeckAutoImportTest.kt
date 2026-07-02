/*
 * Copyright (c) 2026 Ankitects Pty Ltd <http://apps.ankiweb.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManager
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

class GreDeckAutoImportTest : JvmTest() {
    /**
     * Disk-based collection dir so [importAnkiPackage] has a real media folder.
     * Created once per test; cleaned up in [tearDownDiskCollection].
     */
    private val testColDir: File = Files.createTempDirectory("gre-test-col-").toFile()

    override val collectionManager =
        object : InMemoryCollectionManager() {
            override val collectionFiles: CollectionFiles
                get() = CollectionFiles.FolderBasedCollection(testColDir)
        }

    @After
    fun tearDownDiskCollection() {
        testColDir.deleteRecursively()
    }

    /** Resolve the bundled deck from the AnkiDroid module source tree. */
    private fun apkgFile(): File {
        // Gradle runs unit tests with CWD set to the module root (AnkiDroid/).
        val fromModuleRoot = File("src/main/assets/gre-study-deck.apkg")
        if (fromModuleRoot.exists()) return fromModuleRoot

        // Fallback: if CWD is the repo root (e.g. some IDE configurations).
        return File("AnkiDroid/src/main/assets/gre-study-deck.apkg")
    }

    @Test
    fun `first import loads deck and stamps version`() {
        val apkgFile = apkgFile()
        assertTrue(apkgFile.exists(), "apkg asset must exist at: ${apkgFile.absolutePath}")

        val imported = importGreDeckIntoCollection(col, apkgFile.absolutePath)

        assertTrue(imported, "importGreDeckIntoCollection must return true on first run")
        assertThat(
            "card count should exceed 5000 after import",
            col.cardCount(),
            greaterThan(5000),
        )
        assertThat(
            "config key must be stamped with GRE_DECK_VERSION",
            col.config.get<String>("gre_deck_version"),
            equalTo(GRE_DECK_VERSION),
        )
    }

    @Test
    fun `second import is a no-op (idempotent)`() {
        val apkgFile = apkgFile()
        assertTrue(apkgFile.exists(), "apkg asset must exist at: ${apkgFile.absolutePath}")

        // First pass
        importGreDeckIntoCollection(col, apkgFile.absolutePath)
        val countAfterFirst = col.cardCount()

        // Second pass — must be a no-op
        val importedAgain = importGreDeckIntoCollection(col, apkgFile.absolutePath)

        assertThat("second call must return false (already at GRE_DECK_VERSION)", importedAgain, equalTo(false))
        assertThat(
            "card count must not change on second import",
            col.cardCount(),
            equalTo(countAfterFirst),
        )
    }
}
