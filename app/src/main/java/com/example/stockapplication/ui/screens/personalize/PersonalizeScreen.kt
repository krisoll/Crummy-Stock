package com.example.stockapplication.ui.screens.personalize

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppFontFamily
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.ArgbColor
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.data.SaleSound
import com.example.stockapplication.data.ScanBeep
import com.example.stockapplication.data.ScanVibration
import com.example.stockapplication.data.ScanVibrationStore
import com.example.stockapplication.data.TextSize
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.viewmodel.ColorDraft

/**
 * The four groups the Personalize page is divided into.
 *
 * One card each on the landing page, one page each behind it. Ordered the way the settings are
 * usually reached for: colours most often, animations least.
 */
private enum class PersonalizePage {
    /** The landing page: the four entry cards. */
    HOME,
    COLOURS,
    SOUNDS,
    TEXT,
    ANIMATIONS,
}

/**
 * What the two Lottie pickers accept.
 *
 * `application/json` covers a plain `.json` animation; `application/zip` — plus the
 * `x-zip-compressed` alias some providers report — covers a dotLottie `.lottie` bundle, which is a
 * ZIP. `application/octet-stream` is the catch-all for providers that declare no type at all;
 * without it, `.lottie` files simply grey out on those pickers.
 */
private val LottieMimeTypes = arrayOf(
    "application/json",
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)

/**
 * Everything that only changes how the app **looks and feels**: colours, sounds, text and
 * animations.
 *
 * Split out of the Config tab because those settings are tuned far less often than the day-to-day
 * ones (export folder, language, rates) and each one is a large, gesture-heavy control. Keeping
 * them on their own page means the Config tab stays short and scannable, and colour dragging never
 * fights the scroll of a page that also holds text fields.
 *
 * Within this page the four groups are separate sub-pages rather than one long scroll: each group
 * has grown enough controls (the sounds page alone has three lists and two sliders) that stacking
 * them put five unrelated sections between the user and the one they came for. The landing page now
 * shows four cards, and each opens its own page.
 *
 * The unsaved-colour bar and the colour preview live on the **Colours** page: they are only
 * meaningful next to the pickers that feed them, and leaving them behind on the landing page would
 * strand the user with a pending change they cannot see the result of.
 *
 * @param draft colours edited in the pickers but not yet committed; see [ColorDraft].
 */
@Composable
fun PersonalizeScreen(
    settings: AppSettings,
    draft: ColorDraft,
    onBack: () -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onPreviewBackgroundColor: (ArgbColor) -> Unit,
    onToggleButton: (Boolean) -> Unit,
    onPreviewButtonColor: (ArgbColor) -> Unit,
    onTogglePanel: (Boolean) -> Unit,
    onPreviewPanelColor: (ArgbColor) -> Unit,
    onToggleText: (Boolean) -> Unit,
    onPreviewTextColor: (ArgbColor) -> Unit,
    onToggleHighlight: (Boolean) -> Unit,
    onPreviewHighlightColor: (ArgbColor) -> Unit,
    onFontFamilyChange: (AppFontFamily) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onMotionSpeedChange: (MotionSpeed) -> Unit,
    onLoadingIconChange: (LoadingIcon) -> Unit,
    onLoadingSpinChange: (LoadingSpin) -> Unit,
    onLoadingCircleChange: (LoadingCircle) -> Unit,
    onLoadingImageSizeChange: (Int) -> Unit,
    onLoadingCircleSizeChange: (Int) -> Unit,
    onToggleLoadingScreen: (Boolean) -> Unit,
    onResetLoadingScreen: () -> Unit,
    /** Copies a picture picked from the device into app storage and adopts it. */
    onLoadingImagePicked: (android.net.Uri) -> Unit,
    /** Drops the picked picture and falls back to the chosen icon. */
    onClearLoadingImage: () -> Unit,
    /** Copies a picked Lottie animation into app storage and makes it the startup screen. */
    onPickLoadingLottieFile: (android.net.Uri) -> Unit,
    /** Drops the startup screen's animation and falls back to the chosen icon. */
    onClearLoadingLottieFile: () -> Unit,
    onApplyColors: () -> Unit,
    onDiscardColors: () -> Unit,
    onResetAppearance: () -> Unit,
    /** Applies a whole palette at once, bypassing the draft-and-Apply flow. */
    onApplyTheme: (ColorTheme) -> Unit,
    /** Saves the colours currently in effect — including the ink — as a named theme. */
    onSaveTheme: (String, ArgbColor, ArgbColor, ArgbColor, ArgbColor, ArgbColor) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onToggleScanBeep: (Boolean) -> Unit,
    onScanBeepChange: (ScanBeep) -> Unit,
    /** Plays [ScanBeep] once so the user can hear what they are choosing. */
    onPreviewScanBeep: (ScanBeep) -> Unit,
    /** Copies an audio file picked from the device into app storage and adopts it. */
    onPickScanBeepFile: (android.net.Uri) -> Unit,
    /** Drops the picked sound and falls back to the chosen tone. */
    onClearScanBeepFile: () -> Unit,
    /** Turns the sale chime on or off. */
    onToggleSaleSound: (Boolean) -> Unit,
    onSaleSoundChange: (SaleSound) -> Unit,
    /** Plays [SaleSound] once so the user can hear what they are choosing. */
    onPreviewSaleSound: (SaleSound) -> Unit,
    /** Copies an audio file picked from the device and adopts it as the sale sound. */
    onPickSaleSoundFile: (android.net.Uri) -> Unit,
    /** Drops the picked sale sound and falls back to the chosen chime. */
    onClearSaleSoundFile: () -> Unit,
    /** Which glyph the receipt throws when a sale completes. */
    onSaleAnimationChange: (SaleAnimation) -> Unit,
    /** How large that glyph is drawn, in dp. */
    onSaleAnimationSizeChange: (Int) -> Unit,
    /** Copies a picked Lottie animation into app storage and adopts it. */
    onPickLottieFile: (android.net.Uri) -> Unit,
    /** Drops the imported animation and falls back to a built-in glyph. */
    onClearLottieFile: () -> Unit,
    onToggleScanVibration: (Boolean) -> Unit,
    onScanVibrationChange: (ScanVibration) -> Unit,
    /** Fires [ScanVibration] once so the user can feel what they are choosing. */
    onPreviewScanVibration: (ScanVibration, Int) -> Unit,
    onScanVibrationStrengthChange: (Int) -> Unit,
    onResetScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system photo picker: no storage permission on any supported API level, so the startup
    // image can be chosen without asking the user for anything.
    val loadingImagePicker = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri -> if (uri != null) onLoadingImagePicked(uri) }

    // Audio is picked by content type rather than by extension: providers disagree about the
    // `audio/*` wildcard, and AndroidImageOnly equivalents do not exist for sound. The store
    // copies whatever comes back, and SoundPool decides then whether it can play it.
    val beepPicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri -> if (uri != null) onPickScanBeepFile(uri) }

    // Same contract and same reasoning as the scanner's picker, but a separate launcher: the two
    // lists store their files separately, and one shared launcher would have to remember which
    // list opened it.
    val saleSoundPicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri -> if (uri != null) onPickSaleSoundFile(uri) }

    // Lottie animations, filtered to the two documents that can hold one. `*/*` would show the
    // user every image and PDF on the device for a picker that accepts only these.
    val lottiePicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri -> if (uri != null) onPickLottieFile(uri) }

    // A second launcher for the startup screen's animation: the two store their files in
    // different folders and are cleared by different resets, so one launcher would have to
    // remember which one opened it.
    val loadingLottiePicker = rememberLauncherForActivityResult(
        contract = OpenDocument()
    ) { uri -> if (uri != null) onPickLoadingLottieFile(uri) }

    // Probed once per visit rather than on every recomposition: the answer cannot change while
    // the page is open, and this reaches for a system service.
    val context = LocalContext.current
    val vibrationSupported = remember { ScanVibrationStore.isSupported(context) }

    // Which page is showing. Local rather than hoisted into `StockApp` because nothing outside
    // this screen needs to know: the groups are one level deeper than Personalize, not four more
    // destinations, and the tab switch and back gesture only care that *something* here is open.
    var page by rememberSaveable { mutableStateOf(PersonalizePage.HOME) }

    // Colours have to be applied or discarded before the page is abandoned, otherwise the pending
    // draft would either be lost without a word or leak into the next visit. Leaving for another
    // group commits first for the same reason: the Apply bar only exists on the Colours page, so
    // stepping away from it would otherwise carry an unappliable change around invisibly.
    val leavePersonalize = {
        if (draft.hasPending) onApplyColors()
        onBack()
    }
    val leavePage = {
        if (draft.hasPending) onApplyColors()
        page = PersonalizePage.HOME
    }

    // Back steps out of a group before it closes Personalize, mirroring the two back arrows. This
    // works by depth rather than by disabling the root handler: `StockApp`'s BackHandler is
    // registered first, and the innermost enabled handler is the one that runs, so this wins while
    // a group is open and the root one takes over once we are back on the landing page.
    //
    // The pending draft is committed here, exactly as the header's back arrow does, so the gesture
    // and the arrow cannot disagree about what happens to it.
    BackHandler(enabled = page != PersonalizePage.HOME) { leavePage() }

    // Resolved here, at composable scope, because `AppMotion`'s specs are @Composable: the
    // `transitionSpec` block is not.
    val enterFade = fadeIn(animationSpec = AppMotion.enterFade())
    // Typed on the offset it animates, like the tab transitions in `StockApp`: a float-based fade
    // spec cannot be shared with a slide.
    val slide = slideInVertically(
        animationSpec = tween<IntOffset>(
            durationMillis = AppMotion.screenEnterMillis(),
            easing = AppMotion.Emphasised
        ),
        initialOffsetY = { it / 8 }
    )
    val exitFade = fadeOut(animationSpec = AppMotion.exitFade())

    // Opaque, not just transparent-over-whatever-is-behind: this page replaces Config rather
    // than floating over it, and the cross-fade into it must not show the Config content
    // bleeding through mid-transition.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Five pages inside one destination. The groups are sub-pages rather than destinations in
        // `StockApp` because they still belong to this page: the unsaved colour draft, the Apply
        // bar and the preview are shared, and pushing them through the root would mean exposing
        // all of that to the tab bar for nothing.
        //
        // `AnimatedContent` rather than a plain `when` so switching keeps the same cross-fade the
        // rest of the app uses, and so the outgoing page is not torn down mid-way: an open colour
        // picker collapses with it instead of vanishing in one frame.
        AnimatedContent(
            targetState = page,
            transitionSpec = { (enterFade + slide) togetherWith exitFade },
            label = "personalize-page"
        ) { current ->
            when (current) {
                PersonalizePage.HOME -> PersonalizeHomePage(
                    hasPendingColours = draft.hasPending,
                    onBack = leavePersonalize,
                    onOpenPage = { page = it.toPage() },
                )

                PersonalizePage.COLOURS -> ColourSettingsPage(
                    settings = settings,
                    draft = draft,
                    onBack = { leavePage() },
                    onToggleBackground = onToggleBackground,
                    onPreviewBackgroundColor = onPreviewBackgroundColor,
                    onToggleButton = onToggleButton,
                    onPreviewButtonColor = onPreviewButtonColor,
                    onTogglePanel = onTogglePanel,
                    onPreviewPanelColor = onPreviewPanelColor,
                    onToggleText = onToggleText,
                    onPreviewTextColor = onPreviewTextColor,
                    onToggleHighlight = onToggleHighlight,
                    onPreviewHighlightColor = onPreviewHighlightColor,
                    onApplyColors = onApplyColors,
                    onDiscardColors = onDiscardColors,
                    onResetAppearance = onResetAppearance,
                    onApplyTheme = onApplyTheme,
                    onSaveTheme = onSaveTheme,
                    onDeleteTheme = onDeleteTheme,
                )

                PersonalizePage.SOUNDS -> SoundsPage(
                    settings = settings,
                    onBack = { leavePage() },
                    onToggleBeep = onToggleScanBeep,
                    onPickBeep = onScanBeepChange,
                    onPreviewBeep = onPreviewScanBeep,
                    onOpenBeepPicker = {
                        beepPicker.launch(
                            arrayOf("audio/*", "application/ogg", "application/octet-stream")
                        )
                    },
                    onClearBeepFile = onClearScanBeepFile,
                    onToggleSaleSound = onToggleSaleSound,
                    onSaleSoundChange = onSaleSoundChange,
                    onPreviewSaleSound = onPreviewSaleSound,
                    onOpenSaleSoundPicker = {
                        saleSoundPicker.launch(
                            arrayOf("audio/*", "application/ogg", "application/octet-stream")
                        )
                    },
                    onClearSaleSoundFile = onClearSaleSoundFile,
                    onToggleVibration = onToggleScanVibration,
                    onPickVibration = onScanVibrationChange,
                    onPreviewVibration = onPreviewScanVibration,
                    onVibrationStrengthChange = onScanVibrationStrengthChange,
                    onResetScanner = onResetScanner,
                    vibrationSupported = vibrationSupported,
                )

                PersonalizePage.TEXT -> TextPage(
                    settings = settings,
                    onBack = { leavePage() },
                    onFontFamilyChange = onFontFamilyChange,
                    onTextSizeChange = onTextSizeChange,
                )

                PersonalizePage.ANIMATIONS -> AnimationsPage(
                    settings = settings,
                    onBack = { leavePage() },
                    onMotionSpeedChange = onMotionSpeedChange,
                    onLoadingIconChange = onLoadingIconChange,
                    onLoadingSpinChange = onLoadingSpinChange,
                    onLoadingCircleChange = onLoadingCircleChange,
                    onLoadingImageSizeChange = onLoadingImageSizeChange,
                    onLoadingCircleSizeChange = onLoadingCircleSizeChange,
                    onToggleLoadingScreen = onToggleLoadingScreen,
                    onResetLoadingScreen = onResetLoadingScreen,
                    // The launcher lives up here because `rememberLauncherForActivityResult` has
                    // to be called from this composable, not from the page that happens to
                    // contain the button.
                    onLoadingImagePicker = {
                        loadingImagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    onLoadingAnimationPicker = {
                        loadingLottiePicker.launch(LottieMimeTypes)
                    },
                    onClearLoadingAnimation = onClearLoadingLottieFile,
                    onClearLoadingImage = onClearLoadingImage,
                    onSaleAnimationChange = onSaleAnimationChange,
                    onSaleAnimationSizeChange = onSaleAnimationSizeChange,
                    onOpenLottiePicker = {
                        lottiePicker.launch(LottieMimeTypes)
                    },
                    onClearLottie = onClearLottieFile,
                )
            }
        }
    }
}

/**
 * Bridges the private [PersonalizePage] enum to the entry cards, which are declared before it and
 * therefore cannot name it.
 *
 * The four card targets are the only pages reachable from the landing page, so a tiny enum of its
 * own keeps the cards' contract honest: there is no way to write a card that opens `HOME`.
 */
private enum class PersonalizeEntry { COLOURS, SOUNDS, TEXT, ANIMATIONS }

private fun PersonalizeEntry.toPage(): PersonalizePage = when (this) {
    PersonalizeEntry.COLOURS -> PersonalizePage.COLOURS
    PersonalizeEntry.SOUNDS -> PersonalizePage.SOUNDS
    PersonalizeEntry.TEXT -> PersonalizePage.TEXT
    PersonalizeEntry.ANIMATIONS -> PersonalizePage.ANIMATIONS
}

/**
 * Personalize's landing page: four cards, one per group.
 *
 * A page of pure navigation on purpose. The groups' contents used to be stacked here, which made
 * the page a long scroll where the startup-screen preview and the scanner's five sub-lists were
 * neighbours — the two least related things in the app. Four cards let the user see the whole map
 * at a glance and go straight to the group they want.
 */
@Composable
private fun PersonalizeHomePage(
    hasPendingColours: Boolean,
    onBack: () -> Unit,
    onOpenPage: (PersonalizeEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Painted after the background so the colour reaches the status bar, then padded so
            // the back arrow and title clear it.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonalizeHeader(
            title = stringResource(R.string.settings_personalize),
            description = stringResource(R.string.settings_personalize_description),
            backLabel = stringResource(R.string.action_back),
            onBack = onBack,
        )

        PersonalizeEntryCard(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_colours_open),
            description = stringResource(R.string.settings_colours_open_description),
            // The only card that can have something waiting on it: a colour draft is the one
            // setting here that is edited but not yet committed.
            badge = if (hasPendingColours) {
                stringResource(R.string.settings_colours_pending)
            } else {
                null
            },
            onClick = { onOpenPage(PersonalizeEntry.COLOURS) },
        )

        PersonalizeEntryCard(
            icon = Icons.Default.GraphicEq,
            title = stringResource(R.string.settings_sounds_open),
            description = stringResource(R.string.settings_sounds_open_description),
            onClick = { onOpenPage(PersonalizeEntry.SOUNDS) },
        )

        PersonalizeEntryCard(
            icon = Icons.Default.TextFields,
            title = stringResource(R.string.settings_text_open),
            description = stringResource(R.string.settings_text_open_description),
            onClick = { onOpenPage(PersonalizeEntry.TEXT) },
        )

        PersonalizeEntryCard(
            icon = Icons.Default.Animation,
            title = stringResource(R.string.settings_animations_open),
            description = stringResource(R.string.settings_animations_open_description),
            onClick = { onOpenPage(PersonalizeEntry.ANIMATIONS) },
        )

        HorizontalDivider()
        Text(
            stringResource(R.string.settings_storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
