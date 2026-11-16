package com.kobe.smels.repository.timetable

import com.kobe.smels.model.timetable.TimeSlotGrid
import com.kobe.smels.model.timetable.Timetable
import com.kobe.smels.model.timetable.TimetableChangeRequest
import com.kobe.smels.model.timetable.TimetableChangeStatus
import com.kobe.smels.model.timetable.TimetableEntry
import com.kobe.smels.model.timetable.TimetableStatus
import com.kobe.smels.model.timetable.TimetableSubjectAlias
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.DayOfWeek

interface TimeSlotGridRepository : ReactiveMongoRepository<TimeSlotGrid, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TimeSlotGrid>
    fun findBySchoolIdAndAcademicYearId(schoolId: String, academicYearId: String): Flux<TimeSlotGrid>
    fun findBySchoolIdAndAcademicYearIdAndActiveTrue(
        schoolId: String,
        academicYearId: String
    ): Flux<TimeSlotGrid>
}

interface TimetableRepository : ReactiveMongoRepository<Timetable, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Timetable>
    fun findBySchoolIdAndAcademicYearIdOrderByRevisionDesc(
        schoolId: String,
        academicYearId: String
    ): Flux<Timetable>

    fun findBySchoolIdAndAcademicYearIdAndStatus(
        schoolId: String,
        academicYearId: String,
        status: TimetableStatus
    ): Flux<Timetable>

    fun findFirstBySchoolIdAndAcademicYearIdAndStatusOrderByRevisionDesc(
        schoolId: String,
        academicYearId: String,
        status: TimetableStatus
    ): Mono<Timetable>
}

interface TimetableEntryRepository : ReactiveMongoRepository<TimetableEntry, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TimetableEntry>
    fun findBySchoolIdAndTimetableId(schoolId: String, timetableId: String): Flux<TimetableEntry>
    fun findBySchoolIdAndTimetableIdAndClassId(
        schoolId: String,
        timetableId: String,
        classId: String
    ): Flux<TimetableEntry>

    fun findBySchoolIdAndTimetableIdAndClassIdAndDayAndSlotKeyAndOptionCode(
        schoolId: String,
        timetableId: String,
        classId: String,
        day: DayOfWeek,
        slotKey: String,
        optionCode: String
    ): Mono<TimetableEntry>

    fun findBySchoolIdAndTimetableIdAndTeacherId(
        schoolId: String,
        timetableId: String,
        teacherId: String
    ): Flux<TimetableEntry>

    fun findBySchoolIdAndTimetableIdAndDayAndSlotKeyAndOptionCode(
        schoolId: String,
        timetableId: String,
        day: DayOfWeek,
        slotKey: String,
        optionCode: String
    ): Flux<TimetableEntry>

    fun findBySchoolIdAndTimetableIdAndSessionGroupId(
        schoolId: String,
        timetableId: String,
        sessionGroupId: String
    ): Flux<TimetableEntry>

    fun deleteBySchoolIdAndTimetableId(schoolId: String, timetableId: String): Mono<Void>
}

interface TimetableChangeRequestRepository : ReactiveMongoRepository<TimetableChangeRequest, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TimetableChangeRequest>
    fun findBySchoolIdAndStatusOrderByRequestedAtDesc(
        schoolId: String,
        status: TimetableChangeStatus
    ): Flux<TimetableChangeRequest>

    fun findBySchoolIdAndRequestedByOrderByRequestedAtDesc(
        schoolId: String,
        requestedBy: String
    ): Flux<TimetableChangeRequest>

    fun findBySchoolIdAndTeacherIdOrderByRequestedAtDesc(
        schoolId: String,
        teacherId: String
    ): Flux<TimetableChangeRequest>

    fun findBySchoolIdAndSourceTimetableIdAndSourceEntryIdAndStatus(
        schoolId: String,
        sourceTimetableId: String,
        sourceEntryId: String,
        status: TimetableChangeStatus
    ): Flux<TimetableChangeRequest>
}

interface TimetableSubjectAliasRepository : ReactiveMongoRepository<TimetableSubjectAlias, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TimetableSubjectAlias>
    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String
    ): Flux<TimetableSubjectAlias>

    fun findBySchoolIdAndAcademicYearIdAndSectionKeyAndAliasNormalized(
        schoolId: String,
        academicYearId: String,
        sectionKey: String,
        aliasNormalized: String
    ): Mono<TimetableSubjectAlias>
}
