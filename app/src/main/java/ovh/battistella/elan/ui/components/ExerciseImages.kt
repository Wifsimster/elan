// Illustrations photo des exercices (paire départ → fin), résolues localement
// depuis les ressources bundlées (`res/drawable-nodpi/exercise_<clé>_{0,1}.jpg`).
// Photos domaine public (free-exercise-db) : aucun accès réseau.
package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import ovh.battistella.elan.R

/** Les deux positions d'un mouvement : départ puis fin. */
data class ExercisePhotos(@DrawableRes val start: Int, @DrawableRes val end: Int)

private val EXERCISE_IMAGES: Map<String, ExercisePhotos> = mapOf(
    "goblet-squat" to ExercisePhotos(R.drawable.exercise_goblet_squat_0, R.drawable.exercise_goblet_squat_1),
    "dumbbell-bench-press" to ExercisePhotos(R.drawable.exercise_dumbbell_bench_press_0, R.drawable.exercise_dumbbell_bench_press_1),
    "one-arm-dumbbell-row" to ExercisePhotos(R.drawable.exercise_one_arm_dumbbell_row_0, R.drawable.exercise_one_arm_dumbbell_row_1),
    "dumbbell-lunges" to ExercisePhotos(R.drawable.exercise_dumbbell_lunges_0, R.drawable.exercise_dumbbell_lunges_1),
    "plank" to ExercisePhotos(R.drawable.exercise_plank_0, R.drawable.exercise_plank_1),
    "dumbbell-romanian-deadlift" to ExercisePhotos(R.drawable.exercise_dumbbell_romanian_deadlift_0, R.drawable.exercise_dumbbell_romanian_deadlift_1),
    "standing-dumbbell-press" to ExercisePhotos(R.drawable.exercise_standing_dumbbell_press_0, R.drawable.exercise_standing_dumbbell_press_1),
    "bent-over-two-dumbbell-row" to ExercisePhotos(R.drawable.exercise_bent_over_two_dumbbell_row_0, R.drawable.exercise_bent_over_two_dumbbell_row_1),
    "bulgarian-split-squat" to ExercisePhotos(R.drawable.exercise_bulgarian_split_squat_0, R.drawable.exercise_bulgarian_split_squat_1),
    "side-plank" to ExercisePhotos(R.drawable.exercise_side_plank_0, R.drawable.exercise_side_plank_1),
)

/** Paire de photos d'une clé d'illustration (`imageKey` du programme / catalogue), ou `null`. */
fun exerciseImages(imageKey: String?): ExercisePhotos? = if (imageKey == null) null else EXERCISE_IMAGES[imageKey]
