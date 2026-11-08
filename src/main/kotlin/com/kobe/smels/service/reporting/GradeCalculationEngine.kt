package com.kobe.smels.service.reporting

import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.RankingPolicy
import com.kobe.smels.model.reporting.ReportPeriodType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Arithmétique pure des étapes 1–9 (docs/04) — sans accès base.
 * Appelée par [GradeCalculationService] et testable avec docs/fixtures.
 */
object GradeCalculationEngine {
    private val MC = MathContext.DECIMAL128
    private val TWO = 2
    private val HUNDRED = BigDecimal("100")
    private val TWENTY = BigDecimal("20")

    data class ScoreBandInput(
        val code: String,
        val minInclusive: BigDecimal,
        val maxExclusive: BigDecimal? = null
    )

    data class ScaleInput(
        val code: String,
        val bands: List<ScoreBandInput>
    )

    /** Paramètres de barème nécessaires au calcul (DTO-friendly, pas le document Mongo). */
    data class SchemeParams(
        val passMark: BigDecimal = BigDecimal("10"),
        val classSuccessThreshold: BigDecimal = BigDecimal("12"),
        val roundTermSubjectAverages: Boolean = true,
        val rankingPolicy: RankingPolicy = RankingPolicy.COMPETITION,
        val effortSubjectThreshold: BigDecimal? = BigDecimal("8"),
        val appreciationScales: List<ScaleInput> = listOf(defaultLetterScale()),
        val overallMentionBands: List<ScoreBandInput> = emptyList()
    )

    data class SequenceValue(
        val sessionNumber: Int,
        val value: BigDecimal?,
        val status: GradeStatus
    )

    data class SubjectInput(
        val classSubjectId: String,
        val subjectId: String,
        val subjectCode: String,
        val subjectName: String,
        val groupId: String,
        val groupCode: String,
        val groupName: String,
        val groupDisplayOrder: Int,
        val coefficient: BigDecimal,
        val teacherName: String?,
        val countsInAverage: Boolean,
        val sequences: List<SequenceValue>,
        val competency: String? = null
    )

    data class StudentSubjects(
        val studentId: String,
        val subjects: List<SubjectInput>
    )

    data class LineResult(
        val classSubjectId: String,
        val subjectCode: String,
        val subjectName: String,
        val teacherName: String?,
        val coefficient: BigDecimal,
        val sequences: List<SequenceValue>,
        /** Moyenne exacte (peut être non arrondie pour le rang annuel). */
        val averageExact: BigDecimal?,
        /** Moyenne affichée (2 décimales). */
        val averageDisplay: BigDecimal?,
        val totalExact: BigDecimal?,
        val totalDisplay: BigDecimal?,
        val appreciationCode: String?,
        val countsInAverage: Boolean,
        val groupId: String,
        val groupCode: String,
        val groupName: String,
        val groupDisplayOrder: Int,
        val competency: String? = null
    )

    data class GroupResult(
        val groupId: String,
        val code: String,
        val name: String,
        val displayOrder: Int,
        val lines: List<LineResult>,
        val totalCoefficient: BigDecimal,
        val totalPointsExact: BigDecimal?,
        val averageExact: BigDecimal?,
        val averageDisplay: BigDecimal?
    )

    data class StudentResult(
        val studentId: String,
        val groups: List<GroupResult>,
        val averageExact: BigDecimal?,
        val averageDisplay: BigDecimal?,
        val totalCoefficient: BigDecimal,
        val totalPointsExact: BigDecimal?,
        val totalPointsDisplay: BigDecimal?,
        val subjectsPassed: Int,
        val subjectsCounted: Int,
        val effortSubjectNames: List<String>,
        val mentionCode: String?,
        val termAveragesExact: Map<Int, BigDecimal?>,
        val ranked: Boolean
    )

    data class SubjectClassStat(
        val classSubjectId: String,
        val ranks: Map<String, Int>,
        val classAverage: BigDecimal?,
        val passedCount: Int
    )

    data class ClassComputation(
        val students: Map<String, StudentResult>,
        val ranks: Map<String, Int?>,
        val rankedCount: Int,
        val classProfile: ClassProfileResult?,
        val subjectStats: Map<String, SubjectClassStat> = emptyMap()
    )

    data class ClassProfileResult(
        val threshold: BigDecimal,
        val countAtOrAboveThreshold: Int,
        val classAverage: BigDecimal?,
        val successRate: BigDecimal?,
        val highestAverage: BigDecimal?,
        val lowestAverage: BigDecimal?
    )

    fun computeClass(
        students: List<StudentSubjects>,
        scheme: SchemeParams,
        periodType: ReportPeriodType,
        termNumber: Int? = null,
        appreciationScaleCode: String = "LETTER",
        classProfileThreshold: BigDecimal? = null,
        sequenceColumns: List<Int> = listOf(1, 2, 3, 4, 5, 6)
    ): ClassComputation {
        val scale = scheme.appreciationScales.firstOrNull { it.code == appreciationScaleCode }
            ?: defaultLetterScale()
        val perStudent = students.associate { ss ->
            ss.studentId to computeStudent(
                ss, scheme, periodType, termNumber, scale, sequenceColumns
            )
        }
        val ranked = perStudent.filter { it.value.ranked }
        val rankMap = competitionRanks(
            ranked.map { it.key to it.value.averageExact!! },
            scheme.rankingPolicy
        )
        val allRanks = perStudent.keys.associateWith { id ->
            if (perStudent[id]!!.ranked) rankMap[id] else null
        }
        val threshold = classProfileThreshold ?: scheme.classSuccessThreshold
        val profile = if (ranked.isEmpty()) {
            ClassProfileResult(threshold, 0, null, null, null, null)
        } else {
            val avgs = ranked.values.map { it.averageExact!! }
            val above = avgs.count { it.compareTo(threshold) >= 0 }
            val classAvg = avgs.fold(BigDecimal.ZERO) { a, b -> a.add(b, MC) }
                .divide(BigDecimal(avgs.size), MC)
            ClassProfileResult(
                threshold = threshold,
                countAtOrAboveThreshold = above,
                classAverage = round2(classAvg),
                successRate = round2(
                    BigDecimal(above).multiply(HUNDRED, MC)
                        .divide(BigDecimal(avgs.size), MC)
                ),
                highestAverage = round2(avgs.maxOrNull()!!),
                lowestAverage = round2(avgs.minOrNull()!!)
            )
        }
        val subjectStats = if (periodType == ReportPeriodType.TERM) {
            computeSubjectStats(perStudent, scheme)
        } else {
            emptyMap()
        }
        return ClassComputation(perStudent, allRanks, ranked.size, profile, subjectStats)
    }

    /** Étape 7 bis — rang / moyenne / validés par matière (bulletin de trimestre). */
    private fun computeSubjectStats(
        perStudent: Map<String, StudentResult>,
        scheme: SchemeParams
    ): Map<String, SubjectClassStat> {
        val bySubject = mutableMapOf<String, MutableList<Pair<String, BigDecimal>>>()
        perStudent.forEach { (studentId, result) ->
            result.groups.flatMap { it.lines }.forEach { line ->
                val avg = line.averageExact ?: return@forEach
                bySubject.getOrPut(line.classSubjectId) { mutableListOf() }.add(studentId to avg)
            }
        }
        return bySubject.mapValues { (csId, scored) ->
            val ranks = competitionRanks(scored, scheme.rankingPolicy)
            val avgs = scored.map { it.second }
            val classAvg = avgs.fold(BigDecimal.ZERO) { a, b -> a.add(b, MC) }
                .divide(BigDecimal(avgs.size), MC)
            SubjectClassStat(
                classSubjectId = csId,
                ranks = ranks,
                classAverage = round2(classAvg),
                passedCount = avgs.count { it.compareTo(scheme.passMark) >= 0 }
            )
        }
    }

    fun computeStudent(
        input: StudentSubjects,
        scheme: SchemeParams,
        periodType: ReportPeriodType,
        termNumber: Int?,
        scale: ScaleInput,
        sequenceColumns: List<Int>
    ): StudentResult {
        val lines = input.subjects.map { subject ->
            computeLine(subject, scheme, periodType, termNumber, scale, sequenceColumns)
        }
        val counted = lines.filter { it.countsInAverage && it.averageExact != null }
        val totalCoef = counted.fold(BigDecimal.ZERO) { a, l -> a.add(l.coefficient, MC) }
        val totalPts = counted.fold(BigDecimal.ZERO) { a, l -> a.add(l.totalExact ?: BigDecimal.ZERO, MC) }
        val averageExact = if (counted.isEmpty() || totalCoef.compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            totalPts.divide(totalCoef, MC)
        }
        val groups = lines.groupBy { it.groupId }.map { (_, groupLines) ->
            val ordered = groupLines.sortedBy { it.subjectName }
            val gCounted = ordered.filter { it.countsInAverage && it.averageExact != null }
            val gCoef = gCounted.fold(BigDecimal.ZERO) { a, l -> a.add(l.coefficient, MC) }
            val gPts = gCounted.fold(BigDecimal.ZERO) { a, l -> a.add(l.totalExact ?: BigDecimal.ZERO, MC) }
            val gAvg = if (gCounted.isEmpty() || gCoef.compareTo(BigDecimal.ZERO) == 0) null
            else gPts.divide(gCoef, MC)
            val first = ordered.first()
            GroupResult(
                groupId = first.groupId,
                code = first.groupCode,
                name = first.groupName,
                displayOrder = first.groupDisplayOrder,
                lines = ordered,
                totalCoefficient = gCoef,
                totalPointsExact = if (gCounted.isEmpty()) null else gPts,
                averageExact = gAvg,
                averageDisplay = gAvg?.let { round2(it) }
            )
        }.sortedBy { it.displayOrder }

        val passed = counted.count { it.averageExact!!.compareTo(scheme.passMark) >= 0 }
        val effortThreshold = scheme.effortSubjectThreshold
        val effort = if (effortThreshold == null) emptyList()
        else counted.filter { it.averageExact!!.compareTo(effortThreshold) < 0 }
            .map { it.subjectName }

        val termAvgs = (1..3).associateWith { t ->
            termAverageForStudent(input.subjects, scheme, t)
        }

        return StudentResult(
            studentId = input.studentId,
            groups = groups,
            averageExact = averageExact,
            averageDisplay = averageExact?.let { round2(it) },
            totalCoefficient = totalCoef,
            totalPointsExact = if (counted.isEmpty()) null else totalPts,
            totalPointsDisplay = if (counted.isEmpty()) null else round2(totalPts),
            subjectsPassed = passed,
            subjectsCounted = counted.size,
            effortSubjectNames = effort,
            mentionCode = averageExact?.let { findBand(it, scheme.overallMentionBands)?.code },
            termAveragesExact = termAvgs,
            ranked = averageExact != null
        )
    }

    private fun computeLine(
        subject: SubjectInput,
        scheme: SchemeParams,
        periodType: ReportPeriodType,
        termNumber: Int?,
        scale: ScaleInput,
        sequenceColumns: List<Int>
    ): LineResult {
        val bySession = subject.sequences.associateBy { it.sessionNumber }
        val relevantSessions = when (periodType) {
            ReportPeriodType.ANNUAL -> sequenceColumns
            ReportPeriodType.TERM -> {
                val t = termNumber ?: 1
                listOf(t * 2 - 1, t * 2).filter { it in sequenceColumns }
            }
            ReportPeriodType.SEQUENCE -> sequenceColumns.take(1)
        }
        val averageExact = when (periodType) {
            ReportPeriodType.TERM, ReportPeriodType.SEQUENCE ->
                termAverageFromSessions(relevantSessions.mapNotNull { bySession[it] }, scheme)
            ReportPeriodType.ANNUAL -> {
                val termAvgs = (1..3).mapNotNull { t ->
                    val sessions = listOf(t * 2 - 1, t * 2).mapNotNull { bySession[it] }
                    termAverageFromSessions(sessions, scheme)
                }
                if (termAvgs.isEmpty()) null
                else termAvgs.fold(BigDecimal.ZERO) { a, b -> a.add(b, MC) }
                    .divide(BigDecimal(termAvgs.size), MC)
            }
        }
        val totalExact = averageExact?.multiply(subject.coefficient, MC)
        return LineResult(
            classSubjectId = subject.classSubjectId,
            subjectCode = subject.subjectCode,
            subjectName = subject.subjectName,
            teacherName = subject.teacherName,
            coefficient = subject.coefficient,
            sequences = relevantSessions.map { n ->
                bySession[n] ?: SequenceValue(n, null, GradeStatus.PENDING)
            },
            averageExact = averageExact,
            averageDisplay = averageExact?.let { round2(it) },
            totalExact = totalExact,
            totalDisplay = totalExact?.let { round2(it) },
            appreciationCode = averageExact?.let { findBand(it, scale.bands)?.code },
            countsInAverage = subject.countsInAverage,
            groupId = subject.groupId,
            groupCode = subject.groupCode,
            groupName = subject.groupName,
            groupDisplayOrder = subject.groupDisplayOrder,
            competency = subject.competency
        )
    }

    /** Étape 1 : moyenne de trimestre (arrondie si roundTermSubjectAverages). */
    fun termAverageFromSessions(
        sessions: List<SequenceValue>,
        scheme: SchemeParams
    ): BigDecimal? {
        val scored = sessions.filter {
            it.status == GradeStatus.SCORED && it.value != null
        }
        if (scored.isEmpty()) return null
        val raw = scored.fold(BigDecimal.ZERO) { a, s -> a.add(s.value!!, MC) }
            .divide(BigDecimal(scored.size), MC)
        return if (scheme.roundTermSubjectAverages) round2(raw) else raw
    }

    private fun termAverageForStudent(
        subjects: List<SubjectInput>,
        scheme: SchemeParams,
        termNumber: Int
    ): BigDecimal? {
        val sessionNums = listOf(termNumber * 2 - 1, termNumber * 2)
        var coefSum = BigDecimal.ZERO
        var ptsSum = BigDecimal.ZERO
        var any = false
        subjects.filter { it.countsInAverage }.forEach { sub ->
            val bySession = sub.sequences.associateBy { it.sessionNumber }
            val sessions = sessionNums.mapNotNull { bySession[it] }
            val avg = termAverageFromSessions(sessions, scheme) ?: return@forEach
            any = true
            coefSum = coefSum.add(sub.coefficient, MC)
            ptsSum = ptsSum.add(avg.multiply(sub.coefficient, MC), MC)
        }
        if (!any || coefSum.compareTo(BigDecimal.ZERO) == 0) return null
        return ptsSum.divide(coefSum, MC)
    }

    fun competitionRanks(
        scored: List<Pair<String, BigDecimal>>,
        policy: RankingPolicy = RankingPolicy.COMPETITION
    ): Map<String, Int> {
        val sorted = scored.sortedWith { a, b -> b.second.compareTo(a.second) }
        val result = mutableMapOf<String, Int>()
        var i = 0
        var dense = 0
        while (i < sorted.size) {
            val current = sorted[i].second
            var j = i
            while (j < sorted.size && sorted[j].second.compareTo(current) == 0) j++
            val rank = when (policy) {
                RankingPolicy.COMPETITION -> i + 1
                RankingPolicy.DENSE -> {
                    dense += 1
                    dense
                }
            }
            for (k in i until j) result[sorted[k].first] = rank
            i = j
        }
        return result
    }

    fun findBand(value: BigDecimal, bands: List<ScoreBandInput>): ScoreBandInput? =
        bands.firstOrNull { band ->
            val max = band.maxExclusive
            value.compareTo(band.minInclusive) >= 0 &&
                (max == null || value.compareTo(max) < 0)
        }

    fun round2(value: BigDecimal): BigDecimal =
        value.setScale(TWO, RoundingMode.HALF_UP)

    fun defaultLetterScale(): ScaleInput =
        ScaleInput(
            code = "LETTER",
            bands = listOf(
                ScoreBandInput("C", minInclusive = BigDecimal.ZERO, maxExclusive = BigDecimal("12")),
                ScoreBandInput("B", minInclusive = BigDecimal("12"), maxExclusive = BigDecimal("14")),
                ScoreBandInput("B+", minInclusive = BigDecimal("14"), maxExclusive = BigDecimal("16")),
                ScoreBandInput("A", minInclusive = BigDecimal("16"), maxExclusive = null)
            )
        )

    fun maxPoints(totalCoefficient: BigDecimal): BigDecimal =
        totalCoefficient.multiply(TWENTY, MC)
}
