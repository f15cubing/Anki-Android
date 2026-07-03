/*
 * Copyright (c) 2026 Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Read-only view of the desktop-authoritative three-score `gre_scorecard`
 * (synced via `col.conf`). No scoring math on device — parse + display only.
 *
 * The three scores are shown **separately** (never blended); Readiness shows a
 * number only when the desktop gate passed, otherwise the evidence panel (reasons
 * + best-next topic + coverage) — never a bare number.
 */
class GreScorecardFragment : Fragment() {
    private var column: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val col =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20))
            }
        column = col
        return ScrollView(requireContext()).apply { addView(col) }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().setTitle(R.string.gre_scorecard_title)
        lifecycleScope.launch {
            val raw = withCol { config.getObject(GreScorecard.CONFIG_KEY, JSONObject()).toString() }
            render(GreScorecard.parse(raw))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        column = null
    }

    private fun render(card: GreScorecard?) {
        val col = column ?: return
        col.removeAllViews()
        if (card == null || card.version == 0) {
            col.addView(body("No score card synced yet. Open the GRE readiness dashboard on desktop, then sync."))
            return
        }
        col.addView(header("GRE readiness"))
        col.addView(body("Read-only — computed on desktop, synced here."))

        col.addView(sectionTitle("Memory — FSRS recall"))
        col.addView(body(card.memoryLine()))

        col.addView(sectionTitle("Performance — P(correct) on a new item"))
        col.addView(body(card.performanceLine()))

        // Readiness: number + range only when the desktop gate passed; the evidence
        // panel (reasons / confidence / coverage / best-next) is always present — a
        // Readiness number is never shown bare (honesty ceiling).
        col.addView(sectionTitle("Readiness — projected GRE 200–990"))
        card.readinessLines().forEach { col.addView(body(it)) }

        col.addView(footer("Last updated: ${card.updatedAt}"))
        col.addView(footer(card.source))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(t: String) =
        TextView(requireContext()).apply {
            text = t
            textSize = 22f
            setPadding(0, dp(4), 0, dp(10))
        }

    private fun sectionTitle(t: String) =
        TextView(requireContext()).apply {
            text = t
            textSize = 16f
            setPadding(0, dp(14), 0, dp(2))
        }

    private fun body(t: String) =
        TextView(requireContext()).apply {
            text = t
            textSize = 15f
        }

    private fun footer(t: String) =
        TextView(requireContext()).apply {
            text = t
            textSize = 12f
            alpha = 0.7f
            setPadding(0, dp(10), 0, 0)
        }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, GreScorecardFragment::class)
    }
}
