package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Écriture de paiement scolarité (BR-F07, Q-76 : annulation = VOID).
 */
@Document(collection = "fees_payments")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_student_year",
        def = "{'schoolId': 1, 'academicYearId': 1, 'studentId': 1, 'status': 1}"
    ),
    CompoundIndex(
        name = "idx_school_paid_at",
        def = "{'schoolId': 1, 'paidAt': -1}"
    )
)
data class FeePayment(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var studentId: String,
    var enrollmentId: String,
    var classId: String,
    var scheduleId: String,
    var kind: FeePaymentKind,
    /** 1..3 si [kind] == INSTALLMENT. */
    var installmentIndex: Int? = null,
    @Field(targetType = FieldType.DECIMAL128)
    var amount: BigDecimal,
    var paidAt: Instant,
    var method: FeePaymentMethod = FeePaymentMethod.CASH,
    var reference: String? = null,
    var recordedByAccountId: String,
    var note: String? = null,
    var status: FeePaymentStatus = FeePaymentStatus.RECORDED,
    var voidedAt: Instant? = null,
    var voidedByAccountId: String? = null,
    var voidReason: String? = null
) : TenantDocument()
