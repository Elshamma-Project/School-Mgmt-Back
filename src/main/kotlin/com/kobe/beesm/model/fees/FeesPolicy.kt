package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal

/**
 * Paramètres scolarité / rappels / pénalités / exclusion (BR-F10…F14).
 * Un document par établissement ; montants et délais **paramétrables** (pas de seuils métier figés hors défauts).
 */
@Document(collection = "fees_policies")
@CompoundIndexes(
    CompoundIndex(name = "uk_school", def = "{'schoolId': 1}", unique = true)
)
data class FeesPolicy(
    @Id
    var id: String? = null,
    override var schoolId: String,
    /**
     * Jours avant échéance de tranche pour rappels automatiques parents.
     * Défaut : 15, 10, 5, 3, 2, 1 (moitié / important / urgent / 3 derniers jours).
     */
    var reminderDaysBeforeDue: MutableList<Int> = mutableListOf(15, 10, 5, 3, 2, 1),
    /** Envoyer aussi un rappel le jour de l’échéance. */
    var remindOnDueDate: Boolean = true,
    /** Pénalité hebdomadaire après insolvabilité (XAF). Défaut v1 : 1000. */
    @Field(targetType = FieldType.DECIMAL128)
    var weeklyPenaltyAmount: BigDecimal = BigDecimal("1000"),
    var currency: String = "XAF",
    /**
     * Délai max (jours calendaires) après le 1er jour d’insolvabilité
     * avant exclusion définitive (désactivation élève).
     */
    var maxInsolvencyGraceDays: Int = 28,
    var exclusionEnabled: Boolean = true,
    /** Jours avant exclusion pour un avertissement parent dédié. */
    var exclusionWarningDaysBefore: Int = 7,
    var active: Boolean = true
) : TenantDocument()
