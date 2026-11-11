package com.kobe.smels.configuration.seed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Miroir du fichier `seed-el-shammah.json` (champs `_…` et inconnus ignorés).
 * Les BigDecimal arrivent souvent en chaînes ; Jackson les coerce.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ElShammahSeedFile(
    val academic_schools: List<SeedSchool> = emptyList(),
    val academic_years: List<SeedYear> = emptyList(),
    val academic_levels: List<SeedLevel> = emptyList(),
    val academic_subject_groups: List<SeedSubjectGroup> = emptyList(),
    val academic_subjects: List<SeedSubject> = emptyList(),
    val grading_schemes: List<SeedGradingScheme> = emptyList(),
    val reporting_templates: List<SeedReportTemplate> = emptyList(),
    val grading_sessions: List<SeedSession> = emptyList(),
    val academic_classes: List<SeedClass> = emptyList(),
    val academic_class_subjects: List<SeedClassSubject> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedLocalized(
    val fr: String? = null,
    val en: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedContact(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedOfficialHeader(
    val cycles: List<String> = emptyList(),
    val republic: SeedLocalized? = null,
    val motto: SeedLocalized? = null,
    val ministry: SeedLocalized? = null,
    val regionalDelegation: SeedLocalized? = null,
    val divisionalDelegation: SeedLocalized? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedSchool(
    val code: String,
    val name: SeedLocalized,
    val shortName: String? = null,
    val officialHeaders: List<SeedOfficialHeader> = emptyList(),
    val contact: SeedContact = SeedContact(),
    val postalBox: String? = null,
    val signaturePlace: String? = null,
    val headTitle: SeedLocalized? = null,
    val reportCardNotice: SeedLocalized? = null,
    val reportCardDisclaimer: SeedLocalized? = null,
    val timezone: String? = null,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedTerm(
    val number: Int,
    val name: SeedLocalized,
    val startDate: String,
    val endDate: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedYear(
    val schoolCode: String,
    val label: String,
    val startDate: String,
    val endDate: String,
    val current: Boolean = false,
    val status: String = "PLANNED",
    val terms: List<SeedTerm> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedLevel(
    val schoolCode: String,
    val code: String,
    val name: SeedLocalized,
    val cycle: String,
    val section: String,
    val order: Int,
    val equivalenceIndex: Int? = null,
    val nextLevelCode: String? = null,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedSubjectGroup(
    val schoolCode: String,
    val code: String,
    val name: SeedLocalized,
    val displayOrder: Int = 0,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedSubject(
    val schoolCode: String,
    val code: String,
    val name: SeedLocalized,
    val shortName: SeedLocalized? = null,
    val timetableAlias: String? = null,
    val kind: String = "ACADEMIC",
    val teachingLanguage: String? = null,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedScoreBand(
    val code: String,
    val label: SeedLocalized? = null,
    val minInclusive: String,
    val maxExclusive: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedAppreciationScale(
    val code: String,
    val bands: List<SeedScoreBand> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedDistinctionRule(
    val level: String,
    val minAverage: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedGradingScheme(
    val schoolCode: String,
    val code: String,
    val name: SeedLocalized,
    val cycles: List<String> = emptyList(),
    val sections: List<String> = emptyList(),
    val maxScore: String = "20",
    val passMark: String = "10",
    val classSuccessThreshold: String = "12",
    val decimals: Int = 2,
    val roundingPolicy: String = "HALF_UP",
    val roundTermSubjectAverages: Boolean = true,
    val missingMarkPolicy: String = "IGNORE",
    val absencePolicy: String = "EXCLUDE",
    val rankingPolicy: String = "COMPETITION",
    val rankingEligibility: String = "HAS_AVERAGE",
    val annualAveragePolicy: String = "WEIGHTED_TOTAL",
    val subjectYearAveragePolicy: String = "MEAN_OF_TERM_AVERAGES",
    val appreciationScales: List<SeedAppreciationScale> = emptyList(),
    val overallMentionBands: List<SeedScoreBand> = emptyList(),
    val honourRollMinAverage: String = "12",
    val honourRollBlockedBySanction: Boolean = true,
    val effortSubjectThreshold: String? = null,
    val distinctionRules: List<SeedDistinctionRule> = emptyList(),
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedReportTemplate(
    val schoolCode: String,
    val code: String,
    val name: SeedLocalized,
    val periodType: String,
    val cycles: List<String> = emptyList(),
    val sections: List<String> = emptyList(),
    val levelCodes: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val page: String = "A4_PORTRAIT",
    val sequenceColumns: List<Int> = emptyList(),
    val appreciationScaleCode: String = "LETTER",
    val showCompetency: Boolean = false,
    val showSubjectRank: Boolean = false,
    val showSubjectClassAverage: Boolean = false,
    val showSubjectPassedCount: Boolean = false,
    val showGroupTotals: Boolean = true,
    val showTermRecap: Boolean = true,
    val showClassProfile: Boolean = true,
    val showEffortSubjects: Boolean = false,
    val showTotalPointsOverMax: Boolean = false,
    val classProfileThreshold: String? = null,
    val legend: SeedLocalized? = null,
    val minFontSizePt: Double = 6.5,
    val maxFontSizePt: Double = 9.0,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedSession(
    val schoolCode: String,
    val academicYearLabel: String,
    val number: Int,
    val termNumber: Int,
    val shortLabel: String,
    val name: SeedLocalized,
    val startDate: String? = null,
    val endDate: String? = null,
    val entryDeadline: String? = null,
    val status: String = "PLANNED"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedClass(
    val schoolCode: String,
    val academicYearLabel: String,
    val levelCode: String,
    val section: String,
    val cycle: String,
    val name: String,
    val trackCode: String? = null,
    val defaultLanguage: String,
    val availableLanguages: List<String> = emptyList(),
    val gradingSchemeCode: String? = null,
    val capacity: Int? = null,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedClassSubject(
    val schoolCode: String,
    val academicYearLabel: String,
    val className: String,
    val subjectCode: String,
    val groupCode: String,
    val coefficient: String? = null,
    /** Hypothèse documentée si [coefficient] est null (Q-42). */
    val coefficientHypothesis: String? = null,
    val weeklyHours: Int? = null,
    val teachingLanguage: String? = null,
    val displayOrder: Int = 0,
    val maxScore: String = "20",
    val countsInAverage: Boolean = true,
    val optional: Boolean = false,
    val active: Boolean = true
)
