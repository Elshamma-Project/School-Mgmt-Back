package com.kobe.smels.controller.reporting

import com.kobe.smels.dto.reporting.GenerateReportCardsRequest
import com.kobe.smels.service.reporting.ReportCardService
import com.kobe.smels.service.reporting.ReportTemplateService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/report-templates")
class ReportTemplateController(
    private val reportTemplateService: ReportTemplateService
) {
    @GetMapping
    fun list(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany { reportTemplateService.list(it, schoolId) }
}

@RestController
@RequestMapping("/api/v1/report-cards")
class ReportCardController(
    private val reportCardService: ReportCardService
) {
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(@Valid @RequestBody request: GenerateReportCardsRequest) =
        SecuritySupport.currentUser().flatMapMany { reportCardService.generate(it, request) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { reportCardService.getById(it, id, schoolId) }

    @PostMapping("/{id}/validate")
    fun validate(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { reportCardService.validate(it, id, schoolId) }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { reportCardService.publish(it, id, schoolId) }

    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser().flatMap { user ->
            reportCardService.downloadPdf(user, id, schoolId).map { bytes ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"report-card.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes)
            }
        }
}

@RestController
@RequestMapping("/api/v1/public/report-cards")
class ReportCardVerificationController(
    private val reportCardService: ReportCardService
) {
    @GetMapping("/verify/{code}")
    fun verify(@PathVariable code: String) = reportCardService.verify(code)
}
