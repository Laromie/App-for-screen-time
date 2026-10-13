package com.laromie.rostok.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(drawable: Drawable?, size: Dp = 40.dp) {
    val bitmap = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Карточка с просьбой выдать разрешение. */
@Composable
fun PermissionCard(title: String, text: String, button: String, onClick: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onClick) { Text(button) }
    }
}
