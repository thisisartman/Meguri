package io.github.thisisartman.collectibles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.photo.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture loaded at [maxPx] on its long side; null while loading or when it can't be read. */
@Composable
private fun rememberPicture(uri: String, maxPx: Int): Pair<Bitmap?, Boolean> {
    val context = LocalContext.current
    var bmp by remember(uri, maxPx) { mutableStateOf<Bitmap?>(null) }
    var done by remember(uri, maxPx) { mutableStateOf(false) }
    LaunchedEffect(uri, maxPx) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                // A downloaded card picture is a plain file (#53); everything else goes through PhotoStore
                if (uri.startsWith("file:")) android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(java.io.File(uri.removePrefix("file:"))))
                else PhotoStore.thumbnail(context, uri, maxPx)
            }.getOrNull()
        }
        done = true
    }
    return bmp to done
}

/**
 * A photo at card size, whole and uncropped (owner, 2026-11-08: "enlarge the image so it is clearer, with an option
 * for fullscreen"). Tapping it, or the fullscreen button, opens [FullscreenPhoto].
 */
@Composable
fun LargePhoto(uri: String, modifier: Modifier = Modifier, onFullscreen: () -> Unit) {
    val (bmp, done) = rememberPicture(uri, 1200)
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> Image(
                bmp.asImageBitmap(), contentDescription = stringResource(R.string.photo_view_fullscreen),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().pointerInput(uri) { detectTapGestures(onTap = { onFullscreen() }) },
            )
            done -> Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = Color.Gray)
            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        if (bmp != null) FilledTonalIconButton(
            onClick = onFullscreen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) { Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = stringResource(R.string.photo_view_fullscreen)) }
    }
}

/** The photo on black, the whole screen: pinch to zoom, drag to look around, double-tap to zoom in or out. */
@Composable
fun FullscreenPhoto(uri: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val (bmp, done) = rememberPicture(uri, 2400)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                bmp != null -> Image(
                    bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .transformable(state)
                        .pointerInput(uri) {
                            detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
                        },
                )
                done -> Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = Color.Gray)
                else -> CircularProgressIndicator(color = Color.White)
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_view_close)) }
        }
    }
}
