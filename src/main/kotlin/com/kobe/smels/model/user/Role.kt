package com.kobe.smels.model.user

/**
 * Rôles d'accès (droits). Matrice détaillée : docs/05-roles-permissions.md.
 * Un compte peut cumuler plusieurs rôles (ex. un enseignant qui est aussi membre de l'administration).
 */
enum class Role {
    /** Élève : consulte ses propres notes en temps réel (connexion avec son matricule, activée plus tard). */
    STUDENT,

    /** Parent / tuteur : consulte les notes de ses enfants ; télécharge le bulletin annuel publié. */
    PARENT,

    /** Enseignant : CRUD des notes de ses matières/classes tant que la séquence est ouverte. */
    TEACHER,

    /**
     * Surveillant Général : vise les fiches de report de notes (lecture + visa SG).
     * TODO(Q-38) : confirmer le périmètre exact avec l'école (création du rôle validée côté modèle).
     */
    SURVEILLANT_GENERAL,

    /** Administration : tout ce que fait l'enseignant + correction après clôture, bulletins, CRUD scolarité. */
    ADMINISTRATION,

    /**
     * Directeur / chef d'établissement : accès complet, gère aussi les comptes d'administration.
     * Visa final Principal des fiches de report = ce rôle tant que Q-38 n'introduit pas un rôle PRINCIPAL distinct.
     */
    DIRECTOR,

    /** Développeur / support : accès plateforme, au-delà d'un établissement. */
    SUPER_ADMIN
}

/** Type de fiche rattachée à un compte. */
enum class ProfileType { STAFF, STUDENT, GUARDIAN, NONE }
