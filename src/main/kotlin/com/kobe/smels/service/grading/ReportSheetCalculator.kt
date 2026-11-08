package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.AppreciationScale
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.RankingPolicy
import com.kobe.smels.model.grading.ScoreBand
import com.kobe.smels.model.shared.Sex
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Arithmétique pure de la fiche de report (docs/04 § fiche de report) — sans accès base.
 */
object ReportSheetCalculator {
    private val MC = MathContext.DECIMAL128
    private val TWO = 2

    data class ScoredStudent(
        val studentId: String,
        val mark: BigDecimal?
    )

    data class HeadcountResult(
        val headcount: Int,
        val boysCount: Int,
        val girlsCount: Int,
        val sexCountWarning: Boolean
    )

    fun weightedMark(mark: BigDecimal?, coefficient: BigDecimal): BigDecimal? {
        if (mark == null) return null
        return round2(mark.multiply(coefficient, MC))
    }

    /**
     * Rang compétition (1, 2, 2, 4) sur la moyenne exacte parmi les élèves SCORED.
     * PENDING / sans note → rang null.
     */
    fun ranks(
        scored: List<ScoredStudent>,
        policy: RankingPolicy = RankingPolicy.COMPETITION
    ): Map<String, Int?> {
        val withMark = scored.filter { it.mark != null }
            .sortedWith { a, b -> b.mark!!.compareTo(a.mark!!) }
        val result = scored.associate { it.studentId to null as Int? }.toMutableMap()
        var i = 0
        var denseCounter = 0
        while (i < withMark.size) {
            val current = withMark[i].mark!!
            var j = i
            while (j < withMark.size && withMark[j].mark!!.compareTo(current) == 0) j++
            val rank = when (policy) {
                RankingPolicy.COMPETITION -> i + 1
                RankingPolicy.DENSE -> {
                    denseCounter += 1
                    denseCounter
                }
            }
            for (k in i until j) {
                result[withMark[k].studentId] = rank
            }
            i = j
        }
        return result
    }

    fun appreciation(mark: BigDecimal?, status: GradeStatus, scale: AppreciationScale?): String? {
        if (status != GradeStatus.SCORED || mark == null || scale == null) return null
        return findBand(mark, scale.bands)?.code
    }

    fun findBand(value: BigDecimal, bands: List<ScoreBand>): ScoreBand? =
        bands.firstOrNull { band ->
            value.compareTo(band.minInclusive) >= 0 &&
                (band.maxExclusive == null || value.compareTo(band.maxExclusive) < 0)
        }

    fun headcounts(sexes: List<Sex?>): HeadcountResult {
        val headcount = sexes.size
        val boys = sexes.count { it == Sex.MALE }
        val girls = sexes.count { it == Sex.FEMALE }
        return HeadcountResult(
            headcount = headcount,
            boysCount = boys,
            girlsCount = girls,
            sexCountWarning = boys + girls != headcount
        )
    }

    fun expectedPageCount(lineCount: Int, maxRowsPerPage: Int): Int {
        require(maxRowsPerPage > 0) { "maxRowsPerPage must be positive" }
        if (lineCount <= 0) return 1
        return (lineCount + maxRowsPerPage - 1) / maxRowsPerPage
    }

    fun round2(value: BigDecimal): BigDecimal =
        value.setScale(TWO, RoundingMode.HALF_UP)

    /** Échelle LETTER par défaut (Q-10) si le barème n'en définit pas encore. */
    fun defaultLetterScale(): AppreciationScale =
        AppreciationScale(
            code = "LETTER",
            bands = listOf(
                ScoreBand(code = "C", minInclusive = BigDecimal.ZERO, maxExclusive = BigDecimal("12")),
                ScoreBand(code = "B", minInclusive = BigDecimal("12"), maxExclusive = BigDecimal("14")),
                ScoreBand(code = "B+", minInclusive = BigDecimal("14"), maxExclusive = BigDecimal("16")),
                ScoreBand(code = "A", minInclusive = BigDecimal("16"), maxExclusive = null)
            )
        )
}
