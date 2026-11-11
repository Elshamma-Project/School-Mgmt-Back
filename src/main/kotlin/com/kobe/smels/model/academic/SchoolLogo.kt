package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.AuditableDocument
import org.bson.types.Binary
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Logo de l’établissement (UI, bulletins, emploi du temps, documents).
 * Référencé par [School.logoFileId].
 */
@Document(collection = "academic_school_logos")
data class SchoolLogo(
    @Id
    var id: String? = null,
    @Indexed(unique = true)
    var schoolId: String,
    var contentType: String,
    var sizeBytes: Long,
    var data: Binary
) : AuditableDocument()
