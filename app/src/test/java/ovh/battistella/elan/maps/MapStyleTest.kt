package ovh.battistella.elan.maps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.testing.TestSupport

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapStyleTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `isValidMapStyleUrl - vide ou HTTPS seulement`() {
        assertTrue(isValidMapStyleUrl(""))
        assertTrue(isValidMapStyleUrl("   "))
        assertTrue(isValidMapStyleUrl(OPENFREEMAP_STYLE_URL))
        assertTrue(isValidMapStyleUrl("HTTPS://tiles.example.org/style.json"))
        assertFalse(isValidMapStyleUrl("http://tiles.example.org/style.json"))
        assertFalse(isValidMapStyleUrl("tiles.example.org/style.json"))
        assertFalse(isValidMapStyleUrl("file:///sdcard/style.json"))
    }

    @Test
    fun `mapAttribution - OpenFreeMap ou OSM`() {
        assertEquals("© OpenFreeMap · OpenMapTiles · OpenStreetMap", mapAttribution(OPENFREEMAP_STYLE_URL))
        assertEquals("© OpenStreetMap", mapAttribution("https://tiles.mon-homelab.tld/style.json"))
    }

    @Test
    fun `OPENFREEMAP_STYLE_URL est le style liberty`() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", OPENFREEMAP_STYLE_URL)
    }

    @Test
    fun `styleUrl - vide par défaut, mis à jour par setStyleUrl (trim)`() = runTest {
        val repo = MapStyleRepository(repos.settings, CoroutineScope(Dispatchers.Unconfined))
        assertEquals("", repo.styleUrl.value)

        repo.setStyleUrl("  $OPENFREEMAP_STYLE_URL  ")
        advanceUntilIdle()
        assertEquals(OPENFREEMAP_STYLE_URL, repo.styleUrl.value)
        assertEquals(OPENFREEMAP_STYLE_URL, repos.settings.getSetting(SettingsRepository.Keys.MAP_STYLE_URL))

        repo.setStyleUrl("")
        advanceUntilIdle()
        assertEquals("", repo.styleUrl.value)
    }

    @Test
    fun `setStyleUrl - refuse le HTTP en clair sans rien écrire`() = runTest {
        val repo = MapStyleRepository(repos.settings, CoroutineScope(Dispatchers.Unconfined))
        val error = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.setStyleUrl("http://tiles.example.org/style.json") }
        }
        assertEquals("URL de style invalide : HTTPS requis.", error.message)
        assertEquals(null, repos.settings.getSetting(SettingsRepository.Keys.MAP_STYLE_URL))
    }

    @Test
    fun `une URL stockée invalide est ignorée à la lecture`() = runTest {
        // Valeur héritée / restaurée en dehors du setter : re-validation à la lecture.
        repos.settings.setMapStyleUrl("http://tiles.example.org/style.json")
        val repo = MapStyleRepository(repos.settings, CoroutineScope(Dispatchers.Unconfined))
        advanceUntilIdle()
        assertEquals("", repo.styleUrl.value)
    }
}
