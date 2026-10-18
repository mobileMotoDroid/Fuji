package com.mobilemotodroid.fuji.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 画像を縮小して読み込む（端末の向きの情報も反映する）。 */
@Composable
fun rememberPhotoBitmap(path: String, maxSizePx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, maxSizePx) {
        value = withContext(Dispatchers.IO) { decodeScaled(path, maxSizePx)?.asImageBitmap() }
    }
    return bitmap
}

private fun decodeScaled(path: String, maxSizePx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSizePx || bounds.outHeight / (sample * 2) >= maxSizePx) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) bitmap
    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
}.getOrNull()

/** FR-ACT-06：写真の拡大表示。 */
@Composable
fun PhotoViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val bitmap = rememberPhotoBitmap(path, 2048)
            if (bitmap != null) {
                Image(bitmap, contentDescription = "写真", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
            }
        }
    }
}

@Composable
fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap = rememberPhotoBitmap(path, 300)
    Box(modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "写真", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("…")
        }
    }
}
