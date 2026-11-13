package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.GradeAuditEntryResponse
import com.kobe.smels.dto.grading.PageResponse
import com.kobe.smels.model.grading.AuditAction
import com.kobe.smels.model.grading.AuditTarget
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class GradeAuditService(
    private val userAccountApi: UserAccountApi,
    private val mongoTemplate: ReactiveMongoTemplate
) {
    fun list(
        currentUser: CurrentUser,
        page: Int = 0,
        size: Int = 20,
        schoolIdOverride: String? = null,
        action: String? = null,
        target: String? = null,
        afterSessionClosed: Boolean? = null
    ): Mono<PageResponse<GradeAuditEntryResponse>> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "changedAt"))

        val criteria = Criteria.where("schoolId").`is`(schoolId)
        parseAction(action)?.let { criteria.and("action").`is`(it) }
        parseTarget(target)?.let { criteria.and("target").`is`(it) }
        if (afterSessionClosed != null) {
            criteria.and("afterSessionClosed").`is`(afterSessionClosed)
        }

        val countQuery = Query(criteria)
        val pageQuery = Query(criteria).with(pageable)

        return Mono.zip(
            mongoTemplate.find(pageQuery, GradeAuditEntry::class.java).collectList(),
            mongoTemplate.count(countQuery, GradeAuditEntry::class.java)
        ).flatMap { tuple ->
            val entries = tuple.t1
            val total = tuple.t2
            val accountIds = entries.map { it.changedBy }.distinct()
            userAccountApi.findUsernamesByIds(schoolId, accountIds)
                .defaultIfEmpty(emptyMap())
                .map { usernames ->
                    val content = entries.map { entry ->
                        GradeAuditEntryResponse(
                            id = entry.id!!,
                            schoolId = entry.schoolId,
                            target = entry.target,
                            action = entry.action,
                            classSubjectId = entry.classSubjectId,
                            sessionId = entry.sessionId,
                            studentId = entry.studentId,
                            reportSheetId = entry.reportSheetId,
                            gradeId = entry.gradeId,
                            sequenceMarkId = entry.sequenceMarkId,
                            changedBy = entry.changedBy,
                            changedByLabel = usernames[entry.changedBy]?.takeIf { it.isNotBlank() },
                            changedByRole = entry.changedByRole,
                            changedAt = entry.changedAt,
                            reason = entry.reason,
                            afterSessionClosed = entry.afterSessionClosed,
                            previousValue = entry.previousValue,
                            newValue = entry.newValue,
                            previousReportStatus = entry.previousReportStatus?.name,
                            newReportStatus = entry.newReportStatus?.name
                        )
                    }
                    val totalPages = if (total == 0L) 0 else ((total + pageSize - 1) / pageSize).toInt()
                    PageResponse(
                        content = content,
                        page = pageIndex,
                        size = pageSize,
                        totalElements = total,
                        totalPages = totalPages
                    )
                }
        }
    }

    private fun parseAction(raw: String?): AuditAction? {
        if (raw.isNullOrBlank()) return null
        return runCatching { AuditAction.valueOf(raw.trim().uppercase()) }.getOrNull()
    }

    private fun parseTarget(raw: String?): AuditTarget? {
        if (raw.isNullOrBlank()) return null
        return runCatching { AuditTarget.valueOf(raw.trim().uppercase()) }.getOrNull()
    }
}
