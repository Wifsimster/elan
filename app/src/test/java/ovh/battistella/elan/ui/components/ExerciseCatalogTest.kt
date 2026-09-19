package ovh.battistella.elan.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.elan.domain.CATALOG
import ovh.battistella.elan.domain.CatalogExercise
import ovh.battistella.elan.domain.Equipment
import ovh.battistella.elan.domain.ExerciseCategory
import ovh.battistella.elan.domain.RecoProfile
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExerciseCatalogTest {

    @get:Rule val compose = createComposeRule()

    private val profile = RecoProfile(weightKg = 70.0, heightCm = 175.0, sex = null, goal = TrainingGoal.HYPERTROPHIE)

    @Test
    fun `filterCatalog - texte sur nom et muscles, rayon, matériel`() {
        assertEquals(CATALOG.size, filterCatalog("", null, null).size)
        assertTrue(filterCatalog("goblet", null, null).map { it.id } == listOf("goblet-squat"))
        // Recherche sur un muscle, insensible à la casse.
        val glutes = filterCatalog("FESSIERS", null, null)
        assertTrue(glutes.isNotEmpty())
        assertTrue(glutes.all { ex -> ex.muscles.any { it.lowercase().contains("fessiers") } })

        val legs = filterCatalog("", ExerciseCategory.JAMBES, null)
        assertTrue(legs.all { it.category == ExerciseCategory.JAMBES })
        val bands = filterCatalog("", null, Equipment.ELASTIQUE)
        assertTrue(bands.isNotEmpty())
        assertTrue(bands.all { Equipment.ELASTIQUE in it.equipment })
        assertTrue(filterCatalog("zzz", null, null).isEmpty())

        val sections = catalogSections(filterCatalog("", null, null), null)
        assertEquals(listOf("Jambes", "Pectoraux", "Dos", "Épaules", "Bras", "Gainage"), sections.map { it.category.label })
        assertEquals(listOf(ExerciseCategory.DOS), catalogSections(filterCatalog("", ExerciseCategory.DOS, null), ExerciseCategory.DOS).map { it.category })
    }

    @Test
    fun `compteur, sections, recherche et Tout effacer`() {
        compose.setContent { ElanTheme { ExerciseCatalog(profile = profile, onPick = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("${CATALOG.size} exercices").assertIsDisplayed()
        compose.onNodeWithText("JAMBES").assertIsDisplayed()
        compose.onNodeWithText("Tout effacer").assertDoesNotExist()

        compose.onNodeWithContentDescription("Rechercher un exercice, un muscle").performTextInput("goblet")
        compose.waitForIdle()
        compose.onNodeWithText("1 exercice").assertIsDisplayed()
        compose.onNodeWithText("Goblet squat").assertIsDisplayed()
        // Reco du profil défaut : 4 × 8-12 · 21 kg.
        compose.onNodeWithText("4 × 8-12 ·").assertIsDisplayed()
        compose.onNodeWithText("21 kg").assertIsDisplayed()

        compose.onNodeWithText("Tout effacer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("${CATALOG.size} exercices").assertIsDisplayed()
    }

    @Test
    fun `filtres rayon et matériel - re-tap désélectionne, état vide avec réinitialisation`() {
        compose.setContent { ElanTheme { ExerciseCatalog(profile = profile, onPick = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("Gainage").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gainage").assertIsSelected()
        val gainage = filterCatalog("", ExerciseCategory.GAINAGE, null).size
        compose.onNodeWithText("$gainage exercices").assertIsDisplayed()
        compose.onNodeWithText("JAMBES").assertDoesNotExist()

        // Gainage + machine : aucun résultat → état vide + « Réinitialiser les filtres ».
        compose.onNodeWithText("machine").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Aucun exercice trouvé").assertIsDisplayed()
        compose.onNodeWithText("Réinitialiser les filtres").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("${CATALOG.size} exercices").assertIsDisplayed()
        compose.onNodeWithText("Tous").assertIsSelected()
        compose.onNodeWithText("Tout matériel").assertIsSelected()

        compose.onNodeWithText("Dos").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dos").assertIsSelected()
        compose.onNodeWithText("Dos").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tous").assertIsSelected()
    }

    @Test
    fun `fiche détaillée - recommandation et ajout, pastille déjà ajouté`() {
        val picked = mutableListOf<CatalogExercise>()
        compose.setContent {
            ElanTheme { ExerciseCatalog(profile = profile, onPick = { picked += it }, addedNames = setOf("Squat barre")) }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Déjà ajouté").assertIsDisplayed()

        compose.onNodeWithContentDescription("Rechercher un exercice, un muscle").performTextInput("goblet")
        compose.waitForIdle()
        compose.onNodeWithText("Goblet squat").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("CONSEILLÉ · OBJECTIF PRISE DE MUSCLE").assertIsDisplayed()
        compose.onNodeWithText("Repos conseillé ~90 s entre les séries.").assertIsDisplayed()
        // Fenêtre de la feuille : l'injection tactile n'atteint pas le bouton sous Robolectric, on passe par l'action sémantique.
        compose.onNodeWithText("Ajouter à la séance").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("goblet-squat"), picked.map { it.id })
    }
}
