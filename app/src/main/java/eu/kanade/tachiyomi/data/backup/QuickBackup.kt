package eu.kanade.tachiyomi.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import java.io.File

/**
 * One-tap backup straight into the public Downloads folder as a single .tachibk file.
 * Uses MediaStore on Android 10+ (no permission); on Android 9 it writes to the legacy Downloads
 * path (requires WRITE_EXTERNAL_STORAGE, which the caller must have granted first).
 */
object QuickBackup {

    /** Returns the target file name on success, or null if the destination couldn't be created. */
    fun toDownloads(context: Context): String? {
        val filename = BackupCreator.getFilename()
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            Uri.fromFile(File(dir, filename))
        }
        uri ?: return null
        BackupCreateJob.startNow(context, uri, BackupOptions())
        return filename
    }
}
