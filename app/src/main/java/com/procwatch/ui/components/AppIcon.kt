package com.procwatch.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.procwatch.data.IconCache
import com.procwatch.ui.theme.Panel

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = IconCache.load(context, packageName)
    }

    val current = bitmap
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .background(Panel.SurfaceRaised, RoundedCornerShape(6.dp))
        )
    }
}
