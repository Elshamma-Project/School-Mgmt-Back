package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.exception.UnauthorizedException
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.shared.security.CurrentUser
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    private val securityProperties: SecurityProperties
) {
    private val key: SecretKey by lazy {
        val secret = securityProperties.jwt.secret
        require(secret.length >= 32) { "smels.security.jwt.secret must be at least 32 characters" }
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }

    fun createAccessToken(account: UserAccount, sessionId: String): String {
        val now = Instant.now()
        val expiry = now.plusMillis(securityProperties.jwt.accessTokenValidityMs)
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(account.id!!)
            .claim("sid", sessionId)
            .claim("username", account.username)
            .claim("schoolId", account.schoolId)
            .claim("roles", account.roles.map { it.name })
            .claim("profileType", account.profileType.name)
            .claim("profileId", account.profileId)
            .claim("mcp", account.mustChangePassword)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact()
    }

    fun parseAccessToken(token: String): CurrentUser {
        try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            return toCurrentUser(claims)
        } catch (ex: Exception) {
            throw UnauthorizedException("INVALID_TOKEN", "Access token is invalid or expired")
        }
    }

    fun accessTokenValidityMs(): Long = securityProperties.jwt.accessTokenValidityMs

    private fun toCurrentUser(claims: Claims): CurrentUser {
        val roles = (claims["roles"] as? Collection<*>)
            ?.mapNotNull { runCatching { Role.valueOf(it.toString()) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
        val profileType = runCatching {
            ProfileType.valueOf(claims["profileType"]?.toString() ?: ProfileType.NONE.name)
        }.getOrDefault(ProfileType.NONE)

        return CurrentUser(
            accountId = claims.subject
                ?: throw UnauthorizedException("INVALID_TOKEN", "Access token is invalid or expired"),
            sessionId = claims["sid"]?.toString()
                ?: throw UnauthorizedException("INVALID_TOKEN", "Access token is invalid or expired"),
            username = claims["username"]?.toString() ?: "",
            schoolId = claims["schoolId"]?.toString(),
            roles = roles,
            profileType = profileType,
            profileId = claims["profileId"]?.toString(),
            mustChangePassword = claims["mcp"] as? Boolean ?: false
        )
    }
}
