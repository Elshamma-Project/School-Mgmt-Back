package com.kobe.smels.service.staff

import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser

/**
 * Règles RH staff vs rôles (docs/05) :
 * - ADMINISTRATION : pas de Direction (HEAD_OF_SCHOOL / CENSOR) en lecture ni création ;
 *   les autres admins (SECRETARY / SUPERVISOR / ACCOUNTANT) sont visibles en lecture seule ;
 *   gestion des enseignants (TEACHER) et fiches Autres (OTHER) ; acteur toujours journalisé.
 * - DIRECTOR / SUPER_ADMIN : accès complet.
 */
object StaffAccessPolicies {
    val LEADERSHIP_FUNCTIONS: Set<StaffFunction> = setOf(
        StaffFunction.HEAD_OF_SCHOOL,
        StaffFunction.CENSOR
    )

    /** Pairs admin / soutien (pas Direction). */
    val PEER_ADMIN_FUNCTIONS: Set<StaffFunction> = setOf(
        StaffFunction.SECRETARY,
        StaffFunction.SUPERVISOR,
        StaffFunction.ACCOUNTANT
    )

    /** Fonctions qu’un ADMINISTRATION peut attribuer à la création. */
    val ADMINISTRATION_CREATABLE_FUNCTIONS: Set<StaffFunction> = setOf(
        StaffFunction.TEACHER,
        StaffFunction.OTHER
    )

    fun isLeadership(functions: Set<StaffFunction>): Boolean =
        functions.any { it in LEADERSHIP_FUNCTIONS }

    fun isLeadership(staff: Staff): Boolean = isLeadership(staff.functions)

    /**
     * Autre admin / soutien : a une fonction peer et n’est pas enseignant ni « Autre »
     * (sinon la gestion enseignant / autre reste possible).
     */
    fun isPeerAdminOnly(functions: Set<StaffFunction>): Boolean {
        if (isLeadership(functions)) return false
        if (functions.none { it in PEER_ADMIN_FUNCTIONS }) return false
        return functions.none { it == StaffFunction.TEACHER || it == StaffFunction.OTHER }
    }

    fun isPeerAdminOnly(staff: Staff): Boolean = isPeerAdminOnly(staff.functions)

    fun canRead(currentUser: CurrentUser, staff: Staff): Boolean {
        if (currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) return true
        if (currentUser.hasRole(Role.ADMINISTRATION)) {
            return !isLeadership(staff)
        }
        return false
    }

    fun canManage(currentUser: CurrentUser, staff: Staff): Boolean {
        if (currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) return true
        if (currentUser.hasRole(Role.ADMINISTRATION)) {
            return !isLeadership(staff) && !isPeerAdminOnly(staff)
        }
        return false
    }

    fun assertCreatableFunctions(currentUser: CurrentUser, functions: Set<StaffFunction>) {
        if (currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) return
        if (!currentUser.hasRole(Role.ADMINISTRATION)) return
        val forbidden = functions.filterNot { it in ADMINISTRATION_CREATABLE_FUNCTIONS }
        if (forbidden.isNotEmpty()) {
            throw com.kobe.smels.exception.ForbiddenException(
                "FORBIDDEN",
                "ADMINISTRATION cannot assign functions: ${forbidden.joinToString(",") { it.name }}"
            )
        }
    }

    fun assertAssignableOnUpdate(currentUser: CurrentUser, functions: Set<StaffFunction>) {
        assertCreatableFunctions(currentUser, functions)
    }
}
