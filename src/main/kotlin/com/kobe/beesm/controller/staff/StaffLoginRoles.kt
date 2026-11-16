package com.kobe.beesm.controller.staff

import com.kobe.beesm.model.staff.StaffFunction
import com.kobe.beesm.model.user.Role

/**
 * Mapping fonctions RH → rôles de connexion (orchestration contrôleur staff ↔ user).
 * Les rôles admin (ADMINISTRATION / DIRECTOR) ne sont attribués que si la fonction le justifie.
 */
internal object StaffLoginRoles {
    fun fromFunctions(functions: Set<StaffFunction>): Set<Role> {
        val roles = linkedSetOf<Role>()
        for (function in functions) {
            when (function) {
                StaffFunction.TEACHER -> roles += Role.TEACHER
                StaffFunction.HEAD_OF_SCHOOL -> roles += Role.DIRECTOR
                StaffFunction.CENSOR,
                StaffFunction.SECRETARY,
                StaffFunction.ACCOUNTANT -> roles += Role.ADMINISTRATION
                StaffFunction.SUPERVISOR -> roles += Role.SURVEILLANT_GENERAL
                StaffFunction.OTHER -> Unit
            }
        }
        if (roles.isEmpty()) {
            roles += Role.TEACHER
        }
        return roles
    }
}
