package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.AuditableDocument
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.LocalizedText
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Établissement (le « tenant »). Un seul pour l'instant, mais tous les autres documents
 * portent déjà `schoolId` pour pouvoir servir plusieurs écoles plus tard.
 *
 * Contient tout ce qui s'imprime dans l'en-tête et le pied des bulletins.
 */
@Document(collection = "academic_schools")
data class School(
    @Id
    var id: String? = null,

    /** Code court unique, ex. "ELSHAMMAH". */
    @Indexed(unique = true)
    var code: String,

    var name: LocalizedText,
    var shortName: String? = null,

    /** En-têtes officiels (République, ministère, délégations), un par cycle (MINESEC, MINEDUB...). */
    var officialHeaders: List<OfficialHeader> = emptyList(),

    var contact: ContactInfo = ContactInfo(),
    var postalBox: String? = null,

    /** Ville imprimée à côté de la date de signature (« YAOUNDE, le 26 Mai 2026 »). */
    var signaturePlace: String? = null,

    /** Titre du signataire (« LE PRINCIPAL » / « THE PRINCIPAL », « LE DIRECTEUR »...). */
    var headTitle: LocalizedText? = null,

    /** Staff.id du chef d'établissement (son nom est imprimé sous le titre). */
    var headOfSchoolStaffId: String? = null,

    /** Référence du fichier logo (stockage de fichiers à définir). */
    var logoFileId: String? = null,

    /** Mention imprimée en bas du bulletin (consultation par parents, app mobile...). */
    var reportCardNotice: LocalizedText? = null,

    /** Mention de bas de page (« Le bulletin est délivré sans rature ni surcharge »). */
    var reportCardDisclaimer: LocalizedText? = null,

    var timezone: String = "Africa/Douala",
    var active: Boolean = true
) : AuditableDocument()

/** En-tête officiel bilingue imprimé en haut du bulletin, valable pour un ou plusieurs cycles. */
data class OfficialHeader(
    val cycles: Set<Cycle>,
    val republic: LocalizedText,
    val motto: LocalizedText,
    val ministry: LocalizedText,
    val regionalDelegation: LocalizedText? = null,
    val divisionalDelegation: LocalizedText? = null
)
