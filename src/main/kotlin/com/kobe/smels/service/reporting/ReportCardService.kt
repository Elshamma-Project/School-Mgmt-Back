package com.kobe.smels.service.reporting

import com.kobe.smels.dto.reporting.GenerateReportCardsRequest
import com.kobe.smels.dto.reporting.ReportCardSummaryResponse
import com.kobe.smels.dto.reporting.VerificationResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.reporting.ClassProfile
import com.kobe.smels.model.reporting.ClassSnapshot
import com.kobe.smels.model.reporting.OverallResult
import com.kobe.smels.model.reporting.RankStatus
import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportGroup
import com.kobe.smels.model.reporting.ReportLine
import com.kobe.smels.model.reporting.ReportMark
import com.kobe.smels.model.reporting.ReportPeriod
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.reporting.ReviewSnapshot
import com.kobe.smels.model.reporting.SignatureBlock
import com.kobe.smels.model.reporting.StudentSnapshot
import com.kobe.smels.model.reporting.TermRecap
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.reporting.ReportCardRepository
import com.kobe.smels.repository.reporting.StudentPeriodReviewRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.grading.api.EvaluationSessionApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
class ReportCardService(
    private val reportCardRepository: ReportCardRepository,
    private val studentPeriodReviewRepository: StudentPeriodReviewRepository,
    private val reportTemplateService: ReportTemplateService,
    private val gradeCalculationService: GradeCalculationService,
    private val reportCardPdfService: ReportCardPdfService,
    private val schoolClassApi: SchoolClassApi,
    private val academicYearApi: AcademicYearApi,
    private val enrollmentApi: EnrollmentApi,
    private val studentApi: StudentApi,
    private val staffApi: StaffApi,
    private val evaluationSessionApi: EvaluationSessionApi,
    private val outboxWriter: OutboxWriter
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val douala = ZoneId.of("Africa/Douala")

    fun generate(
        currentUser: CurrentUser,
        request: GenerateReportCardsRequest
    ): Flux<ReportCardSummaryResponse> {
        ReportingAccess.assertAdmin(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, request.schoolId)
        return schoolClassApi.getSummary(schoolId, request.classId).flatMapMany { clazz ->
            val language = request.language ?: clazz.defaultLanguage
            val schemeId = GradeCalculationService.requireScheme(clazz.gradingSchemeId)
            reportTemplateService.resolveForPeriod(schoolId, request.periodType, request.templateCode)
                .flatMapMany { template ->
                    assertPeriodMatches(template, request.periodType)
                    val sessionNumbers = template.sequenceColumns
                    evaluationSessionApi.areRelevantSessionsClosed(
                        schoolId, clazz.academicYearId, sessionNumbers
                    ).flatMapMany { allClosed ->
                        // BR-24 : brouillon OK si séquences ouvertes ; définitif sinon
                        val asDraft = !allClosed
                        gradeCalculationService.calculateClass(
                            schoolId = schoolId,
                            classId = request.classId,
                            periodType = request.periodType,
                            periodNumber = request.periodNumber,
                            schemeId = schemeId,
                            template = template,
                            language = language
                        ).flatMapMany { computation ->
                            enrollmentApi.findEnrolledByClass(schoolId, request.classId).collectList()
                                .flatMapMany { enrollments ->
                                    academicYearApi.getSummary(schoolId, clazz.academicYearId).flatMapMany { year ->
                                        resolveMainTeacher(schoolId, null).flatMapMany { mainTeacher ->
                                            Flux.fromIterable(enrollments).concatMap { enrollment ->
                                                studentApi.getIdentity(schoolId, enrollment.studentId).flatMap { identity ->
                                                    loadReview(
                                                        schoolId,
                                                        enrollment.id,
                                                        request.periodType,
                                                        request.periodNumber
                                                    ).flatMap { reviewOpt ->
                                                        supersedePrevious(
                                                            schoolId,
                                                            enrollment.id,
                                                            request.periodType,
                                                            request.periodNumber
                                                        ).flatMap { nextRevision ->
                                                            val studentResult = computation.students[enrollment.studentId]
                                                                ?: return@flatMap Mono.empty()
                                                            val card = buildCard(
                                                                schoolId = schoolId,
                                                                academicYearId = clazz.academicYearId,
                                                                enrollmentId = enrollment.id,
                                                                studentId = enrollment.studentId,
                                                                classId = request.classId,
                                                                periodType = request.periodType,
                                                                periodNumber = request.periodNumber,
                                                                revision = nextRevision,
                                                                language = language,
                                                                identity = identity,
                                                                repeater = enrollment.repeater,
                                                                rollNumber = enrollment.rollNumber,
                                                                className = clazz.name,
                                                                levelCode = clazz.levelCode,
                                                                section = clazz.section,
                                                                mainTeacherName = mainTeacher.ifBlank { null },
                                                                effectif = enrollments.size,
                                                                rankedCount = computation.rankedCount,
                                                                computation = computation,
                                                                studentResult = studentResult,
                                                                template = template,
                                                                schemeId = schemeId,
                                                                review = reviewOpt.orElse(null),
                                                                asDraft = asDraft
                                                            )
                                                            reportCardRepository.save(card).map { toSummary(it) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
        }
    }

    fun validate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<ReportCardSummaryResponse> {
        ReportingAccess.assertAdmin(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return reportCardRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_CARD_NOT_FOUND", "Report card not found")))
            .flatMap { card ->
                if (card.status != ReportCardStatus.DRAFT) {
                    Mono.error(BusinessRuleException("INVALID_STATUS", "Only DRAFT report cards can be validated"))
                } else {
                    card.status = ReportCardStatus.VALIDATED
                    card.validatedAt = Instant.now()
                    card.validatedBy = currentUser.accountId
                    reportCardRepository.save(card).map { toSummary(it) }
                }
            }
    }

    fun publish(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<ReportCardSummaryResponse> {
        ReportingAccess.assertAdmin(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return reportCardRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_CARD_NOT_FOUND", "Report card not found")))
            .flatMap { card ->
                if (card.period.type != ReportPeriodType.ANNUAL) {
                    Mono.error(
                        BusinessRuleException(
                            "PUBLISH_ANNUAL_ONLY",
                            "Only annual report cards can be published for parents/students"
                        )
                    )
                } else if (card.status != ReportCardStatus.VALIDATED && card.status != ReportCardStatus.DRAFT) {
                    Mono.error(BusinessRuleException("INVALID_STATUS", "Cannot publish this report card"))
                } else {
                    card.status = ReportCardStatus.PUBLISHED
                    card.publishedAt = Instant.now()
                    card.publishedBy = currentUser.accountId
                    reportCardRepository.save(card).flatMap { saved ->
                        outboxWriter.append(
                            schoolId = schoolId,
                            module = "reporting",
                            aggregateType = "ReportCard",
                            aggregateId = saved.id!!,
                            eventType = "reporting.report-card.published",
                            payload = mapOf(
                                "reportCardId" to saved.id,
                                "studentId" to saved.studentId,
                                "periodType" to saved.period.type.name,
                                "periodNumber" to saved.period.number
                            )
                        ).thenReturn(toSummary(saved))
                    }
                }
            }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<ReportCardSummaryResponse> {
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return reportCardRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_CARD_NOT_FOUND", "Report card not found")))
            .flatMap { card ->
                assertCanRead(currentUser, card)
                Mono.just(toSummary(card))
            }
    }

    fun downloadPdf(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<ByteArray> {
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return reportCardRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_CARD_NOT_FOUND", "Report card not found")))
            .flatMap { card ->
                assertCanDownload(currentUser, card)
                reportTemplateService.getByCode(
                    schoolId,
                    card.templateCode ?: ReportTemplateService.CODE_ANNUAL
                ).flatMap { template ->
                    academicYearApi.getSummary(schoolId, card.academicYearId).map { year ->
                        reportCardPdfService.render(card, template, year.label)
                    }
                }
            }
    }

    fun verify(verificationCode: String): Mono<VerificationResponse> =
        reportCardRepository.findByVerificationCode(verificationCode.trim())
            .map { card ->
                VerificationResponse(
                    valid = true,
                    className = card.classInfo.className,
                    academicYearLabel = null,
                    issuedAt = card.publishedAt ?: card.generatedAt
                )
            }
            .flatMap { resp ->
                if (!resp.valid) Mono.just(resp)
                else reportCardRepository.findByVerificationCode(verificationCode.trim())
                    .flatMap { card ->
                        academicYearApi.getSummary(card.schoolId, card.academicYearId)
                            .map { year ->
                                resp.copy(academicYearLabel = year.label)
                            }
                            .onErrorReturn(resp)
                    }
            }
            .defaultIfEmpty(VerificationResponse(valid = false))

    private fun assertCanRead(currentUser: CurrentUser, card: ReportCard) {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN, Role.TEACHER)) return
        if (currentUser.hasAnyRole(Role.STUDENT) &&
            currentUser.profileType == ProfileType.STUDENT &&
            currentUser.profileId == card.studentId
        ) {
            if (card.period.type == ReportPeriodType.ANNUAL && card.status == ReportCardStatus.PUBLISHED) return
            throw ForbiddenException("FORBIDDEN", "Students can only read published annual report cards")
        }
        if (currentUser.hasAnyRole(Role.PARENT) && currentUser.profileType == ProfileType.GUARDIAN) {
            // appartenance enfant vérifiée via StudentApi.findGuardianLink à l'appel si besoin
            if (card.period.type == ReportPeriodType.ANNUAL && card.status == ReportCardStatus.PUBLISHED) return
            throw ForbiddenException("FORBIDDEN", "Parents can only read published annual report cards")
        }
        throw ForbiddenException("FORBIDDEN", "Cannot read this report card")
    }

    private fun assertCanDownload(currentUser: CurrentUser, card: ReportCard) {
        assertCanRead(currentUser, card)
        if (currentUser.hasAnyRole(Role.STUDENT, Role.PARENT) &&
            (card.period.type != ReportPeriodType.ANNUAL || card.status != ReportCardStatus.PUBLISHED)
        ) {
            throw ForbiddenException("FORBIDDEN", "Download restricted to published annual report cards")
        }
    }

    private fun assertPeriodMatches(template: ReportTemplate, periodType: ReportPeriodType) {
        if (template.periodType != periodType) {
            throw BusinessRuleException(
                "TEMPLATE_PERIOD_MISMATCH",
                "Template ${template.code} is for ${template.periodType}, not $periodType"
            )
        }
    }

    private fun supersedePrevious(
        schoolId: String,
        enrollmentId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?
    ): Mono<Int> =
        reportCardRepository
            .findBySchoolIdAndEnrollmentIdAndPeriod_TypeAndPeriod_NumberOrderByRevisionDesc(
                schoolId, enrollmentId, periodType, periodNumber
            )
            .collectList()
            .flatMap { existing ->
                val next = (existing.maxOfOrNull { it.revision } ?: 0) + 1
                Flux.fromIterable(existing.filter {
                    it.status == ReportCardStatus.DRAFT ||
                        it.status == ReportCardStatus.VALIDATED ||
                        it.status == ReportCardStatus.PUBLISHED
                }).concatMap { card ->
                    card.status = ReportCardStatus.SUPERSEDED
                    reportCardRepository.save(card)
                }.then(Mono.just(next))
            }

    private fun loadReview(
        schoolId: String,
        enrollmentId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?
    ): Mono<java.util.Optional<ReviewSnapshot>> =
        studentPeriodReviewRepository
            .findBySchoolIdAndEnrollmentIdAndPeriod_TypeAndPeriod_Number(
                schoolId, enrollmentId, periodType, periodNumber
            )
            .map { review ->
                java.util.Optional.of(
                    ReviewSnapshot(
                        absenceHoursTotal = review.absenceHoursTotal,
                        absenceHoursUnjustified = review.absenceHoursUnjustified,
                        lateCount = review.lateCount,
                        distinction = review.distinction,
                        workSanction = review.workSanction,
                        conductSanction = review.conductSanction,
                        councilDecision = review.councilDecision,
                        councilComment = review.councilComment
                    )
                )
            }
            .defaultIfEmpty(java.util.Optional.empty())

    private fun resolveMainTeacher(schoolId: String, teacherId: String?): Mono<String> =
        if (teacherId.isNullOrBlank()) Mono.just("")
        else staffApi.getSummary(schoolId, teacherId).map { it.displayName }.onErrorReturn("")

    private fun buildCard(
        schoolId: String,
        academicYearId: String,
        enrollmentId: String,
        studentId: String,
        classId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?,
        revision: Int,
        language: Language,
        identity: com.kobe.smels.dto.student.StudentIdentityDto,
        repeater: Boolean,
        rollNumber: Int?,
        className: String,
        levelCode: String,
        section: com.kobe.smels.model.shared.Section,
        mainTeacherName: String?,
        effectif: Int,
        rankedCount: Int,
        computation: GradeCalculationEngine.ClassComputation,
        studentResult: GradeCalculationEngine.StudentResult,
        template: ReportTemplate,
        schemeId: String,
        review: ReviewSnapshot?,
        asDraft: Boolean
    ): ReportCard {
        val rank = computation.ranks[studentId]
        val groups = studentResult.groups.map { g ->
            ReportGroup(
                groupId = g.groupId,
                code = g.code,
                name = g.name,
                displayOrder = g.displayOrder,
                lines = g.lines.map { line ->
                    val stat = computation.subjectStats[line.classSubjectId]
                    ReportLine(
                        classSubjectId = line.classSubjectId,
                        subjectId = line.classSubjectId,
                        subjectCode = line.subjectCode,
                        subjectName = line.subjectName,
                        teacherName = line.teacherName,
                        marks = line.sequences.map { seq ->
                            ReportMark(
                                sessionNumber = seq.sessionNumber,
                                label = if (periodType == ReportPeriodType.TERM) "Eval${seq.sessionNumber}"
                                else "DEV${seq.sessionNumber}",
                                value = seq.value,
                                status = seq.status
                            )
                        },
                        average = line.averageDisplay,
                        coefficient = line.coefficient,
                        total = line.totalDisplay,
                        appreciationCode = line.appreciationCode,
                        appreciationLabel = line.appreciationCode,
                        competency = line.competency,
                        subjectRank = if (template.showSubjectRank) stat?.ranks?.get(studentId) else null,
                        subjectClassAverage = if (template.showSubjectClassAverage) stat?.classAverage else null,
                        subjectPassedCount = if (template.showSubjectPassedCount) stat?.passedCount else null,
                        countsInAverage = line.countsInAverage
                    )
                },
                totalCoefficient = g.totalCoefficient,
                totalPoints = g.totalPointsExact?.let { GradeCalculationEngine.round2(it) },
                average = g.averageDisplay
            )
        }
        val mentionCode = studentResult.mentionCode
        val overall = OverallResult(
            average = studentResult.averageDisplay,
            rank = rank,
            rankedCount = rankedCount,
            totalCoefficient = studentResult.totalCoefficient,
            totalPoints = studentResult.totalPointsDisplay,
            mentionCode = mentionCode,
            mentionLabel = mentionCode,
            subjectsPassed = studentResult.subjectsPassed,
            subjectsCounted = studentResult.subjectsCounted,
            rankStatus = if (studentResult.ranked) RankStatus.RANKED else RankStatus.NOT_RANKED,
            effortSubjectNames = studentResult.effortSubjectNames
        )
        val profile = computation.classProfile?.let { p ->
            ClassProfile(
                threshold = p.threshold,
                countAtOrAboveThreshold = p.countAtOrAboveThreshold,
                classAverage = p.classAverage,
                successRate = p.successRate,
                highestAverage = p.highestAverage,
                lowestAverage = p.lowestAverage
            )
        }
        val termRecaps = if (template.showTermRecap) {
            studentResult.termAveragesExact.map { (term, avg) ->
                TermRecap(
                    termNumber = term,
                    average = avg?.let { GradeCalculationEngine.round2(it) },
                    rank = null,
                    rankedCount = rankedCount
                )
            }.sortedBy { it.termNumber }
        } else emptyList()

        return ReportCard(
            schoolId = schoolId,
            academicYearId = academicYearId,
            enrollmentId = enrollmentId,
            studentId = studentId,
            classId = classId,
            period = ReportPeriod(periodType, periodNumber),
            revision = revision,
            status = ReportCardStatus.DRAFT,
            language = language,
            verificationCode = generateVerificationCode(),
            student = StudentSnapshot(
                lastName = identity.lastName,
                firstName = identity.firstName,
                matricule = identity.matricule,
                sex = identity.sex,
                dateOfBirth = identity.dateOfBirth,
                placeOfBirth = identity.placeOfBirth,
                repeater = repeater,
                photoFileId = identity.photoFileId
            ),
            classInfo = ClassSnapshot(
                className = className,
                levelCode = levelCode,
                section = section,
                mainTeacherName = mainTeacherName,
                effectif = effectif,
                rankedCount = rankedCount,
                rollNumber = rollNumber
            ),
            groups = groups,
            overall = overall,
            termRecaps = termRecaps,
            classProfile = profile,
            review = review,
            signature = SignatureBlock(
                place = null,
                date = LocalDate.now(douala),
                signatoryTitle = if (language == Language.FR) "LE PRINCIPAL" else "THE PRINCIPAL",
                signatoryName = null
            ),
            gradingSchemeId = schemeId,
            templateCode = template.code,
            calculationVersion = ReportTemplateService.CALCULATION_VERSION,
            generatedAt = Instant.now()
        ).also {
            if (!asDraft) {
                // séquences closes : reste DRAFT jusqu'à validation manuelle (workflow admin)
                log.debug("report-card generated with closed sessions schoolId={}", schoolId)
            }
        }
    }

    private fun generateVerificationCode(): String {
        val sb = StringBuilder(13)
        repeat(13) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    private fun toSummary(card: ReportCard) = ReportCardSummaryResponse(
        id = card.id!!,
        schoolId = card.schoolId,
        studentId = card.studentId,
        classId = card.classId,
        periodType = card.period.type,
        periodNumber = card.period.number,
        revision = card.revision,
        status = card.status,
        language = card.language,
        verificationCode = card.verificationCode,
        average = card.overall.average,
        rank = card.overall.rank,
        generatedAt = card.generatedAt
    )
}
