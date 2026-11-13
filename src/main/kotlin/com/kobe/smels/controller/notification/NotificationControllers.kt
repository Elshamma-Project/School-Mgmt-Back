package com.kobe.smels.controller.notification

import com.kobe.smels.dto.notification.CreateStaffSignalRequest
import com.kobe.smels.dto.notification.StaffSignalAdminNoteRequest
import com.kobe.smels.model.notification.StaffSignalStatus
import com.kobe.smels.service.notification.NotificationService
import com.kobe.smels.service.notification.StaffSignalService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @GetMapping
    fun list(
        @RequestParam(required = false, defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        notificationService.listMine(it, unreadOnly, schoolId)
    }

    @GetMapping("/unread-count")
    fun unreadCount(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { notificationService.unreadCount(it, schoolId) }

    @PostMapping("/{id}/read")
    fun markRead(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { notificationService.markRead(it, id, schoolId) }

    @PostMapping("/{id}/ack")
    fun acknowledge(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { notificationService.acknowledge(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/staff-signals")
class StaffSignalController(
    private val staffSignalService: StaffSignalService
) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateStaffSignalRequest) =
        SecuritySupport.currentUser().flatMap { staffSignalService.create(it, request) }

    @GetMapping("/mine")
    fun mine(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany { staffSignalService.listMine(it, schoolId) }

    @GetMapping
    fun list(
        @RequestParam(required = false) status: StaffSignalStatus?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        staffSignalService.listForAdmin(it, status, schoolId)
    }

    @PostMapping("/{id}/acknowledge")
    fun acknowledge(
        @PathVariable id: String,
        @RequestBody(required = false) body: StaffSignalAdminNoteRequest?
    ) = SecuritySupport.currentUser().flatMap {
        staffSignalService.acknowledge(it, id, body?.adminNote, body?.schoolId)
    }

    @PostMapping("/{id}/close")
    fun close(
        @PathVariable id: String,
        @RequestBody(required = false) body: StaffSignalAdminNoteRequest?
    ) = SecuritySupport.currentUser().flatMap {
        staffSignalService.close(it, id, body?.adminNote, body?.schoolId)
    }
}
