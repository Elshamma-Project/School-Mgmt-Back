package com.kobe.smels.controller.portal

import com.kobe.smels.dto.portal.DashboardSummaryResponse
import com.kobe.smels.service.portal.DashboardService
import com.kobe.smels.shared.security.SecuritySupport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Portail — agrégat tableau de bord (docs/08 module portal).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardService: DashboardService
) {
    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) schoolId: String?
    ): Mono<DashboardSummaryResponse> =
        SecuritySupport.currentUser().flatMap { dashboardService.summary(it, schoolId) }
}
