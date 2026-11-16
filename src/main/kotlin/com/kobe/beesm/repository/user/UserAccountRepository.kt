package com.kobe.beesm.repository.user

import com.kobe.beesm.model.user.AccountStatus
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.model.user.UserAccount
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserAccountRepository : ReactiveMongoRepository<UserAccount, String> {
    fun findBySchoolIdAndUsername(schoolId: String, username: String): Mono<UserAccount>
    fun findBySchoolIdIsNullAndUsername(username: String): Mono<UserAccount>
    fun findByUsername(username: String): Mono<UserAccount>

    fun findBySchoolIdAndProfileTypeAndProfileIdIn(
        schoolId: String,
        profileType: ProfileType,
        profileIds: Collection<String>
    ): Flux<UserAccount>

    fun findBySchoolIdAndStatusAndRolesIn(
        schoolId: String,
        status: AccountStatus,
        roles: Collection<Role>
    ): Flux<UserAccount>
}
