package com.kobe.smels.repository.student

import com.kobe.smels.model.student.Guardian
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface GuardianRepository : ReactiveMongoRepository<Guardian, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Guardian>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Guardian>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Guardian>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
}
