package com.famiglia.tripcompanion.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** Render the SDK's transparent vector and its own light/dark colors, without tinting it. */
@Composable
internal fun GoogleMapsAttribution(modifier: Modifier = Modifier,
    darkBackground: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    height: Dp = 18.dp) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = height * 5.5f // The official vector's 88:16 aspect ratio.
    val logoContext = remember(context, darkBackground, configuration) {
        context.createConfigurationContext(Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkBackground) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
    }
    // Compose draws the official resource directly into the UI. No native-view
    // overlay is introduced on top of tappable saved-place thumbnails.
    val logo = remember(logoContext, density.density, height) {
        requireNotNull(logoContext.getDrawable(com.google.android.libraries.places.R.drawable.google_maps_attribution_image))
            .toBitmap(with(density) { width.roundToPx() }, with(density) { height.roundToPx() }).asImageBitmap()
    }
    Image(logo, "Google Maps", modifier.width(width).height(height).testTag("google-maps-attribution"))
}
