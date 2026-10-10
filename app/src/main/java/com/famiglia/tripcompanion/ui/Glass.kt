package com.famiglia.tripcompanion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal enum class GlassKind { Control, Card, Details, Dock }

/** Lightweight glass tint and edge lighting. No map snapshots, backdrop capture or blur of text. */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Control,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    focused: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val tint = when (kind) {
        GlassKind.Control, GlassKind.Dock -> if (dark) Color(0xFF16332D) else Color(0xFFEDF8F2)
        GlassKind.Card -> if (dark) Color(0xFF142A24) else Color(0xFFF1F7F3)
        GlassKind.Details -> if (dark) Color(0xFF2B2933) else Color(0xFFFAF8FC)
    }
    val opacity = when (kind) { GlassKind.Control -> 0.96f; GlassKind.Card -> 0.96f; GlassKind.Details, GlassKind.Dock -> 0.99f }
    val fill = Brush.verticalGradient(listOf(
        tint.copy(alpha = opacity),
        tint.copy(alpha = if (kind == GlassKind.Control) 0.94f else opacity),
    ))
    val rim = Brush.linearGradient(listOf(
        if (focused) colors.primary else if (dark) Color(0xFFB6E9DD).copy(alpha = 0.65f) else colors.primary.copy(alpha = 0.40f),
        colors.outline.copy(alpha = 0.22f),
        colors.primary.copy(alpha = if (focused) 0.8f else 0.32f),
    ))
    Surface(modifier, shape = shape, color = Color.Transparent, contentColor = colors.onSurface,
        border = BorderStroke(1.dp, rim), shadowElevation = if (kind == GlassKind.Control) 6.dp else 2.dp) {
        Box(Modifier.background(fill), propagateMinConstraints = true) { content() }
    }
}

@Composable
internal fun GlassAction(label: String, icon: ImageVector, click: () -> Unit) {
    GlassSurface(Modifier.clickable(role = Role.Button, onClick = click), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** An opaque, drawing-only surround joins the sheet and dock without intercepting map input. */
@Composable
internal fun mapLowerBackdrop(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Color(0xFF10241E) else Color(0xFFEDF5F0)
