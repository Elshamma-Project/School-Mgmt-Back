package com.kobe.smels.service.reporting

import com.kobe.smels.dto.academic.SchoolBulletinBrandDto

/**
 * Contexte visuel du bulletin PDF (maquette KOBE) — aligné sur l’aperçu HTML.
 */
data class BulletinPdfContext(
    val schoolNameFr: String = "NOM DE L'ÉTABLISSEMENT",
    val schoolNameEn: String = "NAME OF THE SCHOOL",
    val postalBox: String = "Yaoundé",
    val phones: List<String> = emptyList(),
    val republicFr: String = "RÉPUBLIQUE DU CAMEROUN",
    val republicEn: String = "REPUBLIC OF CAMEROON",
    val mottoFr: String = "Paix – Travail – Patrie",
    val mottoEn: String = "Peace – Work – Fatherland",
    val ministryFr: String = "MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES",
    val ministryEn: String = "MINISTRY OF SECONDARY EDUCATION",
    val regionalFr: String = "DÉLÉGATION RÉGIONALE DU CENTRE",
    val regionalEn: String = "REGIONAL DELEGATION OF THE CENTRE",
    val divisionalFr: String = "DÉLÉGATION DÉPARTEMENTALE DU MFOUNDI",
    val divisionalEn: String = "DIVISIONAL DELEGATION OF MFOUNDI",
    val logoBytes: ByteArray? = null,
) {
    val phoneLine: String
        get() = phones.filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "670 00 00 00" }

    companion object {
        fun fromBrand(brand: SchoolBulletinBrandDto): BulletinPdfContext =
            BulletinPdfContext(
                schoolNameFr = brand.nameFr.ifBlank { "NOM DE L'ÉTABLISSEMENT" },
                schoolNameEn = brand.nameEn.ifBlank { "NAME OF THE SCHOOL" },
                postalBox = brand.postalBox?.takeIf { it.isNotBlank() } ?: "Yaoundé",
                phones = brand.phones,
                republicFr = brand.republicFr?.takeIf { it.isNotBlank() } ?: "RÉPUBLIQUE DU CAMEROUN",
                republicEn = brand.republicEn?.takeIf { it.isNotBlank() } ?: "REPUBLIC OF CAMEROON",
                mottoFr = brand.mottoFr?.takeIf { it.isNotBlank() } ?: "Paix – Travail – Patrie",
                mottoEn = brand.mottoEn?.takeIf { it.isNotBlank() } ?: "Peace – Work – Fatherland",
                ministryFr = brand.ministryFr?.takeIf { it.isNotBlank() }
                    ?: "MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES",
                ministryEn = brand.ministryEn?.takeIf { it.isNotBlank() }
                    ?: "MINISTRY OF SECONDARY EDUCATION",
                regionalFr = brand.regionalDelegationFr?.takeIf { it.isNotBlank() }
                    ?: "DÉLÉGATION RÉGIONALE DU CENTRE",
                regionalEn = brand.regionalDelegationEn?.takeIf { it.isNotBlank() }
                    ?: "REGIONAL DELEGATION OF THE CENTRE",
                divisionalFr = brand.divisionalDelegationFr?.takeIf { it.isNotBlank() }
                    ?: "DÉLÉGATION DÉPARTEMENTALE DU MFOUNDI",
                divisionalEn = brand.divisionalDelegationEn?.takeIf { it.isNotBlank() }
                    ?: "DIVISIONAL DELEGATION OF MFOUNDI",
                logoBytes = brand.logoBytes,
            )
    }
}
