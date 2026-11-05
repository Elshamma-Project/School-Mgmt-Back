package com.kobe.smels.model.reporting

import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Éléments du bulletin qui ne se calculent pas à partir des notes mais se saisissent :
 * absences, retards, tableau d'honneur, avertissements, blâmes, décision du conseil de classe.
 * Un document par élève (inscription) et par période (trimestre ou année).
 *
 * Saisi par l'administration (surveillants, secrétariat) et par le conseil de classe.
 */
@Document(collection = "reporting_period_reviews")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_enrollment_period",
        def = "{'schoolId': 1, 'enrollmentId': 1, 'period.type': 1, 'period.number': 1}",
        unique = true
    ),
    CompoundIndex(name = "idx_class_period", def = "{'schoolId': 1, 'classId': 1, 'period.type': 1, 'period.number': 1}")
)
data class StudentPeriodReview(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var enrollmentId: String,
    var studentId: String,
    var classId: String,
    var period: ReportPeriod,

    /** Absences totales en heures (« Absences Totales 2H »). */
    var absenceHoursTotal: Int = 0,

    /** Dont non justifiées (« Absences NJ »). */
    var absenceHoursUnjustified: Int = 0,
    var lateCount: Int = 0,

    /** Absentéisme en jours (« Absenteisme 0 Days » sur le bulletin de trimestre anglophone). */
    var absenceDays: Int? = null,

    /** Tableau d'honneur : NONE, HONOR_ROLL, avec encouragements, avec félicitations. */
    var distinction: DistinctionLevel = DistinctionLevel.NONE,

    /** « Avertissement Travail » / « Blâme Travail ». */
    var workSanction: SanctionLevel = SanctionLevel.NONE,

    /** « Avertissement Conduite » / « Blâme Conduite ». */
    var conductSanction: SanctionLevel = SanctionLevel.NONE,

    /** Décision du conseil de classe (période ANNUAL surtout). */
    var councilDecision: CouncilDecision? = null,
    var councilComment: String? = null
) : TenantDocument()


/** WARNING = avertissement ; BLAME = blâme (« S. Academic Warning » en anglais). */
enum class SanctionLevel { NONE, WARNING, BLAME }

enum class CouncilDecision { PROMOTED, REPEAT, EXCLUDED, OTHER }
