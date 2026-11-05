package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Salle (classe, laboratoire, salle informatique, terrain...). Sert surtout au module
 * emplois du temps (conflits de salles) ; n'apparaît pas sur le bulletin.
 */
@Document(collection = "academic_rooms")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_name", def = "{'schoolId': 1, 'name': 1}", unique = true)
)
data class Room(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var name: String,
    var type: RoomType = RoomType.CLASSROOM,
    var capacity: Int? = null,
    var active: Boolean = true
) : TenantDocument()

enum class RoomType { CLASSROOM, LABORATORY, COMPUTER_LAB, SPORTS, WORKSHOP, HALL, OTHER }
