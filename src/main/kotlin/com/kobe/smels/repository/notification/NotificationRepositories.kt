package com.kobe.smels.repository.notification

import com.kobe.smels.model.notification.InAppNotification
import com.kobe.smels.model.notification.StaffSignal
import com.kobe.smels.model.notification.StaffSignalStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface InAppNotificationRepository : ReactiveMongoRepository<InAppNotification, String> {
    fun findByIdAndSchoolIdAndRecipientAccountId(
        id: String,
        schoolId: String,
        recipientAccountId: String
    ): Mono<InAppNotification>

    fun findBySchoolIdAndRecipientAccountIdOrderByCreatedAtDesc(
        schoolId: String,
        recipientAccountId: String
    ): Flux<InAppNotification>

    fun findBySchoolIdAndRecipientAccountIdAndReadAtIsNullOrderByCreatedAtDesc(
        schoolId: String,
        recipientAccountId: String
    ): Flux<InAppNotification>

    fun countBySchoolIdAndRecipientAccountIdAndReadAtIsNull(
        schoolId: String,
        recipientAccountId: String
    ): Mono<Long>

    fun findBySchoolIdAndSourceEventIdAndRecipientAccountId(
        schoolId: String,
        sourceEventId: String,
        recipientAccountId: String
    ): Mono<InAppNotification>
}

interface StaffSignalRepository : ReactiveMongoRepository<StaffSignal, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<StaffSignal>

    fun findBySchoolIdAndStatusOrderByCreatedAtDesc(
        schoolId: String,
        status: StaffSignalStatus
    ): Flux<StaffSignal>

    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: String): Flux<StaffSignal>

    fun findBySchoolIdAndAuthorAccountIdOrderByCreatedAtDesc(
        schoolId: String,
        authorAccountId: String
    ): Flux<StaffSignal>
}
