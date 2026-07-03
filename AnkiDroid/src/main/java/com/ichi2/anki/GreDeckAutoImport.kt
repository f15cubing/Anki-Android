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

import android.content.Context
import anki.import_export.ImportAnkiPackageUpdateCondition
import anki.import_export.importAnkiPackageOptions
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import java.io.File

/** Monotonically increasing version tag — bump when the deck content changes. */
const val GRE_DECK_VERSION = "2026-07-03"

private const val CONFIG_KEY = "gre_deck_version"

// Note GUIDs are derived from a stable, rendering-independent uid (pipeline
// build_deck). A deck bundled under the OLD content-hash scheme can't be matched
// by GUID, so a plain re-import would duplicate the whole deck. We stamp the
// scheme we imported under this key; on a mismatch we run the one-time cleanup
// below before importing. (Mirrors desktop deck_autoimport.py.)
private const val GUID_SCHEME_KEY = "gre_deck_guid_scheme"
private const val GUID_SCHEME = "uid"

// The two note types the bundled deck ships (see pipeline/build_deck.py); used to
// identify previously-bundled notes for the one-time pre-uid cleanup.
private val BUNDLED_NOTETYPES =
    listOf(
        "GRE Math Basic (leaf-tagged)",
        "GRE Math MCQ (leaf-tagged)",
    )

/**
 * One-time migration off the legacy content-hash GUID scheme: remove the
 * previously-bundled notes (identified by our two note types) so the following
 * import lays down uid-GUID cards instead of duplicating the deck. A fresh
 * install has no such notes, so this is a harmless no-op there.
 */
private fun removePreUidBundledNotes(col: Collection) {
    val noteIds = BUNDLED_NOTETYPES.flatMap { col.findNotes("note:\"$it\"") }
    if (noteIds.isNotEmpty()) {
        col.removeNotes(noteIds = noteIds)
    }
}

/**
 * Performs the actual import of the GRE study deck from [apkgPath] into [col].
 *
 * Returns `true` if the import ran, `false` if the stored version already matches
 * [GRE_DECK_VERSION] (idempotent no-op path).
 *
 * Exposed as `internal` so host-JVM tests can call it directly with a real
 * [Collection] without needing an Android [Context].
 */
internal fun importGreDeckIntoCollection(
    col: Collection,
    apkgPath: String,
): Boolean {
    val current = col.config.get<String>(CONFIG_KEY)
    if (current == GRE_DECK_VERSION) return false

    // One-time cleanup when the previously-bundled deck predates the uid GUID
    // scheme; otherwise a GUID-mismatched re-import would duplicate the deck.
    if (col.config.get<String>(GUID_SCHEME_KEY) != GUID_SCHEME) {
        removePreUidBundledNotes(col)
    }

    val options =
        importAnkiPackageOptions {
            mergeNotetypes = true
            updateNotes = ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_IF_NEWER
            updateNotetypes = ImportAnkiPackageUpdateCondition.IMPORT_ANKI_PACKAGE_UPDATE_CONDITION_IF_NEWER
            withScheduling = false
            withDeckConfigs = false
        }
    col.importAnkiPackage(apkgPath, options)
    col.config.set(CONFIG_KEY, GRE_DECK_VERSION)
    col.config.set(GUID_SCHEME_KEY, GUID_SCHEME)
    return true
}

/**
 * Version-gated auto-import of the GRE study deck bundled as an app asset.
 *
 * - Returns immediately if [GRE_DECK_VERSION] is already recorded in the collection config.
 * - Otherwise copies the asset to a temp file, imports it via [withCol], stamps the version,
 *   and deletes the temp file.
 * - Must be called from a coroutine; [withCol] dispatches to the correct thread.
 */
suspend fun maybeImportGreDeck(context: Context) {
    val current = withCol { config.get<String>(CONFIG_KEY) }
    if (current == GRE_DECK_VERSION) return

    val tmp = File.createTempFile("gre-study-deck", ".apkg", context.cacheDir)
    try {
        context.assets.open("gre-study-deck.apkg").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        withCol { importGreDeckIntoCollection(this, tmp.absolutePath) }
    } finally {
        tmp.delete()
    }
}
