package com.kobe.smels.controller.timetable

import com.kobe.smels.dto.timetable.CreateTimeSlotGridRequest
import com.kobe.smels.dto.timetable.CreateTimetableChangeRequest
import com.kobe.smels.dto.timetable.CreateTimetableRequest
import com.kobe.smels.dto.timetable.DuplicateTimetableRequest
import com.kobe.smels.dto.timetable.LinkSessionGroupRequest
import com.kobe.smels.dto.timetable.ReviewTimetableChangeRequest
import com.kobe.smels.dto.timetable.UnlinkSessionGroupRequest
import com.kobe.smels.dto.timetable.UpdateTimeSlotGridRequest
import com.kobe.smels.dto.timetable.UpdateTimetableRequest
import com.kobe.smels.dto.timetable.UpsertTimetableEntryRequest
import com.kobe.smels.dto.timetable.UpsertTimetableSubjectAliasRequest
import com.kobe.smels.model.shared.Language
import com.kobe.smels.service.timetable.TimeSlotGridService
import com.kobe.smels.service.timetable.TimetableChangeRequestService
import com.kobe.smels.service.timetable.TimetableConflictService
import com.kobe.smels.service.timetable.TimetableEntryService
import com.kobe.smels.service.timetable.TimetablePdfService
import com.kobe.smels.service.timetable.TimetableService
import com.kobe.smels.service.timetable.TimetableSessionGroupService
import com.kobe.smels.service.timetable.TimetableSubjectAliasService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/timetable-grids")
class TimeSlotGridController(private val timeSlotGridService: TimeSlotGridService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateTimeSlotGridRequest) =
        SecuritySupport.currentUser().flatMap { timeSlotGridService.create(it, request) }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser()
        .flatMapMany { timeSlotGridService.listByYear(it, academicYearId, activeOnly, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { timeSlotGridService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateTimeSlotGridRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timeSlotGridService.update(it, id, request, schoolId) }
}

@RestController
@RequestMapping("/api/v1/timetables")
class TimetableController(
    private val timetableService: TimetableService,
    private val timetableEntryService: TimetableEntryService,
    private val timetableConflictService: TimetableConflictService,
    private val timetableSessionGroupService: TimetableSessionGroupService,
    private val timetablePdfService: TimetablePdfService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDraft(@Valid @RequestBody request: CreateTimetableRequest) =
        SecuritySupport.currentUser().flatMap { timetableService.createDraft(it, request) }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser()
        .flatMapMany { timetableService.listByYear(it, academicYearId, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { timetableService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun updateDraft(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateTimetableRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableService.updateDraft(it, id, request, schoolId) }

    @PostMapping("/{id}/publish")
    fun publish(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableService.publish(it, id, schoolId) }

    @PostMapping("/{id}/revise")
    @ResponseStatus(HttpStatus.CREATED)
    fun revise(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableService.revise(it, id, schoolId) }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    fun duplicate(
        @PathVariable id: String,
        @Valid @RequestBody request: DuplicateTimetableRequest
    ) = SecuritySupport.currentUser().flatMap { timetableService.duplicateToYear(it, id, request) }

    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: String,
        @RequestParam classId: String,
        @RequestParam(required = false) language: Language?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser().flatMap { user ->
            timetablePdfService.renderClassPdf(user, id, classId, language, schoolId).map { bytes ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"timetable.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes)
            }
        }

    @GetMapping("/{id}/entries")
    fun listEntries(
        @PathVariable id: String,
        @RequestParam(required = false) classId: String?,
        @RequestParam(required = false) teacherId: String?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        timetableEntryService.listByTimetable(it, id, classId, teacherId, schoolId)
    }

    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun upsertEntry(
        @PathVariable id: String,
        @Valid @RequestBody request: UpsertTimetableEntryRequest
    ) = SecuritySupport.currentUser().flatMap { timetableEntryService.upsert(it, id, request) }

    @GetMapping("/{id}/conflicts")
    fun conflicts(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableConflictService.analyze(it, id, schoolId) }

    @PostMapping("/{id}/session-groups")
    @ResponseStatus(HttpStatus.CREATED)
    fun linkSessionGroup(
        @PathVariable id: String,
        @Valid @RequestBody request: LinkSessionGroupRequest
    ) = SecuritySupport.currentUser().flatMap { timetableSessionGroupService.link(it, id, request) }

    @PostMapping("/{id}/session-groups/{sessionGroupId}/unlink")
    fun unlinkSessionGroup(
        @PathVariable id: String,
        @PathVariable sessionGroupId: String,
        @RequestBody(required = false) request: UnlinkSessionGroupRequest?
    ) = SecuritySupport.currentUser().flatMap {
        timetableSessionGroupService.unlink(
            it,
            id,
            sessionGroupId,
            request ?: UnlinkSessionGroupRequest()
        )
    }
}

@RestController
@RequestMapping("/api/v1/timetable-entries")
class TimetableEntryController(private val timetableEntryService: TimetableEntryService) {
    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { timetableEntryService.getById(it, id, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpsertTimetableEntryRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableEntryService.update(it, id, request, schoolId) }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { timetableEntryService.delete(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/timetable-subject-aliases")
class TimetableSubjectAliasController(
    private val timetableSubjectAliasService: TimetableSubjectAliasService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upsert(@Valid @RequestBody request: UpsertTimetableSubjectAliasRequest) =
        SecuritySupport.currentUser().flatMap { timetableSubjectAliasService.upsert(it, request) }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser()
        .flatMapMany { timetableSubjectAliasService.listByYear(it, academicYearId, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { timetableSubjectAliasService.getById(it, id, schoolId) }

    @DeleteMapping("/{id}")
    fun deactivate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser()
            .flatMap { timetableSubjectAliasService.deactivate(it, id, schoolId) }
}

/** BR-67 : demandes de modification de créneau (enseignant) + revue admin/directeur. */
@RestController
@RequestMapping("/api/v1/timetable-change-requests")
class TimetableChangeRequestController(
    private val timetableChangeRequestService: TimetableChangeRequestService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateTimetableChangeRequest) =
        SecuritySupport.currentUser().flatMap { timetableChangeRequestService.create(it, request) }

    @GetMapping("/pending")
    fun pending(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany { timetableChangeRequestService.listPending(it, schoolId) }

    @GetMapping("/mine")
    fun mine(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany { timetableChangeRequestService.listMine(it, schoolId) }

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableChangeRequestService.approve(it, id, schoolId) }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: String,
        @Valid @RequestBody request: ReviewTimetableChangeRequest
    ) = SecuritySupport.currentUser().flatMap {
        timetableChangeRequestService.reject(it, id, request.reviewNote, request.schoolId)
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { timetableChangeRequestService.cancel(it, id, schoolId) }
}
