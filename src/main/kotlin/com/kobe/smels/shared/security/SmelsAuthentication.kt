package com.kobe.smels.shared.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class SmelsAuthentication(
    val currentUser: CurrentUser
) : AbstractAuthenticationToken(
    currentUser.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): CurrentUser = currentUser
}
