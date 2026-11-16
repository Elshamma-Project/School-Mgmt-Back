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
 * Références par identifiants uniquement (module séparé / futur microservice). Libellés copiés
 * (`subjectLabel`, `teacherLabel`) pour imprimer sans appeler academic/staff ; rafraîchis à la publication.
 *
 * Phase 0 (PDF EDT 2026-2027) :
 *  - [sessionGroupId] : cours groupé multi-classes (même enseignant/salle au même créneau) — BR-09 ;
 *  - [optionGroupCode] / [optionCode] : options parallèles (ex. LV2 ALL/ESP) — unicité par option ;
 *  - [classSubjectIds] : un ou plusieurs ClassSubject (libellé EDT ≠ une seule matière bulletin) — BR-63 ;
 *  - hors grille : [customStart]/[customEnd], y compris [DayOfWeek.SATURDAY] (Q-33) ;
 *  - [EntryKind.ACTIVITY] : CHINOIS, MUSIQUE, CLUB, BIBLIOTHÈQUE, MÉNAGE… sans note (Q-47 v1).
 *
 * Ne jamais saisir par texte libre d’alias : passer par [TimetableSubjectAlias] (Q-30 / Q-56).
 */
@Document(collection = "timetable_entries")
@CompoundIndexes(
    // optionCode = "" pour une case sans option (évite l’ambiguïté null de l’index unique Mongo).
    CompoundIndex(
        name = "uk_class_slot_option",
        def = "{'schoolId': 1, 'timetableId': 1, 'classId': 1, 'day': 1, 'slotKey': 1, 'optionCode': 1}",
        unique = true
    ),
    CompoundIndex(name = "idx_teacher_day", def = "{'schoolId': 1, 'timetableId': 1, 'teacherId': 1, 'day': 1}"),
    CompoundIndex(name = "idx_room_day", def = "{'schoolId': 1, 'timetableId': 1, 'roomId': 1, 'day': 1}"),
    CompoundIndex(name = "idx_classsubjects", def = "{'schoolId': 1, 'timetableId': 1, 'classSubjectIds': 1}"),
    CompoundIndex(name = "idx_session_group", def = "{'schoolId': 1, 'timetableId': 1, 'sessionGroupId': 1}")
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

    /**
     * Cours hors grille (ex. samedi) : heures exactes.
     * Observé 2026-2027 : 3ème ALL 7h30–10h30, 1ère A ALL 10h30–13h30, 1ère C/D TD Chimie 8h–10h (Q-33).
     */
    var customStart: LocalTime? = null,
    var customEnd: LocalTime? = null,

    /** Clé de créneau pour l'unicité : "P4" ou "T0800". Calculée par le service. */
    var slotKey: String,

    var kind: EntryKind = EntryKind.LESSON,

    /**
     * ClassSubject liés à cette case (souvent 1 ; plusieurs si un libellé EDT regroupe
     * expression écrite + orale + …). Vide pour [EntryKind.ACTIVITY].
     */
    var classSubjectIds: List<String> = emptyList(),

    /** Subject.id optionnel (activité catalogue ou matière principale affichée). */
    var subjectId: String? = null,
    var teacherId: String? = null,
    var roomId: String? = null,
    var activityType: ActivityType? = null,

    /**
     * Séance commune multi-classes (même créneau, même enseignant/salle).
     * Les cases partageant le même id ne se conflictuent pas entre elles (BR-09, Q-55).
     */
    var sessionGroupId: String? = null,

    /**
     * Groupe d’options parallèles (ex. `"LV2"`). Null / absent si case unique pour la classe.
     * Voir Q-41 : l’EDT 2026-2027 suggère une seule classe avec options, sans trancher le modèle notes.
     */
    var optionGroupCode: String? = null,

    /**
     * Code d’option dans le groupe (ex. `"ALL"`, `"ESP"`). Chaîne vide `""` si pas d’option
     * (requis pour l’index unique `uk_class_slot_option`).
     */
    var optionCode: String = "",

    /** Libellé libre (activités, cases mixtes, « TD CHIMIE ») — pas un alias de matière. */
    var label: LocalizedText? = null,
    var note: String? = null,

    /** Copies pour l'impression. */
    var subjectLabel: String? = null,
    var teacherLabel: String? = null
) : TenantDocument()

enum class EntryKind { LESSON, ACTIVITY }

enum class ActivityType {
    CLUB,
    LIBRARY,
    MUSIC,
    /** Chinois vu sur l’EDT, absent des bulletins — v1 = activité (Q-47). */
    CHINESE,
    GENERAL_CLEANING,
    STUDY_HALL,
    OTHER
}
