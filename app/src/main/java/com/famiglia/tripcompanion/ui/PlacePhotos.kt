package com.famiglia.tripcompanion.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.famiglia.tripcompanion.maps.MapViewModel
import com.famiglia.tripcompanion.maps.PlacePhoto
import com.famiglia.tripcompanion.data.PlaceCategory

/** Google photo URLs, author metadata and remote images are not written to persistent caches. */
@Composable
internal fun PlaceImage(localUri: String, photo: PlacePhoto?, name: String, modifier: Modifier, category: String = "") {
    val context = LocalContext.current
    val source = localUri.takeIf(String::isNotBlank) ?: photo?.uri
    var failed by remember(source) { mutableStateOf(false) }
    var loaded by remember(source) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) {
            Box(contentAlignment = Alignment.Center) {
                val icon = when (category) {
                    PlaceCategory.RESTAURANT.name -> Icons.Default.Restaurant
                    PlaceCategory.CAFE.name -> Icons.Default.LocalCafe
                    PlaceCategory.SHOPPING.name -> Icons.Default.ShoppingBag
                    PlaceCategory.LANDMARK.name -> Icons.Default.AccountBalance
                    else -> Icons.Default.Place
                }
                Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (source != null && !failed) AsyncImage(
            model = ImageRequest.Builder(context).data(source).apply {
                if (localUri.isBlank()) { diskCachePolicy(CachePolicy.DISABLED); memoryCachePolicy(CachePolicy.DISABLED) }
            }.build(), contentDescription = "Photo of $name", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(), onSuccess = { loaded = true }, onError = { failed = true },
        )
        if (loaded && localUri.isBlank()) Surface(Modifier.align(Alignment.BottomStart),
            color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(4.dp)) {
            GoogleMapsAttribution(Modifier.padding(2.dp), darkBackground = true, height = 12.dp)
        }
    }
}

@Composable
internal fun PhotoCredits(photo: PlacePhoto?, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    if (photo == null) return
    val credits: @Composable () -> Unit = {
        photo.credits.forEach { credit ->
            val uri = credit.uri?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (uri != null) TextButton(onClick = { uriHandler.openUri(uri) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text("Photo: ${credit.name}", style = MaterialTheme.typography.labelSmall)
            } else Text("Photo: ${credit.name}", style = MaterialTheme.typography.labelSmall)
        }
        if (photo.credits.isEmpty() && photo.attributionHtml.isNotBlank()) AndroidView(
            factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
            update = { it.text = HtmlCompat.fromHtml(photo.attributionHtml, HtmlCompat.FROM_HTML_MODE_LEGACY); it.setTextColor(textColor); it.setLinkTextColor(linkColor) },
        )
    }
    if (horizontal) Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) { credits() }
    else Column(modifier) { credits() }
}

@Composable
internal fun rememberPlaceThumbnail(id: String?, localUri: String, maps: MapViewModel?): PlacePhoto? {
    var photo by remember(id, localUri, maps) { mutableStateOf<PlacePhoto?>(null) }
    LaunchedEffect(id, localUri, maps) {
        if (id != null && localUri.isBlank()) photo = maps?.thumbnail(id)
    }
    return photo
}
