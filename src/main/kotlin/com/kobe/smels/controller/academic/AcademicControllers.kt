package com.kobe.smels.controller.academic

import com.kobe.smels.dto.academic.CreateAcademicYearRequest
import com.kobe.smels.dto.academic.CreateClassSubjectRequest
import com.kobe.smels.dto.academic.CreateLevelRequest
import com.kobe.smels.dto.academic.CreateRoomRequest
import com.kobe.smels.dto.academic.CreateSchoolClassRequest
import com.kobe.smels.dto.academic.CreateSchoolRequest
import com.kobe.smels.dto.academic.CreateSubjectGroupRequest
import com.kobe.smels.dto.academic.CreateSubjectRequest
import com.kobe.smels.dto.academic.UpdateAcademicYearRequest
import com.kobe.smels.dto.academic.UpdateClassSubjectRequest
import com.kobe.smels.dto.academic.UpdateLevelRequest
import com.kobe.smels.dto.academic.UpdateRoomRequest
import com.kobe.smels.dto.academic.UpdateSchoolClassRequest
import com.kobe.smels.dto.academic.UpdateSchoolRequest
import com.kobe.smels.dto.academic.UpdateSubjectGroupRequest
import com.kobe.smels.dto.academic.UpdateSubjectRequest
import com.kobe.smels.dto.academic.UpsertTermCompetencyRequest
import com.kobe.smels.service.academic.AcademicYearService
import com.kobe.smels.service.academic.ClassSubjectService
import com.kobe.smels.service.academic.LevelService
import com.kobe.smels.service.academic.RoomService
import com.kobe.smels.service.academic.SchoolClassService
import com.kobe.smels.service.academic.SchoolService
import com.kobe.smels.service.academic.SubjectGroupService
import com.kobe.smels.service.academic.SubjectService
import com.kobe.smels.service.academic.TermCompetencyService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/schools")
class SchoolController(private val schoolService: SchoolService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateSchoolRequest) =
        SecuritySupport.currentUser().flatMap { schoolService.create(it, request) }

    @GetMapping
    fun list() = SecuritySupport.currentUser().flatMapMany { schoolService.list(it) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String) =
        SecuritySupport.currentUser().flatMap { schoolService.getById(it, id) }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @Valid @RequestBody request: UpdateSchoolRequest) =
        SecuritySupport.currentUser().flatMap { schoolService.update(it, id, request) }

    @PutMapping("/{id}/logo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadLogo(
        @PathVariable id: String,
        @RequestPart("file") file: FilePart
    ) = SecuritySupport.currentUser().flatMap { schoolService.uploadLogo(it, id, file) }

    @GetMapping("/{id}/logo")
    fun getLogo(@PathVariable id: String): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser()
            .flatMap { schoolService.getLogo(it, id) }
            .map { logo ->
                ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(logo.contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .body(logo.data.data)
            }

    @DeleteMapping("/{id}/logo")
    fun deleteLogo(@PathVariable id: String) =
        SecuritySupport.currentUser().flatMap { schoolService.deleteLogo(it, id) }
}

@RestController
@RequestMapping("/api/v1/academic-years")
class AcademicYearController(private val academicYearService: AcademicYearService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateAcademicYearRequest) =
        SecuritySupport.currentUser().flatMap { academicYearService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { academicYearService.list(it, page, size, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { academicYearService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateAcademicYearRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { academicYearService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/set-current")
    fun setCurrent(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { academicYearService.setCurrent(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/levels")
class LevelController(private val levelService: LevelService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateLevelRequest) =
        SecuritySupport.currentUser().flatMap { levelService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { levelService.list(it, page, size, active, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { levelService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateLevelRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { levelService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { levelService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { levelService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/classes")
class SchoolClassController(private val schoolClassService: SchoolClassService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateSchoolClassRequest) =
        SecuritySupport.currentUser().flatMap { schoolClassService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser()
        .flatMap { schoolClassService.list(it, academicYearId, page, size, active, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { schoolClassService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateSchoolClassRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { schoolClassService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { schoolClassService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { schoolClassService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/rooms")
class RoomController(private val roomService: RoomService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateRoomRequest) =
        SecuritySupport.currentUser().flatMap { roomService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { roomService.list(it, page, size, active, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { roomService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateRoomRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { roomService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { roomService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { roomService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/subjects")
class SubjectController(private val subjectService: SubjectService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateSubjectRequest) =
        SecuritySupport.currentUser().flatMap { subjectService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { subjectService.list(it, page, size, active, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateSubjectRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { subjectService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/subject-groups")
class SubjectGroupController(private val subjectGroupService: SubjectGroupService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateSubjectGroupRequest) =
        SecuritySupport.currentUser().flatMap { subjectGroupService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { subjectGroupService.list(it, page, size, active, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectGroupService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateSubjectGroupRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { subjectGroupService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectGroupService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { subjectGroupService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/class-subjects")
class ClassSubjectController(private val classSubjectService: ClassSubjectService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateClassSubjectRequest) =
        SecuritySupport.currentUser().flatMap { classSubjectService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam classId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser()
        .flatMap { classSubjectService.listByClass(it, classId, page, size, active, schoolId) }

    /** Affectations d’enseignement d’un membre du personnel (enseignant titulaire). */
    @GetMapping("/by-teacher")
    fun listByTeacher(
        @RequestParam teacherId: String,
        @RequestParam(required = false) academicYearId: String?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        classSubjectService.listAssignmentsForTeacher(it, teacherId, academicYearId, schoolId)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { classSubjectService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateClassSubjectRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { classSubjectService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { classSubjectService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { classSubjectService.reactivate(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/term-competencies")
class TermCompetencyController(private val termCompetencyService: TermCompetencyService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upsert(@Valid @RequestBody request: UpsertTermCompetencyRequest) =
        SecuritySupport.currentUser().flatMap { termCompetencyService.upsert(it, request) }

    @GetMapping
    fun list(
        @RequestParam classSubjectId: String,
        @RequestParam(required = false) schoolId: String?
    ) =
        SecuritySupport.currentUser().flatMapMany {
            termCompetencyService.listByClassSubject(it, classSubjectId, schoolId)
        }
}
