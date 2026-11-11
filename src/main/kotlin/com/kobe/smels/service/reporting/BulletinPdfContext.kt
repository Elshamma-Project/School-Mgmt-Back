package com.kobe.smels.service.reporting

import com.kobe.smels.dto.academic.SchoolBulletinBrandDto

/**
 * Contexte visuel du bulletin PDF — données école (API academic), sans inventer
 * délégation / BP / téléphone s’ils ne sont pas paramétrés.
 */
data class BulletinPdfContext(
    val schoolNameFr: String = "",
    val schoolNameEn: String = "",
    val postalBox: String = "",
    val phones: List<String> = emptyList(),
    val republicFr: String = "",
    val republicEn: String = "",
    val mottoFr: String = "",
    val mottoEn: String = "",
    val ministryFr: String = "",
    val ministryEn: String = "",
    val regionalFr: String = "",
    val regionalEn: String = "",
    val divisionalFr: String = "",
    val divisionalEn: String = "",
    val logoBytes: ByteArray? = null,
) {
    val phoneLine: String
        get() = phones.filter { it.isNotBlank() }.joinToString(" / ")

    fun headerLinesFr(): List<String> = listOfNotNull(
        republicFr.takeIf { it.isNotBlank() },
        mottoFr.takeIf { it.isNotBlank() },
        ministryFr.takeIf { it.isNotBlank() },
        regionalFr.takeIf { it.isNotBlank() },
        divisionalFr.takeIf { it.isNotBlank() },
        schoolNameFr.takeIf { it.isNotBlank() },
        listOfNotNull(
            postalBox.takeIf { it.isNotBlank() }?.let { "B.P. : $it" },
            phoneLine.takeIf { it.isNotBlank() }?.let { "Tél. : $it" },
        ).takeIf { it.isNotEmpty() }?.joinToString("  ·  "),
    )

    fun headerLinesEn(): List<String> = listOfNotNull(
        republicEn.takeIf { it.isNotBlank() },
        mottoEn.takeIf { it.isNotBlank() },
        ministryEn.takeIf { it.isNotBlank() },
        regionalEn.takeIf { it.isNotBlank() },
        divisionalEn.takeIf { it.isNotBlank() },
        schoolNameEn.takeIf { it.isNotBlank() },
        listOfNotNull(
            postalBox.takeIf { it.isNotBlank() }?.let { "P.O. Box : $it" },
            phoneLine.takeIf { it.isNotBlank() }?.let { "Tel. : $it" },
        ).takeIf { it.isNotEmpty() }?.joinToString("  ·  "),
    )

    companion object {
        fun fromBrand(brand: SchoolBulletinBrandDto): BulletinPdfContext =
            BulletinPdfContext(
                schoolNameFr = brand.nameFr.trim(),
                schoolNameEn = brand.nameEn.trim(),
                postalBox = brand.postalBox?.trim().orEmpty(),
                phones = brand.phones,
                republicFr = brand.republicFr?.trim().orEmpty(),
                republicEn = brand.republicEn?.trim().orEmpty(),
                mottoFr = brand.mottoFr?.trim().orEmpty(),
                mottoEn = brand.mottoEn?.trim().orEmpty(),
                ministryFr = brand.ministryFr?.trim().orEmpty(),
                ministryEn = brand.ministryEn?.trim().orEmpty(),
                regionalFr = brand.regionalDelegationFr?.trim().orEmpty(),
                regionalEn = brand.regionalDelegationEn?.trim().orEmpty(),
                divisionalFr = brand.divisionalDelegationFr?.trim().orEmpty(),
                divisionalEn = brand.divisionalDelegationEn?.trim().orEmpty(),
                logoBytes = brand.logoBytes,
            )
    }
}
