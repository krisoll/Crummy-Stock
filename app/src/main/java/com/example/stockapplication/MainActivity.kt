package com.example.stockapplication

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stockapplication.data.OnboardingProgress
import com.example.stockapplication.data.ScanBeepStore
import com.example.stockapplication.ui.StockApp
import com.example.stockapplication.ui.screens.onboarding.OnboardingScreen
import com.example.stockapplication.ui.theme.StockApplicationTheme
import com.example.stockapplication.ui.viewmodel.AppViewModelFactory
import com.example.stockapplication.ui.viewmodel.CartViewModel
import com.example.stockapplication.ui.viewmodel.InventoryViewModel
import com.example.stockapplication.ui.viewmodel.SettingsViewModel
import com.example.stockapplication.ui.viewmodel.SuppliesViewModel
import kotlinx.coroutines.launch

/**
 * Extends [AppCompatActivity] rather than ComponentActivity because per-app language selection
 * (AppCompatDelegate.setApplicationLocales) relies on AppCompat to recreate the activity and
 * re-resolve resources when the locale changes.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as StockApplication

        setContent {
            val inventoryViewModel: InventoryViewModel = viewModel(
                factory = AppViewModelFactory.factory(app)
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(app)
            )
            val cartViewModel: CartViewModel = viewModel(
                factory = CartViewModel.factory(app)
            )
            val suppliesViewModel: SuppliesViewModel = viewModel(
                factory = SuppliesViewModel.factory(app)
            )
            // The theme is hoisted above the app so colour changes apply instantly.
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val scope = rememberCoroutineScope()
            val repository = app.settingsRepository
            val onboarding by repository.onboarding.collectAsStateWithLifecycle(
                initialValue = OnboardingProgress.Fresh,
            )

            StockApplicationTheme(settings = settings) {
                if (onboarding.isComplete) {
                    StockApp(
                        viewModel = inventoryViewModel,
                        settingsViewModel = settingsViewModel,
                        cartViewModel = cartViewModel,
                        suppliesViewModel = suppliesViewModel,
                    )
                } else {
                    // The step is persisted as it is shown, so a process death or the activity
                    // recreation that a language change triggers resumes here rather than
                    // restarting the flow.
                    LaunchedEffect(onboarding.step) {
                        repository.setOnboardingStep(onboarding.step)
                    }
                    OnboardingScreen(
                        settings = settings,
                        step = onboarding.step,
                        onStepChange = { scope.launch { repository.setOnboardingStep(it) } },
                        onLanguageChange = settingsViewModel::setLanguage,
                        onCurrencyChange = settingsViewModel::setCurrency,
                        onShowDecimalsChange = settingsViewModel::setShowMoneyDecimals,
                        onFinish = { scope.launch { repository.completeOnboarding() } },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The scanner releases its own pool when the camera closes; the sale chime's pool lives as
        // long as the activity so it is not re-decoded for every sale. This is where it is finally
        // freed, so a long-lived process does not keep an audio buffer alive after the UI is gone.
        // Only on a real finish: a configuration change would otherwise leave the surviving
        // activity re-decoding the chime on the next sale, which is harmless but pointless.
        if (isFinishing) ScanBeepStore.releaseSalePool()
    }
}
