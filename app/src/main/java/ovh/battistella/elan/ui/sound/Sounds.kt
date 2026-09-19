// Sons de l'app (port de `lib/sounds.ts`). Complètent les retours haptiques :
// « fire and forget », un échec (lecteur indisponible, son non chargé) est
// ignoré. 100 % local — le fichier audio est embarqué, aucun réseau.
package ovh.battistella.elan.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import ovh.battistella.elan.R

object Sounds {
    private var pool: SoundPool? = null
    private var restDoneId: Int = 0
    private var restDoneLoaded = false

    /**
     * Lecteur créé paresseusement et réutilisé. Usage « sonification
     * d'assistance » : le carillon se superpose à une musique en cours plutôt
     * que de la couper, et suit le volume des notifications/système.
     */
    @Synchronized
    private fun ensurePool(context: Context): SoundPool? {
        pool?.let { return it }
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val created = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
            created.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId == restDoneId && status == 0) restDoneLoaded = true
            }
            restDoneId = created.load(context.applicationContext, R.raw.rest_done, 1)
            pool = created
            created
        } catch (_: Exception) {
            null
        }
    }

    /** Carillon de fin de repos : invite à reprendre la série suivante. Best-effort. */
    fun restDone(context: Context) {
        try {
            val p = ensurePool(context) ?: return
            // Tout premier appel : le son peut encore se charger ; SoundPool
            // ignore alors la lecture et le suivant sonnera.
            if (restDoneLoaded) p.play(restDoneId, 1f, 1f, 1, 0, 1f)
        } catch (_: Exception) {
            // Lecture impossible : on se contente du retour haptique.
        }
    }
}
