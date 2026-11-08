package com.kobe.smels.repository.academic

import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.Level
import com.kobe.smels.model.academic.Room
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.academic.Subject
import com.kobe.smels.model.academic.SubjectGroup
import com.kobe.smels.model.academic.TermCompetency
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SchoolRepository : ReactiveMongoRepository<School, String> {
    fun findByCode(code: String): Mono<School>
    fun findByActiveTrue(): Flux<School>
}

interface AcademicYearRepository : ReactiveMongoRepository<AcademicYear, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<AcademicYear>
    fun findBySchoolIdAndLabel(schoolId: String, label: String): Mono<AcademicYear>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<AcademicYear>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndCurrentTrue(schoolId: String): Mono<AcademicYear>
    fun findBySchoolIdOrderByStartDateDesc(schoolId: String): Flux<AcademicYear>
}

interface LevelRepository : ReactiveMongoRepository<Level, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Level>
    fun findBySchoolIdAndCode(schoolId: String, code: String): Mono<Level>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Level>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Level>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<Level>
}

interface SchoolClassRepository : ReactiveMongoRepository<SchoolClass, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<SchoolClass>
    fun findBySchoolIdAndAcademicYearIdAndName(schoolId: String, academicYearId: String, name: String): Mono<SchoolClass>
    fun findBySchoolIdAndAcademicYearId(schoolId: String, academicYearId: String, pageable: Pageable): Flux<SchoolClass>
    fun countBySchoolIdAndAcademicYearId(schoolId: String, academicYearId: String): Mono<Long>
    fun findBySchoolIdAndAcademicYearIdAndActive(
        schoolId: String,
        academicYearId: String,
        active: Boolean,
        pageable: Pageable
    ): Flux<SchoolClass>
    fun countBySchoolIdAndAcademicYearIdAndActive(
        schoolId: String,
        academicYearId: String,
        active: Boolean
    ): Mono<Long>
    fun findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId: String, academicYearId: String): Flux<SchoolClass>
}

interface RoomRepository : ReactiveMongoRepository<Room, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Room>
    fun findBySchoolIdAndName(schoolId: String, name: String): Mono<Room>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Room>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Room>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
}

interface SubjectRepository : ReactiveMongoRepository<Subject, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Subject>
    fun findBySchoolIdAndCode(schoolId: String, code: String): Mono<Subject>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Subject>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Subject>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<Subject>
}

interface SubjectGroupRepository : ReactiveMongoRepository<SubjectGroup, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<SubjectGroup>
    fun findBySchoolIdAndCode(schoolId: String, code: String): Mono<SubjectGroup>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<SubjectGroup>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<SubjectGroup>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
}

interface ClassSubjectRepository : ReactiveMongoRepository<ClassSubject, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<ClassSubject>
    fun findBySchoolIdAndClassIdAndSubjectId(schoolId: String, classId: String, subjectId: String): Mono<ClassSubject>
    fun findBySchoolIdAndClassId(schoolId: String, classId: String): Flux<ClassSubject>
    fun findBySchoolIdAndTeacherIdAndAcademicYearId(
        schoolId: String,
        teacherId: String,
        academicYearId: String
    ): Flux<ClassSubject>
    fun findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId: String, academicYearId: String): Flux<ClassSubject>
    fun findBySchoolIdAndClassIdAndActive(
        schoolId: String,
        classId: String,
        active: Boolean,
        pageable: Pageable
    ): Flux<ClassSubject>
    fun countBySchoolIdAndClassIdAndActive(schoolId: String, classId: String, active: Boolean): Mono<Long>
    fun findBySchoolIdAndClassId(schoolId: String, classId: String, pageable: Pageable): Flux<ClassSubject>
    fun countBySchoolIdAndClassId(schoolId: String, classId: String): Mono<Long>
}

interface TermCompetencyRepository : ReactiveMongoRepository<TermCompetency, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TermCompetency>
    fun findBySchoolIdAndClassSubjectIdAndTermNumber(
        schoolId: String,
        classSubjectId: String,
        termNumber: Int
    ): Mono<TermCompetency>
    fun findBySchoolIdAndClassSubjectId(schoolId: String, classSubjectId: String): Flux<TermCompetency>
}
