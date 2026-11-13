package com.kobe.smels.dto.user

import java.time.Instant

/** Compte UserAccount lié à une fiche Staff (`profileType=STAFF`). */
data class StaffLinkedAccountDto(
    val accountId: String,
    val username: String,
    val lastLoginAt: Instant? = null
)
