package ovh.battistella.elan.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileShare {

    const val AUTHORITY_SUFFIX = ".files"

    const val SHARE_DIR = "share"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun shareDir(context: Context): File = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(context, authority(context), file)

    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun purgeShared(context: Context) {
        File(context.cacheDir, SHARE_DIR).listFiles()?.forEach { it.delete() }
    }
}
