package com.example.stockapplication

import com.example.stockapplication.data.OnboardingProgress
import com.example.stockapplication.data.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the first-run flow's ordering and resumption rules.
 *
 * The order matters in a way that is not obvious from the enum: language is first because choosing
 * it recreates the activity and re-renders every later step in that language. If someone "tidies"
i  * the steps into a more natural sequence, a Spanish user would read the rest of onboarding in
 * English.
 *
 * The app is free and open source, so there is no trial disclosure step. Every remaining step is a
 * preference with a sensible default, which is why all of them are skippable.
 */
class OnboardingStateTest {

    @Test
    fun languageComesFirstBecauseItChangesTheLanguageOfEverythingElse() {
        assertEquals(OnboardingStep.LANGUAGE, OnboardingStep.entries.first())
    }

    @Test
    fun theFlowEndsOnASettingsStep() {
        assertEquals(OnboardingStep.SHOW_DECIMALS, OnboardingStep.entries.last())
        assertTrue(OnboardingStep.SHOW_DECIMALS.isLast)
    }

    @Test
    fun nextWalksForwardAndStopsAtTheEnd() {
        assertEquals(OnboardingStep.CURRENCY, OnboardingStep.LANGUAGE.next())
        assertEquals(OnboardingStep.SHOW_DECIMALS, OnboardingStep.CURRENCY.next())
        // Still at the end rather than wrapping to the start.
        assertEquals(OnboardingStep.SHOW_DECIMALS, OnboardingStep.SHOW_DECIMALS.next())
    }

    @Test
    fun previousWalksBackAndStopsAtTheStart() {
        assertEquals(OnboardingStep.LANGUAGE, OnboardingStep.LANGUAGE.previous())
        assertEquals(OnboardingStep.CURRENCY, OnboardingStep.SHOW_DECIMALS.previous())
        assertEquals(OnboardingStep.LANGUAGE, OnboardingStep.CURRENCY.previous())
    }

    @Test
    fun everyStepIsSkippable() {
        // Nothing here carries a disclosure, so forcing a choice would just be friction.
        OnboardingStep.entries.forEach { step ->
            assertTrue("${step.name} should be skippable", step.isSkippable)
        }
    }

    @Test
    fun onlyTheLastStepIsLast() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step == OnboardingStep.SHOW_DECIMALS, step.isLast)
        }
    }

    @Test
    fun aFreshInstallStartsAtTheBeginning() {
        val fresh = OnboardingProgress.Fresh
        assertFalse(fresh.isComplete)
        assertEquals(OnboardingStep.LANGUAGE, fresh.step)
    }

    @Test
    fun aResumedFlowReturnsToTheSameStep() {
        // Process death mid-flow must not restart setup from scratch.
        val resumed = OnboardingProgress.stored(
            complete = false,
            stepName = OnboardingStep.SHOW_DECIMALS.name,
        )
        assertFalse(resumed.isComplete)
        assertEquals(OnboardingStep.SHOW_DECIMALS, resumed.step)
    }

    @Test
    fun aRemovedStepStoredOnAnOldDeviceFallsBackToTheBeginning() {
        // A device that finished a build with a `TRIAL_NOTICE` step still has that name in
        // DataStore. It must not crash, and the completion flag must still win.
        val resumed = OnboardingProgress.stored(complete = false, stepName = "TRIAL_NOTICE")
        assertEquals(OnboardingStep.LANGUAGE, resumed.step)
    }

    @Test
    fun anUnknownStepFallsBackToTheBeginning() {
        val resumed = OnboardingProgress.stored(complete = false, stepName = "SOMETHING_ELSE")
        assertEquals(OnboardingStep.LANGUAGE, resumed.step)
    }

    @Test
    fun completionWinsOverAStaleStep() {
        // The flag is authoritative: once finished, no stored step can reopen the flow part-way.
        val done = OnboardingProgress.stored(complete = true, stepName = OnboardingStep.CURRENCY.name)
        assertTrue(done.isComplete)
    }
}
