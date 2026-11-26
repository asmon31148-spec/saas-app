package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TierCode(val label: String) {
    STARTER("Starter"),
    GROWTH("Growth"),
    ENTERPRISE("Enterprise"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String): TierCode =
            entries.firstOrNull { it.name == raw } ?: STARTER
    }
}

enum class SlaSupport(val label: String) {
    STANDARD("Standard 8x5"),
    PRIORITY("Priority 16x5"),
    DEDICATED_24_7("Dedicated 24/7");

    companion object {
        fun fromRaw(raw: String): SlaSupport =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

enum class BillingCycle(val label: String) {
    MONTHLY("Monthly"),
    ANNUAL("Annual");

    companion object {
        fun fromRaw(raw: String): BillingCycle =
            entries.firstOrNull { it.name == raw } ?: MONTHLY
    }
}

enum class AccountStatus(val label: String) {
    TRIAL("Trial"),
    ACTIVE("Active"),
    PAST_DUE("Past Due"),
    CHURNED("Churned");

    companion object {
        fun fromRaw(raw: String): AccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class AccountHealth(val label: String) {
    HEALTHY("Healthy"),
    EXPANDING("Expanding"),
    AT_RISK("At Risk");

    companion object {
        fun fromRaw(raw: String): AccountHealth =
            entries.firstOrNull { it.name == raw } ?: HEALTHY
    }
}

enum class InvoiceStatus(val label: String) {
    DRAFT("Draft"),
    SENT("Sent"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    VOID("Void");

    val isTerminal: Boolean
        get() = this == PAID || this == VOID

    companion object {
        fun fromRaw(raw: String): InvoiceStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

@Keep
@IgnoreExtraProperties
data class SaasPlan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val tierCode: String = TierCode.STARTER.name,
    val baseMonthlyCents: Long = 0L,
    val perSeatMonthlyCents: Long = 0L,
    val includedSeats: Long = 5L,
    val annualDiscountPct: Long = 15L,
    val apiCallsLimitK: Long = 100L,
    val slaSupport: String = SlaSupport.STANDARD.name,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun computeMonthlyMrrCents(seats: Long, cycle: BillingCycle): Long {
        val extraSeats = (seats - includedSeats).coerceAtLeast(0L)
        val grossMonthly = baseMonthlyCents + (extraSeats * perSeatMonthlyCents)
        return if (cycle == BillingCycle.ANNUAL && annualDiscountPct in 1..60) {
            (grossMonthly * (100L - annualDiscountPct)) / 100L
        } else {
            grossMonthly
        }
    }
}

@Keep
@IgnoreExtraProperties
data class B2bAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val companyName: String = "",
    val industry: String = "",
    val contactName: String = "",
    val contactEmail: String = "",
    val planId: String = "",
    val planName: String = "",
    val tierCode: String = TierCode.STARTER.name,
    val billingCycle: String = BillingCycle.MONTHLY.name,
    val licensedSeats: Long = 5L,
    val activeSeats: Long = 1L,
    val mrrCents: Long = 0L,
    val status: String = AccountStatus.ACTIVE.name,
    val health: String = AccountHealth.HEALTHY.name,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SaasInvoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = "",
    val companyName: String = "",
    val invoiceNumber: String = "",
    val billingPeriod: String = "",
    val amountCents: Long = 0L,
    val status: String = InvoiceStatus.DRAFT.name,
    val dueDateLabel: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class UnitEconomicsInput(
    val targetAccounts: Int = 45,
    val avgSeatsPerAccount: Int = 18,
    val basePriceMonthlyUsd: Int = 499,
    val perSeatPriceUsd: Int = 25,
    val includedSeats: Int = 10,
    val grossMarginPct: Int = 82,
    val monthlyChurnPct: Float = 1.8f,
    val annualExpansionPct: Float = 18f,
    val cacUsd: Int = 3200
) {
    val arpaMonthlyUsd: Double
        get() {
            val extraSeats = (avgSeatsPerAccount - includedSeats).coerceAtLeast(0)
            return basePriceMonthlyUsd + (extraSeats * perSeatPriceUsd).toDouble()
        }

    val projectedMrrUsd: Double
        get() = targetAccounts * arpaMonthlyUsd

    val projectedArrUsd: Double
        get() = projectedMrrUsd * 12.0

    val ltvUsd: Double
        get() {
            val churnDecimal = (monthlyChurnPct / 100.0).coerceAtLeast(0.002)
            val marginDecimal = (grossMarginPct / 100.0).coerceIn(0.1, 1.0)
            return (arpaMonthlyUsd * marginDecimal) / churnDecimal
        }

    val ltvToCacRatio: Double
        get() = if (cacUsd > 0) ltvUsd / cacUsd else 0.0

    val cacPaybackMonths: Double
        get() {
            val monthlyGrossProfit = arpaMonthlyUsd * (grossMarginPct / 100.0)
            return if (monthlyGrossProfit > 0) cacUsd / monthlyGrossProfit else 0.0
        }

    val netRevenueRetentionPct: Double
        get() {
            val annualizedChurn = (1.0 - Math.pow(1.0 - (monthlyChurnPct / 100.0), 12.0)) * 100.0
            return (100.0 + annualExpansionPct - annualizedChurn).coerceIn(0.0, 250.0)
        }
}
