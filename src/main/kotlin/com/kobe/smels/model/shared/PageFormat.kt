package com.kobe.smels.model.shared

/**
 * Format de page des documents imprimés. Exigence : un bulletin et un emploi du temps tiennent sur UNE SEULE
 * page A4 (le générateur réduit la taille de police ou la hauteur des lignes pour y arriver, jamais de
 * seconde page). Seuls les documents personnels d'un enseignant (fiches de notes) peuvent s'étendre sur
 * plusieurs pages.
 */
enum class PageFormat {
    /** Bulletin. */
    A4_PORTRAIT,

    /** Emploi du temps (les modèles de l'école sont en paysage). */
    A4_LANDSCAPE
}
