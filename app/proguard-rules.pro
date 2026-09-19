# Règles R8 d'Élan — volontairement minimales.
#
# Hilt / Dagger, Room, Compose et WorkManager embarquent leurs propres règles
# consumer : aucun keep n'est ajouté ici sans un crash constaté en release qui
# le justifie (et un commentaire qui explique lequel).

# Supprime les logs verbose/debug/info des builds release pour que rien de
# personnel (mesures, positions, identifiants de capteurs) n'atteigne logcat.
# Les vrais avertissements/erreurs (Log.w / Log.e) sont conservés.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
