package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.academic.SchoolBulletinBrandDto
import com.lowagie.text.pdf.PdfReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class FeeEnrollmentReceiptPdfServiceTest {

    private val service = FeeEnrollmentReceiptPdfService()

    @Test
    fun `Q-69 enrollment receipt PDF has exactly 1 page`() {
        val bytes = service.render(
            brand = SchoolBulletinBrandDto(
                schoolId = "school-1",
                nameFr = "Collège Démo",
                nameEn = "Demo College",
                postalBox = null,
                republicFr = "République du Cameroun",
                republicEn = "Republic of Cameroon",
                mottoFr = null,
                mottoEn = null,
                ministryFr = null,
                ministryEn = null,
                regionalDelegationFr = null,
                regionalDelegationEn = null,
                divisionalDelegationFr = null,
                divisionalDelegationEn = null
            ),
            studentName = "Élève Démo",
            matricule = "DEMO-001",
            classLabel = "6ème A",
            academicYearLabel = "2026-2027",
            amount = BigDecimal("25000"),
            currency = "XAF",
            paidAt = Instant.parse("2026-09-07T10:00:00Z"),
            paymentReference = "CAISSE-1",
            receiptId = "pay-1"
        )
        PdfReader(bytes).use { reader ->
            assertEquals(1, reader.numberOfPages)
        }
    }
}
