package com.kobe.smels.service.user

import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.model.user.UserAccount

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
