// Zone de confidentialité pour les exports GPS.
//
// Un tracé commence et finit presque toujours au domicile : exporter le tracé
// brut révèle l'adresse. Cette zone retire les points situés dans un rayon
// donné autour du premier et du dernier point avant tout export hors-appareil.
// Le rognage n'altère jamais les données stockées : il ne s'applique qu'au
// moment de construire un artefact partagé (GPX, etc.). La lecture/écriture du
// réglage (`privacy_zone_m`, 0 = désactivé) vit dans la couche données.
package ovh.battistella.elan.domain

/** Rayons proposés dans les Réglages (mètres). 0 = aucune zone (tracé complet). */
val PRIVACY_ZONE_OPTIONS: List<Int> = listOf(0, 100, 200, 500)

/**
 * Retire les points de début et de fin situés à moins de `radiusM` du premier /
 * dernier point. Conserve au moins deux points : si le rognage viderait le tracé
 * (boucle entièrement dans la zone), on renvoie le tracé d'origine inchangé
 * plutôt qu'un export vide.
 */
fun <T : GeoPoint> trimPrivacyZone(points: List<T>, radiusM: Double): List<T> {
    if (radiusM <= 0 || points.size < 2) return points
    val first = points[0]
    val last = points[points.size - 1]

    var start = 0
    while (start < points.size && haversineMeters(first, points[start]) < radiusM) start++
    var end = points.size - 1
    while (end > start && haversineMeters(last, points[end]) < radiusM) end--

    // Garde-fou : il faut ≥ 2 points pour un tracé exploitable.
    if (end - start < 1) return points
    return points.subList(start, end + 1)
}
