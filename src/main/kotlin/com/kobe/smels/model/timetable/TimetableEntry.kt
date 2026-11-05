package com.kobe.smels.model.timetable

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Une case de l'emploi du temps d'une classe : « mardi, période 4, FRANÇAIS, Mme X ».
 *
 * Références : classe, matière de la classe (ClassSubject), enseignant (Staff), salle (Room) : par identifiants
 * uniquement, car timetable est un module séparé (futur microservice). Les libellés copiés
 * (`subjectLabel`, `teacherLabel`) permettent d'imprimer la page sans appeler les autres modules ; ils sont
 * rafraîchis à la publication.
 *
 * Cas observés :
 *  - cours normal : kind LESSON avec classSubjectId et teacherId ;
 *  - double période : `periodCount = 2` (ex. MATHS en périodes 1-2) ;
 *  - activité sans note : kind ACTIVITY (CLUB, BIBLIOTHÈQUE, MUSIQUE, MÉNAGE GÉNÉRAL avec « Délégué ») ;
 *  - case mixte (« CLUB / MATHS / SVT ») : `note` ;
 *  - travaux dirigés (« TD CHIMIE ») : LESSON avec note ;
 *  - cours hors grille (samedi 8h-10h) : `customStart` / `customEnd` à la place de `startPeriod`.
 *
 * Contraintes à contrôler par le service (MongoDB ne peut pas les exprimer) : un enseignant n'est jamais dans
 * deux classes sur le même créneau, une salle non plus, une classe a au plus une case par créneau (garanti
 * par l'index unique `slotKey`), et le nombre de créneaux d'une matière correspond à ClassSubject.weeklyHours.
 */
@Document(collection = "timetable_entries")
@CompoundIndexes(
    CompoundIndex(name = "uk_class_slot", def = "{'schoolId': 1, 'timetableId': 1, 'classId': 1, 'day': 1, 'slotKey': 1}", unique = true),
    CompoundIndex(name = "idx_teacher_day", def = "{'schoolId': 1, 'timetableId': 1, 'teacherId': 1, 'day': 1}"),
    CompoundIndex(name = "idx_room_day", def = "{'schoolId': 1, 'timetableId': 1, 'roomId': 1, 'day': 1}"),
    CompoundIndex(name = "idx_classsubject", def = "{'schoolId': 1, 'timetableId': 1, 'classSubjectId': 1}")
)
data class TimetableEntry(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var timetableId: String,
    var classId: String,
    var day: DayOfWeek,

    /** Numéro de la première période de la grille (1..8). Null si le cours est hors grille. */
    var startPeriod: Int? = null,
    var periodCount: Int = 1,

    /** Pour un cours hors grille (samedi) : heures exactes. */
    var customStart: LocalTime? = null,
    var customEnd: LocalTime? = null,

    /** Clé de créneau pour l'unicité : "P4" ou "T0800". Calculée par le service. */
    var slotKey: String,

    var kind: EntryKind = EntryKind.LESSON,
    var classSubjectId: String? = null,
    var subjectId: String? = null,
    var teacherId: String? = null,
    var roomId: String? = null,
    var activityType: ActivityType? = null,

    /** Libellé libre (activités, cases mixtes, « TD CHIMIE »). */
    var label: LocalizedText? = null,
    var note: String? = null,

    /** Copies pour l'impression (voir description). */
    var subjectLabel: String? = null,
    var teacherLabel: String? = null
) : TenantDocument()

enum class EntryKind { LESSON, ACTIVITY }

enum class ActivityType {
    CLUB,
    LIBRARY,
    MUSIC,
    GENERAL_CLEANING,
    STUDY_HALL,
    OTHER
}
