package br.com.kindlelib.ui

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.kindlelib.model.Book
import br.com.kindlelib.model.BookFormat
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun hasStorageAccess(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun CoverImage(book: Book, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, book.coverPath, book.id) {
        value = book.coverPath?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(p, opts)
                    var sample = 1
                    val target = 480
                    while ((opts.outWidth / sample) > target || (opts.outHeight / sample) > target) sample *= 2
                    val opts2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    android.graphics.BitmapFactory.decodeFile(p, opts2)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val img = bmp
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = book.title.trim().ifEmpty { "?" }.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.format.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun BookFormat.label(): String = when (this) {
    BookFormat.EPUB -> "EPUB"
    BookFormat.MOBI -> "MOBI"
    BookFormat.AZW3 -> "AZW3"
    BookFormat.PDF -> "PDF"
    BookFormat.FB2 -> "FB2"
    BookFormat.TXT -> "TXT"
    BookFormat.CBZ -> "CBZ"
    BookFormat.CBR -> "CBR"
    BookFormat.OTHER -> "LIVRO"
}
