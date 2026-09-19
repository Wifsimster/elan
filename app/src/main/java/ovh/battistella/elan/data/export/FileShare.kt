// Partage d'un fichier du cache via la feuille de partage du système
// (FileProvider `${applicationId}.files`, dossier `cache/share/`). L'utilisateur
// choisit la destination (Drive, mail, Strava…) ; aucun appel réseau, fidèle
// à la promesse 100 % hors-ligne de l'app. Les exports contiennent le tracé
// GPS complet : on ne les laisse pas traîner dans le cache après le partage.
package ovh.battistella.elan.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileShare {

    /** Suffixe d'autorité du FileProvider (voir le manifeste et `res/xml/file_paths.xml`). */
    const val AUTHORITY_SUFFIX = ".files"

    /** Sous-dossier du cache exposé par le FileProvider. */
    const val SHARE_DIR = "share"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /** Dossier des fichiers à partager (créé au besoin). */
    fun shareDir(context: Context): File = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    /** URI `content://` d'un fichier du dossier partagé. */
    fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(context, authority(context), file)

    /** Ouvre la feuille de partage pour `file` (type MIME et titre du sélecteur donnés). */
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

    /**
     * Supprime tous les fichiers du dossier partagé. La feuille de partage ne
     * signale pas la fin de la lecture par l'app destinataire : la purge se fait
     * au lancement suivant (`StartupTasks`), quand plus aucun partage n'est en cours.
     */
    fun purgeShared(context: Context) {
        File(context.cacheDir, SHARE_DIR).listFiles()?.forEach { it.delete() }
    }
}
