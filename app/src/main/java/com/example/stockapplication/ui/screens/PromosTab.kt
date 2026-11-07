package com.example.stockapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.PromoStatus
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion

/**
 * The Promos tab: every promo the shop has, with its live status.
 *
 * Deliberately *not* filtered to active promos. An owner opening this tab wants to see the promo
 * they switched off last week and the one that expired yesterday, because those are the ones that
 * need attention — hiding them would make "where did my promo go?" the most common question.
 */
@Composable
fun PromosTab(
    promos: List<Promo>,
    categories: List<Category>,
    products: List<Product>,
    now: Long,
    onPromoClick: (Promo) -> Unit,
    onToggleEnabled: (Promo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (promos.isEmpty()) {
        PromosEmptyState(modifier)
        return
    }

    // Resolved once rather than per row: a promo names its category, and looking that up in a map
    // beats scanning the category list for every card.
    val categoryNames = categories.associate { it.id to it.name }
    val catalogueIds: Set<Long> = products.map { it.id }.toSet()
    val staggerMillis = AppMotion.stagger()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items = promos, key = { _, promo -> promo.id }) { index, promo ->
            PromoCard(
                promo = promo,
                categoryName = promo.categoryId?.let(categoryNames::get),
                catalogueIds = catalogueIds,
                now = now,
                onClick = { onPromoClick(promo) },
                onToggleEnabled = { enabled -> onToggleEnabled(promo, enabled) },
                modifier = rememberRevealModifier(index, staggerMillis)
            )
        }
    }
}

@Composable
private fun PromoCard(
    promo: Promo,
    categoryName: String?,
    catalogueIds: Set<Long>,
    now: Long,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = PromoStatus.of(promo, now)
    // Inactive promos are dimmed rather than hidden: the card stays tappable so the owner can fix
    // the dates or switch it back on.
    val inactive = status != PromoStatus.ACTIVE

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (inactive) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LocalOffer,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (inactive) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    promo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    promoSummary(promo, categoryName),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Flags products that have since been deleted, so a promo quietly matching nothing
                // is visible from the list rather than only from the editor.
                val missing = promo.productIdList.count { it !in catalogueIds }
                if (promo.kind == PromoKind.PRODUCTS && missing > 0) {
                    Text(
                        stringResource(R.string.promo_products_missing, missing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                PromoStatusChip(status)
            }

            Switch(
                checked = promo.enabled,
                onCheckedChange = onToggleEnabled
            )
        }
    }
}

@Composable
private fun PromoStatusChip(status: PromoStatus) {
    val container = when (status) {
        PromoStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        PromoStatus.SCHEDULED -> MaterialTheme.colorScheme.tertiaryContainer
        PromoStatus.EXPIRED, PromoStatus.OFF -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        PromoStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        PromoStatus.SCHEDULED -> MaterialTheme.colorScheme.onTertiaryContainer
        PromoStatus.EXPIRED, PromoStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            stringResource(status.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** "Any 3 from Drinks · 10% off" — the one line that says what the promo actually does. */
@Composable
fun promoSummary(promo: Promo, categoryName: String?): String {
    val scope = when (promo.kind) {
        PromoKind.CATEGORY -> stringResource(
            R.string.promo_summary_category,
            promo.thresholdQuantity,
            categoryName ?: stringResource(R.string.promo_category_none)
        )

        PromoKind.PRODUCTS -> stringResource(
            R.string.promo_summary_products,
            promo.thresholdQuantity,
            promo.productIdList.size
        )
    }

    val discount = when (promo.discountType) {
        DiscountType.PERCENT -> stringResource(
            R.string.promo_summary_percent,
            formatPercent(promo.discountPercentBasisPoints)
        )

        DiscountType.FIXED -> stringResource(
            R.string.promo_summary_fixed,
            formatCents(promo.discountFixedCents)
        )
    }

    return stringResource(R.string.promo_summary, scope, discount)
}

/**
 * Renders basis points as a short percentage for prose: 1000 -> "10", 1250 -> "12.5".
 *
 * Trailing zeros are dropped so "10% off" does not read as "10.00% off" in a sentence.
 */
fun formatPercent(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return when {
        fraction == 0 -> whole.toString()
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

@Composable
private fun PromosEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocalOffer,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                stringResource(R.string.promo_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.promo_empty_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
