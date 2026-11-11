package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.SchoolBulletinBrandDto
import reactor.core.publisher.Mono

/** Branding école pour documents (bulletins) — frontière reporting ↔ academic. */
interface SchoolBrandApi {
    fun getBulletinBrand(schoolId: String): Mono<SchoolBulletinBrandDto>
}
