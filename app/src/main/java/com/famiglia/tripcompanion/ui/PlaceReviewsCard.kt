package com.famiglia.tripcompanion.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.famiglia.tripcompanion.maps.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RatingLink(details: PlaceDetails, click: () -> Unit) {
    val rating = details.rating ?: return
    GlassSurface(Modifier.fillMaxWidth().testTag("map-reviews")
        .clickable(role = Role.Button, onClick = click)
        .semantics { contentDescription = "Read reviews" }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("★ $rating" + (details.ratingCount?.let { " · $it ratings" } ?: ""),
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun PlaceReviewsCard(place: MapLocation, state: MapSearchState, model: MapViewModel) {
    val context = LocalContext.current
    fun open(uri: android.net.Uri) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app is available for this link.", Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("place-reviews-content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoogleMapsAttribution()
        state.details?.rating?.let { rating ->
            Text("★ $rating" + (state.details.ratingCount?.let { " · $it ratings" } ?: ""), style = MaterialTheme.typography.headlineMedium)
        }
        Text("Google-selected reviews", style = MaterialTheme.typography.titleMedium)
        Text("A selection of up to five reviews, chosen by relevance. These may not be the latest reviews.",
            style = MaterialTheme.typography.bodyMedium)
        if (state.reviewsBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading reviews…") }
        state.reviewsMessage?.let { Text(it); TextButton(onClick = { model.loadReviews(force = true) }) { Text("Retry reviews") } }
        state.reviews?.let { result ->
            if (result.reviews.isEmpty()) Text("No written reviews are available here. You can check Google Maps for more information.", Modifier.testTag("reviews-empty"))
            result.reviews.forEachIndexed { index, review ->
                GlassSurface(Modifier.fillMaxWidth().testTag("place-review-$index"), kind = GlassKind.Card, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val authorLink = PlaceLinks.website(review.authorUri)
                            var photoFailed by remember(review.authorPhotoUri) { mutableStateOf(false) }
                            Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(contentAlignment = Alignment.Center) { Text(review.author.take(1), style = MaterialTheme.typography.titleLarge) }
                                }
                                if (!photoFailed && PlaceLinks.website(review.authorPhotoUri) != null) AsyncImage(
                                    ImageRequest.Builder(context).data(review.authorPhotoUri)
                                        .diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED).build(),
                                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    onError = { photoFailed = true })
                            }
                            Column(Modifier.weight(1f)) {
                                if (authorLink != null) TextButton(onClick = { open(authorLink) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(review.author, style = MaterialTheme.typography.titleMedium)
                                } else Text(review.author, style = MaterialTheme.typography.titleMedium)
                                val time = review.relativeTime.ifBlank { review.publishTime?.let { value ->
                                    runCatching { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrNull()
                                }.orEmpty() }
                                if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        review.rating?.let { Text("★ $it / 5", style = MaterialTheme.typography.titleMedium) }
                        if (review.text.isNotBlank()) Text(review.text, style = MaterialTheme.typography.bodyLarge)
                        else Text("Rating without written text", style = MaterialTheme.typography.bodyMedium)
                        if (review.translated) Text("Translated by Google", style = MaterialTheme.typography.bodySmall)
                        if (review.attributionHtml.isNotBlank()) PlaceAttributions(listOf(review.attributionHtml))
                        PlaceLinks.website(review.flagUri)?.let { uri -> TextButton(onClick = { open(uri) }) { Text("Report review") } }
                    }
                }
            }
            PlaceAttributions(result.attributions)
            TextButton(onClick = { model.loadReviews(force = true) }, enabled = !state.reviewsBusy) { Text("Refresh reviews") }
        }
        GlassAction("View all in Google Maps", Icons.AutoMirrored.Outlined.OpenInNew, modifier = Modifier.testTag("reviews-open-maps")) { open(PlaceLinks.view(place)) }
        Text("Reviews load online. Your saved notes stay on your device.", style = MaterialTheme.typography.bodySmall)
    }
}
