package com.kobe.smels.model.user

import com.kobe.smels.model.shared.AuditableDocument
import org.bson.types.Binary
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Avatar de compte (UI seulement).
 * Distinct de `Student.photoFileId` figé sur les bulletins.
 */
@Document(collection = "user_avatars")
data class UserAvatar(
    @Id
    var id: String? = null,
    @Indexed(unique = true)
    var accountId: String,
    var contentType: String,
    var sizeBytes: Long,
    var data: Binary
) : AuditableDocument()
