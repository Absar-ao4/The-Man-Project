package com.example.fitapp.feature.account.data

import android.content.Context
import androidx.core.content.FileProvider
import com.example.fitapp.feature.account.domain.ShareableFile
import java.io.File

/** Where reports and exports are written: the app's cache, in a folder the file provider can share. */
internal object FileExports {
    private const val DIR = "exports"

    fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Only the latest export is kept, so old reports and data exports do not pile up on the phone. */
    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    fun shareable(
        context: Context,
        file: File,
        mimeType: String,
    ): ShareableFile = ShareableFile(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString(), mimeType, file.name)
}