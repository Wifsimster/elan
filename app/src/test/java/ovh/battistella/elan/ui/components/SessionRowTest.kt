package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class SessionRowTest {

    @get:Rule val compose = createComposeRule()

    private fun session(
        type: ActivityType,
        durationSec: Int = 3600,
        movingTimeSec: Int? = null,
        distanceM: Double? = null,
        avgHr: Double? = null,
        setCount: Int? = null,
        exerciseCount: Int? = null,
    ) = Session(
        id = 1,
        type = type,
        startedAt = 1_700_000_000_000,
        endedAt = null,
        durationSec = durationSec,
        movingTimeSec = movingTimeSec,
        notes = null,
        avgHr = avgHr,
        maxHr = null,
        distanceM = distanceM,
        avgSpeedKmh = null,
        maxSpeedKmh = null,
        elevationGainM = null,
        avgCadence = null,
        maxCadence = null,
        calories = null,
        source = null,
        externalId = null,
        setCount = setCount,
        exerciseCount = exerciseCount,
    )

    @Test
    fun muscuShowsSetsAndExercises() {
        compose.setContent {
            ElanTheme { SessionRow(session(ActivityType.MUSCU, setCount = 12, exerciseCount = 1), onClick = {}) }
        }

        compose.onNodeWithText("Musculation").assertIsDisplayed()
        compose.onNodeWithText("12 séries").assertIsDisplayed()
        compose.onNodeWithText("1 exercice").assertIsDisplayed()
    }

    @Test
    fun gpsShowsMovingTimeAndDistance() {
        compose.setContent {
            ElanTheme {
                SessionRow(
                    session(ActivityType.VELO, durationSec = 3900, movingTimeSec = 3720, distanceM = 24_300.0),
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText("Vélo").assertIsDisplayed()
        // Le temps en mouvement prime sur la durée brute.
        compose.onNodeWithText("1 h 02").assertIsDisplayed()
        compose.onNodeWithText("24,3 km").assertIsDisplayed()
    }

    @Test
    fun gpsWithoutDistanceFallsBackToAverageHr() {
        compose.setContent {
            ElanTheme { SessionRow(session(ActivityType.COURSE, durationSec = 1500, avgHr = 142.0), onClick = {}) }
        }

        compose.onNodeWithText("25 min").assertIsDisplayed()
        compose.onNodeWithText("142 bpm moy.").assertIsDisplayed()
    }

    @Test
    fun clickInvokesCallback() {
        var clicks = 0
        compose.setContent {
            ElanTheme { SessionRow(session(ActivityType.MARCHE), onClick = { clicks++ }) }
        }

        compose.onNodeWithText("Marche").performClick()

        assertEquals(1, clicks)
    }
}
