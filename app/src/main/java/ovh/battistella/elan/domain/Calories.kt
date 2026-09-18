// Estimation des calories : modèle MET interpolé (vélo, course, marche,
// musculation), avec mélange optionnel d'une estimation cardio (style Keytel,
// ajustée sans âge). kcal_MET = MET × poids(kg) × durée(h).
package ovh.battistella.elan.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** FCmax par défaut quand l'appelant ne la fournit pas (cohérent avec le profil par défaut). */
private const val DEFAULT_MAX_HR = 190.0

/** Points de référence du Compendium (km/h → MET) pour le vélo, loisir à vigoureux. */
private val VELO_MET_TABLE = listOf(
    10.0 to 3.5, // très tranquille / promenade
    16.0 to 4.0, // loisir
    19.0 to 6.8, // allure soutenue
    22.5 to 8.0, // entraînement
    25.5 to 10.0, // vigoureux
    30.5 to 12.0, // course / rapide
    33.0 to 15.8, // > 32 km/h, effort maximal
)

/** Points de référence du Compendium (km/h → MET) pour la course à pied. */
private val COURSE_MET_TABLE = listOf(
    6.4 to 6.0, // footing très lent
    8.0 to 8.3,
    9.7 to 9.8,
    11.3 to 11.0,
    12.9 to 11.8,
    14.5 to 12.8,
    16.1 to 14.5,
    17.7 to 16.0, // allure de compétition
)

/** Points de référence du Compendium (km/h → MET) pour la marche, plat à soutenu. */
private val MARCHE_MET_TABLE = listOf(
    3.2 to 2.8, // flânerie
    4.0 to 3.0,
    4.8 to 3.5, // allure courante
    5.6 to 4.3,
    6.4 to 5.0, // marche rapide
    7.2 to 7.0,
    8.0 to 8.3, // marche athlétique
)

/** Interpolation linéaire entre les points de référence (extrapolation plate aux bords). */
private fun metFromTable(table: List<Pair<Double, Double>>, avgSpeedKmh: Double): Double {
    val first = table.first()
    val last = table.last()
    if (avgSpeedKmh <= first.first) return first.second
    if (avgSpeedKmh >= last.first) return last.second
    for (i in 1 until table.size) {
        val (x1, y1) = table[i]
        if (avgSpeedKmh <= x1) {
            val (x0, y0) = table[i - 1]
            val t = (avgSpeedKmh - x0) / (x1 - x0)
            return y0 + t * (y1 - y0)
        }
    }
    return last.second
}

/** MET musculation : « moderate resistance training » du Compendium (5 est trop pour du circuit). */
private const val MUSCU_MET = 4.5

private class SpeedMet(val table: List<Pair<Double, Double>>, val fallbackKmh: Double)

/**
 * Table MET et vitesse de repli par activité tracée au GPS. La vitesse de repli
 * ne sert que si l'appelant n'en fournit pas : une allure ordinaire pour l'activité.
 */
private fun speedMetFor(type: ActivityType): SpeedMet? = when (type) {
    ActivityType.VELO -> SpeedMet(VELO_MET_TABLE, 18.0)
    ActivityType.COURSE -> SpeedMet(COURSE_MET_TABLE, 10.0)
    ActivityType.MARCHE -> SpeedMet(MARCHE_MET_TABLE, 5.0)
    ActivityType.MUSCU -> null
}

/**
 * Énergie cardio estimée (kcal/min) inspirée de Keytel et al., variante sans âge :
 * le %FCmax sert de proxy d'intensité. ~5 à ~15 kcal/min pour 70 kg entre 70 % et 95 %.
 */
private fun hrKcalPerMin(weightKg: Double, avgHr: Double, maxHr: Double): Double {
    val pct = max(0.4, min(1.05, avgHr / maxHr))
    return 0.082 * weightKg * pct.pow(1.7)
}

/**
 * Bonus d'énergie lié au dénivelé positif (~0,77 kcal/m pour 70 kg). Le travail
 * vertical est du même ordre à pied qu'à vélo : la même constante sert aux deux.
 */
private fun elevationBonusKcal(weightKg: Double, elevationGainM: Double): Double {
    if (elevationGainM <= 0) return 0.0
    return weightKg * 0.011 * elevationGainM
}

fun estimateCalories(
    type: ActivityType,
    weightKg: Double,
    durationSec: Int,
    avgSpeedKmh: Double? = null,
    elevationGainM: Double? = null,
    avgHr: Double? = null,
    maxHr: Double? = null,
): Double {
    val hours = durationSec / 3600.0
    if (hours <= 0 || weightKg <= 0) return 0.0

    val speedMet = speedMetFor(type)
    val met = if (speedMet != null) metFromTable(speedMet.table, avgSpeedKmh ?: speedMet.fallbackKmh) else MUSCU_MET
    val metKcal = met * weightKg * hours

    // Mélange cardio (Keytel sans âge) si FC moyenne disponible : 60 % cardio, 40 % MET.
    val effectiveMaxHr = maxHr ?: DEFAULT_MAX_HR
    var baseKcal = metKcal
    if (avgHr != null && avgHr > 0 && effectiveMaxHr > 0) {
        val hrKcal = hrKcalPerMin(weightKg, avgHr, effectiveMaxHr) * (durationSec / 60.0)
        baseKcal = 0.4 * metKcal + 0.6 * hrKcal
    }

    // Bonus dénivelé : toute activité qui déplace le corps en montée. Ignoré en muscu.
    val elevBonus = if (type == ActivityType.MUSCU) 0.0 else elevationBonusKcal(weightKg, elevationGainM ?: 0.0)

    return baseKcal + elevBonus
}

/** Zone cardio (1 à 5) à partir de la FC et de la FC max. */
fun heartRateZone(hr: Double, maxHr: Double): Int {
    val pct = hr / maxHr
    return when {
        pct < 0.6 -> 1
        pct < 0.7 -> 2
        pct < 0.8 -> 3
        pct < 0.9 -> 4
        else -> 5
    }
}

val ZONE_LABELS: Map<Int, String> = mapOf(
    1 to "Récupération",
    2 to "Endurance",
    3 to "Aérobie",
    4 to "Seuil",
    5 to "Maximal",
)
