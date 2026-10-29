package ovh.battistella.elan.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import ovh.battistella.elan.data.repository.ListSessionsOptions

object SessionQueryBuilder {

    fun escapeLike(input: String): String =
        input.replace(Regex("""[\\%_]""")) { "\\" + it.value }

    fun build(options: ListSessionsOptions): SimpleSQLiteQuery {
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
            val like = "%${escapeLike(trimmed)}%"
            where += """(s.type LIKE ? ESCAPE '\' OR IFNULL(s.notes, '') LIKE ? ESCAPE '\' OR s.id IN (
                SELECT sessionId FROM muscu_sets WHERE exercise LIKE ? ESCAPE '\'
            ))"""
            params += like
            params += like
            params += like
        }

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
