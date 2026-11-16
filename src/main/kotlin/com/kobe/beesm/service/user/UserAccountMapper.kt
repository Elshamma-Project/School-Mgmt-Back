package com.kobe.beesm.service.user

import com.kobe.beesm.dto.user.UserAccountResponse
import com.kobe.beesm.model.user.UserAccount

object UserAccountMapper {
    fun toResponse(account: UserAccount): UserAccountResponse =
        UserAccountResponse(
            id = account.id!!,
            schoolId = account.schoolId,
            username = account.username,
            roles = account.roles,
            profileType = account.profileType,
            profileId = account.profileId,
            hasAvatar = !account.avatarFileId.isNullOrBlank(),
            status = account.status,
            mustChangePassword = account.mustChangePassword,
            lastLoginAt = account.lastLoginAt
        )
}
