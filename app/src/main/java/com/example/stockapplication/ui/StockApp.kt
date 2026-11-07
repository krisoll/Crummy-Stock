package com.example.stockapplication.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stockapplication.R
import com.example.stockapplication.data.BarcodeGenerator
import com.example.stockapplication.data.BackupArchive
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.ProductImages
import com.example.stockapplication.data.RestoredImages
import com.example.stockapplication.data.DailyReport
import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.ExportFiles
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.StockDatabase
import com.example.stockapplication.data.jsonBackupFileName
import com.example.stockapplication.data.toJson
import com.example.stockapplication.data.QrCodeExporter
import com.example.stockapplication.data.ReportRenderer
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.ScanBeepSound
import com.example.stockapplication.data.ScanBeepStore
import com.example.stockapplication.data.ScanVibrationStore
import com.example.stockapplication.data.showCents
import com.example.stockapplication.data.currencySymbol
import com.example.stockapplication.data.buildDailyReport
import com.example.stockapplication.data.buildReportSeries
import com.example.stockapplication.data.InventoryViewMode
import com.example.stockapplication.data.transfer.BackupServer
import com.example.stockapplication.data.transfer.BackupTransfer
import com.example.stockapplication.data.transfer.LocalAddress
import com.example.stockapplication.data.dayOrdersCsvFileName
import com.example.stockapplication.data.dayOrdersToCsv
import com.example.stockapplication.data.ordersCsvFileName
import com.example.stockapplication.data.ordersToCsv
import com.example.stockapplication.ui.components.ImportBackupDialog
import com.example.stockapplication.ui.screens.CategoryEditScreen
import com.example.stockapplication.ui.screens.TagEditScreen
import com.example.stockapplication.ui.screens.InventoryScreen
import com.example.stockapplication.ui.screens.InventoryTab
import com.example.stockapplication.ui.screens.LoadingScreen
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.screens.ProductDraft
import com.example.stockapplication.ui.screens.ProductEditScreen
import com.example.stockapplication.ui.screens.PromoEditScreen
import com.example.stockapplication.ui.screens.SalesScreen
import com.example.stockapplication.ui.screens.SalesCartScreen
import com.example.stockapplication.ui.screens.analytics.AnalyticsScreen
import com.example.stockapplication.ui.screens.personalize.PersonalizeScreen
import com.example.stockapplication.ui.screens.SettingsScreen
import com.example.stockapplication.ui.screens.supplies.SuppliesScreen
import com.example.stockapplication.ui.screens.transfer.ReceiveState
import com.example.stockapplication.ui.screens.transfer.TransferScreen
import com.example.stockapplication.ui.viewmodel.CartViewModel
import com.example.stockapplication.ui.viewmodel.InventoryViewModel
import com.example.stockapplication.ui.viewmodel.SettingsViewModel
import com.example.stockapplication.ui.viewmodel.SuppliesViewModel
import com.example.stockapplication.ui.viewmodel.toText
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppDestinations(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    INVENTORY(R.string.nav_inventory, Icons.Default.Inventory2),
    // The scanner lives inside this tab now, so there is no separate Scan destination.
    SALES(R.string.nav_sales, Icons.Default.ShoppingCart),
    HISTORY(R.string.nav_history, Icons.AutoMirrored.Filled.ReceiptLong),
    SUPPLIES(R.string.nav_supplies, Icons.Default.Warehouse),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings),
}

/**
 * How long the startup screen stays up at minimum.
 *
 * Without a floor, a warm start resolves the catalogue in a few frames and the screen appears as a
 * jarring one-frame flash — worse now that it plays a Lottie animation, which would be gone before
 * it had drawn its first few frames.
 *
 * Two seconds rather than the "long enough to read" 900 ms it used to be: the startup screen is
 * branded, the animation has a scripted entry, and a shop opening the app is not waiting on this
 * for any amount of time that matters. The floor is a **maximum wait imposed on a fast start**,
 * not a delay added to a slow one — a device that takes three seconds to load is unaffected.
 */
private const val startupMinDurationMillis = 2_000L

/**
 * A backup that has been read but not yet written, plus the name to show in the dialog.
 *
 * Kept as one value rather than two states so the dialog can only ever open with a matching pair:
 * a [BackupContents] with no file name would render a dialog the user cannot identify.
 */
private data class PendingImport(val fileName: String, val contents: BackupContents)

/** Root composable: bottom navigation suite plus a tiny in-memory screen stack. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StockApp(
    viewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    suppliesViewModel: SuppliesViewModel,
) {
    val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(AppDestinations.INVENTORY) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val colorDraft by settingsViewModel.draft.collectAsStateWithLifecycle()
    val ordersByDay by viewModel.ordersByDay.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val promos by viewModel.promos.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagIdsByProduct by viewModel.tagIdsByProduct.collectAsStateWithLifecycle()
    val selectedTagIds by viewModel.selectedTagIds.collectAsStateWithLifecycle()

    // Which category folder is open in the inventory's folder layout, or null for the grid.
    // Hoisted here rather than kept inside `InventoryScreen` so the back handler below can close
    // a folder before it leaves the tab — opening one is a navigation step as far as the user is
    // concerned. Saveable, so it survives a rotation like every other browsing state.
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    // ---- Busy overlay for slow exports -------------------------------------
    // `activeExports` counts in-flight jobs (see runExport); `exportLabel` is what the overlay
    // says it is doing. Kept as plain state rather than a ViewModel concern because the label is
    // already-resolved UI text and the jobs finish with UI side effects (snackbars, share sheets).
    var activeExports by remember { mutableIntStateOf(0) }
    var exportLabel by remember { mutableStateOf<String?>(null) }
    // Retained across the overlay's exit fade, so the caption does not vanish before it does.
    var rememberedExportLabel by remember { mutableStateOf("") }

    // Day whose report sheet is open, or null when it's closed.
    var reportDay by remember { mutableStateOf<DayOrders?>(null) }
    // Retains the day during the sheet's exit animation: `reportDay` is already null by then, and
    // an empty animated box would pop instead of sliding away.
    var rememberedReportDay by remember { mutableStateOf<DayOrders?>(null) }
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val pendingScan by viewModel.pendingScan.collectAsStateWithLifecycle()
    val suppliesState by suppliesViewModel.uiState.collectAsStateWithLifecycle()
    val suppliesMessage by suppliesViewModel.messages.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Hoisted out of the UI tree so resource lookups done from coroutines use the same locale.
    val resources = context.resources
    val scope = rememberCoroutineScope()

    // Whether the Personalize page is covering the Config tab.
    var personalizeOpen by rememberSaveable { mutableStateOf(false) }

    // ---- Backup import -----------------------------------------------------
    // A backup that has been read and validated but not yet written, plus the file name to show
    // in the confirmation. Held here rather than in the ViewModel because it is modal UI state: a
    // pending dialog must not survive a rotation into a database write the user never confirmed.
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
  // Whether the pending restore should also apply the file's settings. Part of the dialog's own
  // state rather than `PendingImport`, because it is the answer to a question the dialog asked —
  // not a property of the file. Reset with it, so the next file starts from "off".
  var restoreSettingsToo by remember { mutableStateOf(false) }

    // Leaving Config with Personalize open would otherwise drop the user straight back into the
    // editor on their next visit, so the tab switch closes it.
    LaunchedEffect(destination) {
        if (destination != AppDestinations.SETTINGS) personalizeOpen = false
    }

    // Keeps the process-wide money formatter in step with the stored preference. Done here, at
    // the root, so it is set before any screen can format an amount: `formatCents` is called from
    // dozens of non-composable places, and passing a flag through all of them would guarantee some
    // call site (usually an exporter) quietly disagreeing with the screen.
    LaunchedEffect(settings.showMoneyDecimals) {
        showCents = settings.showMoneyDecimals
    }

    // Same treatment for the currency symbol, and for the same reason: `formatCents` is called
    // from the exporters and the PDF renderer as well as from every screen.
    LaunchedEffect(settings.currencyId) {
        currencySymbol = settings.currency.symbol
    }

    // Back closes Personalize before it does anything else, matching the visible back arrow.
    //
    // The colour sub-page inside Personalize handles its own back press (see `PersonalizeScreen`)
    // and registers it later in the tree, so it wins while it is open and this one only sees the
    // press once the landing page is showing again.
    BackHandler(enabled = personalizeOpen) { personalizeOpen = false }

    // Back leaves an open category folder before it leaves the tab. Registered after Personalize
    // so Personalize wins when both could apply, and guarded on the folder actually being open so
    // it never swallows a press the rest of the app should handle.
    BackHandler(enabled = openFolderId != null) { openFolderId = null }

    // Editor state: null = closed, otherwise the product being created/edited.
    var editingProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var draftBarcode by rememberSaveable { mutableStateOf("") }

    // Which half of the Inventory destination is showing. Local to this screen because nothing
    // outside Inventory needs it, and it should not survive the tab being left.
    var inventoryTab by rememberSaveable { mutableStateOf(InventoryTab.INVENTORY) }

    // Whether the next backup should carry promo rules. Session state, not a persisted setting:
    // it is a decision about *this* export, and remembering it would quietly start including
    // promos in every future backup.
    var includePromosInBackup by remember { mutableStateOf(false) }

    // Whether the next backup should bundle the product photos alongside the JSON. Same reasoning
    // as the promos flag: a per-export decision, so it starts off and is not remembered.
    var includeImagesInBackup by remember { mutableStateOf(false) }

    // Promo editor state, mirroring the product editor: null id + creating = a new promo.
    var editingPromoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isCreatingPromo by rememberSaveable { mutableStateOf(false) }

    // ---- Startup screen ----------------------------------------------------
    // Shown until **both** the catalogue has arrived *and* the minimum duration has passed.
    // `splashElapsed` guards the floor, `dataReady` reports the real load, and the splash stays up
    // until the later of the two — so a slow device waits as long as it needs, and a fast one still
    // shows the animation for its full entrance rather than blinking past it.
    //
    // `rememberSaveable` on the min-duration flag matters: without it a configuration change (a
    // rotation during startup) would restart the two-second clock and hold the splash up a second
    // time. `dataReady` is memory-only because it is re-derived from the load state anyway.
    val showStartupScreen = rememberSaveable { settings.showLoadingScreen }
    var splashElapsed by rememberSaveable { mutableStateOf(false) }
    var dataReady by remember { mutableStateOf(!state.isLoading) }

    LaunchedEffect(showStartupScreen) {
        if (!showStartupScreen) return@LaunchedEffect
        delay(startupMinDurationMillis)
        splashElapsed = true
    }

    LaunchedEffect(Unit) {
        // Also covers the case where the first emission already carries data (isLoading == false),
        // so the splash never waits for a change that will not come.
        snapshotFlow { state.isLoading }.distinctUntilChanged().collect { loading ->
            if (!loading) dataReady = true
        }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.toText(context))
        viewModel.clearMessage()
    }

    LaunchedEffect(suppliesMessage) {
        val current = suppliesMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.toText(context))
        suppliesViewModel.clearMessage()
    }

    // ---- Sale chime --------------------------------------------------------
    // Fires on the receipt rather than on the tap, for two reasons: a checkout can still fail
    // (out of stock on the shelf underneath the cart), and the sound should only ever celebrate a
    // sale that actually went through; and `lastOrder` is exactly the "a sale was just completed"
    // edge, so this needs no extra state to tell a successful checkout from a repeated emission.
    //
    // Keyed on the order id: two sales in the same millisecond would otherwise be indistinguishable
    // and the second chime would be dropped.
    val saleSound = settings.saleSound
    LaunchedEffect(cartState.lastOrder?.orderId) {
        val order = cartState.lastOrder ?: return@LaunchedEffect
        if (!settings.playsSaleSound) return@LaunchedEffect
        // Card and cash differ only in what is being celebrated, not in whether the sale happened,
        // so one sound covers both; the volume is nudged up for card because the terminal's own
        // beep tends to mask it.
        ScanBeepStore.playSale(
            context = context,
            sound = saleSound,
            volume = if (order.paymentMethod == PaymentMethod.CARD) 1f else 0.9f,
        )
    }

    // A scanned code that isn't in the catalog opens the "new product" form prefilled with it.
    LaunchedEffect(pendingScan) {
        val code = pendingScan ?: return@LaunchedEffect
        draftBarcode = code
        isCreating = true
        editingProductId = null
        viewModel.clearPendingScan()
    }

    // Non-composable helper: snackbars are shown from coroutines, so the string must be resolved
    // here rather than through `stringResource`.
    fun exportFailed(error: Throwable): String = resources.getString(
        R.string.message_export_failed,
        error.message ?: resources.getString(R.string.message_unknown_error)
    )

    /**
     * Runs a slow export behind the busy overlay.
     *
     * The counter rather than a boolean is deliberate: exports can overlap (a day CSV from the
     * report sheet while the history CSV is still writing), and a boolean would let the first one
     * to finish hide the overlay while the second is still running. Counting keeps it up until
     * the last one is done.
     *
     * The label is resolved here, from the caller's already-composed strings, so no coroutine has
     * to reach back into composition for it.
     */
    fun runExport(label: String, block: suspend () -> Unit) {
        activeExports++
        exportLabel = label
        scope.launch {
            try {
                block()
            } finally {
                // `finally`, not `onSuccess`/`onFailure`: an exception escaping here would
                // otherwise strand the overlay on screen with no way to dismiss it.
                activeExports--
                if (activeExports == 0) exportLabel = null
            }
        }
    }

    /** Exports the whole order history as CSV, one row per sale. */
    fun exportOrders() = runExport(resources.getString(R.string.export_in_progress_csv)) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                ExportFiles.writeCsv(
                    context = context,
                    fileName = ordersCsvFileName(),
                    // Headers follow the app language, so resolve them here (not inside the
                    // background block) against the same resources the screen uses.
                    content = ordersToCsv(ordersByDay, context),
                    folderUri = settings.exportFolderUri
                )
            }
        }
        result.onSuccess { export ->
            snackbarHostState.showSnackbar(
                resources.getString(
                    R.string.message_saved_to_folder,
                    export.fileName,
                    settings.exportFolderLabel
                )
            )
            runCatching { ExportFiles.open(context, export) }
        }.onFailure { error ->
            snackbarHostState.showSnackbar(exportFailed(error))
        }
    }

    /**
     * Writes a full backup and opens it.
     *
     * Both the plain export and the share variant use this, so they get the busy overlay and
     * cannot overlap with the CSV/PDF exporters.
     *
     * The output follows [includeImagesInBackup]: with images it is a `.zip` holding the JSON and
     * every product photo, without them a plain `.json`. The JSON itself is identical either way,
     * so a `.zip` backup is still readable by anything that understands the old format.
     *
     * @param share fires the share sheet instead of trying to open the file. The right default
     *   differs by device: a phone often has nothing that can display a `.json`, while the whole
     *   point of the file is getting it onto a computer — hence two separate actions.
     */
    fun exportJsonBackup(share: Boolean) =
        runExport(
            resources.getString(
                if (includeImagesInBackup) R.string.export_in_progress_archive
                else R.string.export_in_progress_json
            )
        ) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    // The archive is built by the helper the Wi-Fi transfer also uses, so the two
                    // export paths cannot disagree about what a backup contains.
                    ExportFiles.writeArchive(
                        context = context,
                        fileName = BackupArchive.archiveFileName(System.currentTimeMillis()),
                        bytes = viewModel.buildBackupArchive(
                            context = context,
                            settings = settings,
                            appVersionName = StockDatabase.appVersionName(context),
                            includePromos = includePromosInBackup,
                            includeImages = includeImagesInBackup,
                        ),
                        folderUri = settings.exportFolderUri,
                    )
                }
            }
            result.onSuccess { export ->
                snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.message_saved_to_folder,
                        export.fileName,
                        settings.exportFolderLabel
                    )
                )
                if (share) {
                    runCatching { ExportFiles.share(context, export) }
                } else {
                    runCatching { ExportFiles.open(context, export) }
                }
            }.onFailure { error ->
                snackbarHostState.showSnackbar(exportFailed(error))
            }
        }

    /**
     * Reads a backup the user picked and asks for confirmation before writing anything.
     *
     * Nothing is imported here: the write is destructive, so the file is parsed and validated
     * first and the result is parked in [pendingImport] until the user confirms. Parsing runs in
     * [runExport] so it shares the busy overlay — a large backup takes a visible moment, and a
     * second import must not start on top of it.
     */
    fun readBackupForImport(uri: android.net.Uri) =
        runExport(resources.getString(R.string.import_reading)) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = ExportFiles.displayName(context, uri)
                    // A .zip carries the JSON *and* the photos, so it needs a second step the
                    // plain .json path does not: the photos have to be unpacked onto this device
                    // before the products can point at them.
                    if (BackupArchive.isArchive(name)) {
                        val archive = BackupArchive.read(ExportFiles.readArchive(context, uri))
                        val contents = JsonImport.readBackup(archive.json)
                        val written = RestoredImages.writeAll(context, archive.images)
                        name to RestoredImages.apply(contents, written)
                    } else {
                        // Still accepts a plain .json backup, so old exports keep working.
                        name to JsonImport.readBackup(ExportFiles.readJson(context, uri))
                    }
                }
            }
            result.onSuccess { (name, contents) ->
                if (contents.isEmpty) {
                    // Nothing to restore, so there is nothing to confirm: say so and stop here
                    // rather than opening a dialog whose only outcome is a no-op.
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.import_nothing_to_import)
                    )
                } else {
                    // Remembered against this file only: a new import always starts with the
                    // switch off, so restoring twice cannot quietly re-arm the second time.
                    restoreSettingsToo = false
                    pendingImport = PendingImport(name, contents)
                }
            }.onFailure { error ->
                // Both the parser and the format validator throw plain exceptions with a message
                // written for the user, so the generic export wording would be misleading here.
                snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.message_import_failed,
                        error.message ?: resources.getString(R.string.message_unknown_error)
                    )
                )
            }
        }

    /** Exports just the tapped day, named after that day rather than today. */
    fun exportDayCsv(day: DayOrders) = runExport(resources.getString(R.string.export_in_progress_csv)) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                ExportFiles.writeCsv(
                    context = context,
                    fileName = dayOrdersCsvFileName(day.dayStartMillis),
                    content = dayOrdersToCsv(day, context),
                    folderUri = settings.exportFolderUri
                )
            }
        }
        result.onSuccess { export ->
            snackbarHostState.showSnackbar(
                resources.getString(
                    R.string.message_saved_to_folder,
                    export.fileName,
                    settings.exportFolderLabel
                )
            )
            runCatching { ExportFiles.open(context, export) }
        }.onFailure { error ->
            snackbarHostState.showSnackbar(exportFailed(error))
        }
    }

    /** Renders the day's report as a PDF and opens the share sheet. */
    fun shareReportPdf(report: DailyReport) =
        runExport(resources.getString(R.string.export_in_progress_pdf)) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val labels = ReportRenderer.Labels(
                        title = resources.getString(R.string.sales_report_title),
                        company = resources.getString(R.string.sales_report_company),
                        orders = resources.getString(R.string.sales_report_orders),
                        items = resources.getString(R.string.sales_report_items),
                        gross = resources.getString(R.string.sales_report_gross),
                        tax = resources.getString(R.string.sales_report_tax),
                        cardFee = resources.getString(R.string.sales_report_card_fee),
                        cardFeeFixed = resources.getString(R.string.sales_report_card_fee_fixed),
                        net = resources.getString(R.string.sales_report_net),
                        topProducts = resources.getString(R.string.sales_report_best_sellers),
                        hourly = resources.getString(R.string.sales_report_by_hour),
                        payments = resources.getString(R.string.sales_report_payment_split),
                        cash = resources.getString(R.string.payment_cash),
                        card = resources.getString(R.string.payment_card),
                        empty = resources.getString(R.string.sales_report_empty),
                        tableItems = resources.getString(R.string.sales_report_table_items),
                        tablePrice = resources.getString(R.string.sales_report_table_price),
                        tableQty = resources.getString(R.string.sales_report_table_qty),
                        tableAmount = resources.getString(R.string.sales_report_table_amount),
                        tableTaxRate = resources.getString(R.string.sales_report_table_tax_rate),
                        tableTax = resources.getString(R.string.sales_report_table_tax),
                        tableCardFee = resources.getString(R.string.sales_report_table_card_fee),
                        tableTotal = resources.getString(R.string.sales_report_table_total),
                        tableGrandTotal =
                            resources.getString(R.string.sales_report_table_grand_total),
                        unitsSold = resources.getString(R.string.sales_report_units_sold_chart),
                        totalSales = resources.getString(R.string.sales_report_total_sales_chart),
                        itemSold = resources.getString(R.string.sales_report_item_sold_chart),
                        notes = resources.getString(R.string.sales_report_notes),
                        noteTotal = resources.getString(R.string.sales_report_note_total),
                        noteBestSeller =
                            resources.getString(R.string.sales_report_note_best_seller),
                        noteBusiestHour =
                            resources.getString(R.string.sales_report_note_busiest_hour),
                        mixedRate = resources.getString(R.string.sales_report_mixed_rate),
                        tableMoreRows = resources.getString(R.string.sales_report_table_more),
                    )
                    val bytes = ReportRenderer.renderPdf(report, labels)
                    ExportFiles.writePdf(
                        context = context,
                        fileName = ReportRenderer.pdfFileName(report.dayStartMillis),
                        bytes = bytes,
                        folderUri = settings.exportFolderUri
                    )
                }
            }
            result.onSuccess { export ->
                snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.message_saved_to_folder,
                        export.fileName,
                        settings.exportFolderLabel
                    )
                )
                runCatching { ExportFiles.share(context, export) }
            }.onFailure { error ->
                snackbarHostState.showSnackbar(exportFailed(error))
            }
        }

    fun exportQrLabel(productName: String, barcode: String) {
        // Answered before the overlay goes up: a snackbar behind a full-covering scrim would look
        // like the tap did nothing at all.
        if (barcode.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.message_add_barcode_first))
            }
            return
        }
        runExport(resources.getString(R.string.export_in_progress_qr)) {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val bitmap = QrCodeExporter.renderLabel(barcode)
                    ExportFiles.writePng(
                        context = context,
                        fileName = QrCodeExporter.fileNameFor(productName, barcode),
                        bitmap = bitmap,
                        folderUri = settings.exportFolderUri
                    )
                }
            }
            result.onSuccess { export ->
                snackbarHostState.showSnackbar(
                    resources.getString(
                        R.string.message_saved_to_folder,
                        export.fileName,
                        settings.exportFolderLabel
                    )
                )
                ExportFiles.open(context, export)
            }.onFailure { error ->
                snackbarHostState.showSnackbar(exportFailed(error))
            }
        }
    }

    /**
     * Copies a newly picked photo into private storage, then saves the product.
     *
     * The copy happens here (off the composable) so it survives a rotation mid-save: it runs in
     * the ViewModel's scope and completes even if the form is recreated.
     *
     * [ownerId] namespaces the stored file per product. Without it every pick produced a name from
     * the clock plus a process-local counter, so two picks could collide on the same filename,
     * overwrite the file in place, and leave the thumbnail showing the old decoded bitmap — which is
     * why replacing a product's photo appeared to do nothing. A new product has no id yet, hence
     * `0L` for the insert path; its file is named with the barcode instead.
     */
    fun saveDraftImage(draft: ProductDraft, ownerId: Long, onReady: (String?) -> Unit) {
        val uri = draft.pickedImage
        if (uri == null) {
            onReady(draft.resolvedImagePath)
            return
        }
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                viewModel.savePickedImage(context, uri, sourceId = ownerId)
            }
            if (stored == null) {
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.product_image_failed)
                )
            }
            onReady(stored ?: draft.resolvedImagePath)
        }
    }

    val editingProduct = if (isCreating) {
        null
    } else {
        state.products.firstOrNull { it.id == editingProductId }
    }
    val isEditorOpen = isCreating || editingProductId != null

    val editingPromo = if (isCreatingPromo) {
        null
    } else {
        promos.firstOrNull { it.id == editingPromoId }
    }
    val isPromoEditorOpen = isCreatingPromo || editingPromoId != null

    BackHandler(enabled = isEditorOpen) {
        isCreating = false
        editingProductId = null
    }

    // Declared after the product editor's handler so this one wins when both are somehow open:
    // the promo editor is drawn on top, so Back should close it first.
    BackHandler(enabled = isPromoEditorOpen) {
        isCreatingPromo = false
        editingPromoId = null
    }

    // The category manager is a panel over the inventory, not another destination: it edits the
    // data the screen behind it is showing, and leaving it should put you back where you were
    // rather than at the top of a tab. Its own BackHandler (inside `CategoryEditScreen`) only
    // handles the narrower case of cancelling an in-progress rename, so this one still sees the
    // press once no row is being edited.
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = categoriesOpen) { categoriesOpen = false }

    // Same treatment for the tag manager, which is only reachable while tags are switched on.
    // The setting is re-checked here rather than only at the entry point: turning tags off from
    // Config while the panel is open must not leave it standing.
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = tagsOpen) { tagsOpen = false }
    LaunchedEffect(settings.tagsEnabled) {
        if (!settings.tagsEnabled) tagsOpen = false
    }

    // ---- Wi-Fi transfer ----------------------------------------------------
    // The whole transfer lives and dies with this flag. The listening socket is owned by the
    // server object below, which is stopped whenever the flag goes false — that is what keeps the
    // port open for exactly as long as the user is looking at the screen.
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = transferOpen) { transferOpen = false }

    // Android 17 (API 37) blocks local network access for apps that target 37 until the user
    // grants ACCESS_LOCAL_NETWORK. Older releases keep the implicit access INTERNET already
    // gives, so the permission is only requested on 37+. It is in the NEARBY_DEVICES group;
    // a phone that granted another nearby permission is not prompted twice.
    val localNetworkPermission = if (Build.VERSION.SDK_INT >= 37) {
        rememberPermissionState("android.permission.ACCESS_LOCAL_NETWORK")
    } else {
        null
    }
    val localNetworkGranted =
        localNetworkPermission == null || localNetworkPermission.status.isGranted

    // Ask as soon as the transfer screen opens. The server is useless without it, and a prompt
    // the user did not expect halfway through "Start connection" is worse than one on arrival.
    LaunchedEffect(transferOpen) {
        if (transferOpen && localNetworkPermission != null && !localNetworkPermission.status.isGranted) {
            localNetworkPermission.launchPermissionRequest()
        }
    }

    val transferScope = rememberCoroutineScope()
    var transferServer by remember { mutableStateOf<BackupServer?>(null) }
    var receiveState by remember { mutableStateOf(ReceiveState.Idle) }
    var receiveProgress by remember { mutableStateOf<Float?>(null) }
    var transferError by remember { mutableStateOf<String?>(null) }
    val transferPort = BackupTransfer.DEFAULT_PORT
    val transferCode = remember(transferServer) { transferServer?.code.orEmpty() }
    val wifiAddress = remember(transferOpen, localNetworkGranted) {
        if (transferOpen) LocalAddress.wifiAddress() else null
    }

    /** Stops the server and resets the receive half. Also the single place that closes the port. */
    fun stopReceiving() {
        transferServer?.close()
        transferServer = null
        receiveProgress = null
        if (receiveState != ReceiveState.Done) receiveState = ReceiveState.Idle
    }

    /**
     * Imports an archive that arrived over the network.
     *
     * Mirrors `readBackupForImport` exactly, minus the file picker: read the archive, parse it,
     * write any photos, then restore. Returns a string resource id on failure, null on success.
     *
     * Appearance is applied too, but only appearance: the desktop page can now choose one of this
     * app's palettes, so the colours have to travel back with the data. Tax, currency, language
     * and scanner settings belong to the phone and are deliberately left alone.
     *
     * Declared above [startReceiving] on purpose: Kotlin local functions cannot be forward-referenced
     * from a lambda, and the server's `onReceived` callback captures this one.
     */
    suspend fun importReceivedBytes(bytes: ByteArray): Int? {
        val contents = runCatching {
            withContext(Dispatchers.IO) {
                val archive = BackupArchive.read(bytes)
                val parsed = JsonImport.readBackup(archive.json)
                if (parsed.isEmpty && archive.images.isEmpty()) return@withContext null
                val restored = RestoredImages.writeAll(context, archive.images)
                RestoredImages.apply(parsed, restored)
            }
        }.getOrNull() ?: return R.string.transfer_error_not_backup

        return runCatching {
            viewModel.restoreBackup(contents, replace = true)
            contents.settings?.let { settingsViewModel.applyImportedAppearance(it) }
            null
        }.getOrElse { R.string.transfer_failed }
    }

    /**
     * Binds the port and starts waiting.
     *
     * A fresh [BackupServer] — and therefore a fresh code — is created on every start, so a code
     * read out yesterday does not open today's server.
     */
    fun startReceiving() {
        if (!localNetworkGranted) {
            // The card on the screen explains what is missing; just surface the system prompt.
            localNetworkPermission?.launchPermissionRequest()
            return
        }
        transferError = null
        receiveProgress = null
        stopReceiving()

        val server = BackupServer(scope = transferScope, port = transferPort)
        // The same temporary server also serves this phone's archive, so the desktop can pull
        // instead of push. Built lazily: the snapshot is taken when the download starts, so an
        // edit made while the screen is open is included.
        server.exportProvider = {
            viewModel.buildBackupArchive(
                context = context,
                settings = settings,
                appVersionName = StockDatabase.appVersionName(context),
                includePromos = includePromosInBackup,
                // Always send photos over Wi-Fi. The "include images" switch is about the size
                // of a file export; a transfer that silently dropped every product picture was
                // a surprise, and the desktop needs the images to show the catalogue properly.
                includeImages = true,
            )
        }
        server.onExported = {
            transferScope.launch {
                receiveState = ReceiveState.Done
                snackbarHostState.showSnackbar(resources.getString(R.string.transfer_export_done))
            }
        }
        server.onProgress = { bytes, total ->
            if (total > 0L) receiveProgress = (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            receiveState = ReceiveState.Receiving
        }
        server.onReceived = { bytes ->
            // The received archive goes through exactly the same validation and restore path as a
            // file picked from storage, so a network import cannot behave differently from a local
            // one — including refusing something that is not a backup.
            transferScope.launch {
                val failure = importReceivedBytes(bytes)
                if (failure == null) {
                    receiveState = ReceiveState.Done
                    snackbarHostState.showSnackbar(resources.getString(R.string.transfer_receive_done))
                } else {
                    transferError = resources.getString(failure)
                    receiveState = ReceiveState.Idle
                }
                receiveProgress = null
            }
        }
        server.onStopped = {
            // Only downgrade a still-waiting server; a finished one keeps showing its result.
            if (receiveState == ReceiveState.Waiting || receiveState == ReceiveState.Receiving) {
                receiveState = ReceiveState.Idle
            }
        }

        if (server.start()) {
            transferServer = server
            receiveState = ReceiveState.Waiting
        } else {
            transferServer = null
            receiveState = ReceiveState.Failed
            transferError = resources.getString(
                R.string.transfer_port_unavailable,
                transferPort.toString(),
            )
        }
    }

    // Closing the screen must not leave a socket bound. This runs on back, on navigation, and on
    // disposal, so there is no path out of the screen that forgets the server.
    LaunchedEffect(transferOpen) {
        if (!transferOpen) {
            stopReceiving()
            receiveState = ReceiveState.Idle
            transferError = null
        }
    }

    val splashVisible = showStartupScreen && !(splashElapsed && dataReady)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Edge-to-edge: `enableEdgeToEdge()` in MainActivity already let the window draw under
        // both system bars, so this Scaffold must not reserve space for them — otherwise that
        // space is given up twice and the app looks padded on a window it no longer shares.
        // The NavigationSuiteScaffold below paints into the navigation-bar strip instead.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val startupEnterMillis = AppMotion.screenEnterMillis()
            val startupExitMillis = AppMotion.screenExitMillis()
            AnimatedContent(
                targetState = splashVisible,
                transitionSpec = {
                    // The startup screen never slides: the real UI fades up underneath it so the
                    // icon does not appear to be dragged off screen.
                    (fadeIn(animationSpec = tween(startupEnterMillis)) +
                        slideInVertically(
                            animationSpec = tween(startupEnterMillis),
                            initialOffsetY = { it / 24 }
                        )) togetherWith
                        (fadeOut(animationSpec = tween(startupExitMillis)) +
                            scaleOut(
                                animationSpec = tween(startupExitMillis),
                                targetScale = 1.12f
                            ))
                },
                label = "startup"
            ) { showingSplash ->
                if (showingSplash) {
                    LoadingScreen(
                        image = settings.loadingImage,
                        spin = settings.loadingSpin,
                        circle = settings.loadingCircle,
                        imageSize = settings.loadingImageSizeDp.dp,
                        circleSize = settings.effectiveLoadingCircleSizeDp.dp,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }

            if (splashVisible) return@Box

            val editorEnterMillis = AppMotion.screenEnterMillis()
            val editorEnter = tween<Float>(editorEnterMillis)
            // Slide specs are typed on the offset they animate, so they cannot share `editorEnter`.
            val editorSlide = tween<IntOffset>(editorEnterMillis, easing = AppMotion.Emphasised)
            val editorExit = tween<Float>(AppMotion.screenExitMillis())

            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    AppDestinations.entries.forEach {
                        item(
                            icon = { Icon(it.icon, contentDescription = null) },
                            label = { Text(stringResource(it.labelRes)) },
                            selected = it == destination,
                            onClick = { destination = it }
                        )
                    }
                }
            ) {
                // Tabs cross-fade rather than slide: they are peers, not a hierarchy, so a
                // directional push would imply a back stack that does not exist.
                Crossfade(
                    targetState = destination,
                    animationSpec = tween(AppMotion.screenEnterMillis()),
                    label = "tabs"
                ) { tab ->
                when (tab) {
                    AppDestinations.INVENTORY -> InventoryScreen(
                        state = state,
                        selectedTab = inventoryTab,
                        promos = promos,
                        onTabChange = { inventoryTab = it },
                        onQueryChange = viewModel::setSearchQuery,
                        onFilterChange = viewModel::setFilter,
                        onProductClick = { product -> editingProductId = product.id },
                        onQuickRestock = { product -> viewModel.restock(product, 1) },
                        onQuickRemoveStock = { product -> viewModel.removeStock(product, 1) },
                        onAddClick = {
                            draftBarcode = BarcodeGenerator.next()
                            isCreating = true
                        },
                        onPromoClick = { promo ->
                            editingPromoId = promo.id
                            isCreatingPromo = false
                        },
                        onPromoToggleEnabled = viewModel::setPromoEnabled,
                        onAddPromoClick = {
                            editingPromoId = null
                            isCreatingPromo = true
                        },
                        onEditCategoriesClick = { categoriesOpen = true },
                        onEditTagsClick = { tagsOpen = true },
                        tagsEnabled = settings.tagsEnabled,
                        // Only offered while the feature is on: with tags off the chip row would
                        // be an empty strip taking up space above the list.
                        tags = if (settings.tagsEnabled) tags else emptyList(),
                        selectedTagIds = selectedTagIds,
                        onTagToggle = viewModel::toggleTagFilter,
                        viewMode = InventoryViewMode.fromId(settings.inventoryViewModeId),
                        onViewModeChange = settingsViewModel::setInventoryViewMode,
                        openFolderId = openFolderId,
                        onOpenFolder = { openFolderId = it },
                        onToggleFavourite = viewModel::toggleFavourite,
                    )

                    AppDestinations.SALES -> SalesCartScreen(
                        state = cartState,
                        products = state.products,
                        categories = categories,
                        onCodeScanned = cartViewModel::addByBarcode,
                        onAddProduct = cartViewModel::addProduct,
                        onIncrement = cartViewModel::increment,
                        onDecrement = cartViewModel::decrement,
                        onRemove = cartViewModel::remove,
                        onClearCart = { cartViewModel.clearCart() },
                        onPaymentMethodChange = cartViewModel::setPaymentMethod,
                        onNoteChange = cartViewModel::setNote,
                        onCheckout = { cartViewModel.checkout() },
                        // Dismissing the receipt is not a write: it only closes a dialog, and
                        // blocking it would leave the user trapped behind a sold receipt.
                        onDismissReceipt = cartViewModel::dismissReceipt,
                        // Beep, popup and the debounce window all come from Personalize.
                        settings = settings,
                        onDismissScanResult = cartViewModel::clearLastScan,
                        onTogglePromoSuppressed = cartViewModel::togglePromoSuppressed,
                    )

                    AppDestinations.HISTORY -> SalesScreen(
                        ordersByDay = ordersByDay,
                        products = state.products,
                        onCancelOrder = viewModel::cancelOrder,
                        onExportCsv = ::exportOrders,
                        onExportDayCsv = ::exportDayCsv,
                        onOpenReport = { day -> reportDay = day }
                    )

                    AppDestinations.SUPPLIES -> SuppliesScreen(
                        state = suppliesState,
                        // Saving and deleting go through the supplies ViewModel, which owns the
                        // result messages.
                        onSaveProvider = suppliesViewModel::saveProvider,
                        onDeleteProvider = suppliesViewModel::deleteProvider,
                        onSaveMaterial = suppliesViewModel::saveMaterial,
                        onDeleteMaterial = suppliesViewModel::deleteMaterial,
                        onAdjustStock = suppliesViewModel::adjustStock,
                        onRecordEntry = suppliesViewModel::recordEntry,
                        onDeleteEntry = suppliesViewModel::deleteEntry,
                    )

                    // Config and Personalize are mutually exclusive: exactly one is composed at a
                    // time, so the page you are not on cannot show through, scroll underneath, or
                    // take focus. `AnimatedContent` rather than a plain `if` so the swap still
                    // cross-fades instead of popping.
                    AppDestinations.SETTINGS -> AnimatedContent(
                        targetState = personalizeOpen,
                        transitionSpec = {
                            (fadeIn(animationSpec = editorEnter) +
                                slideInVertically(
                                    animationSpec = editorSlide,
                                    initialOffsetY = { it / 8 }
                                )) togetherWith fadeOut(animationSpec = editorExit)
                        },
                        label = "config-personalize"
                    ) { showPersonalize ->
                        if (showPersonalize) {
                            PersonalizeScreen(
                                settings = settings,
                                draft = colorDraft,
                                onBack = { personalizeOpen = false },
                                onToggleBackground = { enabled ->
                                        settingsViewModel.setBackgroundColor(
                                            enabled = enabled,
                                            argb = colorDraft.backgroundArgb
                                                ?: settings.backgroundColorArgb
                                        )
                                    },
                                    onPreviewBackgroundColor =
                                        settingsViewModel::previewBackgroundColor,
                                    onToggleButton = { enabled ->
                                        settingsViewModel.setButtonColor(
                                            enabled = enabled,
                                            argb = colorDraft.buttonArgb ?: settings.buttonColorArgb
                                        )
                                    },
                                    onPreviewButtonColor = settingsViewModel::previewButtonColor,
                                    onTogglePanel = { enabled ->
                                        settingsViewModel.setPanelColor(
                                            enabled = enabled,
                                            argb = colorDraft.panelArgb ?: settings.panelColorArgb
                                        )
                                    },
                                    onPreviewPanelColor = settingsViewModel::previewPanelColor,
                                    onToggleText = { enabled ->
                                        settingsViewModel.setTextColor(
                                            enabled = enabled,
                                            argb = colorDraft.textArgb ?: settings.textColorArgb
                                        )
                                    },
                                    onPreviewTextColor = settingsViewModel::previewTextColor,
                                    onToggleHighlight = { enabled ->
                                        settingsViewModel.setHighlightColor(
                                            enabled = enabled,
                                            argb = colorDraft.highlightArgb
                                                ?: settings.highlightColorArgb
                                        )
                                    },
                                    onPreviewHighlightColor =
                                        settingsViewModel::previewHighlightColor,
                                    onFontFamilyChange = { family ->
                                        settingsViewModel.setFontFamily(family.id)
                                    },
                                    onTextSizeChange = settingsViewModel::setTextSize,
                                    onMotionSpeedChange = settingsViewModel::setMotionSpeed,
                                    onLoadingIconChange = settingsViewModel::setLoadingIcon,
                                    onLoadingSpinChange = settingsViewModel::setLoadingSpin,
                                    onLoadingCircleChange = settingsViewModel::setLoadingCircle,
                                    onLoadingImageSizeChange =
                                        settingsViewModel::setLoadingImageSize,
                                    onLoadingCircleSizeChange =
                                        settingsViewModel::setLoadingCircleSize,
                                    onToggleLoadingScreen = settingsViewModel::setShowLoadingScreen,
                                    onResetLoadingScreen = {
                                        settingsViewModel.resetLoadingScreen(context)
                                    },
                                    onLoadingImagePicked = { uri ->
                                        scope.launch {
                                            val stored =
                                                settingsViewModel.setLoadingImage(uri, context)
                                            if (!stored) {
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        R.string.settings_loading_image_failed
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClearLoadingImage = {
                                        settingsViewModel.clearLoadingImage(context)
                                    },
                                    // Validated before it is stored, so a `false` leaves the
                                    // startup screen exactly as it was rather than pointing at a
                                    // file that would fail on the next launch.
                                    onPickLoadingLottieFile = { uri ->
                                        scope.launch {
                                            val stored = settingsViewModel
                                                .setLoadingLottie(uri, context)
                                            if (!stored) {
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        R.string.settings_loading_animation_failed
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClearLoadingLottieFile = {
                                        scope.launch {
                                            settingsViewModel.clearLoadingLottie(context)
                                        }
                                    },
                                    onApplyColors = {
                                        settingsViewModel.applyDraft()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                resources.getString(R.string.message_colours_applied)
                                            )
                                        }
                                    },
                                    onDiscardColors = settingsViewModel::discardDraft,
                                    onResetAppearance = settingsViewModel::resetAppearance,
                                    onApplyTheme = { theme ->
                                        settingsViewModel.applyTheme(theme)
                                        // Confirmed by name so the change is attributed: a palette
                                        // swap repaints the whole screen, and without this the user
                                        // sees the app change colour with no statement of what they
                                        // just picked.
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                resources.getString(
                                                    R.string.settings_themes_applied,
                                                    theme.nameRes
                                                        ?.let { resources.getString(it) }
                                                        ?: theme.name.orEmpty()
                                                )
                                            )
                                        }
                                    },
                                    // Takes the ink as well, so a theme saved from here is a
                                    // complete palette and can be re-applied without inheriting
                                    // whatever the previous theme set.
                                    onSaveTheme = settingsViewModel::saveTheme,
                                    onDeleteTheme = settingsViewModel::deleteTheme,
                                    onToggleScanBeep = settingsViewModel::setScannerBeepEnabled,
                                    onScanBeepChange = settingsViewModel::setScannerBeep,
                                    // Auditioned right here rather than from inside the ViewModel:
                                    // SoundPool playback is a UI effect and must stay on the main
                                    // thread, where this callback already runs.
                                    onPreviewScanBeep = { beep ->
                                        ScanBeepStore.play(
                                            context,
                                            ScanBeepSound.BuiltIn(beep)
                                        )
                                    },
                                    onPickScanBeepFile = { uri ->
                                        scope.launch {
                                            val stored = settingsViewModel
                                                .setScannerBeepFile(uri, context)
                                            if (!stored) {
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        R.string.settings_scan_beep_failed
                                                    )
                                                )
                                            } else {
                                                // Play it back so the user immediately knows the
                                                // file they picked is actually audible.
                                                ScanBeepStore.play(
                                                    context,
                                                    ScanBeepSound.Picked(
                                                        settingsViewModel.settings.value
                                                            .scannerBeepFileName.orEmpty()
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClearScanBeepFile = {
                                        settingsViewModel.clearScannerBeepFile(context)
                                    },
                                    onToggleSaleSound = settingsViewModel::setSaleSoundEnabled,
                                    onSaleSoundChange = settingsViewModel::setSaleSound,
                                    // Previewed here for the same reason as the beep: SoundPool
                                    // playback is a main-thread UI effect. The sale pool is used
                                    // so the preview sounds exactly as loud and as long as the
                                    // real thing will.
                                    onPreviewSaleSound = { sound ->
                                        ScanBeepStore.playSale(
                                            context,
                                            ScanBeepSound.SaleChime(sound)
                                        )
                                    },
                                    onPickSaleSoundFile = { uri ->
                                        scope.launch {
                                            val stored = settingsViewModel
                                                .setSaleSoundFile(uri, context)
                                            if (!stored) {
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        R.string.settings_sale_sound_failed
                                                    )
                                                )
                                            } else {
                                                ScanBeepStore.playSale(
                                                    context,
                                                    ScanBeepSound.Picked(
                                                        settingsViewModel.settings.value
                                                            .saleSoundFileName.orEmpty()
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClearSaleSoundFile = {
                                        settingsViewModel.clearSaleSoundFile(context)
                                    },
                                    onSaleAnimationChange =
                                        settingsViewModel::setSaleAnimation,
                                    onSaleAnimationSizeChange =
                                        settingsViewModel::setSaleAnimationSize,
                                    // The store validates the file before anything is written, so
                                    // `false` means the pick was not a playable animation and the
                                    // settings are left exactly as they were.
                                    onPickLottieFile = { uri ->
                                        scope.launch {
                                            val stored = settingsViewModel
                                                .setSaleLottieFile(uri, context)
                                            if (!stored) {
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        R.string.settings_sale_animation_failed
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClearLottieFile = {
                                        scope.launch {
                                            settingsViewModel.clearSaleLottieFile(context)
                                        }
                                    },
                                    onToggleScanVibration =
                                        settingsViewModel::setScanVibrationEnabled,
                                    onScanVibrationChange =
                                        settingsViewModel::setScanVibration,
                                    // Felt from here for the same reason the beep is heard from
                                    // here: this is a UI effect on the main thread, and the
                                    // settings page is the only place that previews it.
                                    onPreviewScanVibration = { pattern, amplitude ->
                                        ScanVibrationStore.vibrate(
                                            context = context,
                                            pattern = pattern,
                                            amplitude = amplitude,
                                        )
                                    },
                                    onScanVibrationStrengthChange =
                                        settingsViewModel::setScanVibrationStrength,
                                    onResetScanner = { settingsViewModel.resetScanner(context) }
                                )
                        } else {
                            SettingsScreen(
                                settings = settings,
                                onFolderPicked = { uri ->
                                    // The picked folder and the persistable grant are two separate
                                    // things, and conflating them was losing the choice outright:
                                    // when `takePersistableUriPermission` threw, the folder the user
                                    // had just selected was thrown away too and every export silently
                                    // fell back to Downloads for the rest of the install's life.
                                    //
                                    // The URI is stored whenever the folder is genuinely writable.
                                    // The grant only decides whether it survives a reboot; if it does
                                    // not, the write path detects the dead folder and says so.
                                    val picked = android.net.Uri.parse(uri)
                                    val writable = ExportFiles.canWriteTo(context, uri)
                                    val persisted =
                                        ExportFiles.persistFolderPermission(context, picked)
                                    settingsViewModel.setExportFolder(if (writable) picked else null)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            resources.getString(
                                                when {
                                                    !writable ->
                                                        R.string.message_folder_unavailable
                                                    persisted ->
                                                        R.string.message_folder_selected
                                                    // Stored, but only for this session: saying
                                                    // "selected" alone would promise more than the
                                                    // grant can deliver after a restart.
                                                    else ->
                                                        R.string.message_folder_selected_session
                                                }
                                            )
                                        )
                                    }
                                },
                                onClearFolder = { settingsViewModel.setExportFolder(null) },
                                onLanguageChange = settingsViewModel::setLanguage,
                                onTaxRateChange = settingsViewModel::setTaxRate,
                                onCardFeeRateChange = settingsViewModel::setCardFeeRate,
                                onCardFeeFixedChange = settingsViewModel::setCardFeeFixed,
                                showMoneyDecimals = settings.showMoneyDecimals,
                                onShowMoneyDecimalsChange =
                                    settingsViewModel::setShowMoneyDecimals,
                                currency = settings.currency,
                                onCurrencyChange = settingsViewModel::setCurrency,
                                // Moved here from Personalize: the scanner's timing and popup are
                                // day-to-day working settings, not appearance choices.
                                onScanDelayChange = settingsViewModel::setScanDelay,
                                onProductChangeDelayChange = settingsViewModel::setProductChangeDelay,
                                showScanPopup = settings.showScanPopup,
                                onShowScanPopupChange = settingsViewModel::setShowScanPopup,
                                tagsEnabled = settings.tagsEnabled,
                                onTagsEnabledChange = settingsViewModel::setTagsEnabled,
                                // Opening Personalize is local to this tab, so the flag lives
                                // here rather than in the ViewModel: nothing outside Config
                                // needs it.
                                onOpenPersonalize = { personalizeOpen = true },
                                onExportJsonBackup = { exportJsonBackup(share = false) },
                                onShareJsonBackup = { exportJsonBackup(share = true) },
                                onImportJsonBackup = { uri -> readBackupForImport(uri) },
                                includePromosInBackup = includePromosInBackup,
                                onIncludePromosChange = { includePromosInBackup = it },
                                includeImagesInBackup = includeImagesInBackup,
                                onIncludeImagesChange = { includeImagesInBackup = it },
                                // Disabled mid-export: snapshotting the database again while a
                                // write is in flight would just queue a second, redundant copy.
                                isExporting = activeExports > 0,
                                onOpenTransfer = { transferOpen = true },
                            )
                        }
                    }
                }
                    }
            }

            // The report sheet is a modal overlay on top of whichever tab is showing, so it fades
            // and lifts rather than replacing the tab.
            AnimatedVisibility(
                visible = reportDay != null,
                enter = fadeIn(animationSpec = tween(AppMotion.screenEnterMillis())) +
                    slideInVertically(
                        animationSpec = tween(
                            AppMotion.screenEnterMillis(),
                            easing = AppMotion.Emphasised
                        ),
                        initialOffsetY = { it / 12 }
                    ),
                exit = fadeOut(animationSpec = tween(AppMotion.screenExitMillis())) +
                    slideOutVertically(
                        animationSpec = tween(AppMotion.screenExitMillis()),
                        targetOffsetY = { it / 12 }
                    ),
                label = "daily-report"
            ) {
                // `reportDay` is nulled on dismiss, so keep the last non-null day to keep drawing
                // during the exit animation instead of popping an empty box.
                val visibleDay = reportDay ?: rememberedReportDay
                if (visibleDay != null) {
                    LaunchedEffect(visibleDay) { rememberedReportDay = visibleDay }
                    val report = buildDailyReport(visibleDay, topLimit = 7)
                    // The range charts span the last week, which is re-derived from the same
                    // `ordersByDay` the History tab already observes — no extra query.
                    val series = remember(ordersByDay) {
                        buildReportSeries(
                            buckets = ordersByDay,
                            nowMillis = System.currentTimeMillis(),
                            days = 7,
                        )
                    }
                    AnalyticsScreen(
                        report = report,
                        series = series,
                        onExportCsv = { exportDayCsv(visibleDay) },
                        onExportPdf = { shareReportPdf(report) },
                        onDismiss = { reportDay = null },
                        isExporting = activeExports > 0,
                        exportLabel = exportLabel,
                    )
                }
            }

            // The import confirmation floats above everything, like the report sheet: it is a
            // decision the user has to answer before anything else can be tapped, and restoring
            // is destructive. Rendered unconditionally from a single nullable state so there is
            // no way to leave a stale copy on screen.
            pendingImport?.let { pending ->
                ImportBackupDialog(
                    contents = pending.contents,
                    fileName = pending.fileName,
                    restoreSettings = restoreSettingsToo,
                    onRestoreSettingsChange = { restoreSettingsToo = it },
                    onConfirm = {
                        // Cleared before the write starts, so a fast double-tap cannot queue a
                        // second restore of the same file.
                        pendingImport = null
                        // Settings first, then the data: the settings write is the cheap one and
                        // it also drops any pending colour draft (see `applyImportedSettings`),
                        // so a draft cannot outlive the restore and repaint the old theme over the
                        // one that was just brought back.
                        pending.contents.settings?.let { imported ->
                            if (restoreSettingsToo) {
                                settingsViewModel.applyImportedSettings(imported)
                            }
                        }
                        viewModel.restoreBackup(pending.contents, replace = true)
                    },
                    onDismiss = { pendingImport = null }
                )
            }

            // The editor is the last child of the Box so it is drawn — and hit-tested — above the
            // tabs and the FAB. It slides up over them rather than replacing them, so the tab
            // content is still there when the form is dismissed.
            //
            // This must animate on `isEditorOpen` alone. Anything that keeps a composable in the
            // Box while the editor is closed would sit above the tabs and swallow the taps meant
            // for the "add product" FAB.
            AnimatedContent(
                targetState = isEditorOpen,
                transitionSpec = {
                    (fadeIn(animationSpec = editorEnter) +
                        slideInVertically(
                            animationSpec = editorSlide,
                            initialOffsetY = { it }
                        )) togetherWith fadeOut(animationSpec = editorExit)
                },
                // Only the open editor occupies space in the hit test. When closed the container
                // collapses to zero size, so taps reach the tabs beneath instead of stopping here.
                modifier = if (isEditorOpen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                },
                label = "editor"
            ) { editorOpen ->
                if (editorOpen) {
                    ProductEditScreen(
                        existing = editingProduct,
                        initialBarcode = draftBarcode,
                        categories = categories,
                        onCreateCategory = viewModel::createCategory,
                        // Tags are only meaningful while the feature is on: passing an empty list
                        // and `tagsEnabled = false` is what makes the whole block disappear from
                        // the form rather than showing an empty picker.
                        tags = if (settings.tagsEnabled) tags else emptyList(),
                        selectedTagIds = editingProduct?.let {
                            tagIdsByProduct[it.id].orEmpty()
                        }.orEmpty(),
                        tagsEnabled = settings.tagsEnabled,
                        onExportQrClick = { name, barcode -> exportQrLabel(name, barcode) },
                        onSave = { draft ->
                            saveDraftImage(draft, ownerId = 0L) { imagePath ->
                                viewModel.addProduct(
                                    barcode = draft.barcode,
                                    name = draft.name,
                                    priceCents = draft.priceCents,
                                    quantity = draft.quantity,
                                    context = context,
                                    categoryId = draft.categoryId,
                                    imagePath = imagePath,
                                    // Handed to the ViewModel as a follow-up rather than as an
                                    // extra argument: the tag write needs the product's new id,
                                    // which does not exist until the insert has happened.
                                    tagIds = draft.tagIds,
                                )
                            }
                            isCreating = false
                            draftBarcode = ""
                        },
                        onUpdate = { product, draft ->
                            saveDraftImage(draft, ownerId = product.id) { imagePath ->
                                viewModel.updateProduct(
                                    product.copy(
                                        name = draft.name,
                                        priceCents = draft.priceCents,
                                        quantity = draft.quantity,
                                        categoryId = draft.categoryId,
                                        imagePath = imagePath,
                                    ),
                                    context
                                )
                            }
                            // Only written when the feature is on: an empty set from a form that
                            // never showed the chips must not clear tags a backup restored.
                            if (settings.tagsEnabled) {
                                viewModel.setProductTags(product.id, draft.tagIds)
                            }
                            editingProductId = null
                        },
                        onDelete = { product -> viewModel.deleteProduct(product, context) },
                        onBack = {
                            isCreating = false
                            editingProductId = null
                        },
                        settings = settings
                    )
                }
            }

            // The promo editor is a peer of the product editor: same slide-over treatment, same
            // zero-size-when-closed trick so it never swallows taps meant for the tabs.
            AnimatedContent(
                targetState = isPromoEditorOpen,
                transitionSpec = {
                    (fadeIn(animationSpec = editorEnter) +
                        slideInVertically(
                            animationSpec = editorSlide,
                            initialOffsetY = { it }
                        )) togetherWith fadeOut(animationSpec = editorExit)
                },
                modifier = if (isPromoEditorOpen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                },
                label = "promo-editor"
            ) { promoEditorOpen ->
                if (promoEditorOpen) {
                    PromoEditScreen(
                        existing = editingPromo,
                        categories = categories,
                        products = state.products,
                        // So the fixed amount is seeded the same way every other money figure is
                        // displayed — 12 rather than 12.00 when cents are off.
                        showMoneyDecimals = settings.showMoneyDecimals,
                        onSave = { promo ->
                            viewModel.savePromo(promo)
                            isCreatingPromo = false
                            editingPromoId = null
                        },
                        onDelete = { promo ->
                            viewModel.deletePromo(promo)
                            isCreatingPromo = false
                            editingPromoId = null
                        },
                        onBack = {
                            isCreatingPromo = false
                            editingPromoId = null
                        }
                    )
                }
            }

            // The category manager is a third peer panel, with the same slide-over treatment and
            // the same zero-size-when-closed trick. It is anchored here rather than inside
            // `InventoryScreen` so it can sit above the tab bar: managing categories is not a
            // sub-view of one tab, it is a place you go and come back from.
            AnimatedContent(
                targetState = categoriesOpen,
                transitionSpec = {
                    (fadeIn(animationSpec = editorEnter) +
                        slideInVertically(
                            animationSpec = editorSlide,
                            initialOffsetY = { it / 8 }
                        )) togetherWith fadeOut(animationSpec = editorExit)
                },
                modifier = if (categoriesOpen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                },
                label = "category-editor"
            ) { categoryEditorOpen ->
                if (categoryEditorOpen) {
                    CategoryEditScreen(
                        categories = categories,
                        products = state.products,
                        onBack = { categoriesOpen = false },
                        onRename = viewModel::renameCategory,
                        onDelete = viewModel::deleteCategory,
                        onCreate = viewModel::createCategory
                    )
                }
            }

            // The tag manager, a fourth peer panel with the same treatment. Drawn after the
            // category panel because only one of them is ever open at a time, and giving it the
            // later slot means it is the one on top if that ever stops being true.
            AnimatedContent(
                targetState = tagsOpen,
                transitionSpec = {
                    (fadeIn(animationSpec = editorEnter) +
                        slideInVertically(
                            animationSpec = editorSlide,
                            initialOffsetY = { it / 8 }
                        )) togetherWith fadeOut(animationSpec = editorExit)
                },
                modifier = if (tagsOpen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                },
                label = "tag-editor"
            ) { tagEditorOpen ->
                if (tagEditorOpen) {
                    TagEditScreen(
                        tags = tags,
                        tagIdsByProduct = tagIdsByProduct,
                        onBack = { tagsOpen = false },
                        onRename = viewModel::renameTag,
                        onDelete = viewModel::deleteTag,
                        onCreate = viewModel::createTag
                    )
                }
            }

            // The Wi-Fi transfer screen. A peer of the editors rather than a destination, because
            // it is reached from Settings and returns there — and because the listening socket it
            // owns must be torn down the moment the panel closes, which the `modifier` below does
            // by collapsing it to zero size when not shown.
            AnimatedContent(
                targetState = transferOpen,
                transitionSpec = {
                    (fadeIn(animationSpec = editorEnter) +
                        slideInVertically(
                            animationSpec = editorSlide,
                            initialOffsetY = { it / 8 }
                        )) togetherWith fadeOut(animationSpec = editorExit)
                },
                modifier = if (transferOpen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                },
                label = "wifi-transfer"
            ) { transferShowing ->
                if (transferShowing) {
                    TransferScreen(
                        wifiAddress = wifiAddress,
                        port = transferPort.toString(),
                        code = transferCode,
                        localNetworkGranted = localNetworkGranted,
                        onRequestLocalNetwork = {
                            localNetworkPermission?.launchPermissionRequest()
                        },
                        receiveState = receiveState,
                        receiveProgress = receiveProgress,
                        // The error belongs to the receive half now that the send half is gone.
                        receiveError = transferError,
                        onStartReceiving = { startReceiving() },
                        onStopReceiving = { stopReceiving() },
                        onBack = { transferOpen = false },
                    )
                }
            }

            // The busy overlay is the last child of the Box, so it covers absolutely everything:
            // tabs, the editor, and the report sheet. That matters because an export can be
            // started from any of them.
            AnimatedVisibility(
                visible = exportLabel != null,
                enter = fadeIn(animationSpec = tween(AppMotion.screenExitMillis())),
                exit = fadeOut(animationSpec = tween(AppMotion.screenEnterMillis())),
                label = "export-busy",
            ) {
                // Keeps the last label alive through the exit fade instead of blanking it mid-fade.
                exportLabel?.let { current ->
                    LaunchedEffect(current) { rememberedExportLabel = current }
                }
                LoadingScreen(
                    image = settings.loadingImage,
                    spin = settings.loadingSpin,
                    circle = settings.loadingCircle,
                    scrim = true,
                    label = exportLabel ?: rememberedExportLabel,
                    imageSize = settings.loadingImageSizeDp.dp,
                    circleSize = settings.effectiveLoadingCircleSizeDp.dp,
                )
            }
        }
    }
}
