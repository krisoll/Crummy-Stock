package com.example.stockapplication.ui.screens.personalize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The building blocks shared by every page under Personalize.
 *
 * These were private helpers inside the single `PersonalizeScreen.kt`; splitting that file into one
 * page per group means they now need to be visible across files, so they live here rather than
 * being duplicated four times.
 */

/**
 * The title row shared by the landing page and all four group pages.
 *
 * A back button rather than the system gesture alone: Personalize is pushed over the Config tab
 * inside the same navigation destination, so there is no tab of its own to return to and the
 * affordance has to be visible. Every page reuses the row with its own title, so they are told
 * apart by the text rather than by different header designs.
 *
 * @param backLabel spoken description of the back arrow; the label differs between "back to
 *   Config" and "back to Personalize", which are genuinely different navigations.
 */
@Composable
internal fun PersonalizeHeader(
    title: String,
    description: String,
    backLabel: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = backLabel,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A titled card holding a group of controls.
 *
 * [content] is a `ColumnScope` so a section can stack its rows and sliders without wrapping them
 * in another column just to get the shared spacing.
 */
@Composable
internal fun PersonalizeSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            // Matching the Config tab's density (see `SettingsSection` there): these pages are
            // the same kind of long list, and the two should not feel like different apps.
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Explanations, not content: many of these wrapped to three lines and pushed
                    // the controls they describe off the screen.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

/**
 * The row that opens one of the group sub-pages.
 *
 * Styled like [PersonalizeSection] and sitting in its place on the landing page, so the four
 * entries read as the page's real content rather than as a list of links bolted on top. The
 * chevron says "there is more behind this"; the optional [badge] says something is waiting there.
 *
 * @param badge shown under the description when non-null. Used by Colours to flag an unapplied
 *   draft, and null the rest of the time so a page nobody has touched stays quiet.
 */
@Composable
internal fun PersonalizeEntryCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Only when there is something to say: an always-visible "0 pending" line is
                // noise on a page most people open to change the font.
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        // A badge is words, so it follows the font colour. Weight carries the
                        // emphasis; the accent would have made it fight the heading above it.
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A label with a switch on the far end, for the on/off settings. */
@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One option in a mutually-exclusive list: a radio button plus its label.
 *
 * Replaces the four near-identical `Row { RadioButton; Text }` blocks that used to be pasted into
 * the font, animation-speed, spin, beep, sale-sound and vibration lists. The whole row is the hit
 * target, and [onSelect] fires from both the row and the button — a radio button that ignores taps
 * on its own label is a common and irritating dead zone.
 *
 * @param label drawn with [style] so the font picker can preview the typeface in the option itself.
 * @param supporting optional second line under the label, e.g. a vibration pattern's duration.
 */
@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            // 4 dp rather than 6: this is a list of radio options, and a dozen of them at 6 dp
            // each is a third of a screen of pure gap. Ripple and radio button keep the row well
            // above the minimum touch target.
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        // A column only when there is a second line, so single-label rows keep the radio button's
        // first-line baseline alignment with the text.
        if (supporting == null) {
            Text(label, style = style)
        } else {
            Column {
                Text(label, style = style)
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A labelled dp slider with the current value shown in dp.
 *
 * The value is shown numerically as well as positionally because the sliders here are the only
 * controls whose effect is a measurement — "96 dp" is checkable in a bug report in a way that
 * "about three-quarters along" is not.
 */
@Composable
internal fun SizeSlider(
    label: String,
    value: Int,
    range: IntRange,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Whole dp steps: fractional dp on an icon is not meaningfully visible, and rounding
            // inside the callback would otherwise fill DataStore with hundreds of near-identical
            // writes during a single drag.
            steps = range.last - range.first - 1,
            modifier = Modifier.fillMaxWidth(),
            colors = appSliderColors(),
        )
    }
}

/**
 * Colours for a [Slider] that stays legible on a coloured panel.
 *
 * Material's defaults are unreadable on this app's palette. The active track and the thumb are
 * `primary` — the **button fill** colour — while the inactive track is `surfaceVariant`, which is
 * the **panel** colour. With a green panel, that produced a green track with a green thumb and a
 * green remainder: the control disappeared into the card it sits on.
 *
 * The fix follows the same split the rest of the app now uses: the accent owns the part that means
 * *how far along the value is*, and the text colour owns everything that has to be seen for the
 * control to be usable at all — the thumb and the unfilled remainder.
 *
 * - **thumb** — text colour, so the current position is always findable.
 * - **active track** — the button colour, which is the accent; on the default palette it is now a
 *   deeper green than the panel, so the filled part reads.
 * - **inactive track** — the text colour at low opacity. A full-strength remainder would look like
 *   a second active track; at 32% it is unmistakably "the rest of the range" while still being
 *   visible against the panel.
 */
@Composable
internal fun appSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurface,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
    // The little tick dots between discrete steps read as noise on a saturated panel and are
    // invisible at their default `outline` strength here, so they take the remainder's colour.
    activeTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
    inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
    // Disabled still has to be findable, just visibly muted.
    disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
)

/** A one-line caption, dimmed, used for the explanatory text under a control. */
@Composable
internal fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A small bold label that introduces a cluster of controls inside a section. */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
}
