package com.kobe.smels.model.shared

/** Coordonnées de contact (objet embarqué). */
data class ContactInfo(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)
