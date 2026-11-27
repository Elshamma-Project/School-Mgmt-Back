package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.academic.SchoolBulletinBrandDto
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reçu PDF frais d’inscription (Q-69) — A4 portrait, 1 page.
 * En-tête bilingue type bulletin + logo ; sans marque tierce.
 */
@Service
class FeeEnrollmentReceiptPdfService {

    private val zone: ZoneId = ZoneId.of("Africa/Douala")
    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone)

    fun render(
        brand: SchoolBulletinBrandDto,
        studentName: String,
        matricule: String,
        classLabel: String,
        academicYearLabel: String,
        amount: BigDecimal,
        currency: String,
        paidAt: Instant,
        paymentReference: String?,
        receiptId: String
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(document, out)
        document.open()

        val ink = Color(15, 61, 76)
        val muted = Color(90, 110, 120)
        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, ink)
        val regular = FontFactory.getFont(FontFactory.HELVETICA, 10f, ink)
        val small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, muted)
        val title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, ink)

        document.add(header(brand, bold, small))
        document.add(spacer(10f))
        document.add(
            Paragraph("REÇU D’INSCRIPTION / ENROLLMENT RECEIPT", title).apply {
                alignment = Element.ALIGN_CENTER
            }
        )
        document.add(
            Paragraph("Année scolaire / School year : $academicYearLabel", regular).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 12f
            }
        )

        val table = PdfPTable(floatArrayOf(0.38f, 0.62f))
        table.widthPercentage = 100f
        fun row(label: String, value: String) {
            table.addCell(labelCell(label, small))
            table.addCell(valueCell(value, regular))
        }
        row("N° reçu / Receipt n°", receiptId)
        row("Élève / Student", studentName)
        row("Matricule", matricule.ifBlank { "—" })
        row("Classe / Class", classLabel.ifBlank { "—" })
        row("Date de paiement / Paid at", dateFmt.format(paidAt))
        row("Montant / Amount", "${amount.stripTrailingZeros().toPlainString()} $currency")
        row("Mode", "Espèces / Cash")
        row("Référence", paymentReference?.ifBlank { null } ?: "—")
        document.add(table)

        document.add(spacer(18f))
        document.add(
            Paragraph(
                "Les frais d’inscription sont payés directement à l’établissement. " +
                    "Enrollment fees are paid in cash at the school.",
                small
            )
        )
        document.add(spacer(28f))
        val signs = PdfPTable(2)
        signs.widthPercentage = 100f
        signs.addCell(signCell("Cachet de l’établissement / School stamp", small))
        signs.addCell(signCell("Signature / Signature", small))
        document.add(signs)

        document.close()
        return out.toByteArray()
    }

    private fun header(brand: SchoolBulletinBrandDto, bold: Font, small: Font): PdfPTable {
        val wrap = PdfPTable(floatArrayOf(1f, 0.28f, 1f))
        wrap.widthPercentage = 100f
        wrap.addCell(headerCol(headerLinesFr(brand), bold, small))
        val logo = PdfPCell()
        logo.border = Rectangle.NO_BORDER
        logo.horizontalAlignment = Element.ALIGN_CENTER
        logo.verticalAlignment = Element.ALIGN_MIDDLE
        val bytes = brand.logoBytes
        if (bytes != null && bytes.isNotEmpty()) {
            runCatching {
                val img = Image.getInstance(bytes)
                img.scaleToFit(52f, 52f)
                logo.addElement(img)
            }.onFailure { logo.addElement(Paragraph(" ")) }
        } else {
            logo.addElement(Paragraph(" "))
        }
        wrap.addCell(logo)
        wrap.addCell(headerCol(headerLinesEn(brand), bold, small))
        return wrap
    }

    private fun headerLinesFr(brand: SchoolBulletinBrandDto): List<String> {
        val phones = brand.phones.filter { it.isNotBlank() }.joinToString(" / ")
        return listOfNotNull(
            brand.republicFr?.trim()?.takeIf { it.isNotEmpty() },
            brand.mottoFr?.trim()?.takeIf { it.isNotEmpty() },
            brand.ministryFr?.trim()?.takeIf { it.isNotEmpty() },
            brand.regionalDelegationFr?.trim()?.takeIf { it.isNotEmpty() },
            brand.divisionalDelegationFr?.trim()?.takeIf { it.isNotEmpty() },
            brand.nameFr.trim().takeIf { it.isNotEmpty() },
            listOfNotNull(
                brand.postalBox?.trim()?.takeIf { it.isNotEmpty() }?.let { "B.P. : $it" },
                phones.takeIf { it.isNotEmpty() }?.let { "Tél. : $it" },
            ).takeIf { it.isNotEmpty() }?.joinToString("  ·  "),
        )
    }

    private fun headerLinesEn(brand: SchoolBulletinBrandDto): List<String> {
        val phones = brand.phones.filter { it.isNotBlank() }.joinToString(" / ")
        return listOfNotNull(
            brand.republicEn?.trim()?.takeIf { it.isNotEmpty() },
            brand.mottoEn?.trim()?.takeIf { it.isNotEmpty() },
            brand.ministryEn?.trim()?.takeIf { it.isNotEmpty() },
            brand.regionalDelegationEn?.trim()?.takeIf { it.isNotEmpty() },
            brand.divisionalDelegationEn?.trim()?.takeIf { it.isNotEmpty() },
            brand.nameEn.trim().takeIf { it.isNotEmpty() },
            listOfNotNull(
                brand.postalBox?.trim()?.takeIf { it.isNotEmpty() }?.let { "P.O. Box : $it" },
                phones.takeIf { it.isNotEmpty() }?.let { "Tel. : $it" },
            ).takeIf { it.isNotEmpty() }?.joinToString("  ·  "),
        )
    }

    private fun headerCol(lines: List<String>, bold: Font, small: Font): PdfPCell {
        val p = Paragraph().apply {
            alignment = Element.ALIGN_CENTER
            leading = 11f
            if (lines.isEmpty()) {
                add(Chunk(" ", small))
            } else {
                lines.forEachIndexed { i, line ->
                    add(Chunk(if (i < lines.lastIndex) "$line\n" else line, if (i == 0) bold else small))
                }
            }
        }
        return PdfPCell(p).apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_CENTER
        }
    }

    private fun labelCell(text: String, font: Font) =
        PdfPCell(Paragraph(text, font)).apply {
            border = Rectangle.BOX
            borderColor = Color(210, 220, 224)
            paddingTop = 6f
            paddingBottom = 6f
            paddingLeft = 6f
        }

    private fun valueCell(text: String, font: Font) =
        PdfPCell(Paragraph(text, font)).apply {
            border = Rectangle.BOX
            borderColor = Color(210, 220, 224)
            paddingTop = 6f
            paddingBottom = 6f
            paddingLeft = 6f
        }

    private fun signCell(label: String, font: Font) =
        PdfPCell(Paragraph(label, font)).apply {
            border = Rectangle.NO_BORDER
            fixedHeight = 64f
            paddingTop = 8f
        }

    private fun spacer(h: Float): Paragraph =
        Paragraph(" ").apply { spacingAfter = h }
}
