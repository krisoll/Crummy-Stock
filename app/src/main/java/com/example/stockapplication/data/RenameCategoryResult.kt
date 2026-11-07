package com.example.stockapplication.data

/**
 * What happened when a category was renamed.
 *
 * An explicit result rather than a boolean or a thrown exception because the three outcomes mean
 * genuinely different things to the user, and only one of them is worth a message: an empty name
 * and a clash both leave the list exactly as it was, so the screen has to know which explanation
 * to give rather than reporting a generic failure.
 */
enum class RenameCategoryResult {
    /** Written. */
    Renamed,

    /** The name was blank once trimmed, so nothing was written. */
    Empty,

    /** Another category already holds that name (case-insensitively). */
    Duplicate,
}
