package tachiyomi.core.common.storage

import android.content.Context
import androidx.core.net.toUri
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Jenkins").apply { if (!exists()) mkdirs() }
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
