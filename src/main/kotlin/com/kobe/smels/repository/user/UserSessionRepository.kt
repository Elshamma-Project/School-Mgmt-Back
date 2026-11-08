package com.kobe.smels.repository.user

import com.kobe.smels.model.user.SessionStatus
import com.kobe.smels.model.user.UserSession
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserSessionRepository : ReactiveMongoRepository<UserSession, String> {
    fun findByRefreshTokenHashAndStatus(refreshTokenHash: String, status: SessionStatus): Mono<UserSession>
    fun findByAccountIdAndStatusOrderByLastSeenAtAsc(accountId: String, status: SessionStatus): Flux<UserSession>
    fun findByAccountIdAndStatus(accountId: String, status: SessionStatus): Flux<UserSession>
    fun findByIdAndAccountId(id: String, accountId: String): Mono<UserSession>
    fun countByAccountIdAndStatus(accountId: String, status: SessionStatus): Mono<Long>
}
