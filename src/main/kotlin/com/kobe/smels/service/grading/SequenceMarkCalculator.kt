package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.TotalizationFormula
import com.kobe.smels.model.grading.TotalizationRule
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Arithmétique pure de l'étape 0 (docs/04) — sans accès base.
 * Formule confirmée (Q-25) : moyenne des notes + bonus − malus, plafond à maxScore.
 * Une fiche Excel chiffrée reste utile pour les cas limites (pondération, plafond bonus).
 */
object SequenceMarkCalculator {
    private val MC = MathContext.DECIMAL128
    private val TWENTY = BigDecimal("20")
    private val TWO = 2

    data class InputColumn(
        val assessment: Assessment,
        val grade: Grade?
    )

    data class Result(
        val value: BigDecimal?,
        val status: GradeStatus
    )

    fun totalize(rule: TotalizationRule, columns: List<InputColumn>): Result {
        return when (rule.formula) {
            TotalizationFormula.MANUAL,
            TotalizationFormula.AVERAGE_PLUS_BONUS_MINUS_MALUS,
            TotalizationFormula.WEIGHTED_AVERAGE_PLUS_BONUS_MINUS_MALUS,
            TotalizationFormula.SUM -> compute(rule, columns)
        }
    }

    private fun compute(rule: TotalizationRule, columns: List<InputColumn>): Result {
        if (rule.formula == TotalizationFormula.MANUAL) {
            val mark = columns.firstOrNull { it.assessment.type == AssessmentType.SEQUENCE_MARK }
                ?: columns.firstOrNull()
            val grade = mark?.grade
            return if (grade == null || grade.status != GradeStatus.SCORED || grade.value == null) {
                Result(null, GradeStatus.PENDING)
            } else {
                Result(round2(applyBounds(grade.value!!, rule)), GradeStatus.SCORED)
            }
        }

        val evaluations = columns.filter { it.assessment.type.isEvaluation() }
        val bonuses = columns.filter { it.assessment.type == AssessmentType.BONUS }
        val maluses = columns.filter { it.assessment.type == AssessmentType.MALUS }

        val scoredEvals = evaluations.mapNotNull { col ->
            val grade = col.grade ?: return@mapNotNull null
            if (grade.status != GradeStatus.SCORED || grade.value == null) return@mapNotNull null
            val scaled = scaleToTwenty(grade.value!!, col.assessment.maxScore)
            ScaledScore(scaled, col.assessment.weight)
        }

        if (scoredEvals.isEmpty() && bonuses.none { scored(it) } && maluses.none { scored(it) }) {
            // Absent/exempted only on sequence mark column → propagate
            val sole = evaluations.singleOrNull()?.grade
            if (sole != null && sole.status != GradeStatus.PENDING && sole.status != GradeStatus.SCORED) {
                return Result(null, sole.status)
            }
            return Result(null, GradeStatus.PENDING)
        }

        val average = when {
            scoredEvals.isEmpty() -> BigDecimal.ZERO
            rule.formula == TotalizationFormula.SUM ->
                scoredEvals.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.value, MC) }
            rule.formula == TotalizationFormula.WEIGHTED_AVERAGE_PLUS_BONUS_MINUS_MALUS -> {
                val weightSum = scoredEvals.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.weight, MC) }
                if (weightSum.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
                else scoredEvals.fold(BigDecimal.ZERO) { acc, s ->
                    acc.add(s.value.multiply(s.weight, MC), MC)
                }.divide(weightSum, MC)
            }
            else -> {
                val n = BigDecimal(scoredEvals.size)
                scoredEvals.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.value, MC) }.divide(n, MC)
            }
        }

        val bonusSum = sumScored(bonuses)
        val malusSum = sumScored(maluses)
        val raw = average.add(bonusSum, MC).subtract(malusSum, MC)
        return Result(round2(applyBounds(raw, rule)), GradeStatus.SCORED)
    }

    private fun scored(col: InputColumn): Boolean =
        col.grade?.status == GradeStatus.SCORED && col.grade.value != null

    private fun sumScored(columns: List<InputColumn>): BigDecimal =
        columns.fold(BigDecimal.ZERO) { acc, col ->
            val grade = col.grade
            if (grade?.status == GradeStatus.SCORED && grade.value != null) {
                acc.add(grade.value!!, MC)
            } else acc
        }

    private fun scaleToTwenty(value: BigDecimal, maxScore: BigDecimal): BigDecimal {
        if (maxScore.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO
        if (maxScore.compareTo(TWENTY) == 0) return value
        return value.multiply(TWENTY, MC).divide(maxScore, MC)
    }

    private fun applyBounds(value: BigDecimal, rule: TotalizationRule): BigDecimal {
        var v = value
        if (v.compareTo(rule.minScore) < 0) v = rule.minScore
        if (rule.capAtMax && v.compareTo(rule.maxScore) > 0) v = rule.maxScore
        return v
    }

    fun round2(value: BigDecimal): BigDecimal =
        value.setScale(TWO, RoundingMode.HALF_UP)

    fun assertScoreBounds(value: BigDecimal, maxScore: BigDecimal) {
        require(value.scale() <= 2 || value.stripTrailingZeros().scale() <= 2) {
            "Score must have at most 2 decimal places"
        }
        require(value.compareTo(BigDecimal.ZERO) >= 0) { "Score cannot be negative" }
        require(value.compareTo(maxScore) <= 0) { "Score exceeds maxScore" }
    }

    private fun AssessmentType.isEvaluation(): Boolean =
        this != AssessmentType.BONUS && this != AssessmentType.MALUS

    private data class ScaledScore(val value: BigDecimal, val weight: BigDecimal)
}
