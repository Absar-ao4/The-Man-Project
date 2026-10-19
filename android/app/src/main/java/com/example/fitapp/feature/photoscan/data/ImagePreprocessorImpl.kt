package com.example.fitapp.feature.photoscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.fitapp.feature.photoscan.domain.ImageMath
import com.example.fitapp.feature.photoscan.domain.ImagePreprocessor
import com.example.fitapp.feature.photoscan.domain.ScanImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the photo, turns it upright from its EXIF tag, shrinks it so the longest edge is at most 1024 px and
 * saves it as a quality 80 JPEG. A photo taken with our own camera request is deleted from the cache as soon as
 * it has been read, so no meal photo stays on the device or leaves it as anything but the shrunk upload.
 */
@Singleton
class ImagePreprocessorImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ImagePreprocessor {
        override suspend fun prepare(uri: String): ScanImage? =
            withContext(Dispatchers.IO) {
                val parsed = Uri.parse(uri)
                try {
                    runCatching { decode(parsed) }.getOrNull()
                } finally {
                    if (parsed.authority == "${context.packageName}.fileprovider") {
                        // FileProvider.delete removes the underlying cache file.
                        runCatching { context.contentResolver.delete(parsed, null, null) }
                    }
                }
            }

        private fun decode(uri: Uri): ScanImage? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply { inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

            val orientation =
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            val turn = ImageMath.exifTransform(orientation)

            val turnedWidth = if (ImageMath.swapsAxes(turn)) decoded.height else decoded.width
            val turnedHeight = if (ImageMath.swapsAxes(turn)) decoded.width else decoded.height
            val (targetWidth, targetHeight) = ImageMath.targetSize(turnedWidth, turnedHeight)

            val matrix =
                Matrix().apply {
                    postRotate(turn.degrees.toFloat())
                    if (turn.mirror) postScale(-1f, 1f)
                    postScale(targetWidth.toFloat() / turnedWidth, targetHeight.toFloat() / turnedHeight)
                }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val bytes =
                ByteArrayOutputStream().use { out ->
                    upright.compress(Bitmap.CompressFormat.JPEG, ImageMath.JPEG_QUALITY, out)
                    out.toByteArray()
                }
            if (upright !== decoded) decoded.recycle()
            upright.recycle()
            return ScanImage(bytes, ScanImage.JPEG)
        }
    }
