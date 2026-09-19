package ovh.battistella.elan.ui.navigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.tracking.LiveNotification

/** Route à ouvrir depuis l'intent de `MainActivity` : extra de notification ou lien profond. */
@RunWith(RobolectricTestRunner::class)
class OpenRouteTest {

    private fun launcher() = Intent(Intent.ACTION_MAIN)
    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun `lancement ordinaire - aucune route`() {
        assertNull(openRouteFor(launcher(), ActivityType.VELO, outingLive = false))
    }

    @Test
    fun `extra de notification - sortie du type courant, muscu, inconnu ignoré`() {
        val outing = launcher().putExtra(LiveNotification.EXTRA_OPEN_ROUTE, "outing")
        assertEquals("outing/course", openRouteFor(outing, ActivityType.COURSE, outingLive = true))
        // L'extra est consommé : une relecture de l'intent ne rejoue pas la navigation.
        assertFalse(outing.hasExtra(LiveNotification.EXTRA_OPEN_ROUTE))
        assertNull(openRouteFor(outing, ActivityType.COURSE, outingLive = true))

        val muscu = launcher().putExtra(LiveNotification.EXTRA_OPEN_ROUTE, "muscu")
        assertEquals("muscu", openRouteFor(muscu, ActivityType.VELO, outingLive = false))

        val other = launcher().putExtra(LiveNotification.EXTRA_OPEN_ROUTE, "poids")
        assertNull(openRouteFor(other, ActivityType.VELO, outingLive = false))
    }

    @Test
    fun `lien profond - type demandé hors sortie, type en cours sinon`() {
        assertEquals("outing/course", openRouteFor(view("elan://outing/course"), ActivityType.VELO, outingLive = false))
        assertEquals("outing/velo", openRouteFor(view("elan://outing/course"), ActivityType.VELO, outingLive = true))
        // Type inconnu ou absent : type courant du contrôleur.
        assertEquals("outing/marche", openRouteFor(view("elan://outing/xyz"), ActivityType.MARCHE, outingLive = false))
        assertEquals("outing/marche", openRouteFor(view("elan://outing"), ActivityType.MARCHE, outingLive = false))
        assertEquals("muscu", openRouteFor(view("elan://muscu"), ActivityType.VELO, outingLive = false))
    }

    @Test
    fun `lien profond - autre hôte, autre schéma ou autre action ignorés`() {
        assertNull(openRouteFor(view("elan://import/abc"), ActivityType.VELO, outingLive = false))
        assertNull(openRouteFor(view("https://outing/velo"), ActivityType.VELO, outingLive = false))
        assertNull(openRouteFor(Intent(Intent.ACTION_SEND, Uri.parse("elan://outing/velo")), ActivityType.VELO, outingLive = false))
    }
}
