package com.kobe.smels.repository.staff

import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface StaffRepository : ReactiveMongoRepository<Staff, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Staff>
    fun findBySchoolIdAndStaffNumber(schoolId: String, staffNumber: String): Mono<Staff>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<Staff>
    fun countBySchoolId(schoolId: String): Mono<Long>
    fun findBySchoolIdAndActive(schoolId: String, active: Boolean, pageable: Pageable): Flux<Staff>
    fun countBySchoolIdAndActive(schoolId: String, active: Boolean): Mono<Long>
    fun findBySchoolIdAndFunctionsContainingAndActive(
        schoolId: String,
        function: StaffFunction,
        active: Boolean,
        pageable: Pageable
    ): Flux<Staff>
    fun countBySchoolIdAndFunctionsContainingAndActive(
        schoolId: String,
        function: StaffFunction,
        active: Boolean
    ): Mono<Long>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<Staff>
}
