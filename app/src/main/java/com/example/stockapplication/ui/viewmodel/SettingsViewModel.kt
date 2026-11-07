package com.example.stockapplication.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.stockapplication.StockApplication
import com.example.stockapplication.data.AppLanguage
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.ArgbColor
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.DefaultLoadingIconId
import com.example.stockapplication.data.LoadingIconLottieId
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.InventoryViewMode
import com.example.stockapplication.data.ImportedSettings
import com.example.stockapplication.data.LoadingImageStore
import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.DefaultSaleAnimationId
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.data.SaleAnimationLottieId
import com.example.stockapplication.data.SaleAnimationStore
import com.example.stockapplication.data.SaleSound
import com.example.stockapplication.data.TextSize
import com.example.stockapplication.data.ScanBeep
import com.example.stockapplication.data.ScanBeepStore
import com.example.stockapplication.data.ScanVibration
import com.example.stockapplication.data.SettingsRepository
import com.example.stockapplication.data.Currency
import com.example.stockapplication.data.showCents
import com.example.stockapplication.data.currencySymbol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Colours the user is currently editing but has not committed yet.
 *
 * Dragging inside an HSV picker can produce dozens of events per second. Writing every one to
 * DataStore re-emits [AppSettings], which recomposes the whole app (and re-derives the Material
 * colour scheme) on each frame — that is what made the picker feel laggy. Edits now stay local to
 * this draft until the user taps Apply.
 */
data class ColorDraft(
    val backgroundArgb: ArgbColor? = null,
    val buttonArgb: ArgbColor? = null,
    val panelArgb: ArgbColor? = null,
    val textArgb: ArgbColor? = null,
    val highlightArgb: ArgbColor? = null,
) {
    val hasPending: Boolean
        get() = backgroundArgb != null || buttonArgb != null ||
            panelArgb != null || textArgb != null || highlightArgb != null
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val _draft = MutableStateFlow(ColorDraft())
    val draft: StateFlow<ColorDraft> = _draft.asStateFlow()

    fun setExportFolder(uri: Uri?) {
        viewModelScope.launch { repository.setExportFolder(uri) }
    }

    /**
     * Enabling toggles the live theme immediately (nothing to drag, no performance concern).
     * Disabling commits straight away too, so the user sees the app revert right away.
     */
    fun setBackgroundColor(enabled: Boolean, argb: ArgbColor?) {
        viewModelScope.launch { repository.setBackgroundColor(enabled, argb) }
        _draft.value = _draft.value.copy(backgroundArgb = null)
    }

    fun setButtonColor(enabled: Boolean, argb: ArgbColor?) {
        viewModelScope.launch { repository.setButtonColor(enabled, argb) }
        _draft.value = _draft.value.copy(buttonArgb = null)
    }

    fun setPanelColor(enabled: Boolean, argb: ArgbColor?) {
        viewModelScope.launch { repository.setPanelColor(enabled, argb) }
        _draft.value = _draft.value.copy(panelArgb = null)
    }

    fun setTextColor(enabled: Boolean, argb: ArgbColor?) {
        viewModelScope.launch { repository.setTextColor(enabled, argb) }
        _draft.value = _draft.value.copy(textArgb = null)
    }

    fun setHighlightColor(enabled: Boolean, argb: ArgbColor?) {
        viewModelScope.launch { repository.setHighlightColor(enabled, argb) }
        _draft.value = _draft.value.copy(highlightArgb = null)
    }

    /** Font choice has no drag gesture, so it commits straight away. */
    fun setFontFamily(fontFamilyId: String) {
        viewModelScope.launch { repository.setFontFamily(fontFamilyId) }
    }

    /** Rates likewise commit immediately — they are typed, not dragged. */
    fun setTaxRate(basisPoints: Int) {
        viewModelScope.launch { repository.setTaxRate(basisPoints) }
    }

    fun setCardFeeRate(basisPoints: Int) {
        viewModelScope.launch { repository.setCardFeeRate(basisPoints) }
    }

    /** Flat card fee, in cents. Typed like the rates, so it commits straight away. */
    fun setCardFeeFixed(cents: Long) {
        viewModelScope.launch { repository.setCardFeeFixed(cents) }
    }

    /**
     * Shows or hides the cents on every money label.
     *
     * The global mirror is set eagerly rather than waiting for DataStore to round-trip, so the
     * toggle feels instant; the stored value re-asserts the same thing moments later.
     */
    fun setShowMoneyDecimals(show: Boolean) {
        showCents = show
        viewModelScope.launch { repository.setShowMoneyDecimals(show) }
    }

    /**
     * Switches the currency symbol.
     *
     * The process-wide symbol is set synchronously as well as persisted, exactly like
     * [setShowMoneyDecimals]: the exporter and PDF paths read it outside composition, so a pick
     * that only went to DataStore would leave the next export still using the old symbol.
     */
    fun setCurrency(currency: Currency) {
        currencySymbol = currency.symbol
        viewModelScope.launch { repository.setCurrency(currency.id) }
    }

    /**
     * Persists the language and applies it immediately.
     *
     * The locale is applied through [AppCompatDelegate.setApplicationLocales], which recreates the
     * activity and re-resolves every string resource, so no manual refresh is needed.
     */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguageTag(language.tag)
            val locales = if (language.tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** Animation speed. A pick, not a drag, so it commits straight away. */
    fun setMotionSpeed(speed: MotionSpeed) {
        viewModelScope.launch { repository.setMotionSpeed(speed.id) }
    }

    /** Text size. A pick like the animation speed, so it commits straight away. */
    fun setTextSize(size: TextSize) {
        viewModelScope.launch { repository.setTextSize(size.id) }
    }

    /** Shows or hides the whole tagging feature. Never touches the stored tags. */
    fun setTagsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTagsEnabled(enabled) }
    }

    /** Switches the inventory layout. Display-only; no product or category is touched. */
    fun setInventoryViewMode(mode: InventoryViewMode) {
        viewModelScope.launch { repository.setInventoryViewMode(mode.id) }
    }

    fun setLoadingIcon(icon: LoadingIcon) {
        viewModelScope.launch { repository.setLoadingIcon(icon.id) }
    }

    fun setLoadingSpin(spin: LoadingSpin) {
        viewModelScope.launch { repository.setLoadingSpin(spin.id) }
    }

    fun setLoadingCircle(circle: LoadingCircle) {
        viewModelScope.launch { repository.setLoadingCircle(circle.id) }
    }

    fun setShowLoadingScreen(show: Boolean) {
        viewModelScope.launch { repository.setShowLoadingScreen(show) }
    }

    /** Mutes or unmutes the beep. A switch, so it commits straight away. */
    fun setScannerBeepEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setScannerBeepEnabled(enabled) }
    }

    fun setScannerBeep(beep: ScanBeep) {
        viewModelScope.launch { repository.setScannerBeep(beep.id) }
    }

    /** Turns the sale chime on or off. A switch, so it commits straight away. */
    fun setSaleSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSaleSoundEnabled(enabled) }
    }

    fun setSaleSound(sound: SaleSound) {
        viewModelScope.launch { repository.setSaleSound(sound.id) }
    }

    /** Which glyph the receipt flourish draws. A pick, so it commits straight away. */
    fun setSaleAnimation(animation: SaleAnimation) {
        viewModelScope.launch { repository.setSaleAnimation(animation.id) }
    }

    /**
     * Imports a Lottie animation and switches the flourish to it.
     *
     * The store validates the file before anything is written, so a `false` here means the pick
     * was not an animation this build can play — the settings are left untouched rather than
     * pointing at a file that would fail at the receipt.
     *
     * @return true when the animation was stored and is now in use.
     */
    suspend fun setSaleLottieFile(uri: Uri, context: Context): Boolean {
        val fileName = SaleAnimationStore.save(context, uri) ?: return false
        val previous = repository.setSaleLottieFile(fileName)
        if (previous != null && previous != fileName) {
            SaleAnimationStore.delete(context, previous)
        }
        repository.setSaleAnimation(SaleAnimationLottieId)
        return true
    }

    /** Removes the imported animation and falls back to the default glyph. */
    suspend fun clearSaleLottieFile(context: Context) {
        val previous = repository.setSaleLottieFile(null)
        repository.setSaleAnimation(DefaultSaleAnimationId)
        SaleAnimationStore.delete(context, previous)
    }

    /**
     * Size of the sale flourish, in dp.
     *
     * A slider, so this fires on every frame of the drag — the same trade-off as the loading
     * sizes: one small int that feeds no colour scheme, so writing straight through is cheaper
     * than the draft/pending machinery the colour pickers need.
     */
    fun setSaleAnimationSize(dp: Int) {
        viewModelScope.launch { repository.setSaleAnimationSize(dp) }
    }

    /**
     * Copies a picked file into app storage and points the sale chime at it.
     *
     * Mirrors [setScannerBeepFile] — same copy-then-delete ordering, so a failed save can never
     * leave the user with no sale sound at all.
     *
     * @return true when the sound was stored and is now in use.
     */
    suspend fun setSaleSoundFile(uri: Uri, context: Context): Boolean {
        val fileName = ScanBeepStore.save(context, uri) ?: return false
        val previous = repository.setSaleSoundFile(fileName)
        if (previous != null && previous != fileName) {
            ScanBeepStore.delete(context, previous)
        }
        return true
    }

    /** Falls back to the built-in chime and deletes the picked file. */
    fun clearSaleSoundFile(context: Context) {
        viewModelScope.launch {
            val previous = repository.setSaleSoundFile(null)
            if (previous != null) ScanBeepStore.delete(context, previous)
        }
    }

    /**
     * Waits between accepted scans, in milliseconds.
     *
     * A slider, so this fires on every frame of the drag — the same trade-off as the loading
     * sizes: one small int, feeding no colour scheme, so writing straight through is cheaper than
     * the draft/pending machinery the colour pickers need.
     */
    fun setScanDelay(millis: Int) {
        viewModelScope.launch { repository.setScanDelay(millis) }
    }

    /** How long before a *different* code is accepted. The short of the two scanner delays. */
    fun setProductChangeDelay(millis: Int) {
        viewModelScope.launch { repository.setProductChangeDelay(millis) }
    }

    fun setShowScanPopup(show: Boolean) {
        viewModelScope.launch { repository.setShowScanPopup(show) }
    }

    /** Turns the scan vibration on or off. A switch, so it commits straight away. */
    fun setScanVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setScanVibrationEnabled(enabled) }
    }

    fun setScanVibration(pattern: ScanVibration) {
        viewModelScope.launch { repository.setScanVibration(pattern.id) }
    }

    /**
     * Vibration amplitude, 0-255, where 0 lets the device pick its own intensity.
     *
     * A slider, so this fires on every frame of the drag — the same trade-off as the scan delay:
     * one small int, feeding nothing but the vibrator, so it writes straight through.
     */
    fun setScanVibrationStrength(amplitude: Int) {
        viewModelScope.launch { repository.setScanVibrationStrength(amplitude) }
    }

    /**
     * Copies a picked sound into app storage and points the scanner at it.
     *
     * Mirrors [setLoadingImage]: the copy happens here so it survives a rotation mid-pick, and the
     * previous file is deleted only after the new one is safely stored — a failed save must never
     * leave the user with no beep at all.
     *
     * @return true when the sound was stored and is now in use.
     */
    suspend fun setScannerBeepFile(uri: Uri, context: Context): Boolean {
        val fileName = ScanBeepStore.save(context, uri) ?: return false
        val previous = repository.setScannerBeepFile(fileName)
        if (previous != null && previous != fileName) {
            ScanBeepStore.delete(context, previous)
        }
        return true
    }

    /** Falls back to the built-in tone and deletes the picked file. */
    fun clearScannerBeepFile(context: Context) {
        viewModelScope.launch {
            val previous = repository.setScannerBeepFile(null)
            if (previous != null) ScanBeepStore.delete(context, previous)
        }
    }

    fun resetScanner(context: Context) {
        viewModelScope.launch {
            val orphan = repository.resetScanner()
            val saleOrphans = repository.resetSaleSound()
            // Both are the same section's reset and the same folder on disk, so they are cleaned
            // up together rather than leaving a picked chime orphaned by a full reset.
            (listOfNotNull(orphan) + saleOrphans).forEach { ScanBeepStore.delete(context, it) }
        }
    }

    /**
     * Size of the startup image, in dp.
     *
     * A slider, so this fires on every frame of the drag. That is acceptable here where the HSV
     * pickers needed a draft: the value is a single small int, and unlike a colour it feeds no
     * colour scheme, so recomposition costs one settings row and the preview — not the whole app.
     */
    fun setLoadingImageSize(dp: Int) {
        viewModelScope.launch { repository.setLoadingImageSize(dp) }
    }

    /** Size of the disc behind the startup image, in dp. Same drag reasoning as the image size. */
    fun setLoadingCircleSize(dp: Int) {
        viewModelScope.launch { repository.setLoadingCircleSize(dp) }
    }

    /**
     * Copies a picked picture into app storage and points the startup screen at it.
     *
     * The copy happens here rather than in the composable so it survives a rotation mid-pick, and
     * the *previous* file is deleted only after the new one is safely stored — a failed save must
     * never leave the user with no startup image at all.
     *
     * @return true when the image was stored and is now in use.
     */
    suspend fun setLoadingImage(uri: Uri, context: Context): Boolean {
        val fileName = LoadingImageStore.save(context, uri) ?: return false
        val previous = repository.setLoadingImage(fileName)
        if (previous != null && previous != fileName) {
            LoadingImageStore.delete(context, previous)
        }
        return true
    }

    /**
     * Imports a Lottie animation for the startup screen.
     *
     * Validated before anything is written, so a `false` here leaves the settings exactly as they
     * were rather than pointing at a file that would fail on the next launch.
     *
     * @return true when the animation was stored and is now in use.
     */
    suspend fun setLoadingLottie(uri: Uri, context: Context): Boolean {
        val fileName = SaleAnimationStore.save(context, uri, forLoadingScreen = true)
            ?: return false
        val previous = repository.setLoadingLottieFile(fileName)
        if (previous != null && previous != fileName) {
            SaleAnimationStore.delete(context, previous, forLoadingScreen = true)
        }
        // Selecting the source: without this the file would be stored but the icon would still be
        // drawn, because `LoadingImage.from` only reaches the Lottie branch on this id.
        repository.setLoadingIcon(LoadingIconLottieId)
        // A picked picture outranks the animation, so it has to go for this to take effect.
        repository.setLoadingImage(null)?.let { LoadingImageStore.delete(context, it) }
        return true
    }

    /** Drops the imported animation and goes back to the chosen icon. */
    suspend fun clearLoadingLottie(context: Context) {
        val previous = repository.setLoadingLottieFile(null)
        SaleAnimationStore.delete(context, previous, forLoadingScreen = true)
        repository.setLoadingIcon(DefaultLoadingIconId)
    }

    /** Reverts to the built-in icon and deletes the picked file. */
    fun clearLoadingImage(context: Context) {
        viewModelScope.launch {
            val previous = repository.setLoadingImage(null)
            if (previous != null) LoadingImageStore.delete(context, previous)
        }
    }

    fun resetLoadingScreen(context: Context) {
        viewModelScope.launch {
            val orphan = repository.resetLoadingScreen()
            if (orphan != null) LoadingImageStore.delete(context, orphan)
        }
    }

    /** Records a pending background colour without touching persisted settings. */
    fun previewBackgroundColor(argb: ArgbColor) {
        _draft.value = _draft.value.copy(backgroundArgb = argb)
    }

    /** Records a pending button colour without touching persisted settings. */
    fun previewButtonColor(argb: ArgbColor) {
        _draft.value = _draft.value.copy(buttonArgb = argb)
    }

    /** Records a pending panel colour without touching persisted settings. */
    fun previewPanelColor(argb: ArgbColor) {
        _draft.value = _draft.value.copy(panelArgb = argb)
    }

    /** Records a pending text colour without touching persisted settings. */
    fun previewTextColor(argb: ArgbColor) {
        _draft.value = _draft.value.copy(textArgb = argb)
    }

    /** Records a pending highlight colour without touching persisted settings. */
    fun previewHighlightColor(argb: ArgbColor) {
        _draft.value = _draft.value.copy(highlightArgb = argb)
    }

    fun discardDraft() {
        _draft.value = ColorDraft()
    }

    /** Commits every pending colour at once and clears them. */
    fun applyDraft() {
        val pending = _draft.value
        viewModelScope.launch {
            pending.backgroundArgb?.let { repository.setBackgroundColor(true, it) }
            pending.buttonArgb?.let { repository.setButtonColor(true, it) }
            pending.panelArgb?.let { repository.setPanelColor(true, it) }
            pending.textArgb?.let { repository.setTextColor(true, it) }
            pending.highlightArgb?.let { repository.setHighlightColor(true, it) }
        }
        _draft.value = ColorDraft()
    }

    fun resetAppearance() {
        viewModelScope.launch { repository.resetAppearance() }
        _draft.value = ColorDraft()
    }

    /**
     * Applies [theme] immediately, without going through the draft.
     *
     * Deliberately **not** routed through `applyDraft`: a theme is chosen as a finished thing, so
     * the "preview then Apply" flow the pickers use would add a pointless confirmation step. A
     * picker left open on top of the change is a different matter — its pending values are cleared
     * so they cannot be Apply'd over the theme the user just chose.
     */
    fun applyTheme(theme: ColorTheme) {
        viewModelScope.launch { repository.applyTheme(theme) }
        _draft.value = ColorDraft()
    }

    /**
     * Saves [name] plus the colours currently in effect as a reusable theme.
     *
     * [textArgb] has no default on purpose: a saved theme that carries no ink is re-applied over
     * whatever the previous theme wrote, which is how Midnight's white text survived onto a pale
     * page. Callers read the value from the settings and fall back to the app's own ink; there is
     * no "leave the text alone" theme.
     */
    fun saveTheme(
        name: String,
        backgroundArgb: ArgbColor,
        buttonArgb: ArgbColor,
        panelArgb: ArgbColor,
        highlightArgb: ArgbColor,
        textArgb: ArgbColor,
    ) {
        val theme = ColorTheme.saved(
            name = name,
            backgroundArgb = backgroundArgb,
            buttonArgb = buttonArgb,
            panelArgb = panelArgb,
            highlightArgb = highlightArgb,
            textArgb = textArgb,
        )
        viewModelScope.launch { repository.saveTheme(theme) }
    }

    fun deleteTheme(id: String) {
        viewModelScope.launch { repository.deleteTheme(id) }
    }

    /**
     * Merges settings from a restored backup into this device.
     *
     * Any half-finished colour draft is dropped first: the draft is a set of *pending* edits
     * against the old values, and applying it after a restore would paint the previous theme's
     * colours back over the ones that were just restored.
     */
    fun applyImportedSettings(imported: ImportedSettings) {
        viewModelScope.launch { repository.applyImported(imported) }
        _draft.value = ColorDraft()
    }

    /**
     * Applies only appearance from a Wi-Fi import, so a desktop theme follows the data back to
     * the phone without replacing the phone's tax, language or scanner choices.
     */
    fun applyImportedAppearance(imported: ImportedSettings) {
        viewModelScope.launch { repository.applyImportedAppearance(imported) }
        _draft.value = ColorDraft()
    }

    companion object {
        fun factory(app: StockApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    }
}
