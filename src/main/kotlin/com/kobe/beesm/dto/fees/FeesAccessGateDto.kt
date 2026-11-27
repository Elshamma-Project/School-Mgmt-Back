package com.kobe.beesm.dto.fees

import com.kobe.beesm.model.fees.SolvencyStatus

/**
 * Porte d’accès academics (BR-F05 / Q-65).
 * - Parent / élève / enseignant : bloqués si non soldé.
 * - Direction / Admin / SG : consultation autorisée ; modification interdite si bloqué.
 */
data class FeesAccessGateDto(
    val studentId: String,
    val academicYearId: String,
    val solvency: SolvencyStatus,
    /** true si inscription non soldée ou statut INSOLVENT. */
    val blocked: Boolean,
    val enrollmentSettled: Boolean,
    val reasonCode: String? = null,
    val messageFr: String? = null,
    val messageEn: String? = null
)
