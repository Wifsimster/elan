package ovh.battistella.elan.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import ovh.battistella.elan.data.repository.ListSessionsOptions

/**
 * Construit la requête de `listSessions` : historique des séances terminées,
 * filtré (type, fenêtre, recherche libre), avec les agrégats de complétion
 * muscu, paginé. Le SQL est celui de l'app d'origine ; seule la liste des
 * prédicats varie, les valeurs passent toujours par des paramètres liés.
 */
object SessionQueryBuilder {

    /**
     * Neutralise les jokers LIKE (`%`, `_`) et l'échappement (`\`) d'une saisie
     * utilisateur — sinon taper « 50 % » ou « a_b » matcherait n'importe quoi.
     * À utiliser avec `ESCAPE '\'`.
     */
    fun escapeLike(input: String): String =
        input.replace(Regex("""[\\%_]""")) { "\\" + it.value }

    fun build(options: ListSessionsOptions): SimpleSQLiteQuery {
        // Prédicats qualifiés par l'alias `s` : la jointure sur `muscu_sets`
        // (alias `ms`) rendrait les colonnes nues ambiguës.
        val where = mutableListOf("s.endedAt IS NOT NULL")
        val params = mutableListOf<Any>()

        options.type?.let {
            where += "s.type = ?"
            params += it.key
        }
        options.fromMs?.let {
            where += "s.startedAt >= ?"
            params += it
        }
        options.toMs?.let {
            where += "s.startedAt < ?"
            params += it
        }
        val trimmed = options.search?.trim()
        if (!trimmed.isNullOrEmpty()) {
            // LIKE est insensible à la casse pour l'ASCII par défaut. On matche
            // le code du type (« velo »/« muscu »), les notes ou un exercice
            // muscu rattaché.
            val like = "%${escapeLike(trimmed)}%"
            where += """(s.type LIKE ? ESCAPE '\' OR IFNULL(s.notes, '') LIKE ? ESCAPE '\' OR s.id IN (
                SELECT sessionId FROM muscu_sets WHERE exercise LIKE ? ESCAPE '\'
            ))"""
            params += like
            params += like
            params += like
        }

        // Agrégat de lecture : nombre de séries et d'exercices distincts par
        // séance (complétion muscu). Vélo → 0. Purement calculé à la lecture.
        val sql = """
            SELECT s.*,
                   COUNT(ms.id)                AS setCount,
                   COUNT(DISTINCT ms.exercise) AS exerciseCount
              FROM sessions s
              LEFT JOIN muscu_sets ms ON ms.sessionId = s.id
             WHERE ${where.joinToString(" AND ")}
             GROUP BY s.id
             ORDER BY s.startedAt DESC LIMIT ? OFFSET ?
        """.trimIndent()
        params += options.limit
        params += options.offset
        return SimpleSQLiteQuery(sql, params.toTypedArray())
    }
}
