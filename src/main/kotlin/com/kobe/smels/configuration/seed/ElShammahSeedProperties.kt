package com.kobe.smels.configuration.seed

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Seed structure académique El Shammah (fichier JSON).
 *
 * Politique : **insert-if-absent** par clé naturelle — une fois créé, un document
 * modifié dans l’UI n’est pas réécrit au redémarrage. Les nouvelles entrées du JSON
 * manquantes en base sont encore ajoutées.
 */
@ConfigurationProperties(prefix = "smels.seed.el-shammah")
data class ElShammahSeedProperties(
    val enabled: Boolean = true,
    /**
     * Identifiant Mongo du tenant (aligné sur les comptes mock / front).
     * Le JSON ne porte que `schoolCode` ; cet id est fixé ici.
     */
    val schoolId: String = "school-elshammah",
    /** Resource Spring (`classpath:…` ou `file:…`). */
    val resource: String = "classpath:seed/seed-el-shammah.json",
    /**
     * Si true : met à jour les documents existants depuis le JSON (réécrit les
     * modifications UI). Réservé au rechargement contrôlé — défaut false.
     */
    val overwriteExisting: Boolean = false
)
