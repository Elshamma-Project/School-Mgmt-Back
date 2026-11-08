package com.kobe.smels.repository.user

import com.kobe.smels.model.user.UserAvatar
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface UserAvatarRepository : ReactiveMongoRepository<UserAvatar, String> {
    fun findByAccountId(accountId: String): Mono<UserAvatar>
    fun deleteByAccountId(accountId: String): Mono<Void>
}
