package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.CreateGradingSchemeRequest
import com.kobe.smels.dto.grading.LocalizedTextDto
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test

class GradingSchemeAccessTest {
    private val gradingSchemeRepository: GradingSchemeRepository = mock()
    private val service = GradingSchemeService(gradingSchemeRepository)

    @Test
    fun `ADMINISTRATION cannot create grading scheme`() {
        val request = CreateGradingSchemeRequest(
            code = "STD",
            name = LocalizedTextDto(fr = "Standard", en = "Standard")
        )

        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun adminUser() = CurrentUser(
        accountId = "acc-a",
        sessionId = "s1",
        username = "admin",
        schoolId = "school-1",
        roles = setOf(Role.ADMINISTRATION),
        profileType = ProfileType.STAFF,
        profileId = "staff-a",
        mustChangePassword = false
    )
}
