package com.example.stockapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.stockapplication.data.formatDayLabelResource

/**
 * Resolves a day heading ("Today", "Yesterday" or a short date) with the current locale.
 *
 * The string resource depends on the wall clock, so it is looked up here rather than passed in as
 * an already-formatted string from a caller that has no access to resources. Takes a raw day-start
 * timestamp so both [com.example.stockapplication.data.DaySales] and
 * [com.example.stockapplication.data.DayOrders] can use it.
 */
@Composable
fun rememberDayLabel(dayStartMillis: Long): String =
    stringResource(formatDayLabelResource(dayStartMillis), dayStartMillis)
