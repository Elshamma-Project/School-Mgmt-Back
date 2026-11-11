package com.kobe.smels.dto.academic

/**
 * Marque établissement exposée aux autres modules (bulletins PDF, etc.)
 * sans importer le model academic.
 */
data class SchoolBulletinBrandDto(
    val schoolId: String,
    val nameFr: String,
    val nameEn: String,
    val postalBox: String?,
    val phones: List<String> = emptyList(),
    val republicFr: String?,
    val republicEn: String?,
    val mottoFr: String?,
    val mottoEn: String?,
    val ministryFr: String?,
    val ministryEn: String?,
    val regionalDelegationFr: String?,
    val regionalDelegationEn: String?,
    val divisionalDelegationFr: String?,
    val divisionalDelegationEn: String?,
    val logoBytes: ByteArray? = null,
    val logoContentType: String? = null,
)
