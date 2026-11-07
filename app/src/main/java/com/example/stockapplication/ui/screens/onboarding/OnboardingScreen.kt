package com.example.stockapplication.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppLanguage
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.Currency
import com.example.stockapplication.data.OnboardingStep

/**
 * The first-run flow.
 *
 * ## Why this is a separate screen and not a dialog
 *
 * It runs *before* [com.example.stockapplication.ui.StockApp], so the app has never been seen and
 * there is no navigation state to fight with. That also means it can safely recreate the activity
 * for a language change without losing the user's place — the step index is persisted, so the flow
 * returns to exactly where it was.
 *
 * ## Why every choice is written straight through
 *
 * Each step calls the same `SettingsViewModel` setter the settings screen uses, so nothing is held
 * in a draft. That is what makes "Skip" honest: skipping leaves the current value, which is the
 * app's default, which is exactly what the user is telling us they are happy with.
 */
@Composable
fun OnboardingScreen(
    settings: AppSettings,
    step: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onShowDecimalsChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            StepIndicator(step)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    OnboardingStep.LANGUAGE -> LanguageStep(
                        current = AppLanguage.fromTag(settings.languageTag),
                        onLanguageChange = onLanguageChange,
                    )

                    OnboardingStep.CURRENCY -> CurrencyStep(
                        current = settings.currency,
                        onCurrencyChange = onCurrencyChange,
                    )

                    OnboardingStep.SHOW_DECIMALS -> ShowDecimalsStep(
                        showDecimals = settings.showMoneyDecimals,
                        symbol = settings.currency.symbol.trim(),
                        onShowDecimalsChange = onShowDecimalsChange,
                    )
                }
            }

            NavigationRow(
                step = step,
                onStepChange = onStepChange,
                onFinish = onFinish,
            )
        }
    }
}

/** "Step 2 of 4", plus a row of dots so the length of the flow is obvious at a glance. */
@Composable
private fun StepIndicator(step: OnboardingStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(
                R.string.onboarding_step_of,
                step.ordinal + 1,
                OnboardingStep.entries.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingStep.entries.forEach { dot ->
                Surface(
                    modifier = Modifier.height(6.dp).fillMaxWidth(0.06f),
                    shape = RoundedCornerShape(3.dp),
                    color = if (dot.ordinal <= step.ordinal) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {}
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Language, first.
 *
 * Choosing here calls `AppCompatDelegate.setApplicationLocales`, which recreates the activity — so
 * the remaining steps re-render in the chosen language immediately. Nothing special is needed to
 * make that happen; it falls out of persisting the step rather than the selected language.
 */
@Composable
private fun LanguageStep(
    current: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.onboarding_language_title),
        description = stringResource(R.string.onboarding_language_description),
    )
    Card {
        AppLanguage.entries.forEach { language ->
            ChoiceRow(
                selected = current == language,
                label = stringResource(language.labelRes),
                onClick = { onLanguageChange(language) },
            )
        }
    }
}

/** The currency symbol list, reusing the same [Currency] enum the settings screen shows. */
@Composable
private fun CurrencyStep(
    current: Currency,
    onCurrencyChange: (Currency) -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.onboarding_currency_title),
        description = stringResource(R.string.onboarding_currency_description),
    )
    Card {
        Currency.entries.forEach { option ->
            ChoiceRow(
                selected = current == option,
                // The symbol is what the user is choosing and what appears on every receipt;
                // `.trim()` because `CHF ` carries a trailing space for the amount prefix.
                label = "${option.symbol.trim()}  ${stringResource(option.labelRes)}",
                onClick = { onCurrencyChange(option) },
            )
        }
    }
}

/** The cents on/off toggle, with a sample amount so the effect is visible rather than described. */
@Composable
private fun ShowDecimalsStep(
    showDecimals: Boolean,
    symbol: String,
    onShowDecimalsChange: (Boolean) -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.onboarding_decimals_title),
        description = stringResource(R.string.onboarding_decimals_description),
    )
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowDecimalsChange(!showDecimals) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_show_decimals),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.settings_show_decimals_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = showDecimals, onCheckedChange = onShowDecimalsChange)
        }

        HorizontalDivider()

        // A live sample in the user's own currency, so "$12.99" versus "$13" is visible rather than
        // something the description has to spell out.
        Text(
            text = if (showDecimals) "$symbol 12.99" else "$symbol 13",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Back / Skip on the left, Next / Finish on the right. */
@Composable
private fun NavigationRow(
    step: OnboardingStep,
    onStepChange: (OnboardingStep) -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // No "Back" on the first step: there is nowhere to go, and a disabled button there would
        // just be clutter on the very first screen the user ever sees.
        if (step != OnboardingStep.LANGUAGE) {
            TextButton(onClick = { onStepChange(step.previous()) }) {
                Text(stringResource(R.string.onboarding_back))
            }
        }

        Spacer(Modifier.weight(1f))

        // Skip only where the default is a genuinely fine answer.
        if (step.isSkippable) {
            TextButton(onClick = { onStepChange(step.next()) }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Button(
            onClick = { if (step.isLast) onFinish() else onStepChange(step.next()) },
        ) {
            Text(
                stringResource(
                    if (step.isLast) R.string.onboarding_finish else R.string.onboarding_next,
                ),
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card tone the settings screen uses, so onboarding and settings feel like one app. */
@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
