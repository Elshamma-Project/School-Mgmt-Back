package com.kobe.smels.model.shared

/** Langue d'affichage / d'impression. */
enum class Language { FR, EN }

enum class Sex { MALE, FEMALE }

/**
 * Sous-système éducatif d'une classe.
 * BILINGUAL = classe mixte (à clarifier avec l'école, voir docs/07-questions-ouvertes.md, Q-01).
 */
enum class Section { FRANCOPHONE, ANGLOPHONE, BILINGUAL }

/** Cycle d'enseignement (détermine en-tête officiel, barèmes et gabarit de bulletin). */
enum class Cycle {
    MATERNELLE,
    PRIMAIRE,
    SECONDAIRE_1ER_CYCLE,
    SECONDAIRE_2ND_CYCLE
}
