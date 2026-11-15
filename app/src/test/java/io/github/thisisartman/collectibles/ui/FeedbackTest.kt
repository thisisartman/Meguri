package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The subject is what tools' pull script looks for (Feedback/pull_feedback.py matches the tag). */
class FeedbackTest {
    @Test fun subjectCarriesTheTagAndTheFirstLine() {
        assertEquals("[Meguri feedback] Hakkaisan needs a goshuin slot", Feedback.subject("\\n  Hakkaisan needs a goshuin slot\\nmore detail".replace("\\n", "\n")))
        assertEquals("[Meguri feedback] (no text)", Feedback.subject("   "))
        assertTrue(Feedback.subject("x".repeat(200)).length <= Feedback.TAG.length + 1 + 60)
    }
}
