package com.kobe.beesm.configuration.seed

import java.util.UUID

/**
 * Index des séances communes issues de l’extraction PDF.
 *
 * - Union des ids `J-xxx` liés (hints multi-périodes, périodes consécutives même enseignant/classes).
 * - Résolution d’un `sessionGroupId` stable (`sg-…`) pour une case donnée.
 *
 * Compatible avec BR-09 / Q-55 : même groupe → pas de TEACHER_OVERLAP.
 */
internal class EdtJointSessionIndex(
    private val extraction: EdtExtractionFile
) {
    private val parent = linkedMapOf<String, String>()
    private val groupCache = linkedMapOf<String, String>()

    init {
        // 1) Hints tableaux sur les entries (ex. J-002+J-003)
        for (entry in extraction.entries) {
            for (item in entry.resolvedItems()) {
                val hints = item.resolvedJointHints().ifEmpty { entry.resolvedJointHints() }
                unionAll(hints)
            }
        }
        // 2) jointSessions : périodes consécutives même (jour, enseignant, alias, classes)
        val buckets = linkedMapOf<String, MutableList<EdtJointSession>>()
        for (js in extraction.jointSessions) {
            val id = js.id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            ensure(id)
            val classes = js.resolvedClasses().sorted().joinToString("|")
            val key = listOf(
                js.day?.trim()?.uppercase().orEmpty(),
                js.teacherKey?.trim()?.uppercase().orEmpty(),
                js.printedAlias?.trim()?.uppercase().orEmpty(),
                classes
            ).joinToString("::")
            buckets.getOrPut(key) { mutableListOf() }.add(js)
        }
        for (rows in buckets.values) {
            val ordered = rows
                .mapNotNull { row ->
                    val id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val period = row.resolvedStartPeriod() ?: return@mapNotNull null
                    id to period
                }
                .sortedBy { it.second }
            for (i in 1 until ordered.size) {
                val prev = ordered[i - 1]
                val cur = ordered[i]
                if (cur.second == prev.second + 1) {
                    union(prev.first, cur.first)
                }
            }
        }
    }

    /**
     * Résout le groupe de séance pour une case : hints explicites + jointSessions
     * qui couvrent la classe / jour / périodes / enseignant.
     */
    fun resolveGroupId(
        className: String,
        day: String,
        startPeriod: Int?,
        periodCount: Int,
        teacherKey: String?,
        printedAlias: String?,
        hints: List<String>
    ): String? {
        val ids = linkedSetOf<String>()
        hints.map { it.trim() }.filter { it.isNotEmpty() }.forEach { ids += it }

        if (startPeriod != null) {
            val dayU = day.trim().uppercase()
            val teacherU = teacherKey?.trim()?.uppercase()
            val aliasU = printedAlias?.trim()?.uppercase()
            val endPeriod = startPeriod + periodCount.coerceAtLeast(1) - 1
            for (js in extraction.jointSessions) {
                val jid = js.id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val jp = js.resolvedStartPeriod() ?: continue
                if (jp !in startPeriod..endPeriod) continue
                if (js.day?.trim()?.uppercase() != dayU) continue
                if (className !in js.resolvedClasses()) continue
                if (teacherU != null &&
                    js.teacherKey?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } != null &&
                    js.teacherKey.trim().uppercase() != teacherU
                ) {
                    continue
                }
                if (aliasU != null &&
                    js.printedAlias?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } != null &&
                    TimetableSubjectAliasNorm.normalize(js.printedAlias) !=
                    TimetableSubjectAliasNorm.normalize(printedAlias.orEmpty())
                ) {
                    // alias différent → autre séance (ex. même créneau autre matière)
                    // on garde quand même si teacher+classes matchent déjà
                }
                ids += jid
            }
        }

        if (ids.isEmpty()) return null
        ids.forEach { ensure(it) }
        unionAll(ids.toList())
        val root = find(ids.first())
        val members = parent.keys.filter { find(it) == root }.sorted()
        val key = members.joinToString("+")
        return groupCache.getOrPut(key) {
            "sg-" + UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
        }
    }

    fun groupCount(): Int = groupCache.size

    private fun ensure(id: String) {
        parent.putIfAbsent(id, id)
    }

    private fun find(id: String): String {
        ensure(id)
        var cur = id
        while (parent[cur] != cur) {
            val p = parent[cur]!!
            parent[cur] = parent[p] ?: p
            cur = parent[cur]!!
        }
        return cur
    }

    private fun union(a: String, b: String) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }

    private fun unionAll(ids: List<String>) {
        val clean = ids.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.size < 2) {
            clean.forEach { ensure(it) }
            return
        }
        val head = clean.first()
        for (i in 1 until clean.size) union(head, clean[i])
    }
}

/** Normalisation légère d’alias (évite dépendance circulaire au service timetable). */
internal object TimetableSubjectAliasNorm {
    fun normalize(raw: String): String =
        raw.trim()
            .uppercase()
            .replace('É', 'E')
            .replace('È', 'E')
            .replace('Ê', 'E')
            .replace('À', 'A')
            .replace('Ù', 'U')
            .replace('Ô', 'O')
            .replace(Regex("[^A-Z0-9]+"), "")
}
