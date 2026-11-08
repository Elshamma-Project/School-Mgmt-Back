package com.kobe.smels.configuration

import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.user.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Crée un SUPER_ADMIN initial si smels.security.bootstrap.enabled=true
 * et qu'aucun compte n'existe encore. Mot de passe uniquement via configuration / env.
 * Les autres rôles de démo sont créés par [MockUsersBootstrapRunner] (docs/mock-logins.md).
 */
@Component
@Order(10)
class BootstrapAdminRunner(
    private val securityProperties: SecurityProperties,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val bootstrap = securityProperties.bootstrap
        if (!bootstrap.enabled) {
            return
        }
        if (bootstrap.password.isBlank() || bootstrap.password.length < 8) {
            log.warn("Bootstrap admin skipped: password missing or too short")
            return
        }

        userAccountRepository.count()
            .flatMap { count ->
                if (count > 0L) {
                    log.info("Bootstrap admin skipped: accounts already exist")
                    Mono.empty()
                } else {
                    val account = UserAccount(
                        schoolId = bootstrap.schoolId,
                        username = bootstrap.username.trim().lowercase(),
                        passwordHash = passwordEncoder.encode(bootstrap.password),
                        roles = setOf(Role.SUPER_ADMIN),
                        profileType = ProfileType.NONE,
                        status = AccountStatus.ACTIVE,
                        mustChangePassword = false
                    )
                    userAccountRepository.save(account)
                        .doOnSuccess {
                            log.info("Bootstrap SUPER_ADMIN created (username configured via smels.security.bootstrap)")
                        }
                        .then()
                }
            }
            .block()
    }
}
