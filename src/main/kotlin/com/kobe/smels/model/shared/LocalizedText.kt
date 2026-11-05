package com.kobe.smels.model.shared

/**
 * Texte bilingue français / anglais (noms de matières, de groupes, de trimestres, en-têtes...).
 * Le bulletin est imprimé dans la langue de la section de la classe (voir SchoolClass.defaultLanguage ; les documents peuvent aussi être produits dans l'autre langue).
 */
data class LocalizedText(
    val fr: String? = null,
    val en: String? = null
) {
    /**
     * Texte dans la langue demandée ; si absent et [fallback] vaut true, texte de l'autre langue.
     */
    fun resolve(language: Language, fallback: Boolean = true): String? {
        val primary = if (language == Language.FR) fr else en
        if (!primary.isNullOrBlank() || !fallback) return primary
        return if (language == Language.FR) en else fr
    }

    companion object {
        fun of(fr: String?, en: String?) = LocalizedText(fr, en)
    }
}
