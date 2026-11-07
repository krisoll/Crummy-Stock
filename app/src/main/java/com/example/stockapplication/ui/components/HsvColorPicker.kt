package com.example.stockapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.ArgbColor
import com.example.stockapplication.ui.theme.contrastingText
import kotlin.math.roundToInt

/**
 * An HSV colour picker: a saturation/brightness square, a hue slider and a hex field.
 *
 * Hue, saturation and brightness are held as three independent values rather than deriving them
 * from one RGB colour. Converting RGB to HSV loses hue on greys, so a single-source-of-truth model
 * would snap the hue to red whenever the user dragged brightness to zero or picked a grey.
 */
@Composable
fun HsvColorPicker(
    color: ArgbColor?,
    onColorChange: (ArgbColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seed the internal HSV state exactly once, on first composition for this picker instance.
    //
    // It must NOT be keyed on `color`: the picker calls back with every drag step, the caller
    // stores that value, and the recomposition then delivers a *new* `color`. Keying the remember
    // on it reset hue/saturation/brightness mid-gesture, which is what made the square go dead
    // right after the toggle or the first drag.
    //
    // Resetting from the outside is handled explicitly instead, via `seedColor` + LaunchedEffect
    // below, so external changes (Reset, or the saved colour arriving later) still apply.
    val initialHsv = remember { (color?.let { Color(it.toULong() shl 32) } ?: DefaultPickerColor).toHsv() }

    var hue by remember { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var brightness by remember { mutableFloatStateOf(initialHsv.brightness) }

    var hexText by remember { mutableStateOf(Color.hsv(hue, saturation, brightness).toHexString()) }
    var hexError by remember { mutableStateOf(false) }

    // Adopt colours that change from outside the picker (Reset, settings loading, Apply). Dragging
    // writes through `onColorChange`, which would echo straight back here — the tolerance check
    // below stops that echo from fighting the user's finger mid-drag.
    LaunchedEffect(color) {
        val incoming = color?.let { Color(it.toULong() shl 32) } ?: return@LaunchedEffect
        // Our own emit echoes back as a new `color`. Comparing the raw ARGB is exact, whereas
        // comparing hex strings could disagree with the HSV round-trip and spuriously reset the
        // picker to a value it had already superseded.
        if (incoming.toArgb() == Color.hsv(hue, saturation, brightness).toArgb()) {
            return@LaunchedEffect
        }
        val incomingHex = incoming.toHexString()
        val hsv = incoming.toHsv()
        // A grey has no meaningful hue; keep the angle the user already had.
        if (hsv.saturation > 0.001f) hue = hsv.hue
        saturation = hsv.saturation
        brightness = hsv.brightness
        hexText = incomingHex
        hexError = false
    }

    // Gesture math needs the laid-out size. These are held as State objects (not `by` delegates)
    // so the pointerInput(Unit) lambda captures the *container* and reads .value at gesture time.
    // Capturing a plain IntSize would freeze on the zero size present at first composition — that
    // stale capture is what made the pickers work only intermittently.
    val squareSize = remember { mutableStateOf(IntSize.Zero) }
    val sliderSize = remember { mutableStateOf(IntSize.Zero) }

    val current = Color.hsv(hue, saturation, brightness)

    val squareDescription = stringResource(
        R.string.colour_picker_square_description,
        (saturation * 100).roundToInt(),
        (brightness * 100).roundToInt()
    )
    val hueDescription = stringResource(
        R.string.colour_picker_hue_description,
        hue.roundToInt()
    )

    /**
     * Emits a colour built from *explicit* channel values.
     *
     * A gesture callback writes hue/saturation/brightness and then emits in the same frame, before
     * recomposition has run. Reading the `current` val here would therefore send the colour from
     * the *previous* frame — e.g. moving the square after changing the hue reported the stale hue,
     * and the caller then snapped the hue slider back. Always pass the values being applied.
     */
    fun emit(h: Float, s: Float, b: Float) =
        onColorChange(Color.hsv(h, s, b).toArgb().toLong())

    fun refreshHexText(h: Float, s: Float, b: Float) {
        hexText = Color.hsv(h, s, b).toHexString()
        hexError = false
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ---- Saturation / brightness square ---------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.hsv(hue, 1f, 1f))
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .onSizeChanged { squareSize.value = it }
                // Claim every pointer down so the parent verticalScroll cannot steal the gesture
                // mid-drag; otherwise the screen scrolls instead of the colour changing.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        applySquareTouch(down.position, squareSize.value) { s, b ->
                            saturation = s; brightness = b
                            refreshHexText(hue, s, b); emit(hue, s, b)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            change.consume()
                            applySquareTouch(change.position, squareSize.value) { s, b ->
                                saturation = s; brightness = b
                                refreshHexText(hue, s, b); emit(hue, s, b)
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = squareDescription
                }
        ) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val x = saturation * size.width
                val y = (1f - brightness) * size.height
                drawCircle(Color.White, radius = 11f, center = Offset(x, y))
                drawCircle(current, radius = 8f, center = Offset(x, y))
            }
        }

        // ---- Hue slider ------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.colour_picker_hue_label), style = MaterialTheme.typography.labelMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(HueSweep))
                    .onSizeChanged { sliderSize.value = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val newHue = hueFromX(down.position.x, sliderSize.value)
                            hue = newHue
                            refreshHexText(newHue, saturation, brightness)
                            emit(newHue, saturation, brightness)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) break
                                change.consume()
                                val newHue = hueFromX(change.position.x, sliderSize.value)
                                hue = newHue
                                refreshHexText(newHue, saturation, brightness)
                                emit(newHue, saturation, brightness)
                            }
                        }
                    }
                    .semantics {
                        contentDescription = hueDescription
                    }
            ) {
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val center = Offset((hue / 360f) * size.width, size.height / 2f)
                    val ringRadius = (size.height / 2f) - 2f
                    if (ringRadius > 0f) {
                        drawCircle(Color.White, radius = ringRadius, center = center)
                        drawCircle(
                            Color.Black,
                            radius = (ringRadius - 4f).coerceAtLeast(1f),
                            center = center,
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }

        // ---- Hex input + live swatch ----------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(current)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#",
                    color = current.contrastingText(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedTextField(
                value = hexText,
                onValueChange = { typed ->
                    val cleaned = typed.uppercase()
                        .filter { it.isDigit() || it in 'A'..'F' || it == '#' }
                        .removePrefix("#")
                        .take(6)
                    hexText = cleaned
                    val parsed = parseHex(cleaned)
                    if (parsed == null) {
                        hexError = cleaned.length == 6
                    } else {
                        hexError = false
                        val hsv = parsed.toHsv()
                        // A typed grey has no meaningful hue: keep the one the user already had.
                        if (hsv.saturation > 0.001f) hue = hsv.hue
                        saturation = hsv.saturation
                        brightness = hsv.brightness
                        onColorChange(parsed.toArgb().toLong())
                    }
                },
                label = { Text(stringResource(R.string.colour_picker_hex_label)) },
                singleLine = true,
                isError = hexError,
                supportingText = if (hexError) {
                    { Text(stringResource(R.string.colour_picker_hex_error)) }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            stringResource(
                R.string.colour_picker_readout,
                hue.roundToInt(),
                (saturation * 100).roundToInt(),
                (brightness * 100).roundToInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun applySquareTouch(
    offset: Offset,
    size: IntSize,
    onChange: (saturation: Float, brightness: Float) -> Unit,
) {
    if (size.width <= 0 || size.height <= 0) return
    val saturation = (offset.x / size.width).coerceIn(0f, 1f)
    val brightness = (1f - offset.y / size.height).coerceIn(0f, 1f)
    onChange(saturation, brightness)
}

private fun hueFromX(x: Float, size: IntSize): Float {
    if (size.width <= 0) return 0f
    return ((x / size.width) * 360f).coerceIn(0f, 360f)
}

private val DefaultPickerColor = Color(0xFF6750A4)

private val HueSweep = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
)

/** HSV triple in Compose-friendly units: hue in degrees [0,360), the rest [0,1]. */
internal data class Hsv(val hue: Float, val saturation: Float, val brightness: Float)

internal fun Color.toHsv(): Hsv {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta < 1e-6f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max < 1e-6f) 0f else delta / max
    return Hsv(hue, saturation, max)
}

/** Parses "RRGGBB" into an opaque [Color], or null when the input isn't 6 hex digits yet. */
internal fun parseHex(hex: String): Color? {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return null
    val value = clean.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or value).toULong() shl 32)
}

/** Renders as `RRGGBB`; the picker always produces opaque colours, so alpha is dropped. */
internal fun Color.toHexString(): String = "%02X%02X%02X".format(
    (red * 255).roundToInt().coerceIn(0, 255),
    (green * 255).roundToInt().coerceIn(0, 255),
    (blue * 255).roundToInt().coerceIn(0, 255),
)
