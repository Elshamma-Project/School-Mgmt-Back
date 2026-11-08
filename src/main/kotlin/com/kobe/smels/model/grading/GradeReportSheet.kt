package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Fiche de report de notes (document officiel trimestriel), distincte de [GradeSheet]
 * (document de travail avec bonus/malus).
 *
 * Une fiche = une classe + une matière ([ClassSubject]) + un trimestre : deux blocs côte à côte
 * (séquence 1 et séquence 2 du trimestre). Instantané produit à partir des [SequenceMark] ;
 * libellés et compteurs figés à la génération / soumission.
 *
 * Impression : A4 paysage, jusqu'à `GradingScheme.reportSheetMaxRowsPerPage` lignes par page
 * (défaut 45) ; au-delà, pages supplémentaires avec en-tête de tableau répété (exception documentée
 * à la règle « une page » des bulletins / emplois du temps). Version vierge (noms seuls) pour
 * saisie manuscrite (Q-36).
 */
@Document(collection = "grading_report_sheets")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_classsubject_term",
        def = "{'schoolId': 1, 'classSubjectId': 1, 'termNumber': 1}",
        unique = true
    ),
    CompoundIndex(name = "idx_teacher_term", def = "{'schoolId': 1, 'teacherId': 1, 'termNumber': 1}"),
    CompoundIndex(name = "idx_class_term", def = "{'schoolId': 1, 'classId': 1, 'termNumber': 1}"),
    CompoundIndex(name = "idx_year_status", def = "{'schoolId': 1, 'academicYearId': 1, 'status': 1}")
)
data class GradeReportSheet(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,

    /** Trimestre 1..3 (le « N° » de l'en-tête papier = numéro du trimestre, Q-35). */
    var termNumber: Int,

    /** Staff.id de l'enseignant titulaire figé à la génération. */
    var teacherId: String? = null,
    var status: ReportSheetStatus = ReportSheetStatus.DRAFT,

    var submittedAt: Instant? = null,
    var submittedBy: String? = null,
    var visaSupervisor: ReportSheetVisa? = null,
    var visaPrincipal: ReportSheetVisa? = null,
    var rejectionReason: String? = null,
    var rejectedAt: Instant? = null,
    var rejectedBy: String? = null,

    /** Libellés et compteurs figés (photographie). */
    var teacherLabel: String? = null,
    var subjectLabel: LocalizedText = LocalizedText(),
    var classLabel: String? = null,
    @Field(targetType = FieldType.DECIMAL128)
    var coefficient: BigDecimal = BigDecimal.ONE,
    var headcount: Int = 0,
    var boysCount: Int = 0,
    var girlsCount: Int = 0,
    /** Vrai si boysCount + girlsCount != headcount (sexe manquant sur au moins un élève). */
    var sexCountWarning: Boolean = false,

    var sequence1SessionId: String? = null,
    var sequence2SessionId: String? = null,
    var sequence1ExamDate: LocalDate? = null,
    var sequence2ExamDate: LocalDate? = null,
    var sequence1Number: Int? = null,
    var sequence2Number: Int? = null,

    var lines: List<GradeReportSheetLine> = emptyList(),

    /** Échelle d'appréciation utilisée pour App (code dans GradingScheme.appreciationScales). */
    var appreciationScaleCode: String? = null,
    var generatedAt: Instant? = null,
    var lastRegeneratedAt: Instant? = null
) : TenantDocument()

enum class ReportSheetStatus {
    DRAFT,
    SUBMITTED,
    VISA_SG,
    VISA_PRINCIPAL,
    REJECTED
}

data class ReportSheetVisa(
    val userId: String,
    val at: Instant
)

/**
 * Une ligne élève. `appreciationOverride` permet une surcharge manuelle exceptionnelle ;
 * sinon App = calcul automatique (Q-36).
 */
data class GradeReportSheetLine(
    val rollNumber: Int?,
    val studentId: String,
    /** Nom figé « NOM PRÉNOMS ». */
    val studentLabel: String,
    val sex: Sex?,
    val sequence1: ReportSheetSequenceCell = ReportSheetSequenceCell(),
    val sequence2: ReportSheetSequenceCell = ReportSheetSequenceCell()
)

data class ReportSheetSequenceCell(
    @Field(targetType = FieldType.DECIMAL128)
    val mark: BigDecimal? = null,
    val markStatus: GradeStatus = GradeStatus.PENDING,
    @Field(targetType = FieldType.DECIMAL128)
    val coefficient: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val weightedMark: BigDecimal? = null,
    val rank: Int? = null,
    /** Appréciation calculée (vide si PENDING). */
    val appreciation: String? = null,
    /** Surcharge manuelle éventuelle ; prioritaire à l'affichage si non nulle. */
    val appreciationOverride: String? = null
) {
    fun displayedAppreciation(): String? = appreciationOverride ?: appreciation
}
