package com.famiglia.tripcompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.famiglia.tripcompanion.maps.MapLocation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class PlaceFeature { Reviews, StreetView }
private enum class FeatureAnchor { Expanded, Partial, Hidden }

/** Separate card, leaving the original place card mounted so its scroll and sheet position survive.
 * Only the handle moves this card; native panorama gestures never reach a sheet drag recognizer. */
@Composable
internal fun PlaceFeatureCard(feature: PlaceFeature, place: MapLocation, height: Dp, bottomClearance: Dp,
    dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val sheet = remember(feature, place.id) { AnchoredDraggableState(FeatureAnchor.Expanded) }
    val anchors = remember(height, bottomClearance, density) {
        DraggableAnchors {
            FeatureAnchor.Expanded at 0f
            FeatureAnchor.Partial at with(density) { ((height - bottomClearance).coerceAtLeast(1.dp) * 0.35f).toPx() }
            FeatureAnchor.Hidden at with(density) { height.toPx() }
        }
    }
    SideEffect { sheet.updateAnchors(anchors) }
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(dismiss)
    LaunchedEffect(sheet.settledValue) { if (sheet.settledValue == FeatureAnchor.Hidden) close() }
    BackHandler { dismiss() }
    val offset = sheet.offset.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
    val remaining = (height - with(density) { offset.toDp() }).coerceAtLeast(1.dp)
    val title = if (feature == PlaceFeature.Reviews) "Reviews" else "Street View"
    val expanded = sheet.targetValue == FeatureAnchor.Expanded
    Box(Modifier.fillMaxSize().testTag("place-feature-overlay")) {
        // This layer intentionally blocks the underlying map while a secondary card is open.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, onClick = dismiss).clearAndSetSemantics { })
        GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .offset { IntOffset(0, offset.roundToInt()) }.height(remaining)
            .testTag("place-feature-card").semantics { paneTitle = title },
            kind = GlassKind.Details, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxSize()) {
                // 48 dp handle target, separate from the panorama/scrolling content.
                Box(Modifier.fillMaxWidth().height(48.dp).testTag("feature-sheet-handle")
                    .semantics { contentDescription = "Drag $title card"; stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .anchoredDraggable(sheet, Orientation.Vertical)
                    .clickable { scope.launch { sheet.animateTo(if (expanded) FeatureAnchor.Partial else FeatureAnchor.Expanded, spring()) } },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.width(44.dp).height(5.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to place details", click = dismiss)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(if (feature == PlaceFeature.StreetView) "Near ${place.name}" else place.name,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    GlassIconButton(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        if (expanded) "Collapse $title" else "Expand $title") {
                        scope.launch { sheet.animateTo(if (expanded) FeatureAnchor.Partial else FeatureAnchor.Expanded, spring()) }
                    }
                    GlassIconButton(Icons.Default.Close, "Close $title", click = dismiss)
                }
                Column(Modifier.weight(1f).fillMaxWidth().testTag("feature-card-content"), content = content)
                Spacer(Modifier.height(minOf(bottomClearance, remaining)).fillMaxWidth().testTag("feature-dock-clearance"))
            }
        }
    }
}
