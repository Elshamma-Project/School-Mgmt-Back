package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.fees.FeesPolicyResponse
import com.kobe.beesm.dto.fees.UpdateFeesPolicyRequest
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.shared.outbox.OutboxWriter
import com.kobe.beesm.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal

/**
 * Paramètres scolarité (rappels, pénalités, exclusion) — BR-F10…F14.
 * Réservé à la Direction / Super-admin.
 */
@Service
class FeesPolicyService(
    private val policyRepository: FeesPolicyRepository,
    private val outboxWriter: OutboxWriter
) {

    fun getPolicy(currentUser: CurrentUser, schoolIdOverride: String? = null): Mono<FeesPolicyResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanManageOrRead(currentUser)
            .then(getOrCreate(schoolId))
            .map { toResponse(it) }
    }

    fun updatePolicy(
        currentUser: CurrentUser,
        request: UpdateFeesPolicyRequest
    ): Mono<FeesPolicyResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        validate(request)
        return assertCanManage(currentUser)
            .then(getOrCreate(schoolId))
            .flatMap { existing ->
                existing.reminderDaysBeforeDue = request.reminderDaysBeforeDue
                    .map { it.coerceAtLeast(0) }
                    .distinct()
                    .sortedDescending()
                    .toMutableList()
                existing.remindOnDueDate = request.remindOnDueDate
                existing.weeklyPenaltyAmount = request.weeklyPenaltyAmount
                existing.currency = request.currency?.trim()?.ifBlank { "XAF" } ?: "XAF"
                existing.maxInsolvencyGraceDays = request.maxInsolvencyGraceDays
                existing.exclusionEnabled = request.exclusionEnabled
                existing.exclusionWarningDaysBefore = request.exclusionWarningDaysBefore
                existing.active = request.active
                policyRepository.save(existing)
            }
            .flatMap { saved ->
                val policyId = saved.id ?: schoolId
                outboxWriter.append(
                    schoolId = schoolId,
                    module = "fees",
                    aggregateType = "FeesPolicy",
                    aggregateId = policyId,
                    eventType = "fees.policy.updated",
                    payload = mapOf(
                        "policyId" to policyId,
                        "updatedByAccountId" to currentUser.accountId,
                        "weeklyPenaltyAmount" to saved.weeklyPenaltyAmount.toPlainString(),
                        "maxInsolvencyGraceDays" to saved.maxInsolvencyGraceDays,
                        "reminderDaysBeforeDue" to saved.reminderDaysBeforeDue.toList()
                    ),
                    schemaVersion = 1
                ).thenReturn(saved)
            }
            .map { toResponse(it) }
    }

    fun getOrCreate(schoolId: String): Mono<FeesPolicy> =
        policyRepository.findBySchoolId(schoolId)
            .switchIfEmpty(
                Mono.defer {
                    policyRepository.save(
                        FeesPolicy(
                            schoolId = schoolId,
                            reminderDaysBeforeDue = mutableListOf(15, 10, 5, 3, 2, 1),
                            remindOnDueDate = true,
                            weeklyPenaltyAmount = BigDecimal("1000"),
                            currency = "XAF",
                            maxInsolvencyGraceDays = 28,
                            exclusionEnabled = true,
                            exclusionWarningDaysBefore = 7,
                            active = true
                        )
                    )
                }
            )

    private fun validate(request: UpdateFeesPolicyRequest) {
        if (request.reminderDaysBeforeDue.isEmpty()) {
            throw BusinessRuleException("FEES_POLICY_DAYS", "At least one reminder day is required")
        }
        if (request.exclusionWarningDaysBefore > request.maxInsolvencyGraceDays) {
            throw BusinessRuleException(
                "FEES_POLICY_WARNING",
                "exclusionWarningDaysBefore must be <= maxInsolvencyGraceDays"
            )
        }
    }

    private fun assertCanManage(currentUser: CurrentUser): Mono<Void> {
        if (currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Mono.empty()
        }
        return Mono.error(
            ForbiddenException("FEES_POLICY_FORBIDDEN", "Only DIRECTOR can manage fees policy")
        )
    }

    private fun assertCanManageOrRead(currentUser: CurrentUser): Mono<Void> {
        if (currentUser.hasAnyRole(
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.ADMINISTRATION
            )
        ) {
            return Mono.empty()
        }
        return Mono.error(ForbiddenException("FORBIDDEN", "Fees policy read denied"))
    }

    private fun toResponse(policy: FeesPolicy): FeesPolicyResponse =
        FeesPolicyResponse(
            id = policy.id!!,
            schoolId = policy.schoolId,
            reminderDaysBeforeDue = policy.reminderDaysBeforeDue.toList(),
            remindOnDueDate = policy.remindOnDueDate,
            weeklyPenaltyAmount = policy.weeklyPenaltyAmount,
            currency = policy.currency,
            maxInsolvencyGraceDays = policy.maxInsolvencyGraceDays,
            exclusionEnabled = policy.exclusionEnabled,
            exclusionWarningDaysBefore = policy.exclusionWarningDaysBefore,
            active = policy.active
        )
}
