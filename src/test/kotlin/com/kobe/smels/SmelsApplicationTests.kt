package com.kobe.smels

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled

/**
 * Le chargement complet du contexte nécessite Mongo en replica set.
 * Les tests unitaires du module user couvrent JWT / hashing / limite de sessions.
 */
class SmelsApplicationTests {

    @Test
    @Disabled("Requires local Mongo (smels-db) — enable once docker compose is up")
    fun contextLoadsPlaceholder() {
        // Intentionally disabled until Testcontainers is wired (feuille de route étape 1).
    }
}
