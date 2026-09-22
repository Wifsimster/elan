package ovh.battistella.elan.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.RecordKind
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.sessionEffort
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ShareCardTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)
    private val t0 = 1_700_000_000_000L

    private fun session(type: ActivityType, avgHr: Double? = 140.0) = Session(
        id = 1, type = type, startedAt = t0, endedAt = t0 + 3_600_000, durationSec = 3600, movingTimeSec = 3000,
        notes = null, avgHr = avgHr, maxHr = 170.0, distanceM = 30_000.0, avgSpeedKmh = 25.0, maxSpeedKmh = 40.0,
        elevationGainM = 412.4, avgCadence = null, maxCadence = null, calories = 600.0, source = null, externalId = null,
    )

    private fun points(n: Int) = List(n) { i ->
        TrackPoint(id = i.toLong(), sessionId = 1, ts = t0 + i * 1000L, lat = 48.85 + i * 0.001, lon = 2.35 + i * 0.001, altitude = 30.0, speedKmh = 25.0, hr = 140.0, cadence = null)
    }

    @Test
    fun `sortie GPS - tracé, stats vélo et badge record`() {
        val s = session(ActivityType.VELO)
        compose.setContent {
            ElanTheme {
                ShareCard(
                    session = s, points = points(5), sets = emptyList(),
                    records = listOf(SessionRecord(RecordKind.SPEED, RecordScope.YEAR), SessionRecord(RecordKind.DISTANCE, RecordScope.ALL)),
                    effort = sessionEffort(s, 190.0),
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText("Vélo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
        compose.onNodeWithText("30,0 km").assertIsDisplayed()
        // Temps en mouvement plutôt que durée totale.
        compose.onNodeWithText("50:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.share_stat_speed)).assertIsDisplayed()
        compose.onNodeWithText("412 m").assertIsDisplayed()
        compose.onNodeWithText("140 bpm").assertIsDisplayed()
        // Le record absolu passe avant celui de l'année.
        compose.onNodeWithText(text(R.string.share_record_all, "distance")).assertIsDisplayed()
    }

    @Test
    fun `course - allure au lieu de la vitesse, effort sans record`() {
        val s = session(ActivityType.COURSE, avgHr = null)
        compose.setContent {
            ElanTheme {
                ShareCard(session = s, points = points(3), sets = emptyList(), records = emptyList(), effort = sessionEffort(s, 190.0))
            }
        }

        compose.onNodeWithText(text(R.string.share_stat_pace)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.share_stat_hr)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.share_effort, sessionEffort(s, 190.0).label)).assertIsDisplayed()
    }

    @Test
    fun `muscu - héros d'activité, exercices et volume`() {
        val s = session(ActivityType.MUSCU)
        val sets = listOf(
            MuscuSet(1, 1, "Goblet squat", 1, 10, 20.0, null),
            MuscuSet(2, 1, "Goblet squat", 2, 10, 20.0, null),
            MuscuSet(3, 1, "Gainage planche", 1, 30, 0.0, null),
        )
        compose.setContent {
            ElanTheme {
                ShareCard(session = s, points = emptyList(), sets = sets, records = emptyList(), effort = sessionEffort(s, 190.0))
            }
        }

        compose.onNodeWithText("Musculation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.share_stat_exercises)).assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("400 kg").assertIsDisplayed()
        // Durée totale (pas de temps en mouvement en muscu).
        compose.onNodeWithText("1:00:00").assertIsDisplayed()
    }
}
