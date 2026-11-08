package com.kobe.smels.repository.student

import com.kobe.smels.model.student.Student
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface StudentRepository : ReactiveMongoRepository<Student, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Student>
    fun findBySchoolIdAndMatricule(schoolId: String, matricule: String): Mono<Student>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Student>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Student>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<Student>
    fun findBySchoolIdAndGuardiansGuardianId(schoolId: String, guardianId: String): Flux<Student>
}
