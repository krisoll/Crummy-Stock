package com.example.stockapplication.ui.screens.personalize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppFontFamily
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.TextSize
import com.example.stockapplication.ui.theme.fontFamilyFor

/**
 * How the app's text is drawn: the typeface and the size.
 *
 * The two are grouped because they answer the same question — "what does the text look like" — and
 * because they are the two settings that act on *every* screen at once rather than on one
 * component. Both are picks, so both commit immediately; there is nothing to preview or apply.
 *
 * The text **colour** stays on the Colours page with the other four colour channels: it is chosen
 * by dragging a wheel and doesn't share a control or a mental model with these two radio lists.
 */
@Composable
internal fun TextPage(
    settings: AppSettings,
    onBack: () -> Unit,
    onFontFamilyChange: (AppFontFamily) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
) {
    // Resolved once per recomposition of the settings rather than per row: `fontFamilyFor` builds
    // an `AppSettings` wrapper, and the picker renders a dozen families.
    val selectedFamily = AppFontFamily.fromId(settings.fontFamilyId)
    val selectedSize = TextSize.fromId(settings.textSizeId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonalizeHeader(
            title = stringResource(R.string.settings_text),
            description = stringResource(R.string.settings_text_description),
            backLabel = stringResource(R.string.settings_back_to_personalize),
            onBack = onBack,
        )

        // ---- Font family -----------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_font),
            description = stringResource(R.string.settings_font_description)
        ) {
            AppFontFamily.entries.forEach { family ->
                ChoiceRow(
                    label = stringResource(family.labelRes),
                    selected = selectedFamily == family,
                    onSelect = { onFontFamilyChange(family) },
                    // Each option is drawn in its own typeface, so the list previews itself:
                    // "Lacquer" in Lacquer answers the question the label alone cannot.
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = family.toComposeFontFamily()
                    ),
                )
            }
        }

        // ---- Text size -------------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_text_size),
            description = stringResource(R.string.settings_text_size_description)
        ) {
            TextSize.entries.forEach { size ->
                ChoiceRow(
                    label = stringResource(size.labelRes),
                    selected = selectedSize == size,
                    onSelect = { onTextSizeChange(size) },
                    // Drawn at the size being offered, so "Large" is visibly larger than "Small"
                    // before the user commits to it. Scaled from bodyLarge to match the row the
                    // choice will actually apply to, not from a display style that never appears
                    // in a list.
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * size.scale,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * size.scale,
                    ),
                )
            }

        }
    }
}

/**
 * Lets each radio row render its own label in its own typeface.
 *
 * Built by hand rather than by calling `fontFamilyFor(settings)`: that function takes the whole
 * `AppSettings`, and the only field it reads is the font id, so constructing a throwaway copy per
 * option avoided meaning there was no need for a second, narrower overload.
 */
private fun AppFontFamily.toComposeFontFamily(): FontFamily =
    fontFamilyFor(AppSettings(fontFamilyId = id))
