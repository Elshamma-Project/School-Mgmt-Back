package com.kobe.beesm.repository.user

import com.kobe.beesm.model.user.UserAvatar
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface UserAvatarRepository : ReactiveMongoRepository<UserAvatar, String> {
    fun findByAccountId(accountId: String): Mono<UserAvatar>
    fun deleteByAccountId(accountId: String): Mono<Void>
}
