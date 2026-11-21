package com.widgetly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun YouTubeThumbnail(
    thumbnailUrl: String,
    videoId: String,
    modifier: Modifier = Modifier,
    placeholder: String = "▶"
) {
    val context = LocalContext.current
    val url = thumbnailUrl.takeIf { it.startsWith("https://") }
        ?: videoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    if (url == null) {
        Box(modifier.clip(RoundedCornerShape(10.dp))) { ThumbnailFallback(placeholder) }
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            loading = { ThumbnailFallback(placeholder) },
            error = { ThumbnailFallback(placeholder) }
        )
    }
}

@Composable
private fun ThumbnailFallback(placeholder: String) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF243447)),
        contentAlignment = Alignment.Center
    ) {
        Text(placeholder, color = Color(0xFF8FE3C0), fontSize = 18.sp)
    }
}
