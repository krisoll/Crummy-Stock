package com.example.stockapplication.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders product QR code labels as bitmaps so they can be saved or printed.
 *
 * The exported image is the bare QR code only — no product name or barcode text underneath. The
 * code itself already encodes the barcode, and the caption was making stickers larger and harder
 * to lay out on small labels.
 */
object QrCodeExporter {

    /** Renders a QR code for [content] on a white square of [sizePx]. */
    fun renderQr(content: String, sizePx: Int = 512): Bitmap {
        require(content.isNotEmpty()) { "Content cannot be empty" }
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Renders a printable label containing only the QR code — no name or barcode text beneath.
     * Returns a square bitmap of [sizePx].
     */
    fun renderLabel(barcode: String, sizePx: Int = 512): Bitmap = renderQr(barcode, sizePx)

    /** File name used when exporting a single product's label, e.g. "qr-coffee-4451.png". */
    fun fileNameFor(productName: String, barcode: String): String {
        val slug = productName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "product" }
            .take(40)
        return "qr-$slug-$barcode.png"
    }
}
