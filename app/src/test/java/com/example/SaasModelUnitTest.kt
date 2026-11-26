package com.example

import com.example.data.BillingCycle
import com.example.data.SaasPlan
import com.example.data.TierCode
import com.example.data.UnitEconomicsInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaasModelUnitTest {

    @Test
    fun `plan pricing computes monthly and annual discounted MRR accurately`() {
        val growthPlan = SaasPlan(
            name = "Growth Scale",
            tierCode = TierCode.GROWTH.name,
            baseMonthlyCents = 49900L, // $499
            perSeatMonthlyCents = 2500L, // $25/seat
            includedSeats = 10L,
            annualDiscountPct = 20L
        )

        // 10 seats -> within included seats -> $499/mo
        assertEquals(49900L, growthPlan.computeMonthlyMrrCents(10L, BillingCycle.MONTHLY))

        // 18 seats -> 8 extra * $25 = $200 + $499 = $699/mo
        assertEquals(69900L, growthPlan.computeMonthlyMrrCents(18L, BillingCycle.MONTHLY))

        // 18 seats on Annual (20% discount) -> 69900 * 0.8 = 55920 cents
        assertEquals(55920L, growthPlan.computeMonthlyMrrCents(18L, BillingCycle.ANNUAL))
    }

    @Test
    fun `unit economics calculator derives ARPA, ARR, LTV, and LTV to CAC ratio`() {
        val model = UnitEconomicsInput(
            targetAccounts = 50,
            avgSeatsPerAccount = 20,
            basePriceMonthlyUsd = 500,
            perSeatPriceUsd = 25,
            includedSeats = 10,
            grossMarginPct = 80,
            monthlyChurnPct = 2.0f,
            annualExpansionPct = 20f,
            cacUsd = 3000
        )

        // ARPA = 500 + (20-10)*25 = $750/mo
        assertEquals(750.0, model.arpaMonthlyUsd, 0.01)
        // MRR = 50 * 750 = $37,500 -> ARR = $450,000
        assertEquals(450_000.0, model.projectedArrUsd, 0.01)
        // LTV = (750 * 0.8) / 0.02 = $30,000
        assertEquals(30_000.0, model.ltvUsd, 0.1)
        // LTV:CAC = 30000 / 3000 = 10.0x
        assertEquals(10.0, model.ltvToCacRatio, 0.1)
        // Payback = 3000 / (750 * 0.8) = 5.0 months
        assertEquals(5.0, model.cacPaybackMonths, 0.1)
        assertTrue(model.netRevenueRetentionPct > 90.0)
    }
}
