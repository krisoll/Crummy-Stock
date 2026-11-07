package com.example.stockapplication.data

/**
 * The steps of the first-run flow, in the order they are shown.
 *
 * [LANGUAGE] is first for a specific reason rather than by taste: it is the only step whose choice
 * changes the *language of the rest of the flow*. `AppCompatDelegate.setApplicationLocales`
 * recreates the activity and re-resolves every string, so picking Spanish here makes the currency
 * and decimals steps appear in Spanish immediately. Put language last and a Spanish user would read
 * the entire onboarding in a language they may not speak.
 *
 * The app is free and open source, so there is no trial or purchase to disclose and no
 * `TRIAL_NOTICE` step. Every remaining step is a preference with a sensible default.
 */
enum class OnboardingStep {
    LANGUAGE,
    CURRENCY,
    SHOW_DECIMALS,
    ;

    /**
     * True when the user may jump past this step without choosing anything.
     *
     * Every step is optional: the defaults are sensible, so forcing a choice is friction for no
     * benefit. This would only change if a step ever carried a disclosure the user must read.
     */
    val isSkippable: Boolean get() = true

    val isLast: Boolean get() = ordinal == entries.lastIndex

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { this }

    /** Back from the first step stays on the first step rather than leaving the flow. */
    fun previous(): OnboardingStep = entries.getOrElse(ordinal - 1) { this }
}

/**
 * Whether onboarding is finished, and where to resume if it is not.
 *
 * Persisting the *step* as well as the completion flag is what makes the flow survivable: Android
 * kills and recreates the activity routinely (rotation, locale change, process death under memory
 * pressure), and restarting a half-finished setup from step one each time would read as a bug.
 */
data class OnboardingProgress(
    val isComplete: Boolean,
    val step: OnboardingStep,
) {
    companion object {
        /** A fresh install: not done, at the beginning. */
        val Fresh = OnboardingProgress(isComplete = false, step = OnboardingStep.LANGUAGE)

        /** Resolves a stored step name, tolerating a value written by a future version. */
        fun stored(complete: Boolean, stepName: String?): OnboardingProgress {
            if (complete) {
                return OnboardingProgress(isComplete = true, step = OnboardingStep.LANGUAGE)
            }
            val step = OnboardingStep.entries.firstOrNull { it.name == stepName }
                ?: OnboardingStep.LANGUAGE
            return OnboardingProgress(isComplete = false, step = step)
        }
    }
}
