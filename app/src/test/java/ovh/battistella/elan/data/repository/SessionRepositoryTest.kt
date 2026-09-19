package ovh.battistella.elan.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.testing.TestSupport.muscuSetInput
import ovh.battistella.elan.testing.TestSupport.trackPointInput
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transposition de `__tests__/lib/db.test.ts`, cas par cas, sur la vraie base
 * Room en mémoire — puis les cas que le harnais Jest ne couvrait pas.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val sessions get() = repos.sessions
    private val snapshot get() = repos.snapshot

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Le jeu de données du `beforeAll` d'origine : une sortie vélo + une séance muscu + réglages. */
    private suspend fun seedLegacyFixture(): Pair<Long, Long> {
        val veloId = sessions.createSession(ActivityType.VELO, 1_700_000_000_000L)
        sessions.updateSession(
            veloId,
            SessionUpdate {
                endedAt = 1_700_003_600_000L
                durationSec = 3600
                distanceM = 30_000.0
                avgSpeedKmh = 30.0
                maxSpeedKmh = 55.0
                elevationGainM = 400.0
                calories = 700.0
            },
        )
        sessions.insertTrackPoints(
            veloId,
            listOf(
                trackPointInput(ts = 1_700_000_000_000L, speedKmh = 0.0, hr = 110.0, cadence = null),
                trackPointInput(ts = 1_700_000_010_000L, lat = 48.857, lon = 2.3525, altitude = 36.0, hr = 120.0, cadence = 85.0),
            ),
        )

        val muscuId = sessions.createSession(ActivityType.MUSCU, 1_700_100_000_000L)
        sessions.updateSession(muscuId, SessionUpdate { endedAt = 1_700_103_000_000L; durationSec = 3000 })
        sessions.replaceMuscuSets(
            muscuId,
            listOf(
                muscuSetInput("Goblet squat", 1, 10, 20.0),
                muscuSetInput("Goblet squat", 2, 9, 22.0),
            ),
        )

        repos.settings.setSetting("profile", """{"weightKg":78,"maxHr":188}""")
        repos.settings.setSetting("backup_s3", "SECRET-CREDENTIALS")
        repos.settings.setSetting("backup_last", """{"ok":true}""")
        return veloId to muscuId
    }

    private fun importedRow(externalId: String) = ImportedSession(
        type = ActivityType.VELO,
        startedAt = 1_700_500_000_000L,
        endedAt = 1_700_503_600_000L,
        durationSec = 3600,
        movingTimeSec = 3400,
        notes = "Importé depuis Strava",
        avgHr = 140.0,
        maxHr = 165.0,
        distanceM = 25_000.0,
        avgSpeedKmh = 25.0,
        maxSpeedKmh = 48.0,
        elevationGainM = 320.0,
        avgCadence = 85.0,
        maxCadence = 100.0,
        calories = 600.0,
        source = "strava",
        externalId = externalId,
    )

    // ---- exportAll ---------------------------------------------------------

    @Test
    fun `exportAll exporte séances points et séries et exclut les réglages secrets`() = runTest {
        seedLegacyFixture()
        val snap = snapshot.exportAll()
        assertEquals(2, snap.sessions.size)
        assertEquals(2, snap.trackPoints.size)
        assertEquals(2, snap.muscuSets.size)

        val keys = snap.settings.map { it.key }
        assertTrue("profile" in keys)
        assertFalse("backup_s3" in keys)
        assertFalse("backup_last" in keys)
    }

    // ---- round-trip export → clear → import --------------------------------

    @Test
    fun `round-trip restaure des données identiques ids préservés`() = runTest {
        seedLegacyFixture()
        val before = snapshot.exportAll().normalized()

        sessions.clearAllData()
        val cleared = snapshot.exportAll()
        assertEquals(0, cleared.sessions.size)
        assertEquals(0, cleared.trackPoints.size)
        assertEquals(0, cleared.muscuSets.size)

        snapshot.importAll(before)
        val after = snapshot.exportAll().normalized()
        assertEquals(before, after)
    }

    @Test
    fun `les points et séries restent rattachés à leur séance après restauration`() = runTest {
        seedLegacyFixture()
        val before = snapshot.exportAll()
        sessions.clearAllData()
        snapshot.importAll(before)

        val snap = snapshot.exportAll()
        val velo = snap.sessions.first { it.type == ActivityType.VELO }
        val muscu = snap.sessions.first { it.type == ActivityType.MUSCU }
        assertEquals(2, sessions.getTrackPoints(velo.id).size)
        assertEquals(2, sessions.getMuscuSets(muscu.id).size)
    }

    @Test
    fun `importAll ignore les clés exclues et remplace les données existantes`() = runTest {
        seedLegacyFixture()
        val snap = snapshot.exportAll()
        // Une sauvegarde falsifiée tente d'injecter un fond de carte et une config S3.
        val tampered = snap.copy(
            settings = snap.settings + listOf(
                SettingEntry("map_style_url", "https://evil.example/style.json"),
                SettingEntry("backup_s3", "INJECTED"),
            ),
        )
        snapshot.importAll(tampered)
        assertNull(repos.settings.getSetting("map_style_url"))
        assertEquals("SECRET-CREDENTIALS", repos.settings.getSetting("backup_s3"))
        assertEquals("""{"weightKg":78,"maxHr":188}""", repos.settings.getSetting("profile"))
        // Pas de doublon : la base a été vidée avant réinsertion.
        assertEquals(2, snapshot.exportAll().sessions.size)
    }

    @Test
    fun `importAll accepte une sauvegarde sans journal de poids`() = runTest {
        repos.bodyWeight.logBodyWeight(80.0, 1_000L)
        val snap = snapshot.exportAll().copy(bodyMeasurements = null)
        snapshot.importAll(snap)
        assertEquals(emptyList<Any>(), repos.bodyWeight.listBodyMeasurements())
    }

    // ---- insertImportedSession -------------------------------------------

    @Test
    fun `insertImportedSession insère la première fois puis signale un doublon`() = runTest {
        val first = sessions.insertImportedSession(importedRow("strava-abc123"), listOf(trackPointInput()))
        assertTrue(first is ImportResult.Imported)

        val second = sessions.insertImportedSession(importedRow("strava-abc123"), listOf(trackPointInput()))
        assertEquals(ImportResult.Duplicate, second)

        val other = sessions.insertImportedSession(importedRow("strava-def456"), emptyList())
        assertTrue(other is ImportResult.Imported)

        // Le doublon n'a rien écrit : une seule séance abc123, un seul point.
        assertEquals(2, snapshot.exportAll().sessions.size)
        assertEquals(1, sessions.getTrackPoints((first as ImportResult.Imported).id).size)
        assertEquals("strava", sessions.getSession(first.id)?.source)
    }

    // ---- sessionRecords ----------------------------------------------------

    @Test
    fun `un chrono oublié à l'arrêt ne rafle pas le record de durée vélo`() = runTest {
        // Sortie A : 1 h roulée d'affilée (temps en mouvement = temps total).
        val a = sessions.createSession(ActivityType.VELO, 1_800_000_000_000L)
        sessions.updateSession(
            a,
            SessionUpdate { endedAt = 1_800_003_600_000L; durationSec = 3600; movingTimeSec = 3600; distanceM = 30_000.0 },
        )
        // Sortie B : 3 h de temps total mais 10 min en mouvement.
        val b = sessions.createSession(ActivityType.VELO, 1_800_100_000_000L)
        sessions.updateSession(
            b,
            SessionUpdate { endedAt = 1_800_110_800_000L; durationSec = 10_800; movingTimeSec = 600; distanceM = 4_000.0 },
        )

        val recA = sessions.sessionRecords(sessions.getSession(a)!!)
        val recB = sessions.sessionRecords(sessions.getSession(b)!!)
        assertTrue(recA.any { it.kind == RecordKind.DURATION })
        assertFalse(recB.any { it.kind == RecordKind.DURATION })
    }

    @Test
    fun `sessionRecords distingue les portées all et year`() = runTest {
        val zone = ZoneId.systemDefault()
        fun at(date: LocalDate) = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

        // 2023 : 50 km. 2024 : 40 km puis 30 km. 2024-30 km est record de rien ;
        // 2024-40 km est record de l'année, pas de tout l'historique.
        val old = sessions.createSession(ActivityType.VELO, at(LocalDate.of(2023, 6, 1)))
        sessions.updateSession(old, SessionUpdate { endedAt = at(LocalDate.of(2023, 6, 1)) + 1; durationSec = 100; distanceM = 50_000.0 })
        val y40 = sessions.createSession(ActivityType.VELO, at(LocalDate.of(2024, 3, 1)))
        sessions.updateSession(y40, SessionUpdate { endedAt = at(LocalDate.of(2024, 3, 1)) + 1; durationSec = 200; distanceM = 40_000.0 })
        val y30 = sessions.createSession(ActivityType.VELO, at(LocalDate.of(2024, 5, 1)))
        sessions.updateSession(y30, SessionUpdate { endedAt = at(LocalDate.of(2024, 5, 1)) + 1; durationSec = 50; distanceM = 30_000.0 })

        assertEquals(
            listOf(SessionRecord(RecordKind.DISTANCE, RecordScope.ALL), SessionRecord(RecordKind.DURATION, RecordScope.YEAR)),
            sessions.sessionRecords(sessions.getSession(old)!!),
        )
        assertEquals(
            listOf(SessionRecord(RecordKind.DISTANCE, RecordScope.YEAR), SessionRecord(RecordKind.DURATION, RecordScope.ALL)),
            sessions.sessionRecords(sessions.getSession(y40)!!),
        )
        assertEquals(emptyList<SessionRecord>(), sessions.sessionRecords(sessions.getSession(y30)!!))
    }

    @Test
    fun `sessionRecords ignore les métriques absentes et les séances en cours`() = runTest {
        // Muscu : durée seule ; une séance en cours plus longue ne compte pas.
        val m = sessions.createSession(ActivityType.MUSCU, 1_800_000_000_000L)
        sessions.updateSession(m, SessionUpdate { endedAt = 1_800_003_000_000L; durationSec = 3000 })
        val inProgress = sessions.createSession(ActivityType.MUSCU, 1_800_100_000_000L)
        sessions.updateSession(inProgress, SessionUpdate { durationSec = 9000 })

        assertEquals(
            listOf(SessionRecord(RecordKind.DURATION, RecordScope.ALL)),
            sessions.sessionRecords(sessions.getSession(m)!!),
        )
        // Vélo sans distance ni dénivelé ni vitesse : seule la durée concourt.
        val v = sessions.createSession(ActivityType.VELO, 1_800_200_000_000L)
        sessions.updateSession(v, SessionUpdate { endedAt = 1_800_203_000_000L; durationSec = 3000 })
        assertEquals(
            listOf(SessionRecord(RecordKind.DURATION, RecordScope.ALL)),
            sessions.sessionRecords(sessions.getSession(v)!!),
        )
    }

    // ---- statsBetween & tonnageBetween ------------------------------------

    private val T = 2_000_000_000_000L

    private suspend fun seedStats() {
        val v1 = sessions.createSession(ActivityType.VELO, T)
        sessions.updateSession(v1, SessionUpdate { endedAt = T + 100; durationSec = 100; distanceM = 10_000.0 })
        val v2 = sessions.createSession(ActivityType.VELO, T + 1000)
        sessions.updateSession(v2, SessionUpdate { endedAt = T + 1100; durationSec = 100; distanceM = 5_000.0 })
        val m1 = sessions.createSession(ActivityType.MUSCU, T + 2000)
        sessions.updateSession(m1, SessionUpdate { endedAt = T + 2100; durationSec = 100 })
        sessions.replaceMuscuSets(m1, listOf(muscuSetInput("Squat", 1, 10, 20.0), muscuSetInput("Squat", 2, 8, 30.0)))
        // Séance en cours : ne doit jamais être comptée.
        sessions.createSession(ActivityType.VELO, T + 3000)
    }

    @Test
    fun `statsBetween sans filtre compte toutes les séances terminées de la fenêtre`() = runTest {
        seedStats()
        val s = sessions.statsBetween(T, T + 10_000)
        assertEquals(3, s.sessionCount)
        assertEquals(15_000.0, s.totalDistanceM, 0.0)
        assertEquals(300, s.totalDurationSec)
    }

    @Test
    fun `statsBetween filtre vélo ignore la muscu et la séance en cours`() = runTest {
        seedStats()
        val s = sessions.statsBetween(T, T + 10_000, ActivityType.VELO)
        assertEquals(2, s.sessionCount)
        assertEquals(15_000.0, s.totalDistanceM, 0.0)
    }

    @Test
    fun `statsBetween filtre muscu une séance sans distance`() = runTest {
        seedStats()
        val s = sessions.statsBetween(T, T + 10_000, ActivityType.MUSCU)
        assertEquals(1, s.sessionCount)
        assertEquals(0.0, s.totalDistanceM, 0.0)
    }

    @Test
    fun `statsBetween exclut la borne haute`() = runTest {
        seedStats()
        assertEquals(1, sessions.statsBetween(T, T + 1000, ActivityType.VELO).sessionCount)
    }

    @Test
    fun `statsBetween utilise le temps en mouvement quand il est connu`() = runTest {
        val v = sessions.createSession(ActivityType.VELO, T)
        sessions.updateSession(v, SessionUpdate { endedAt = T + 100; durationSec = 3600; movingTimeSec = 1200; calories = 250.0 })
        val s = sessions.statsSince(T - 1)
        assertEquals(1200, s.totalDurationSec)
        assertEquals(250.0, s.totalCalories, 0.0)
        // Fenêtre vide : zéros, pas d'exception.
        assertEquals(ovh.battistella.elan.domain.PeriodStats(0, 0, 0.0, 0.0), sessions.statsBetween(0, 1))
    }

    @Test
    fun `tonnageBetween somme reps x charge des séances muscu de la fenêtre`() = runTest {
        seedStats()
        assertEquals(440.0, sessions.tonnageBetween(T, T + 10_000), 0.0)
        assertEquals(0.0, sessions.tonnageBetween(T, T + 2000), 0.0)
    }

    // ---- exerciseHistory ---------------------------------------------------

    @Test
    fun `exerciseHistory topReps vient de la série la plus lourde pas de l'échauffement`() = runTest {
        val id = sessions.createSession(ActivityType.MUSCU, 2_100_000_000_000L)
        sessions.updateSession(id, SessionUpdate { endedAt = 2_100_003_000_000L; durationSec = 3000 })
        sessions.replaceMuscuSets(
            id,
            listOf(
                muscuSetInput("Développé couché", 1, 15, 20.0, Difficulty.FACILE),
                muscuSetInput("Développé couché", 2, 3, 100.0, Difficulty.FACILE),
            ),
        )

        val hist = sessions.exerciseHistory("Développé couché")
        assertEquals(1, hist.size)
        assertEquals(100.0, hist[0].maxWeightKg, 0.0)
        assertEquals(3, hist[0].topReps)
        assertEquals(Difficulty.FACILE, hist[0].difficulty)
        assertEquals(2, hist[0].sets)
        assertEquals(15 * 20.0 + 3 * 100.0, hist[0].volume, 0.0)
        assertEquals(id, hist[0].sessionId)
    }

    @Test
    fun `exerciseHistory ordonne du plus ancien au plus récent et ignore les séances en cours`() = runTest {
        val late = sessions.saveMuscuSession(null, 2_100_100_000_000L, SessionUpdate { endedAt = 2_100_100_000_001L }, listOf(muscuSetInput("Squat", 1, 8, 40.0)))
        val early = sessions.saveMuscuSession(null, 2_100_000_000_000L, SessionUpdate { endedAt = 2_100_000_000_001L }, listOf(muscuSetInput("Squat", 1, 10, 30.0, Difficulty.DUR)))
        // En cours (endedAt NULL) : exclue.
        sessions.saveMuscuSession(null, 2_100_200_000_000L, SessionUpdate(), listOf(muscuSetInput("Squat", 1, 5, 99.0)))

        val hist = sessions.exerciseHistory("Squat")
        assertEquals(listOf(early, late), hist.map { it.sessionId })
        assertEquals(listOf(Difficulty.DUR, null), hist.map { it.difficulty })
    }

    // ---- listSessions ------------------------------------------------------

    @Test
    fun `listSessions neutralise les jokers LIKE de la saisie`() = runTest {
        val withNote = sessions.createSession(ActivityType.MUSCU, 2_200_000_000_000L)
        sessions.updateSession(withNote, SessionUpdate { endedAt = 2_200_000_300_000L; notes = "100% effort" })
        val other = sessions.createSession(ActivityType.VELO, 2_200_000_400_000L)
        sessions.updateSession(other, SessionUpdate { endedAt = 2_200_000_700_000L; distanceM = 1000.0 })

        val hits = sessions.listSessions(ListSessionsOptions(search = "%"))
        assertEquals(listOf(withNote), hits.map { it.id })
        assertEquals(emptyList<Long>(), sessions.listSessions(ListSessionsOptions(search = "_")).map { it.id })
        assertEquals(emptyList<Long>(), sessions.listSessions(ListSessionsOptions(search = "\\")).map { it.id })
    }

    @Test
    fun `listSessions renseigne setCount et exerciseCount`() = runTest {
        val muscuId = sessions.createSession(ActivityType.MUSCU, 1_700_200_000_000L)
        sessions.updateSession(muscuId, SessionUpdate { endedAt = 1_700_200_300_000L })
        sessions.replaceMuscuSets(
            muscuId,
            listOf(
                muscuSetInput("Squat", 1, 5, 60.0),
                muscuSetInput("Squat", 2, 5, 60.0),
                muscuSetInput("Développé couché", 1, 8, 40.0),
            ),
        )
        val veloId = sessions.createSession(ActivityType.VELO, 1_700_200_400_000L)
        sessions.updateSession(veloId, SessionUpdate { endedAt = 1_700_200_700_000L })

        val rows = sessions.listSessions(ListSessionsOptions(limit = 50))
        val muscu = rows.first { it.id == muscuId }
        val velo = rows.first { it.id == veloId }
        assertEquals(3, muscu.setCount)
        assertEquals(2, muscu.exerciseCount)
        assertEquals(0, velo.setCount)
        assertEquals(0, velo.exerciseCount)
        // Une ligne par séance malgré la jointure, la plus récente d'abord.
        assertEquals(listOf(veloId, muscuId), rows.map { it.id })
    }

    @Test
    fun `listSessions filtre par type fenêtre et recherche sur notes et exercices`() = runTest {
        val v1 = sessions.createSession(ActivityType.VELO, 1000)
        sessions.updateSession(v1, SessionUpdate { endedAt = 1500; notes = "Col du Galibier" })
        val v2 = sessions.createSession(ActivityType.VELO, 2000)
        sessions.updateSession(v2, SessionUpdate { endedAt = 2500 })
        val m1 = sessions.saveMuscuSession(null, 3000, SessionUpdate { endedAt = 3500 }, listOf(muscuSetInput("Goblet squat")))
        sessions.createSession(ActivityType.COURSE, 4000) // en cours

        assertEquals(listOf(m1, v2, v1), sessions.listSessions().map { it.id })
        assertEquals(listOf(v2, v1), sessions.listSessions(ListSessionsOptions(type = ActivityType.VELO)).map { it.id })
        assertEquals(listOf(v2), sessions.listSessions(ListSessionsOptions(fromMs = 2000, toMs = 3000)).map { it.id })
        assertEquals(listOf(v1), sessions.listSessions(ListSessionsOptions(search = "galibier")).map { it.id })
        assertEquals(listOf(m1), sessions.listSessions(ListSessionsOptions(search = "  goblet ")).map { it.id })
        assertEquals(listOf(m1), sessions.listSessions(ListSessionsOptions(search = "MUSCU")).map { it.id })
        // Recherche vide ou blanche = pas de filtre.
        assertEquals(3, sessions.listSessions(ListSessionsOptions(search = "   ")).size)
    }

    @Test
    fun `listSessions pagine avec limit et offset`() = runTest {
        val ids = (1..5).map { i ->
            sessions.createSession(ActivityType.VELO, i * 1000L).also { id ->
                sessions.updateSession(id, SessionUpdate { endedAt = i * 1000L + 1 })
            }
        }
        assertEquals(listOf(ids[4], ids[3]), sessions.listSessions(ListSessionsOptions(limit = 2)).map { it.id })
        assertEquals(listOf(ids[2], ids[1]), sessions.listSessions(ListSessionsOptions(limit = 2, offset = 2)).map { it.id })
        assertEquals(listOf(ids[0]), sessions.listSessions(ListSessionsOptions(limit = 2, offset = 4)).map { it.id })
    }

    // ---- cycle de vie d'une séance ----------------------------------------

    @Test
    fun `createSession puis updateSession n'écrit que les colonnes affectées`() = runTest {
        val id = sessions.createSession(ActivityType.COURSE, 42)
        val created = sessions.getSession(id)!!
        assertEquals(ActivityType.COURSE, created.type)
        assertNull(created.endedAt)
        assertEquals(0, created.durationSec)

        sessions.updateSession(id, SessionUpdate { notes = "a"; calories = 12.5 })
        sessions.updateSession(id, SessionUpdate { notes = null })
        sessions.updateSession(id, SessionUpdate()) // no-op
        val s = sessions.getSession(id)!!
        assertNull(s.notes)
        assertEquals(12.5, s.calories!!, 0.0)
        assertNull(sessions.getSession(999))
    }

    @Test
    fun `finalizeSession est idempotent et remplace les points flushés`() = runTest {
        val id = sessions.createSession(ActivityType.VELO, 1_000L)
        // Flush incrémental pendant la sortie.
        sessions.insertTrackPoints(id, listOf(trackPointInput(ts = 1_000L), trackPointInput(ts = 2_000L)))

        val final = (1..250).map { trackPointInput(ts = 1_000L + it * 1000L) } // > 2 lots de 100
        val patch = SessionUpdate { endedAt = 300_000L; durationSec = 250; distanceM = 1234.0 }
        sessions.finalizeSession(id, patch, final)
        sessions.finalizeSession(id, patch, final) // réessai

        val points = sessions.getTrackPoints(id)
        assertEquals(250, points.size)
        assertEquals(final.map { it.ts }, points.map { it.ts })
        assertEquals(300_000L, sessions.getSession(id)!!.endedAt)
        assertEquals(1234.0, sessions.getSession(id)!!.distanceM!!, 0.0)
        assertEquals(1, snapshot.exportAll().sessions.size)
    }

    @Test
    fun `listInProgressSessions renvoie les séances non terminées plus ancienne d'abord`() = runTest {
        val a = sessions.createSession(ActivityType.VELO, 2000)
        val b = sessions.createSession(ActivityType.MUSCU, 1000)
        val done = sessions.createSession(ActivityType.VELO, 500)
        sessions.updateSession(done, SessionUpdate { endedAt = 600 })

        assertEquals(listOf(b, a), sessions.listInProgressSessions().map { it.id })
        assertEquals(listOf(a), sessions.listInProgressSessions(ActivityType.VELO).map { it.id })
        assertEquals(emptyList<Long>(), sessions.listInProgressSessions(ActivityType.COURSE).map { it.id })
    }

    @Test
    fun `deleteSession supprime points et séries en cascade`() = runTest {
        val v = sessions.createSession(ActivityType.VELO, 1000)
        sessions.insertTrackPoints(v, listOf(trackPointInput()))
        val m = sessions.saveMuscuSession(null, 2000, SessionUpdate { endedAt = 2500 }, listOf(muscuSetInput()))

        sessions.deleteSession(v)
        sessions.deleteSession(m)
        val snap = snapshot.exportAll()
        assertEquals(0, snap.sessions.size)
        assertEquals(0, snap.trackPoints.size)
        assertEquals(0, snap.muscuSets.size)
    }

    @Test
    fun `changeSessionType refuse la muscu et le même type puis ré-estime les calories`() = runTest {
        val m = sessions.saveMuscuSession(null, 1000, SessionUpdate { endedAt = 4600; durationSec = 3600 }, emptyList())
        assertNull(sessions.changeSessionType(m, ActivityType.VELO))

        val v = sessions.createSession(ActivityType.VELO, 1000)
        sessions.updateSession(v, SessionUpdate { endedAt = 4600; durationSec = 3600; avgSpeedKmh = 20.0; avgCadence = 80.0; maxCadence = 95.0; calories = 1.0 })
        assertNull(sessions.changeSessionType(v, ActivityType.VELO))
        assertNull(sessions.changeSessionType(v, ActivityType.MUSCU))
        assertNull(sessions.changeSessionType(999, ActivityType.COURSE))

        repos.settings.saveProfile(Profile(weightKg = 80.0))
        val changed = sessions.changeSessionType(v, ActivityType.COURSE)!!
        assertEquals(ActivityType.COURSE, changed.type)
        assertNull(changed.avgCadence)
        assertNull(changed.maxCadence)
        assertTrue(changed.calories!! > 1.0)
        // Persisté en base à l'identique, en un seul UPDATE.
        assertEquals(changed, sessions.getSession(v))
    }

    // ---- musculation -------------------------------------------------------

    @Test
    fun `saveMuscuSession crée ou réutilise la séance et remplace les séries`() = runTest {
        val id = sessions.saveMuscuSession(
            null, 1000,
            SessionUpdate { endedAt = 2000; durationSec = 1 },
            listOf(muscuSetInput("A", 2, 8, 10.0), muscuSetInput("A", 1, 10, 10.0, Difficulty.MOYEN)),
        )
        val s = sessions.getSession(id)!!
        assertEquals(ActivityType.MUSCU, s.type)
        assertEquals(2000L, s.endedAt)
        // Tri par setIndex puis id.
        assertEquals(listOf(1, 2), sessions.getMuscuSets(id).map { it.setIndex })
        assertEquals(Difficulty.MOYEN, sessions.getMuscuSets(id)[0].difficulty)

        val same = sessions.saveMuscuSession(id, 1000, SessionUpdate(), listOf(muscuSetInput("B", 1, 5, 50.0)))
        assertEquals(id, same)
        assertEquals(listOf("B"), sessions.getMuscuSets(id).map { it.exercise })
        assertEquals(1, snapshot.exportAll().sessions.size)
    }

    @Test
    fun `lastWeightByExercise prend la charge max de la dernière séance terminée`() = runTest {
        sessions.saveMuscuSession(null, 1000, SessionUpdate { endedAt = 1500 }, listOf(muscuSetInput("Squat", 1, 10, 60.0), muscuSetInput("Squat", 2, 8, 70.0)))
        sessions.saveMuscuSession(null, 2000, SessionUpdate { endedAt = 2500 }, listOf(muscuSetInput("Squat", 1, 10, 40.0), muscuSetInput("Squat", 2, 8, 50.0), muscuSetInput("Rowing", 1, 10, 20.0)))
        // Plus récente mais en cours : ignorée.
        sessions.saveMuscuSession(null, 3000, SessionUpdate(), listOf(muscuSetInput("Squat", 1, 10, 99.0)))

        assertEquals(mapOf("Squat" to 50.0, "Rowing" to 20.0), sessions.lastWeightByExercise(listOf("Squat", "Rowing", "Inconnu")))
        assertEquals(emptyMap<String, Double>(), sessions.lastWeightByExercise(emptyList()))
    }

    @Test
    fun `listMuscuExercises résume chaque exercice le plus récent d'abord`() = runTest {
        sessions.saveMuscuSession(null, 1000, SessionUpdate { endedAt = 1500 }, listOf(muscuSetInput("Squat", 1, 10, 60.0, Difficulty.DUR), muscuSetInput("Rowing", 1, 10, 20.0)))
        sessions.saveMuscuSession(null, 2000, SessionUpdate { endedAt = 2500 }, listOf(muscuSetInput("Squat", 1, 10, 40.0, Difficulty.FACILE), muscuSetInput("Squat", 2, 8, 50.0, Difficulty.FACILE)))

        val list = sessions.listMuscuExercises()
        assertEquals(listOf("Squat", "Rowing"), list.map { it.exercise })
        val squat = list[0]
        assertEquals(2, squat.sessions)
        assertEquals(2000L, squat.lastAt)
        assertEquals(50.0, squat.lastWeightKg, 0.0)
        assertEquals(Difficulty.FACILE, squat.lastDifficulty)
        assertNull(list[1].lastDifficulty)
    }

    // ---- dailyDurations ----------------------------------------------------

    @Test
    fun `dailyDurations groupe par jour local et utilise le temps en mouvement`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2024, 5, 20)
        fun at(d: LocalDate, hour: Long) = d.atStartOfDay(zone).plusHours(hour).toInstant().toEpochMilli()
        val now = at(today, 18)

        val a = sessions.createSession(ActivityType.VELO, at(today, 8))
        sessions.updateSession(a, SessionUpdate { endedAt = at(today, 9); durationSec = 3600; movingTimeSec = 3000 })
        val b = sessions.createSession(ActivityType.MUSCU, at(today, 12))
        sessions.updateSession(b, SessionUpdate { endedAt = at(today, 13); durationSec = 1800 })
        val c = sessions.createSession(ActivityType.VELO, at(today.minusDays(2), 7))
        sessions.updateSession(c, SessionUpdate { endedAt = at(today.minusDays(2), 8); durationSec = 600 })
        // Trop ancienne pour une fenêtre de 7 jours, et une séance en cours.
        val old = sessions.createSession(ActivityType.VELO, at(today.minusDays(10), 7))
        sessions.updateSession(old, SessionUpdate { endedAt = at(today.minusDays(10), 8); durationSec = 999 })
        sessions.createSession(ActivityType.VELO, at(today, 15))

        val daily = sessions.dailyDurations(7, now)
        assertEquals(
            listOf(today.minusDays(2).toString() to 600, today.toString() to 4800),
            daily.map { it.day to it.durationSec },
        )
    }

    // ---- réinitialisation --------------------------------------------------

    @Test
    fun `clearAllData garde réglages et journal de poids`() = runTest {
        seedLegacyFixture()
        repos.bodyWeight.logBodyWeight(80.0, 1_000L)

        sessions.clearAllData()
        val snap = snapshot.exportAll()
        assertEquals(0, snap.sessions.size + snap.trackPoints.size + snap.muscuSets.size)
        assertEquals(1, repos.bodyWeight.listBodyMeasurements().size)
        assertNotNull(repos.settings.getSetting("profile"))
        assertNotNull(repos.settings.getSetting("backup_s3"))
    }

    @Test
    fun `clearAllDataIncludingSettings ne garde que les clés de sauvegarde`() = runTest {
        seedLegacyFixture()
        repos.bodyWeight.logBodyWeight(80.0, 1_000L)
        repos.settings.setSetting(Keys.MAP_STYLE_URL, "https://tiles.example/style.json")

        sessions.clearAllDataIncludingSettings()
        val snap = snapshot.exportAll()
        assertEquals(0, snap.sessions.size + snap.trackPoints.size + snap.muscuSets.size)
        assertEquals(0, repos.bodyWeight.listBodyMeasurements().size)
        assertEquals(setOf("backup_s3", "backup_last"), db.settingsDao().getAll().map { it.key }.toSet())
    }

    /** Trie les listes d'un snapshot pour comparer indépendamment de l'ordre SQL. */
    private fun DbSnapshot.normalized() = DbSnapshot(
        sessions = sessions.sortedBy { it.id },
        trackPoints = trackPoints.sortedBy { it.id },
        muscuSets = muscuSets.sortedBy { it.id },
        bodyMeasurements = bodyMeasurements?.sortedBy { it.id },
        settings = settings.sortedBy { it.key },
    )
}
