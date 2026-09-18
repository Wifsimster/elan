package ovh.battistella.elan.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.testing.TestSupport

@RunWith(RobolectricTestRunner::class)
class BodyWeightRepositoryTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val weights get() = repos.bodyWeight

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `logBodyWeight aligne le poids du profil sur la pesée la plus récente`() = runTest {
        repos.settings.saveProfile(Profile(weightKg = 70.0, goal = TrainingGoal.FORCE, sex = Sex.F))
        weights.logBodyWeight(74.5, measuredAt = 2_000L)
        // Une pesée plus ANCIENNE ajoutée après ne devient pas la référence.
        weights.logBodyWeight(90.0, measuredAt = 1_000L)

        val profile = repos.settings.getProfile()
        assertEquals(74.5, profile.weightKg, 0.0)
        // Les autres champs du profil sont conservés.
        assertEquals(TrainingGoal.FORCE, profile.goal)
        assertEquals(Sex.F, profile.sex)
    }

    @Test
    fun `listBodyMeasurements trie par date puis id décroissants et respecte la limite`() = runTest {
        weights.logBodyWeight(70.0, 1_000L)
        weights.logBodyWeight(71.0, 3_000L)
        weights.logBodyWeight(72.0, 3_000L) // même date : le plus récent inséré d'abord
        weights.logBodyWeight(73.0, 2_000L)

        assertEquals(listOf(72.0, 71.0, 73.0, 70.0), weights.listBodyMeasurements().map { it.weightKg })
        assertEquals(listOf(72.0, 71.0), weights.listBodyMeasurements(limit = 2).map { it.weightKg })
        assertEquals(72.0, weights.latestBodyMeasurement()!!.weightKg, 0.0)
        assertEquals(72.0, repos.settings.getProfile().weightKg, 0.0)
    }

    @Test
    fun `deleteBodyMeasurement recale le profil sur la dernière pesée restante`() = runTest {
        weights.logBodyWeight(70.0, 1_000L)
        weights.logBodyWeight(75.0, 2_000L)
        val latest = weights.latestBodyMeasurement()!!
        assertEquals(75.0, repos.settings.getProfile().weightKg, 0.0)

        weights.deleteBodyMeasurement(latest.id)
        assertEquals(70.0, repos.settings.getProfile().weightKg, 0.0)

        // Plus aucune pesée : le profil n'est pas touché.
        weights.deleteBodyMeasurement(weights.latestBodyMeasurement()!!.id)
        assertNull(weights.latestBodyMeasurement())
        assertEquals(70.0, repos.settings.getProfile().weightKg, 0.0)
    }

    @Test
    fun `sans pesée le profil reste au défaut`() = runTest {
        assertNull(weights.latestBodyMeasurement())
        assertEquals(emptyList<Any>(), weights.listBodyMeasurements())
        assertEquals(Profile(), repos.settings.getProfile())
        assertNull(repos.settings.getSetting("profile"))
    }
}
