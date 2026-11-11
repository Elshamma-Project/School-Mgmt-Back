package com.kobe.smels.model.student

import com.kobe.smels.model.shared.AuditableDocument
import org.bson.types.Binary
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Photo scolaire de l’élève (bulletins, cartes) — distincte de l’avatar compte
 * (`UserAvatar` / `UserAccount.avatarFileId`).
 * Référencée par [Student.photoFileId].
 */
@Document(collection = "student_school_photos")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_student_photo", def = "{'schoolId': 1, 'studentId': 1}", unique = true)
)
data class StudentSchoolPhoto(
    @Id
    var id: String? = null,
    var schoolId: String,
    var studentId: String,
    var contentType: String,
    var sizeBytes: Long,
    var data: Binary
) : AuditableDocument()
