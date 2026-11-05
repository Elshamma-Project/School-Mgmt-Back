package com.kobe.smels.model.reporting

import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Bulletin GÉNÉRÉ : photographie figée de ce qui s'imprime. Il ne se recalcule jamais tout seul :
 * si une note change après génération, un nouveau bulletin (revision + 1) est généré et l'ancien
 * passe en SUPERSEDED. Les noms de matières, d'enseignants, de classe sont recopiés tels qu'imprimés,
 * pour qu'un changement d'enseignant ou de coefficient n'altère pas un bulletin déjà délivré.
 *
 * Visibilité : un parent ou un élève ne peut télécharger qu'un bulletin ANNUAL au statut PUBLISHED.
 * Les notes en temps réel viennent de `grading_grades`, pas d'ici.
 */
@Document(collection = "reporting_report_cards")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_enrollment_period",
        def = "{'schoolId': 1, 'enrollmentId': 1, 'period.type': 1, 'period.number': 1, 'revision': -1}"
    ),
    CompoundIndex(
        name = "idx_class_period_status",
        def = "{'schoolId': 1, 'classId': 1, 'period.type': 1, 'period.number': 1, 'status': 1}"
    )
)
data class ReportCard(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var enrollmentId: String,
    var studentId: String,
    var classId: String,
    var period: ReportPeriod,

    /** 1 pour la première génération, +1 à chaque régénération. */
    var revision: Int = 1,
    var status: ReportCardStatus = ReportCardStatus.DRAFT,

    /** Langue dans laquelle le bulletin a été généré. */
    var language: Language,

    /** Code d'authenticité imprimé (code-barres / QR). Aléatoire, non devinable. */
    @Indexed(unique = true)
    var verificationCode: String,

    var student: StudentSnapshot,
    var classInfo: ClassSnapshot,
    var groups: List<ReportGroup> = emptyList(),
    var overall: OverallResult,
    var termRecaps: List<TermRecap> = emptyList(),
    var classProfile: ClassProfile? = null,
    var review: ReviewSnapshot? = null,
    var signature: SignatureBlock? = null,

    /** Barème utilisé (GradingScheme.id) et version des règles de calcul (GradeCalculationService), pour rejouer et expliquer un écart. */
    var gradingSchemeId: String? = null,

    /** Gabarit utilisé (ReportTemplate.code) : mise en page, colonnes, échelle d'appréciation. */
    var templateCode: String? = null,

    /** Fichier PDF A4 généré (une seule page), dans le stockage de fichiers. */
    var pdfFileId: String? = null,
    var calculationVersion: String,
    var generatedAt: Instant,
    var validatedAt: Instant? = null,
    var validatedBy: String? = null,
    var publishedAt: Instant? = null,
    var publishedBy: String? = null
) : TenantDocument()

enum class ReportCardStatus {
    /** Généré, pas encore relu. */
    DRAFT,

    /** Relu par l'administration. */
    VALIDATED,

    /** Visible et téléchargeable par l'élève et les parents (bulletin annuel). */
    PUBLISHED,

    /** Remplacé par une révision plus récente. */
    SUPERSEDED,

    /** Annulé. */
    REVOKED
}

data class StudentSnapshot(
    val lastName: String,
    val firstName: String,
    val matricule: String,
    val sex: Sex,
    val dateOfBirth: LocalDate? = null,
    val placeOfBirth: String? = null,
    val repeater: Boolean = false,
    val photoFileId: String? = null
)

data class ClassSnapshot(
    val className: String,
    val levelCode: String,
    val section: Section,
    val mainTeacherName: String? = null,
    /** Effectif de la classe (inscrits). */
    val effectif: Int,
    /** Nombre d'élèves classés (moyenne calculée). */
    val rankedCount: Int,
    val rollNumber: Int? = null
)

/** Un bloc de matières avec son sous-total (« TOTAL LANGUES ET LITTERATURES »). */
data class ReportGroup(
    val groupId: String,
    val code: String,
    val name: String,
    val displayOrder: Int = 0,
    val lines: List<ReportLine> = emptyList(),
    @Field(targetType = FieldType.DECIMAL128)
    val totalCoefficient: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    val totalPoints: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val average: BigDecimal? = null
)

/** Une ligne de matière : notes DEV1..DEV6, moyenne, coefficient, total, appréciation. */
data class ReportLine(
    val classSubjectId: String,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val teacherId: String? = null,
    val teacherName: String? = null,
    val marks: List<ReportMark> = emptyList(),
    @Field(targetType = FieldType.DECIMAL128)
    val average: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val coefficient: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    val total: BigDecimal? = null,
    val appreciationCode: String? = null,
    /** Libellé de l'appréciation dans la langue du bulletin (« A », « E.C.A », « N.A »...). */
    val appreciationLabel: String? = null,

    /** Compétence visée (bulletin de trimestre) : texte saisi par l'enseignant, figé ici. */
    val competency: String? = null,

    /** Rang dans la matière (« 6th ») : bulletin de trimestre. */
    val subjectRank: Int? = null,

    /** Moyenne de la classe dans la matière. */
    @Field(targetType = FieldType.DECIMAL128)
    val subjectClassAverage: BigDecimal? = null,

    /** Nombre d'élèves de la classe dont la moyenne dans la matière atteint le seuil de validation (« Av >= 10 »). */
    val subjectPassedCount: Int? = null,
    val displayOrder: Int = 0,
    val countsInAverage: Boolean = true
)

/** Une colonne DEVn d'une ligne. */
data class ReportMark(
    val sessionNumber: Int,
    val label: String,
    @Field(targetType = FieldType.DECIMAL128)
    val value: BigDecimal? = null,
    val status: GradeStatus? = null
)

/** Bloc « Résultat annuel de l'élève ». */
data class OverallResult(
    @Field(targetType = FieldType.DECIMAL128)
    val average: BigDecimal? = null,
    val rank: Int? = null,
    val rankedCount: Int = 0,
    @Field(targetType = FieldType.DECIMAL128)
    val totalCoefficient: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    val totalPoints: BigDecimal? = null,
    /** Appréciation générale (« TRAVAIL INSUFFISANT », « FAIR »). */
    val mentionCode: String? = null,
    val mentionLabel: String? = null,
    /** « PASSED IN 15 SUBJECTS » : matières dont la moyenne atteint le seuil de validation. */
    val subjectsPassed: Int? = null,
    val subjectsCounted: Int = 0,

    /** Élève classé ou non. Observé : « 0/0 » pour un élève sans moyenne de trimestre (Q-07). */
    val rankStatus: RankStatus = RankStatus.RANKED,

    /** Matières à améliorer (« MAKE EFFORT IN : LIFE APT., MATHEMATICS »). */
    val effortSubjectNames: List<String> = emptyList()
)

/** Bloc « Rappels moyennes » : moyenne et rang de chaque trimestre. */
data class TermRecap(
    val termNumber: Int,
    @Field(targetType = FieldType.DECIMAL128)
    val average: BigDecimal? = null,
    val rank: Int? = null,
    val rankedCount: Int? = null
)

/** Bloc « Profil de la classe ». */
data class ClassProfile(
    @Field(targetType = FieldType.DECIMAL128)
    val threshold: BigDecimal,
    /** Nombre d'élèves dont la moyenne atteint le seuil (« Moyenne >= 12 : 5 »). */
    val countAtOrAboveThreshold: Int,
    @Field(targetType = FieldType.DECIMAL128)
    val classAverage: BigDecimal? = null,
    /** En pourcentage, ex. 41.67. */
    @Field(targetType = FieldType.DECIMAL128)
    val successRate: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val highestAverage: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val lowestAverage: BigDecimal? = null
)

/** Blocs « Travail de l'élève » et « Conduite de l'élève » figés au moment de la génération. */
data class ReviewSnapshot(
    val absenceHoursTotal: Int = 0,
    val absenceHoursUnjustified: Int = 0,
    val lateCount: Int = 0,
    val distinction: DistinctionLevel = DistinctionLevel.NONE,
    val workSanction: SanctionLevel = SanctionLevel.NONE,
    val conductSanction: SanctionLevel = SanctionLevel.NONE,
    val councilDecision: CouncilDecision? = null,
    val councilComment: String? = null
)

/** Cachet et signature : « YAOUNDE, le 26 Mai 2026 — LE PRINCIPAL — NOM ». */
data class SignatureBlock(
    val place: String? = null,
    val date: LocalDate? = null,
    val signatoryTitle: String? = null,
    val signatoryName: String? = null
)

/** Classement de l'élève. */
enum class RankStatus { RANKED, NOT_RANKED }
