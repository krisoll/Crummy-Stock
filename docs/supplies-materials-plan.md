# Supplies (Providers + Materials + Entries) — Implementation Plan

**Status:** Phases 1–3 implemented (database, migration, backup/restore, repository, Providers + Materials UI, Entries UI) — device validation and release hardening remain.
**Baseline:** `StockApplication` 1.1.1 (versionCode 4), Room schema **8**, 440 unit tests green.
**After Phase 1:** Room schema **9**, 464 unit tests green, including an in-place 8→9 migration test.
**Goal:** a fifth destination for **providers (proveedores)**, a **separate supplies inventory (insumos)** and **material entries (entradas de materiales)** recorded as invoices with multiple lines.

Decisions already confirmed:

| Question | Decision |
|---|---|
| What is a material entry? | A separate supplies catalogue and stock. An entry increases that material's stock. |
| Entry shape | Header + N lines: provider, date, reference, note, then material / quantity / unit cost. |
| Quantities | Whole units plus a unit-of-measure label. |
| Provider money | Cost only. No paid/unpaid balance or payment ledger. |
| Placement | New fifth bottom-navigation destination with Providers / Materials / Entries sub-tabs. |

---

## 1. Scope

### In scope (v1)

- Provider records: name, phone, address, notes.
- Material records: name, unit, current stock, low-stock threshold, notes.
- Purchase entries: one provider, one date, optional invoice reference and note, many material lines.
- Stock increases atomically when an entry is saved.
- Entry history, entry detail, and voiding an entry (which reverses its stock).
- Low-stock badges/filter and a simple supplies summary (count, low count, stock value at last cost).
- Full participation in the existing JSON backup/restore, trial gating, English/Spanish UI and theme.

### Out of scope (v1)

- Recipes / bill of materials, or consuming materials to build products.
- Consumption/usage ledger, wastage tracking, or per-material movement history beyond purchase entries.
- Provider payments, debts, partial payments or purchase orders.
- Editing a saved entry in place. Entries are immutable history; delete and re-enter instead.
- Material photos or barcodes.
- CSV/PDF exports for supplies (JSON backup only).
- Per-entry currency. The app's global currency and `showMoneyDecimals` apply.

These can be added later without changing the v1 schema, except a full consumption ledger (which would add a movement table).

---

## 2. Data model

### 2.1 Entities

```kotlin
enum class MaterialUnit {
    PIECE, PACK, BOX, BAG, DOZEN, KG, GRAM, LITRE, MILLILITRE, METRE, OTHER
}

@Entity(
    tableName = "providers",
    indices = [Index(value = ["name"], unique = true)]
)
data class Provider(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "materials",
    indices = [Index(value = ["name"], unique = true)]
)
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val unit: MaterialUnit = MaterialUnit.PIECE,
    /** Only meaningful when [unit] is OTHER. */
    val customUnit: String = "",
    val stock: Int = 0,
    val lowStockThreshold: Int = 5,
    val notes: String = "",
) {
    val isOutOfStock: Boolean get() = stock <= 0
    val isLowStock: Boolean get() = stock in 1..lowStockThreshold
}

@Entity(
    tableName = "material_entries",
    foreignKeys = [
        ForeignKey(
            entity = Provider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("providerId"), Index("purchasedAt")]
)
data class MaterialEntry(
    /** Stable business key used by backups and duplicate detection. */
    @PrimaryKey val entryId: String,
    val providerId: Long?,
    /** Snapshot, so history survives deleting the provider. */
    val providerName: String,
    val purchasedAt: Long,
    val reference: String = "",
    val note: String = "",
    val totalCents: Long,
    val lineCount: Int,
)

@Entity(
    tableName = "material_entry_lines",
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntry::class,
            parentColumns = ["entryId"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Material::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("entryId"),
        Index("materialId"),
        Index(value = ["entryId", "materialId"], unique = true),
    ]
)
data class MaterialEntryLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entryId: String,
    val materialId: Long?,
    /** Snapshots, so an old invoice still reads correctly after rename/delete. */
    val materialName: String,
    val unit: MaterialUnit,
    val customUnit: String = "",
    val quantity: Int,
    val unitCostCents: Long,
) {
    val totalCents: Long get() = quantity * unitCostCents
}
```

### 2.2 Why these choices

- **Names are snapshotted onto entries/lines.** Deleting a provider or material must never rewrite a past invoice. The foreign keys are `SET NULL`, which is why `providerId`/`materialId` are nullable.
- **Entry ID is a string business key**, like `SaleOrder.orderId`, so restoring the same backup twice is a no-op instead of duplicating purchases.
- **`totalCents` and `lineCount` are stored on the header** (like `SaleOrder`), so lists do not have to aggregate lines, and the invoice total is a fixed historical fact.
- **No `lastUnitCostCents` column.** The material list derives the last paid unit cost with a subquery against the most recent entry. This cannot drift when entries are deleted or backdated.
- **Stock is authoritative.** Entries are history; they are not replayed on restore (see §4), so stock is never double-counted.
- **One DAO.** Supply queries and entry transactions extend `StockDao` so create/delete and backup/restore share the same Room transaction. If the DAO becomes unmanageable later, split it only after moving restore to a database-level transaction.

### 2.3 Units

Store the enum, not the translated label. A material snapshots its unit onto each entry line, so changing a material from "box" to "pack" later does not rewrite old purchases.

UI labels (English / Spanish):

| Enum | English | Spanish |
|---|---|---|
| `PIECE` | piece | pieza |
| `PACK` | pack | paquete |
| `BOX` | box | caja |
| `BAG` | bag | bolsa |
| `DOZEN` | dozen | docena |
| `KG` | kg | kg |
| `GRAM` | g | g |
| `LITRE` | L | L |
| `MILLILITRE` | mL | mL |
| `METRE` | m | m |
| `OTHER` | other | otra |

`OTHER` requires a non-blank `customUnit`; the form validates it.

### 2.4 Invariants

- Quantity > 0, unit cost >= 0, stock >= 0, low-stock threshold >= 0.
- Entry total = `sum(quantity * unitCostCents)`; computed inside the transaction and never trusted from the UI.
- At least one line per entry; no duplicate material within one entry (the unique index is the backstop).
- Deleting an entry is blocked if the material's current stock is lower than the line quantity (the purchase has already been consumed). The app reports which material blocks it.
- Deleting a provider or material is allowed after confirmation; purchase history remains readable with its snapshot name.

---

## 3. Database

### 3.1 Version and migration

- Bump `StockDatabase.SCHEMA_VERSION` **8 → 9**.
- Add a `MIGRATION_8_9` and register it in `addMigrations(...)`.
- Existing installs keep products, sales, orders, tags and settings.

Migration SQL (must match what Room generates; verify with the generated schema or an in-place upgrade):

```sql
CREATE TABLE IF NOT EXISTS `providers` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `phone` TEXT NOT NULL,
  `address` TEXT NOT NULL,
  `notes` TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_providers_name` ON `providers` (`name`);

CREATE TABLE IF NOT EXISTS `materials` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `unit` TEXT NOT NULL,
  `customUnit` TEXT NOT NULL,
  `stock` INTEGER NOT NULL,
  `lowStockThreshold` INTEGER NOT NULL,
  `notes` TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_name` ON `materials` (`name`);

CREATE TABLE IF NOT EXISTS `material_entries` (
  `entryId` TEXT NOT NULL,
  `providerId` INTEGER,
  `providerName` TEXT NOT NULL,
  `purchasedAt` INTEGER NOT NULL,
  `reference` TEXT NOT NULL,
  `note` TEXT NOT NULL,
  `totalCents` INTEGER NOT NULL,
  `lineCount` INTEGER NOT NULL,
  PRIMARY KEY(`entryId`),
  FOREIGN KEY(`providerId`) REFERENCES `providers`(`id`)
    ON UPDATE NO ACTION ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS `index_material_entries_providerId`
  ON `material_entries` (`providerId`);
CREATE INDEX IF NOT EXISTS `index_material_entries_purchasedAt`
  ON `material_entries` (`purchasedAt`);

CREATE TABLE IF NOT EXISTS `material_entry_lines` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `entryId` TEXT NOT NULL,
  `materialId` INTEGER,
  `materialName` TEXT NOT NULL,
  `unit` TEXT NOT NULL,
  `customUnit` TEXT NOT NULL,
  `quantity` INTEGER NOT NULL,
  `unitCostCents` INTEGER NOT NULL,
  FOREIGN KEY(`entryId`) REFERENCES `material_entries`(`entryId`)
    ON UPDATE NO ACTION ON DELETE CASCADE,
  FOREIGN KEY(`materialId`) REFERENCES `materials`(`id`)
    ON UPDATE NO ACTION ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS `index_material_entry_lines_entryId`
  ON `material_entry_lines` (`entryId`);
CREATE INDEX IF NOT EXISTS `index_material_entry_lines_materialId`
  ON `material_entry_lines` (`materialId`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_material_entry_lines_entryId_materialId`
  ON `material_entry_lines` (`entryId`, `materialId`);
```

The unique `(entryId, materialId)` index allows multiple `NULL` material ids in SQLite, which is correct after a material is deleted.

### 3.2 Queries and projections

```kotlin
data class ProviderSummary(
    val id: Long,
    val name: String,
    val phone: String,
    val entryCount: Int,
    val totalSpentCents: Long,
)

data class MaterialRow(
    @Embedded val material: Material,
    /** Derived from the most recent entry line; null before the first purchase. */
    val lastUnitCostCents: Long?,
)

data class MaterialEntryWithLines(
    @Embedded val entry: MaterialEntry,
    @Relation(parentColumn = "entryId", entityColumn = "entryId")
    val lines: List<MaterialEntryLine>,
)
```

Key DAO methods:

- `observeProviders(): Flow<List<ProviderSummary>>`
  - `LEFT JOIN material_entries` grouped by provider, with `COUNT` and `COALESCE(SUM(totalCents), 0)`.
- `observeMaterials(): Flow<List<MaterialRow>>`
  - `SELECT m.*, (SELECT l.unitCostCents ... ORDER BY e.purchasedAt DESC, l.id DESC LIMIT 1) AS lastUnitCostCents`.
- `observeEntries(): Flow<List<MaterialEntry>>`
  - newest first; optional `observeEntriesForProvider(id)`.
- `observeEntry(entryId): Flow<MaterialEntryWithLines?>`
  - for the detail screen.
- `findProviderByName`, `findMaterialByName`, `insert/update/delete` for both.
- `snapshotProviders/Materials/Entries/EntryLines`.
- `clearAll...` for replace-restore.

### 3.3 Transactions

`recordMaterialEntry` (single `@Transaction`):

1. Load the provider (required) and snapshot its current name.
2. Load every material; validate exists, quantity > 0, cost >= 0, and no duplicate ids.
3. Compute `totalCents` and `lineCount`.
4. Insert the header.
5. Insert each line with the material name/unit/custom-unit snapshot.
6. Increment each material's `stock` by the line quantity.
7. Return the created entry.

`deleteMaterialEntry` (single `@Transaction`):

1. Load the entry and its lines.
2. Aggregate quantities per material.
3. If any existing material has `stock < quantity`, abort with `InsufficientStock(name, available, requested)`.
4. Decrement each material's stock.
5. Delete the header; lines cascade.
6. Return the deleted entry.

`saveProvider` / `saveMaterial`:

- Trim fields.
- Blank name is rejected.
- Duplicate name is rejected before insert/update (case-insensitive lookup, matching how categories work).
- `saveMaterial` validates `customUnit` when `unit == OTHER` and clamps stock/threshold at >= 0.
- Deleting is separate and confirmed in the UI.

### 3.4 Repository

New `data/SuppliesRepository.kt` with the same DAO, so `StockDao` keeps the transaction boundary but `StockRepository` does not grow another 500 lines.

```kotlin
class SuppliesRepository(
    private val dao: StockDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeProviders(): Flow<List<ProviderSummary>>
    fun observeMaterials(): Flow<List<MaterialRow>>
    fun observeEntries(): Flow<List<MaterialEntry>>
    fun observeEntry(entryId: String): Flow<MaterialEntryWithLines?>

    suspend fun saveProvider(provider: Provider): SaveProviderResult
    suspend fun deleteProvider(provider: Provider)
    suspend fun saveMaterial(material: Material): SaveMaterialResult
    suspend fun deleteMaterial(material: Material)
    suspend fun adjustStock(material: Material, newStock: Int)
    suspend fun recordEntry(
        providerId: Long,
        purchasedAt: Long,
        reference: String,
        note: String,
        lines: List<NewMaterialLine>,
    ): RecordEntryResult
    suspend fun deleteEntry(entryId: String): DeleteEntryResult
}
```

Entry IDs follow the existing `SaleOrder` convention: `MAT-<base36 timestamp>-<counter>`.

Result types carry enough detail for localized messages:

```kotlin
sealed interface RecordEntryResult {
    data class Success(val entry: MaterialEntry) : RecordEntryResult
    data object Empty : RecordEntryResult
    data object ProviderNotFound : RecordEntryResult
    data class MaterialNotFound(val name: String) : RecordEntryResult
    data class InvalidLine(val materialName: String, val reason: Reason) : RecordEntryResult
}

sealed interface DeleteEntryResult {
    data class Success(val entry: MaterialEntry) : DeleteEntryResult
    data object NotFound : DeleteEntryResult
    data class InsufficientStock(
        val materialName: String,
        val available: Int,
        val requested: Int,
    ) : DeleteEntryResult
}
```

---

## 4. Backup and restore

The JSON backup is the app's safety net, so supplies must be included from day one. The document format stays **version 1**: these are additive optional arrays, and the existing importer already ignores unknown fields.

### 4.1 `DatabaseSnapshot`

Add with defaults so existing tests and callers keep compiling:

```kotlin
val providers: List<Provider> = emptyList(),
val materials: List<Material> = emptyList(),
val materialEntries: List<MaterialEntry> = emptyList(),
val materialEntryLines: List<MaterialEntryLine> = emptyList(),
```

`toJson()` adds:

- Counts: `providers`, `materials`, `materialEntries`, `materialEntryLines`.
- Arrays, sorted by id/entryId:
  - `providers`: id, name, phone, address, notes.
  - `materials`: id, name, unit, customUnit, stock, lowStockThreshold, notes.
  - `materialEntries`: entryId, providerId, providerName, purchasedAt, `purchasedAtIso`, reference, note, totalCents, lineCount.
  - `materialEntryLines`: id, entryId, materialId, materialName, unit, customUnit, quantity, unitCostCents.
- Raw values only; `purchasedAtIso` is presentation-only, like the existing `*Iso` fields.
- Do **not** export derived last cost.

### 4.2 `BackupContents` and `JsonImport`

- Add `providers`, `materials`, `materialEntries`, `materialEntryLines` with `emptyList()` defaults.
- Missing arrays mean "this file predates supplies" and import as empty.
- Validate rows leniently, consistent with tags:
  - Providers: id + non-blank name; duplicate ids/names are dropped and noted.
  - Materials: id + non-blank name; unknown units fall back to `OTHER` with a note; stock/threshold clamped at 0.
  - Entries: non-blank unique `entryId`; provider name snapshot kept even if `providerId` is missing.
  - Lines: valid `entryId`, quantity > 0, cost >= 0; a line whose entry is not in the file is dropped.
- `BackupContents.isEmpty` must include the new arrays, so a supplies-only file opens the import dialog.
- `ImportResult` gains `providersAdded`, `materialsAdded`, `entriesAdded`, `entryLinesAdded`; `totalAdded` includes them.
- `ImportBackupDialog` shows a second summary line only when supplies are present, e.g. "Supplies: 3 providers, 8 materials, 12 entries" (localized).

### 4.3 Restore semantics

**Stock is authoritative; entries are never replayed.** This is the critical rule.

`replace = true`:

1. Clear `material_entry_lines`, `material_entries`, `materials`, `providers` (before the existing tables, respecting FKs).
2. Insert providers and build old→new id map.
3. Insert materials exactly as recorded, including stock, and map ids.
4. Insert entries (dedupe by `entryId`) and map provider ids; keep snapshot names.
5. Insert lines with mapped ids or `NULL`; keep snapshot names/units.
6. **Do not add line quantities to stock** — the material row already carries the file's stock.

`replace = false` (merge):

1. Providers and materials are matched by name; existing rows get updated metadata. **Existing material stock is preserved**, exactly like product quantity in the current restore.
2. New providers/materials are inserted with their file values.
3. Entries are inserted only if their `entryId` is not already present.
4. Lines are inserted for those new entries only.
5. Stock is never adjusted, so importing the same file twice cannot double inventory.

Consequences to document in code comments:

- A backup is a snapshot of current stock, not a purchase ledger to replay.
- An older build importing a 1.2 backup ignores the supply arrays; the app's format contract already accepts that (additive fields). The schema version in the file becomes 9.

---

## 5. App wiring

### 5.1 Container and ViewModel

`StockApplication`:

```kotlin
val suppliesRepository: SuppliesRepository by lazy {
    SuppliesRepository(StockDatabase.get(this).stockDao())
}
```

`ui/viewmodel/SuppliesViewModel.kt`:

```kotlin
data class SuppliesUiState(
    val providers: List<ProviderSummary> = emptyList(),
    val materials: List<MaterialRow> = emptyList(),
    val entries: List<MaterialEntry> = emptyList(),
    val isLoading: Boolean = true,
) {
    val lowStockCount: Int
    val inventoryValueCents: Long
    val currentMonthSpendCents: Long
}
```

- Flows are `stateIn(viewModelScope, WhileSubscribed(5_000), ...)` like the inventory VM.
- `messages: StateFlow<SuppliesMessage?>` uses the existing `Res`/`toText(context)` pattern so localization happens at the last moment.
- Mutations launch in `viewModelScope` and route outcomes to messages.
- `AppViewModelFactory` gains `suppliesFactory(app)`.
- `MainActivity` creates the VM and passes it to `StockApp` alongside the existing three.

### 5.2 Root navigation

`AppDestinations` gains:

```kotlin
SUPPLIES(R.string.nav_supplies, Icons.Default.Warehouse)
```

Order: Inventory, Sales, History, **Supplies**, Config. The adaptively laid-out `NavigationSuiteScaffold` already handles five items (rail on wide screens, bottom bar on phones). Use the short label **"Insumos" / "Supplies"**; "Proveedores" is too wide for the bottom bar.

`StockApp` adds one branch:

```kotlin
AppDestinations.SUPPLIES -> SuppliesScreen(
    viewModel = suppliesViewModel,
    readOnlyGate = ::gateReadOnly,
    onMessage = ...,
)
```

All write callbacks are wrapped by `gateReadOnly()` exactly like inventory/sales. Reads, exports and backups stay available to a read-only user.

### 5.3 Screen state

`SuppliesScreen` owns its internal state, mirroring the product/promo editors:

- `rememberSaveable` selected tab (`PROVIDERS`, `MATERIALS`, `ENTRIES`).
- `rememberSaveable` nullable ids + boolean flags for provider form, material form, entry editor and entry detail.
- `BackHandler` closes the top-most editor; nested confirm dialogs dismiss first.
- The entry form keeps its line draft in `rememberSaveable` with a small list saver, or in the ViewModel if we want full process-death safety. A rotation must not lose a half-entered invoice.

---

## 6. Presentation

### 6.1 `SuppliesScreen` shell

- `PrimaryTabRow` at the top with `statusBarsPadding()` (same as `InventoryScreen`).
- Three tabs: **Providers / Materials / Entries** (`Proveedores / Insumos / Entradas`).
- One contextual `ExtendedFloatingActionButton`:
  - Providers → "New provider"
  - Materials → "New material"
  - Entries → "New entry"
- Content cross-fades between tabs; editor/detail screens slide/fade with `AppMotion`, matching the existing app.
- Empty states use a large tinted icon, one sentence, and the FAB remains the action.

### 6.2 Providers tab

- Summary header: number of providers.
- Search field (name or phone), filtered in memory.
- `LazyColumn` of cards:
  - Provider name, phone.
  - "N purchases · $X total" (derived from `ProviderSummary`).
  - Actions: tap to edit; overflow → **View entries** (switch to Entries tab filtered to that provider) and **Delete**.
- Empty state: `LocalShipping` icon, "No providers yet", explanatory line.
- Delete confirmation: "Delete [name]? Their purchase history will be kept, but it will no longer be linked to a provider."

### 6.3 Materials tab

- Summary header (three compact tiles):
  - Materials count.
  - Low-stock count (tap filters the list).
  - Stock value at last purchase cost (`sum(stock × lastUnitCostCents)`), shown with `formatCents`.
- Search field.
- Filter chips: **All / Low / Out**, matching the inventory chips.
- Material card:
  - Name, unit label (or custom unit).
  - Current stock, e.g. "120 packs" / "3 kg".
  - Low/out badge with the app's existing warning colours.
  - Last paid unit cost, or "No purchases yet".
  - Tap to edit; overflow → **Adjust stock** and **Delete**.
- Empty state: `Inventory2` icon, "No materials yet".
- Material form:
  - Name (required), unit dropdown, custom unit when `OTHER`.
  - Current stock and low-stock threshold (numeric).
  - Notes.
  - Delete confirmation explains that past entries keep the material name and unit.
- Adjust-stock dialog:
  - Shows current stock, numeric new value, and a note that this is a correction, not a purchase. Purchases must go through an entry so they are costed.
  - If we later add a movement ledger, this dialog becomes the manual-adjustment writer.

### 6.4 Entries tab

- Summary header: current-month spend, total entries.
- Provider filter dropdown (All providers) and a text search for reference/note.
- `LazyColumn` of entry cards, newest first:
  - Date (localized `DateFormat.getDateInstance(MEDIUM)`).
  - Provider snapshot name.
  - Reference (invoice/folio) when present.
  - "N lines" and total with `formatCents`.
  - Tap → detail.
- Empty state: `ReceiptLong` icon, "No purchases recorded yet".
- Optional later: month section headers; v1 keeps the date on each card.

### 6.5 Entry editor

Full-screen form with a top app bar (back + "Save"):

1. **Provider** row — required; opens `ProviderPickerSheet`.
2. **Date** row — defaults to today; date picker capped at today.
3. **Reference** — optional invoice/folio; free text.
4. **Note** — optional memo.
5. **Lines**:
   - Each line card: material name + unit, quantity field, unit cost field, line total, remove button.
   - Quantity is an integer > 0; unit cost uses `parseMoneyText`/`formatPriceInput` (the existing money-input helpers).
   - "Add material" opens `MaterialPickerSheet` with search, current stock and last cost; selecting pre-fills quantity 1 and the last paid cost.
   - If the material is already on the invoice, select/highlight the existing line instead of adding a duplicate.
6. **Total** footer: sum of line totals, live.
7. **Save** disabled until the provider and at least one valid line exist.

On save:

- Validate again in the repository (never trust the UI).
- Insert entry + lines and increment stock in one transaction.
- Close the editor, show a localized snackbar ("Purchase saved").
- On failure (provider/material deleted meanwhile), keep the form open and show the reason.

Material/provider creation from inside the pickers:

- Preferred: a small "+ New" action in each picker opens a compact create dialog, so a first purchase is not blocked by a missing catalogue row.
- If that is deferred, the pickers show an empty state with a clear "Create one in the Materials tab" action and the draft must survive the trip. Inline create is the better v1 UX and is recommended.

### 6.6 Entry detail

- Header: provider, date, reference, note.
- Lines: material snapshot name + unit, quantity, unit cost, line total.
- Grand total with `formatCents`.
- **Delete entry** button with confirmation:
  - Copy: "Delete this purchase? The stock it added will be removed. This cannot be undone."
  - If stock was already consumed, show the blocking material and its available quantity; change nothing.
- No edit button in v1. The copy explains "Delete and record a new entry to correct it."

### 6.7 Shared UI behaviour

- All money uses `formatCents`; input fields use the symbol-free money helpers.
- All units/date labels are localized; no hardcoded English.
- Follow the existing theme (`MaterialTheme.colorScheme`), card shapes, 16 dp list padding and `rememberRevealModifier` stagger.
- Content descriptions on every icon-only button.
- Numeric keyboards for quantity/cost/phone fields.
- Long provider/material names ellipsize; totals never truncate.

---

## 7. Strings

Add every key to **both** `res/values/strings.xml` and `res/values-es/strings.xml`.

Groups:

- Navigation: `nav_supplies`, `supplies_tab_providers`, `supplies_tab_materials`, `supplies_tab_entries`.
- Provider: add/edit titles, name/phone/address/notes labels, search, empty state, delete dialog, saved/deleted messages, duplicate-name error.
- Material: add/edit titles, name/unit/stock/threshold/notes, custom unit, last cost, low/out badges, filters, adjust-stock dialog, delete dialog, empty state, saved/deleted messages.
- Entry: new/detail titles, provider/date/reference/note labels, add material, quantity/unit cost/line total/total labels, validation messages, delete dialog, saved/deleted messages, insufficient-stock message, empty state.
- Units: one label per `MaterialUnit` plus "other".
- Import: supplies summary line.

Plurals: use `plurals` for "N lines" / "N purchases" in both languages.

---

## 8. Files

### New

| File | Purpose |
|---|---|
| `data/Provider.kt` | Provider entity. |
| `data/Material.kt` | Material entity + `MaterialUnit`. |
| `data/MaterialEntry.kt` | Entry/line entities, results, projections. |
| `data/SuppliesRepository.kt` | Validation, ID generation, DAO wrappers. |
| `ui/viewmodel/SuppliesViewModel.kt` | State, flows, messages, mutations. |
| `ui/screens/supplies/SuppliesScreen.kt` | Fifth destination shell + tab/FAB routing. |
| `ui/screens/supplies/ProvidersTab.kt` | Provider list/search/cards. |
| `ui/screens/supplies/ProviderEditScreen.kt` | Create/edit provider. |
| `ui/screens/supplies/MaterialsTab.kt` | Materials list/summary/filters. |
| `ui/screens/supplies/MaterialEditScreen.kt` | Create/edit material + stock adjustment. |
| `ui/screens/supplies/EntriesTab.kt` | Entry list/search/filter. |
| `ui/screens/supplies/EntryEditorScreen.kt` | Invoice header + lines. |
| `ui/screens/supplies/EntryDetailScreen.kt` | Read-only invoice + void action. |
| `ui/screens/supplies/MaterialPickerSheet.kt` | Searchable material picker, optional inline create. |
| `ui/screens/supplies/ProviderPickerSheet.kt` | Searchable provider picker, optional inline create. |
| `test/.../SuppliesDaoTest.kt` | Robolectric in-memory Room transactions. |
| `test/.../SuppliesBackupTest.kt` | JSON round-trip and restore semantics. |
| `test/.../MaterialEntryTest.kt` | Totals/validation pure tests. |

### Modified

| File | Change |
|---|---|
| `data/StockDatabase.kt` | Entities, version 9, migration 8→9, migration registration. |
| `data/StockDao.kt` | Supply queries, transactions, snapshot/restore/clear methods. |
| `data/JsonExport.kt` | Snapshot fields, counts, arrays. |
| `data/JsonImport.kt` | BackupContents fields, readers, `isEmpty`. |
| `data/StockRepository.kt` | Only if `ImportResult` or snapshot signatures change. |
| `StockApplication.kt` | `suppliesRepository` container entry. |
| `ui/viewmodel/InventoryViewModel.kt` | `AppViewModelFactory` gains `suppliesFactory` (or a separate factory file). |
| `MainActivity.kt` | Create/pass `SuppliesViewModel`. |
| `ui/StockApp.kt` | `SUPPLIES` destination, message collection, gated routing. |
| `ui/components/ImportBackupDialog.kt` | Supplies summary line. |
| `res/values/strings.xml` | All new English copy. |
| `res/values-es/strings.xml` | All new Spanish copy. |
| `app/build.gradle.kts` | versionCode 5 / versionName 1.2.0; `room-testing` if migration tests are added. |
| `gradle/libs.versions.toml` | `room-testing` alias (optional hardening). |

No new permissions, no new libraries for v1 (unless migration tests use `room-testing`).

---

## 9. Testing

### Automated

- **Pure totals/validation** (`MaterialEntryTest`):
  - Total = sum of `quantity × unitCost`, including zero-cost lines.
  - Empty line list, quantity 0/negative and duplicate materials are rejected.
  - Unit snapshots and custom units round-trip.
- **DAO/Robolectric** (`SuppliesDaoTest`):
  - Recording an entry inserts header + lines, increments stock, computes total, snapshots provider/material names and units.
  - Deleting an entry restores exactly the purchased quantities and removes lines.
  - Deleting an entry is blocked when stock has been consumed; no partial write.
  - Deleting a provider/material leaves entry history readable (`SET NULL` + snapshots).
  - Updating a material's stock does not alter past line costs.
  - Last-unit-cost query picks the newest entry, not the newest row id.
- **Backup** (`SuppliesBackupTest`):
  - Snapshot → JSON → read → restore round-trips all four tables.
  - Missing arrays import as empty and old backups still work.
  - `replace` reproduces stock exactly and does not replay line quantities.
  - `merge` preserves existing stock, is idempotent by `entryId`, and maps provider/material ids.
  - `BackupContents.isEmpty` is false for a supplies-only file.
- **Migration**:
  - Preferred: enable `exportSchema = true` + `room-testing`, generate schema 8 before the bump, and test `MigrationTestHelper` 8→9 with seeded products/sales. This is the only way to test the SQL twice without a device.
  - At minimum: install the current 1.1.1 build, add sample products/sales, then upgrade in place to the new build and verify nothing was lost.
- **Regression**: all 440 existing tests must remain green.
- **Strings**: extend the existing string-resource test pattern (if present) or add one asserting each new key exists in both locales.

### Manual on the Pixel 8 Pro

1. Fresh install → onboarding → fifth tab exists and opens.
2. Create provider → create material → record a two-line entry → stock increases by each quantity and total matches.
3. Low threshold → badge/filter works.
4. Delete the entry → stock returns; delete provider/material → history still shows names.
5. Consume stock below a purchase, then try to delete the entry → blocked with a clear message.
6. Switch English ↔ Spanish → every string/unit/date updates, including validation and dialogs.
7. Expire/read-only build (`-PdevMode=false` with an expired trial) → add/edit/delete buttons open the paywall; lists and backup stay available.
8. Backup → JSON contains the four arrays → restore with replace and merge; counts and stock are correct; importing twice does not duplicate.
9. Upgrade from 1.1.1 with existing products/sales → all old data intact, new tab empty.
10. Rotate during the entry editor → draft survives.

---

## 10. Delivery phases

### Phase 0 — safety net

- Export a full JSON backup from the current app.
- Copy/zip the project (it is not a git repository).
- Optionally initialize git before the schema bump.

### Phase 1 — database + backup (no UI) — **done**

- Entities, schema 9, migration, DAO queries/transactions, repository.
- Snapshot/export/import/restore + `ImportResult`/dialog counts.
- Tests for transactions and backup round-trip.
- Acceptance: project builds, existing suite green, old backup imports, new backup round-trips.
- Also added: a Robolectric 8→9 in-place migration test against a seeded v8 database.

### Phase 2 — providers + materials UI — **done**

- Fifth destination, inner tabs, providers list/form, materials list/form, stock adjust, low-stock filter, strings, gating.
- Acceptance: can manage both catalogues; read-only gate works; both languages complete.
- Also added: `SuppliesViewModel`, shared supplies components, and `SuppliesUiStateTest`.
- The Entries tab is added with the Phase 3 editor rather than shown empty in this phase.

### Phase 3 — entries UI — **done**

- Entries list/detail/editor, pickers, totals, save/delete transactions, validation, messages.
- Acceptance: purchase flow updates stock atomically; void reverses it; insufficient-stock block works.
- Also added: `EntriesTab`, `EntryEditorScreen`, `EntryDetailScreen`, `ProviderPickerDialog`, `MaterialPickerDialog`, `NewMaterialEntry`, `observeMaterialEntriesWithLines()`, `currentMonthSpendCents`, and 2 entry-focused tests.
- Implementation notes: entry editor uses a rotation-safe saver for line drafts; date is capped at today; provider/material pickers support inline quick creation; deletion stays on screen with a message when stock is insufficient.

### Phase 4 — hardening + release prep — **done**

- Migration/backup tests, manual upgrade on the Pixel, accessibility pass, version bump to 1.2.0 (code 5), full suite.
- Acceptance: all automated tests green; checkout on device; backup restore verified.
- Progress: device upgrade succeeded; provider/material/entry create flow, blocked deletion, successful deletion, and JSON backup/restore (including supplies counts) verified on the Pixel 8 Pro; version bumped to 1.2.0 (code 5); lint clean; full unit suite green (469 tests, 48 classes). Accessibility review completed for the new supply screens.

---

## 11. Risks and open choices

| Risk / decision | Proposed handling |
|---|---|
| Five bottom-nav items crowd small screens, especially in Spanish. | Use the short label "Insumos" / "Supplies"; verify on the Pixel and a narrow emulator; `NavigationSuiteScaffold` switches to a rail on wide layouts. |
| Restore double-counts stock by replaying entries. | Stock is authoritative; entries are history and never adjust stock on restore (§4). Tested explicitly. |
| Deleting a provider/material rewrites history. | Snapshot names/units on entries/lines and use `ON DELETE SET NULL`. |
| Deleting a purchase after its stock was consumed. | Block with a clear message rather than allowing negative stock. |
| Backdated entries and "last cost". | Derive last cost from the newest `purchasedAt`, not insertion order. |
| No schema export / migration tests today. | Do an in-place upgrade test on the device; enabling `exportSchema` + `room-testing` is recommended hardening. |
| Direct stock adjustments are not audited. | Matches current product quick-adjust behaviour; the confirm copy calls it a correction, and purchases must go through entries. A movement ledger is future work. |
| UI-only validation could drift. | Re-validate provider/material/quantities/cost inside the DAO transaction. |

Open copy/scope choices to confirm before coding:

1. Bottom-tab label: **"Insumos"** (recommended), "Compras", or "Proveedores".
2. Provider fields: name / phone / address / notes (proposed). Add email or tax ID?
3. Units: fixed list above plus "Other" (proposed), or do you need more (sack, roll, sheet, pair)?
4. Should deleting a purchase be blocked after stock is consumed (proposed), or allowed into negative stock?
5. Entry correction: delete + re-enter (proposed) or in-place editing later?
6. Include supplies automatically in every JSON backup (proposed), or make it another opt-in toggle?

Once these are confirmed, implementation can start with Phase 1 and stop at each phase for review.
