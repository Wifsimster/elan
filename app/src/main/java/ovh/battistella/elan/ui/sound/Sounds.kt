package ovh.battistella.elan.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import ovh.battistella.elan.R

object Sounds {
    private var pool: SoundPool? = null
    private var restDoneId: Int = 0
    private var restDoneLoaded = false

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

    fun restDone(context: Context) {
        try {
            val p = ensurePool(context) ?: return
            if (restDoneLoaded) p.play(restDoneId, 1f, 1f, 1, 0, 1f)
        } catch (_: Exception) {
        }
    }
}
