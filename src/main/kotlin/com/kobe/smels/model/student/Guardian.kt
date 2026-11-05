package com.kobe.smels.model.student

import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Parent ou tuteur légal. Rôle d'accès futur : PARENT (consultation des notes en temps réel de ses
 * enfants, téléchargement du bulletin annuel publié). Le lien avec les enfants est porté par
 * `Student.guardians`.
 */
@Document(collection = "student_guardians")
@CompoundIndexes(
    CompoundIndex(name = "idx_school_name", def = "{'schoolId': 1, 'lastName': 1, 'firstName': 1}")
)
data class Guardian(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var lastName: String,
    var firstName: String = "",
    var sex: Sex? = null,
    var profession: String? = null,
    var contact: ContactInfo = ContactInfo(),
    var active: Boolean = true
) : TenantDocument()

enum class GuardianRelationship { FATHER, MOTHER, LEGAL_GUARDIAN, OTHER }
