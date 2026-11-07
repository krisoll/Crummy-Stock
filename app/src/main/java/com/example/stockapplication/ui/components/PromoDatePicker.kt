package com.example.stockapplication.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.example.stockapplication.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A one-date picker for a promo's start or end bound.
 *
 * Wraps Material 3's [DatePicker] because it deals in **UTC midnight** millis while the rest of the
 * app reasons in local time. Passing its value straight into a promo would put the boundary at the
 * wrong hour for anyone not in UTC — a promo meant to start "today" would begin at 22:00 yesterday
 * in some zones and be active a day early.
 *
 * @param initial the currently stored bound, or null for "not set".
 * @param onPick receives local-midnight millis for the chosen day.
 * @param latestSelectableMillis when non-null, days after this local day are disabled. Used by the
 *   material-entry editor, where a purchase cannot be dated in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePickerDialog(
    initial: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    latestSelectableMillis: Long? = null,
) {
    val selectableDates = remember(latestSelectableMillis) {
        if (latestSelectableMillis == null) {
            DatePickerDefaults.AllDates
        } else {
            val latestUtc = localToUtcPickerMillis(latestSelectableMillis)
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= latestUtc
            }
        }
    }
    // Seeded from the stored value so reopening the picker shows the day already chosen rather
    // than snapping back to today.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::localToUtcPickerMillis),
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis
                    if (picked == null) onDismiss() else onPick(utcPickerMillisToLocal(picked))
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

/** Local midnight of the given day, as epoch millis. */
fun localDayStart(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Converts a stored local-midnight value into the UTC-midnight millis the picker expects.
 *
 * Done by rebuilding the calendar in UTC from the local day-of-month rather than by adding a fixed
 * offset: offsets move with daylight saving, so arithmetic on the millis drifts by an hour twice a
 * year and would occasionally land the picker on the wrong day.
 */
private fun localToUtcPickerMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/** The inverse: a UTC-midnight pick becomes local midnight of the same calendar day. */
private fun utcPickerMillisToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    val localSameDay = Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
        )
    }
    return localDayStart(localSameDay.timeInMillis)
}

/**
 * Renders a stored date bound as a short local date, e.g. "5 May 2025".
 *
 * Resolved through [DateFormat] rather than a fixed pattern so it follows the device locale. It is
 * remembered per value because the editor recomposes on every keystroke in the form.
 */
@Composable
fun formatDateLabel(millis: Long): String = remember(millis) {
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(millis))
}
