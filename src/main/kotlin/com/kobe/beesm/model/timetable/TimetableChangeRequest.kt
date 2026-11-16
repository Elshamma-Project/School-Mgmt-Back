package com.kobe.beesm.model.timetable

import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Demande de modification d’un créneau EDT par un enseignant (miroir BR-80 côté notes).
 *
 * L’enseignant propose un changement sur une case **PUBLISHED** ; l’**administration** ou le
 * **directeur** approuve (applique sur un brouillon / nouvelle révision) ou refuse (motif).
 */
@Document(collection = "timetable_change_requests")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_tt_change_status",
        def = "{'schoolId': 1, 'status': 1, 'requestedAt': -1}"
    ),
    CompoundIndex(
        name = "idx_tt_change_teacher",
        def = "{'schoolId': 1, 'teacherId': 1, 'status': 1, 'requestedAt': -1}"
    ),
    CompoundIndex(
        name = "idx_tt_change_entry_pending",
        def = "{'schoolId': 1, 'sourceTimetableId': 1, 'sourceEntryId': 1, 'status': 1}"
    )
)
data class TimetableChangeRequest(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var sourceTimetableId: String,
    var sourceRevision: Int,
    /** Case publiée concernée (null si [changeKind] = ADD). */
    var sourceEntryId: String? = null,

    /** Empreinte pour retrouver la case après duplication de révision. */
    var sourceClassId: String,
    var sourceDay: DayOfWeek,
    var sourceSlotKey: String,
    var sourceOptionCode: String = "",

    var changeKind: TimetableChangeKind = TimetableChangeKind.UPDATE,

    /** Snapshot actuel (figé à la demande). */
    var currentSnapshot: TimetableEntrySnapshot? = null,
    /** Proposition enseignant. */
    var proposed: TimetableEntrySnapshot,

    var classLabel: String? = null,
    var teacherId: String,
    var teacherLabel: String? = null,

    /** Motif obligatoire (≥ 15 caractères). */
    var reason: String,

    var status: TimetableChangeStatus = TimetableChangeStatus.PENDING,
    var requestedBy: String,
    var requestedAt: Instant = Instant.now(),
    var reviewedBy: String? = null,
    var reviewedAt: Instant? = null,
    var reviewNote: String? = null,
    /** Brouillon sur lequel la décision a été appliquée. */
    var appliedTimetableId: String? = null,
    var appliedEntryId: String? = null
) : TenantDocument()

enum class TimetableChangeKind { UPDATE, REMOVE, ADD }

enum class TimetableChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}

/** Snapshot sérialisable d’une case (sans id). */
data class TimetableEntrySnapshot(
    val classId: String,
    val day: DayOfWeek,
    val startPeriod: Int? = null,
    val periodCount: Int = 1,
    val customStart: LocalTime? = null,
    val customEnd: LocalTime? = null,
    val slotKey: String = "",
    val kind: EntryKind = EntryKind.LESSON,
    val classSubjectIds: List<String> = emptyList(),
    val subjectId: String? = null,
    val teacherId: String? = null,
    val roomId: String? = null,
    val activityType: ActivityType? = null,
    val sessionGroupId: String? = null,
    val optionGroupCode: String? = null,
    val optionCode: String = "",
    val label: LocalizedText? = null,
    val note: String? = null,
    val subjectLabel: String? = null,
    val teacherLabel: String? = null
)
