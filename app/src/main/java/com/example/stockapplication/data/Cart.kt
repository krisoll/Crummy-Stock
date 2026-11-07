package com.example.stockapplication.data

/**
 * One line in the checkout cart.
 *
 * Kept as a pure value type so the whole cart (add / remove / totals) can be tested without a
 * database. Stock is snapshotted at scan time only for display; the authoritative check happens
 * inside the checkout transaction.
 */
data class CartLine(
    val productId: Long,
    val productName: String,
    val barcode: String,
    val unitPriceCents: Long,
    /** How many units are being bought. */
    val quantity: Int,
    /** What the inventory last reported, used to cap the +/- buttons. */
    val availableStock: Int,
) {
    val lineTotalCents: Long get() = unitPriceCents * quantity
}

/** Immutable cart state. Every mutation returns a new instance so Compose can diff it. */
data class Cart(val lines: List<CartLine> = emptyList()) {

    val isEmpty: Boolean get() = lines.isEmpty()

    val itemCount: Int get() = lines.sumOf { it.quantity }

    val subtotalCents: Long get() = lines.sumOf { it.lineTotalCents }

    /** Units of [productId] currently in the cart, or 0 when it has no line. */
    fun countOf(productId: Long): Int =
        lines.firstOrNull { it.productId == productId }?.quantity ?: 0

    /**
     * Adds a product, merging with an existing line rather than duplicating it.
     *
     * @return the updated cart, or the same cart when the product is out of stock or the line is
     *         already at the available quantity.
     */
    fun add(product: Product, quantity: Int = 1): Cart {
        if (quantity <= 0) return this
        val existing = lines.firstOrNull { it.productId == product.id }
        return if (existing == null) {
            if (product.quantity <= 0) return this
            copy(
                lines = lines + CartLine(
                    productId = product.id,
                    productName = product.name,
                    barcode = product.barcode,
                    unitPriceCents = product.priceCents,
                    quantity = quantity.coerceAtMost(product.quantity),
                    availableStock = product.quantity,
                )
            )
        } else {
            updateQuantity(existing.productId, existing.quantity + quantity)
        }
    }

    /** Clamps to [0, availableStock]; setting a line to zero removes it. */
    fun updateQuantity(productId: Long, quantity: Int): Cart {
        return copy(
            lines = lines.mapNotNull { line ->
                if (line.productId != productId) {
                    line
                } else {
                    val clamped = quantity.coerceIn(0, line.availableStock)
                    if (clamped == 0) null else line.copy(quantity = clamped)
                }
            }
        )
    }

    fun remove(productId: Long): Cart =
        copy(lines = lines.filterNot { it.productId == productId })

    fun clear(): Cart = Cart()

    /**
     * Refreshes the stock snapshot for every line, dropping anything that has since sold out or
     * disappeared, and clamping quantities to the new availability.
     */
    fun reconcile(products: List<Product>): Cart {
        val byId = products.associateBy { it.id }
        return copy(
            lines = lines.mapNotNull { line ->
                val product = byId[line.productId] ?: return@mapNotNull null
                if (product.quantity <= 0) return@mapNotNull null
                line.copy(
                    productName = product.name,
                    unitPriceCents = product.priceCents,
                    availableStock = product.quantity,
                    quantity = line.quantity.coerceAtMost(product.quantity),
                )
            }
        )
    }
}
