package com.kobe.smels.repository.student

import com.kobe.smels.model.student.StudentSchoolPhoto
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface StudentSchoolPhotoRepository : ReactiveMongoRepository<StudentSchoolPhoto, String> {
    fun findBySchoolIdAndStudentId(schoolId: String, studentId: String): Mono<StudentSchoolPhoto>
    fun deleteBySchoolIdAndStudentId(schoolId: String, studentId: String): Mono<Void>
}
