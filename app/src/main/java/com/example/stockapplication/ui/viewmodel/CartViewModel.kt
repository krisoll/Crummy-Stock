package com.example.stockapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stockapplication.R
import com.example.stockapplication.StockApplication
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.Cart
import com.example.stockapplication.data.MaxOrderNoteLength
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.PromoEngine
import com.example.stockapplication.data.PromoMatch
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.SettingsRepository
import com.example.stockapplication.data.StockRepository
import com.example.stockapplication.data.computeTotals
import com.example.stockapplication.ui.components.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off cart feedback, surfaced as a snackbar. */
sealed interface CartMessage {
    data class Added(val name: String) : CartMessage
    data class UnknownBarcode(val barcode: String) : CartMessage
    data class OutOfStock(val name: String) : CartMessage
    data class CheckedOut(val isCard: Boolean, val totalCents: Long) : CartMessage
    data class Res(val resId: Int, val args: List<Any> = emptyList()) : CartMessage
}

data class CartUiState(
    val cart: Cart = Cart(),
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val taxRateBasisPoints: Int = 0,
    val cardFeeRateBasisPoints: Int = 0,
    val cardFeeFixedCents: Long = 0L,
    val note: String = "",
    val isCheckingOut: Boolean = false,
    val lastOrder: SaleOrder? = null,
    /**
     * The product behind the most recent scan, for the scanner's banner.
     *
     * Held in the state rather than passed up from the scanner because the lookup is asynchronous:
     * the scanner hands over a barcode string, and only the repository can turn it into a product.
     */
    val lastScan: ScanResult? = null,
    /**
     * Every promo applying to this cart, in the order they were applied. Empty when none qualifies.
     *
     * Carried in the state rather than recomputed by the screen so that the discount shown, the
     * discount charged and the discount written to the order are all the same value.
     */
    val promoMatches: List<PromoMatch> = emptyList(),
    /** The promo the cart is one unit away from unlocking, for the "add 1 more" hint. */
    val nextPromo: PromoMatch? = null,
    /**
     * Promos the cashier took off this sale, so the screen can offer to put them back.
     *
     * Without this, removing is a one-way action — the row disappears and the only way to undo a
     * mis-tap would be to empty the cart.
     */
    val suppressedPromos: List<Promo> = emptyList(),
) {
    /**
     * The money taken off by promos altogether.
     *
     * Summing the individual matches is safe because [PromoEngine.applyPromos] already caps each
     * one at what its own lines still had left, so the total can never exceed the cart.
     */
    val promoDiscountCents: Long get() = promoMatches.sumOf { it.discountCents }

    /**
     * The single match, for the many places that only need to know whether *a* promo applied.
     *
     * Null when none did. With several promos this is the largest, which is what a one-line
     * summary (a receipt label, an empty-state check) should show.
     */
    val promoMatch: PromoMatch?
        get() = promoMatches.maxByOrNull { it.discountCents }

    /** Names of the applying promos, for the order snapshot. Empty when none applied. */
    val promoName: String
        get() = promoMatches.joinToString(PROMO_NAME_SEPARATOR) { it.promo.name }

    val totals = computeTotals(
        itemsSubtotalCents = cart.subtotalCents,
        taxRateBasisPoints = taxRateBasisPoints,
        cardFeeRateBasisPoints = cardFeeRateBasisPoints,
        paymentMethod = paymentMethod,
        cardFeeFixedCents = cardFeeFixedCents,
        promoDiscountCents = promoDiscountCents,
    )

    val canCheckout: Boolean get() = !cart.isEmpty && !isCheckingOut
}

/**
 * Separator between promo names in the snapshot stored on an order, e.g. "Coffee + Pastries".
 *
 * A single column holds the names because the order row is a receipt, not a join table: it only
 * ever needs to *reproduce* what was given, never to be queried by promo.
 */
const val PROMO_NAME_SEPARATOR = " + "

/**
 * Owns the checkout cart. Kept separate from [InventoryViewModel] so that scanning during a sale
 * never mutates inventory until the cart is actually paid for.
 */
class CartViewModel(
    private val stockRepository: StockRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val cart = MutableStateFlow(Cart())
    private val paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    private val note = MutableStateFlow("")
    private val isCheckingOut = MutableStateFlow(false)
    private val lastOrder = MutableStateFlow<SaleOrder?>(null)
    private val lastScan = MutableStateFlow<ScanResult?>(null)

    /**
     * Promos the cashier has taken off this sale.
     *
     * Promos are not *chosen* — [PromoEngine] awards every one that qualifies — so "remove a promo"
     * can only mean "do not apply this one to this cart". Storing the suppression rather than
the
     * outcome keeps the discount live: if the cart then changes so the promo no longer qualifies, it
     * stays suppressed, and if it becomes relevant again the cashier has already said no.
     *
     * Deliberately **not** persisted: it belongs to one sale, so clearing the cart drops it.
     */
    private val suppressedPromoIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _messages = MutableStateFlow<CartMessage?>(null)

    /**
     * Clock for promo windows, swappable in tests.
     *
     * The cart is where a promo's start/end dates are actually enforced, so the clock has to be
     * injectable here or a dated promo would be untestable.
     */
    var nowProvider: () -> Long = { System.currentTimeMillis() }

    val messages: StateFlow<CartMessage?> = _messages.asStateFlow()

    val uiState: StateFlow<CartUiState> = combine(
        cart,
        paymentMethod,
        settingsRepository.settings,
        note,
        isCheckingOut,
        lastOrder,
        lastScan,
        stockRepository.observePromos(),
        stockRepository.observeProducts(),
        suppressedPromoIds,
    ) { values ->
        val cartState = values[0] as Cart
        val method = values[1] as PaymentMethod
        val settings = values[2] as AppSettings
        val noteText = values[3] as String
        val checkingOut = values[4] as Boolean
        val order = values[5] as SaleOrder?
        val promos = values[7] as List<Promo>
        val products = values[8] as List<Product>
        val suppressed = values[9] as Set<Long>

        // Recomputed on every change to the cart, the catalogue or the promo list, so the
        // discount is live as items are scanned and stale matches cannot survive an edit.
        val productsById = products.associateBy { it.id }
        val now = nowProvider()
        // Every qualifying promo, not just the best one: promos stack, each against its own
        // lines. The engine returns them already capped so the sum can never exceed the cart.
        //
        // Suppressed promos are filtered *after* the engine has run, not by hiding them from it:
        // the engine applies promos against the same lines independently, so dropping one cannot
        // change what the others are worth. That is what makes remove/add a clean toggle rather
        // than something the next keystroke could reshuffle.
        val matches = PromoEngine
            .applyPromos(cartState.lines, promos, productsById, now)
            .filterNot { it.promo.id in suppressed }

        CartUiState(
            cart = cartState,
            paymentMethod = method,
            taxRateBasisPoints = settings.taxRateBasisPoints,
            cardFeeRateBasisPoints = settings.cardFeeRateBasisPoints,
            cardFeeFixedCents = settings.cardFeeFixedCents,
            note = noteText,
            isCheckingOut = checkingOut,
            lastOrder = order,
            lastScan = values[6] as ScanResult?,
            promoMatches = matches,
            // Only promos that would actually have applied are listed as removable: a suppressed
            // promo that no longer qualifies has nothing to put back, so offering it would be a
            // button that does nothing.
            suppressedPromos = PromoEngine
                .applyPromos(cartState.lines, promos, productsById, now)
                .filter { it.promo.id in suppressed }
                .map { it.promo },
            // Still offered when promos are already applying: with stacking, "one more unlocks
            // 10% extra" is a real offer, not a contradiction. The promo already applied is
            // excluded so it cannot advertise itself as the next thing to earn.
            nextPromo = PromoEngine.nextPromo(
                lines = cartState.lines,
                promos = promos,
                productsById = productsById,
                now = now,
                applied = matches.map { it.promo.id }.toSet(),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CartUiState()
    )

    /**
     * Keeps cart lines honest when stock changes underneath them (e.g. a unit sold on another
     * screen), so the cart can never offer more units than the shelf holds.
     */
    init {
        viewModelScope.launch {
            stockRepository.observeProducts().collect { products ->
                val reconciled = cart.value.reconcile(products)
                if (reconciled != cart.value) cart.value = reconciled
            }
        }
    }

    fun addProduct(product: Product) {
        val before = cart.value.itemCount
        val updated = cart.value.add(product)
        cart.value = updated
        _messages.value = when {
            product.quantity <= 0 -> CartMessage.OutOfStock(product.name)
            updated.itemCount > before -> CartMessage.Added(product.name)
            else -> CartMessage.Res(R.string.cart_at_max_stock, listOf(product.name))
        }
    }

    /** Adds by barcode, used by the scanner embedded in the sales tab. */
    fun addByBarcode(barcode: String) {
        val clean = barcode.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val product = stockRepository.findByBarcode(clean)
            if (product == null) {
                lastScan.value = ScanResult.UnknownCode(clean)
                _messages.value = CartMessage.UnknownBarcode(clean)
            } else {
                addProduct(product)
                lastScan.value = ScanResult.ProductFound(
                    product = product,
                    // Read from the cart *after* addProduct, so the banner counts this scan too.
                    countInCart = cart.value.countOf(product.id),
                )
            }
        }
    }

    /** Clears the scanner banner without touching the cart. */
    fun clearLastScan() {
        lastScan.value = null
    }

    fun setQuantity(productId: Long, quantity: Int) {
        cart.value = cart.value.updateQuantity(productId, quantity)
    }

    fun increment(productId: Long) {
        val line = cart.value.lines.firstOrNull { it.productId == productId } ?: return
        setQuantity(productId, line.quantity + 1)
    }

    fun decrement(productId: Long) {
        val line = cart.value.lines.firstOrNull { it.productId == productId } ?: return
        setQuantity(productId, line.quantity - 1)
    }

    fun remove(productId: Long) {
        cart.value = cart.value.remove(productId)
    }

    fun setPaymentMethod(method: PaymentMethod) {
        paymentMethod.value = method
    }

    /**
     * Updates the memo attached to the next sale.
     *
     * Capped at [MaxOrderNoteLength] as the user types, so the field can't grow past what the
     * history row and the exported CSV are designed to show.
     */
    fun setNote(text: String) {
        note.value = text.take(MaxOrderNoteLength)
    }

    fun clearCart() {
        cart.value = cart.value.clear()
        // A memo belongs to one sale; emptying the cart means starting a different one.
        note.value = ""
        // So does a removed promo: the next sale starts with the engine's own answer, not with
        // the previous customer's exclusions.
        suppressedPromoIds.value = emptySet()
    }

    /**
     * Takes an applying promo off this sale, or puts it back.
     *
     * A toggle rather than a one-way removal so a mis-tap is recoverable without emptying the
     * cart. The discount is recomputed by the same engine either way, so the total is always the
     * sum of the promos that are actually still on.
     */
    fun togglePromoSuppressed(promoId: Long) {
        suppressedPromoIds.value = suppressedPromoIds.value.let { current ->
            if (promoId in current) current - promoId else current + promoId
        }
    }

    fun clearMessage() {
        _messages.value = null
    }

    /** Pays for the cart, deducting the promo, tax and (for card) the configured fee. */
    fun checkout() {
        val current = uiState.value
        if (!current.canCheckout) return
        isCheckingOut.value = true
        viewModelScope.launch {
            val result = stockRepository.checkout(
                lines = current.cart.lines,
                paymentMethod = current.paymentMethod,
                taxRateBasisPoints = current.taxRateBasisPoints,
                cardFeeRateBasisPoints = current.cardFeeRateBasisPoints,
                cardFeeFixedCents = current.cardFeeFixedCents,
                note = current.note,
                // The amount the cashier was shown, not a fresh calculation: recomputing here
                // would let a promo edited mid-sale change the bill after it was quoted. This is
                // the sum across every stacking promo, exactly as displayed.
                promoDiscountCents = current.promoDiscountCents,
                // Every applying promo's name, joined: the order keeps one row for the sale, so
                // the receipt has to name all of them or it under-reports what was given.
                promoName = current.promoName,
            )
            isCheckingOut.value = false
            _messages.value = when (result) {
                is StockRepository.CheckoutResult.Success -> {
                    lastOrder.value = result.order
                    cart.value = Cart()
                    // The note was consumed by this sale and must not leak into the next one.
                    note.value = ""
                    CartMessage.CheckedOut(
                        isCard = result.order.paymentMethod == PaymentMethod.CARD,
                        totalCents = result.order.totalCents,
                    )
                }

                StockRepository.CheckoutResult.EmptyCart ->
                    CartMessage.Res(R.string.cart_empty_message)

                is StockRepository.CheckoutResult.InsufficientStock ->
                    CartMessage.Res(
                        R.string.cart_insufficient_stock,
                        listOf(
                            result.productName,
                            result.requested.toString(),
                            result.available.toString(),
                        )
                    )

                is StockRepository.CheckoutResult.ProductNotFound ->
                    CartMessage.Res(R.string.cart_product_missing, listOf(result.productName))
            }
        }
    }

    fun dismissReceipt() {
        lastOrder.value = null
    }

    companion object {
        fun factory(app: StockApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { CartViewModel(app.repository, app.settingsRepository) }
        }
    }
}
