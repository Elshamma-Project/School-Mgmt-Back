package com.kobe.smels.configuration.seed

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Seed privé EDT 2026-2027 (enseignants + cases) — fichier hors Git (AGENTS.md règle 12).
 *
 * Défaut : `file:docs/seed-private/edt-2026-2027-extraction.json`.
 * Absent → le runner ignore (pas d’échec au démarrage).
 */
@ConfigurationProperties(prefix = "smels.seed.el-shammah-edt")
data class ElShammahEdtSeedProperties(
    val enabled: Boolean = true,
    val schoolId: String = "school-elshammah",
    val academicYearLabel: String = "2026-2027",
    /**
     * Ressource Spring (`file:…` ou chemin absolu via env `EL_SHAMMAH_EDT_SEED_RESOURCE`).
     * Ne jamais committer le JSON dans le classpath.
     */
    val resource: String = "file:docs/seed-private/edt-2026-2027-extraction.json",
    val gridResource: String = "classpath:fixtures/timetable-grid.json",
    /** false = upsert insert-if-absent / enrichissement ; true = réapplique les champs seed. */
    val overwriteExisting: Boolean = false
)
