package com.example.stockapplication.ui.screens.personalize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.LoadingImage
import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.data.MaxLoadingCircleSizeDp
import com.example.stockapplication.data.MaxLoadingImageSizeDp
import com.example.stockapplication.data.MaxSaleAnimationSizeDp
import com.example.stockapplication.data.MinLoadingCircleSizeDp
import com.example.stockapplication.data.MinLoadingImageSizeDp
import com.example.stockapplication.data.MinSaleAnimationSizeDp
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.ui.components.SaleThumbsUp
import com.example.stockapplication.ui.components.SpinningImage

/**
 * Everything that moves: the transition speed, the startup screen and the sale flourish.
 *
 * Grouped by motion rather than by component because that is the axis the user tunes them on — the
 * startup screen's spin period, for instance, is already stretched by the motion speed setting, so
 * judging one without the other gives a misleading preview.
 *
 * The sale animation lives here rather than with the sale sound even though both fire on the same
 * event: what is being chosen is a moving picture, and the size slider next to it only makes sense
 * beside the tiles that show the result.
 */
@Composable
internal fun AnimationsPage(
    settings: AppSettings,
    onBack: () -> Unit,
    onMotionSpeedChange: (MotionSpeed) -> Unit,
    onLoadingIconChange: (LoadingIcon) -> Unit,
    onLoadingSpinChange: (LoadingSpin) -> Unit,
    onLoadingCircleChange: (LoadingCircle) -> Unit,
    onLoadingImageSizeChange: (Int) -> Unit,
    onLoadingCircleSizeChange: (Int) -> Unit,
    onToggleLoadingScreen: (Boolean) -> Unit,
    onResetLoadingScreen: () -> Unit,
    /** Opens the system photo picker. */
    onLoadingImagePicker: () -> Unit,
    onClearLoadingImage: () -> Unit,
    /** Opens the document picker for a loading-screen Lottie animation. */
    onLoadingAnimationPicker: () -> Unit = {},
    /** Drops the animation and goes back to the chosen icon. */
    onClearLoadingAnimation: () -> Unit = {},
    onSaleAnimationChange: (SaleAnimation) -> Unit,
    onSaleAnimationSizeChange: (Int) -> Unit,
    /** Opens the document picker for a Lottie file. */
    onOpenLottiePicker: () -> Unit = {},
    /** Drops the imported animation and goes back to a built-in glyph. */
    onClearLottie: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonalizeHeader(
            title = stringResource(R.string.settings_animations),
            description = stringResource(R.string.settings_animations_description),
            backLabel = stringResource(R.string.settings_back_to_personalize),
            onBack = onBack,
        )

        // ---- Transition speed ------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_motion),
            description = stringResource(R.string.settings_motion_description)
        ) {
            MotionSpeed.entries.forEach { speed ->
                ChoiceRow(
                    label = stringResource(speed.labelRes),
                    selected = MotionSpeed.fromId(settings.motionSpeedId) == speed,
                    onSelect = { onMotionSpeedChange(speed) },
                )
            }
        }

        // ---- Startup screen --------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_loading_screen),
            description = stringResource(R.string.settings_loading_screen_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_loading_screen_enabled),
                checked = settings.showLoadingScreen,
                onCheckedChange = onToggleLoadingScreen
            )

            if (settings.showLoadingScreen) {
                val loadingImage = settings.loadingImage

                // The preview uses the same composable as the real startup screen, so what the
                // user tunes here is exactly what they get on launch — including the disc.
                //
                // It renders at the *configured* sizes rather than a scaled-down mock, so the two
                // sliders below are judged against the real thing. That is also why it sits on a
                // scrollable page scoped to a fixed-height box: at the maximum size the disc is
                // far taller than the available row, and clipping is a better failure than the
                // preview shoving the rest of the page around as the slider moves.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PreviewMinHeight),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpinningImage(
                            image = loadingImage,
                            spin = settings.loadingSpin,
                            size = settings.loadingImageSizeDp.dp,
                            circle = settings.loadingCircle,
                            circleSize = settings.effectiveLoadingCircleSizeDp.dp,
                            color = MaterialTheme.colorScheme.primary,
                            // Same expression as the real startup screen, and the reason this
                            // preview moves at a different pace when Animations changes.
                            periodMillis = settings.loadingSpin.periodMillisAt(settings.motionSpeed),
                        )
                        Text(
                            stringResource(R.string.settings_loading_preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---- Your own picture ----------------------------------------
                FieldLabel(stringResource(R.string.settings_loading_image))
                HintText(
                    stringResource(R.string.settings_loading_image_description) + " " +
                        stringResource(R.string.settings_loading_image_gif_hint)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Button(
                        onClick = onLoadingImagePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                if (loadingImage is LoadingImage.Picked) {
                                    R.string.settings_loading_image_change
                                } else {
                                    R.string.settings_loading_image_choose
                                }
                            )
                        )
                    }
                    if (loadingImage is LoadingImage.Picked) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onClearLoadingImage() }
                        ) {
                            Text(stringResource(R.string.action_reset))
                        }
                    }
                }

                HorizontalDivider()

                // ---- Your own animation --------------------------------------
                // Above the icon grid, because choosing an animation and choosing an icon are
                // alternatives: whichever was touched last wins, and the animation is the
                // bigger gesture.
                FieldLabel(stringResource(R.string.settings_loading_animation))
                HintText(stringResource(R.string.settings_loading_animation_description))
                val activeLottie = loadingImage as? LoadingImage.Lottie
                if (activeLottie != null) {
                    Text(
                        stringResource(
                            if (activeLottie.fileName == null) {
                                R.string.settings_loading_animation_bundled
                            } else {
                                R.string.settings_loading_animation_custom
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        // A status label is text, so it follows the font colour.
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Button(
                        onClick = onLoadingAnimationPicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                if (activeLottie?.fileName != null) {
                                    R.string.settings_loading_animation_change
                                } else {
                                    R.string.settings_loading_animation_choose
                                }
                            )
                        )
                    }
                    if (loadingImage is LoadingImage.Lottie) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onClearLoadingAnimation
                        ) {
                            Text(stringResource(R.string.settings_loading_animation_remove))
                        }
                    }
                }

                HorizontalDivider()

                FieldLabel(stringResource(R.string.settings_loading_icon))
                HintText(stringResource(R.string.settings_loading_icon_description))
                // A row rather than a dropdown: the icons are the choice, so showing them beats
                // naming them. Disabled-looking while a picture is in use, because the icon is
                // only a fallback then — tapping one clears the picture so the choice sticks.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LoadingIcon.entries, key = { it.id }) { option ->
                        // Tapping an icon also drops any picked picture, so the choice is visible
                        // immediately instead of being silently overridden by the file.
                        val selectedBuiltIn = (loadingImage as? LoadingImage.BuiltIn)?.icon
                        LoadingIconChip(
                            option = option,
                            selected = selectedBuiltIn == option,
                            onClick = {
                                if (loadingImage is LoadingImage.Picked) onClearLoadingImage()
                                onLoadingIconChange(option)
                            }
                        )
                    }
                }

                // ---- Background circle ---------------------------------------
                FieldLabel(stringResource(R.string.settings_loading_circle))
                HintText(stringResource(R.string.settings_loading_circle_description))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LoadingCircle.entries, key = { it.id }) { option ->
                        LoadingCircleChip(
                            option = option,
                            selected = settings.loadingCircle == option,
                            onClick = { onLoadingCircleChange(option) }
                        )
                    }
                }

                // ---- Sizes ---------------------------------------------------
                SizeSlider(
                    label = stringResource(R.string.settings_loading_image_size),
                    value = settings.loadingImageSizeDp,
                    range = MinLoadingImageSizeDp..MaxLoadingImageSizeDp,
                    valueLabel = stringResource(
                        R.string.settings_loading_size_value,
                        settings.loadingImageSizeDp
                    ),
                    onValueChange = onLoadingImageSizeChange,
                )
                // Hidden while the disc is off: a size for something that is not drawn is a
                // control that appears to do nothing.
                if (settings.loadingCircle != LoadingCircle.NONE) {
                    SizeSlider(
                        label = stringResource(R.string.settings_loading_circle_size),
                        value = settings.effectiveLoadingCircleSizeDp,
                        range = MinLoadingCircleSizeDp..MaxLoadingCircleSizeDp,
                        valueLabel = stringResource(
                            R.string.settings_loading_size_value,
                            settings.effectiveLoadingCircleSizeDp
                        ),
                        onValueChange = onLoadingCircleSizeChange,
                    )
                }

                FieldLabel(stringResource(R.string.settings_loading_spin))
                LoadingSpin.entries.forEach { spin ->
                    ChoiceRow(
                        label = stringResource(spin.labelRes),
                        selected = LoadingSpin.fromId(settings.loadingSpinId) == spin,
                        onSelect = { onLoadingSpinChange(spin) },
                    )
                }

                TextButton(onClick = onResetLoadingScreen) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Text(stringResource(R.string.settings_loading_reset))
                }
            }
        }

        // ---- Sale animation --------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_sale_animation),
            description = stringResource(R.string.settings_sale_animation_description)
        ) {
            // A row of the glyphs rather than a grid of labelled tiles. This is now the same
            // control as the startup screen's icon picker above: round chips of equal size, the
            // picture carrying the whole meaning. The glyphs are unmistakable at a glance, so a
            // name under each one only added height and squeezed the preview into three columns
            // of very different-shaped tiles.
            //
            // A `LazyRow`, matching the icon and circle pickers, so a seventh glyph scrolls
            // rather than wrapping into a lopsided second row.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SaleAnimation.entries, key = { it.id }) { animation ->
                    SaleAnimationChip(
                        animation = animation,
                        selected = settings.saleAnimation == animation,
                        onClick = { onSaleAnimationChange(animation) }
                    )
                }
            }

            HorizontalDivider()

            // ---- Glyph size ---------------------------------------------------
            // A slider because this is a value to tune by eye, and the preview below already
            // shows the result live, so the number never has to be imagined.
            SizeSlider(
                label = stringResource(R.string.settings_sale_animation_size),
                value = settings.saleAnimationSizeDp,
                range = MinSaleAnimationSizeDp..MaxSaleAnimationSizeDp,
                valueLabel = stringResource(
                    R.string.settings_sale_animation_size_value,
                    settings.saleAnimationSizeDp
                ),
                onValueChange = onSaleAnimationSizeChange,
            )
            HintText(stringResource(R.string.settings_sale_animation_size_description))

            HorizontalDivider()

            // ---- Your own animation -------------------------------------------
            // Below the built-in grid, mirroring how the sound pickers offer a list and then a
            // file: the grid is the quick answer, this is the "use exactly what I designed one".
            FieldLabel(stringResource(R.string.settings_sale_animation_own))
            HintText(stringResource(R.string.settings_sale_animation_own_description))
            if (settings.usesLottieSaleAnimation) {
                Text(
                    stringResource(R.string.settings_sale_animation_custom_active),
                    style = MaterialTheme.typography.bodySmall,
                    // A status label is text, so it follows the font colour.
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenLottiePicker, modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (settings.saleLottieFileName != null) {
                                R.string.settings_sale_animation_change
                            } else {
                                R.string.settings_sale_animation_choose
                            }
                        )
                    )
                }
                if (settings.saleLottieFileName != null) {
                    OutlinedButton(onClick = onClearLottie) {
                        Text(stringResource(R.string.settings_sale_animation_remove))
                    }
                }
            }

            HorizontalDivider()

            // ---- Full-size preview --------------------------------------------
            // After the size slider, because it is the *answer* to it: the tiles above are
            // uniform thumbnails and cannot show what a 140 dp glyph actually looks like on the
            // receipt.
            SaleAnimationPreview(
                animation = settings.saleAnimation,
                sizeDp = settings.saleAnimationSizeDp,
            )
        }
    }
}

/**
 * Floor for the preview box, in dp.
 *
 * Matched to the largest disc the sliders allow, so the page does not reflow every time the size
 * slider moves; the preview grows into reserved space instead of pushing the controls around.
 */
private val PreviewMinHeight = MaxLoadingCircleSizeDp.dp

/**
 * One selectable disc style in the startup-screen picker.
 *
 * Renders a miniature of the real backdrop rather than a swatch, so "Soft" and "Solid" are told
 * apart by looking at them instead of by reading the labels.
 */
@Composable
private fun LoadingCircleChip(
    option: LoadingCircle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SpinningImage(
            // The chip previews the disc, so it always draws a built-in icon: the user's own
            // picture would make every option look identical.
            image = LoadingImage.BuiltIn(LoadingIcon.CART),
            spin = LoadingSpin.NONE,
            size = 28.dp,
            circle = option,
            circleSize = 52.dp,
            // Tint the mock icon to read as a silhouette, not as a competing image.
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            stringResource(option.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/**
 * One selectable icon in the startup-screen picker.
 *
 * Shows the icon itself rather than a name, because the decision being made is purely visual.
 */
@Composable
private fun LoadingIconChip(
    option: LoadingIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            borderColor
        )
    ) {
        Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = option.icon,
                contentDescription = stringResource(option.labelRes),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * A full-size render of the chosen flourish, on the same dark wash the receipt uses.
 *
 * The grid above shows every option at one small size. That answers "which glyph", but not "how
 * big is it" — and the size slider right above this changes exactly that. So the preview draws the
 * selected glyph at the *configured* dp, against the receipt's scrim, which is the only place the
 * two together can be judged.
 *
 * It is a tap-to-replay surface rather than an auto-looping one: the flourish is a one-shot
 * celebration that lands and stays, and the resting bob in [SaleThumbsUp] is what carries the
 * motion after that. Tapping replays the landing so the entrance can be watched again, which is
 * the part the user is actually choosing — recomposing a fresh instance is what re-launches it.
 */
@Composable
private fun SaleAnimationPreview(
    animation: SaleAnimation,
    sizeDp: Int,
) {
    // Identifies one *played* preview. Bumping it on tap rebuilds the glyph, which restarts the
    // landing animation from the top rather than resuming the idle bob.
    var replayCount by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Tall enough for the largest glyph plus its rings at the biggest size the slider
            // allows, so the box does not grow as the slider moves and shove the page around.
            .height(SalePreviewMinHeight)
            .clickable(onClickLabel = null, onClick = { replayCount++ }),
        shape = RoundedCornerShape(16.dp),
        // The receipt's own wash, not a theme colour: the glyph's accent and halo are tuned to
        // read against black, so previewing it on `surfaceVariant` would flatter it.
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        // A Box so the replay badge can sit in the corner while the glyph stays centred.
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Keyed on the replay count so each tap produces a new instance and re-runs the
                // landing keyframes from the top instead of resuming the idle bob.
                key(replayCount) {
                    SaleThumbsUp(animation = animation, thumbSize = sizeDp.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_sale_animation_preview),
                    style = MaterialTheme.typography.labelSmall,
                    // White rather than a theme colour, for the same reason the receipt's text is:
                    // the wash is fixed black, so `onSurface` could end up dark on dark.
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Says the panel is tappable, so the replay is discoverable rather than a secret
            // gesture. The whole box is the hit target, not just this glyph.
            Icon(
                Icons.Default.Replay,
                contentDescription = stringResource(R.string.settings_sale_animation_replay),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

/**
 * Height of the sale-animation preview box.
 *
 * Fixed rather than wrapping the glyph: the size slider changes the glyph from 24 to 180 dp, and a
 * wrapping box would make the page jump under the user's finger while they drag it.
 */
private val SalePreviewMinHeight = 220.dp

/**
 * One selectable glyph in the sale-animation picker: a round chip of the glyph in its real
 * flourish.
 *
 * Built to match [LoadingIconChip] exactly — same 56 dp circle, same border weights, same gap in
 * the row — so the two pickers on this page read as one control used twice rather than two
 * unrelated widgets. The chips sit on `surface` rather than on the page's panel colour: the glyphs
 * are drawn in the accent, and an accent glyph on an accent-tinted panel is the same
 * invisibility problem the outline family exists to avoid.
 *
 * **The preview is the actual component, not a stand-in.** Rendering a static `Icon` and swapping
 * in the animation only on the receipt would mean the user chooses from a picture of a thing
 * instead of the thing itself — and the whole point of this section is that the motion is the
 * feature. The cost is one running animation per chip, which is why the glyph is scaled right
 * down and the circles never grow to receipt size.
 */
@Composable
private fun SaleAnimationChip(
    animation: SaleAnimation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    // Resolved here rather than inside `semantics`, where `stringResource` cannot be called.
    val label = stringResource(animation.labelRes)

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // Announced as one radio-like choice; the glyph itself is decorative and carries no
            // content description, so the name provided here is all a screen reader gets.
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            borderColor
        )
    ) {
        // No inner padding: `SaleThumbsUp` sizes its rings and halo *relative to* `thumbSize`, so
        // it already reserves the box it needs. Wrapping it in the icon picker's 14 dp inset
        // would shrink the glyph twice over and leave the flourish too small to judge.
        //
        // The glyph takes the text colour, not the accent the receipt uses. The chip sits on the
        // app's light panel, where `primary` is the button colour and the panel is often a tint of
        // a saturated one — an accent glyph would be drawn in nearly the colour behind it. Ink is
        // the channel the user has already chosen to read against this page, so the glyph inherits
        // that contrast instead of fighting it.
        // `clipToBounds` + a fill-max box: `SaleThumbsUp` reserves a square of `thumbSize * 2.2`
        // to hold its expanding rings, which at 22 dp is 48.4 dp — wider than the 56 dp circle
        // once the border is taken off. Clipping here keeps the rings inside the chip instead of
        // letting them paint over the chip's own border.
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SaleThumbsUp(
                animation = animation,
                thumbSize = 22.dp,
                glyphColor = MaterialTheme.colorScheme.onSurface,
                // No disc behind the glyph: drawn at `0.9` alpha over the chip it covered the
                // glyph in a solid blob of the ink colour, which read as "the selected icon is
                // hidden". Transparent skips the disc entirely; the rings still draw.
                haloColor = Color.Transparent,
            )
        }
    }
}
