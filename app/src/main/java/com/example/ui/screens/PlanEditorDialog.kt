package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.SaasPlan
import com.example.data.SlaSupport
import com.example.data.TierCode
import com.example.ui.components.parseDollarsToCents

@Composable
fun PlanEditorDialog(
    initialPlan: SaasPlan?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (SaasPlan) -> Unit
) {
    var name by remember(initialPlan) { mutableStateOf(initialPlan?.name ?: "") }
    var tierCode by remember(initialPlan) {
        mutableStateOf(if (initialPlan != null) TierCode.fromRaw(initialPlan.tierCode) else TierCode.GROWTH)
    }
    var baseMonthlyText by remember(initialPlan) {
        mutableStateOf(((initialPlan?.baseMonthlyCents ?: 49900L) / 100.0).toInt().toString())
    }
    var perSeatText by remember(initialPlan) {
        mutableStateOf(((initialPlan?.perSeatMonthlyCents ?: 2500L) / 100.0).toInt().toString())
    }
    var includedSeatsText by remember(initialPlan) {
        mutableStateOf((initialPlan?.includedSeats ?: 10L).toString())
    }
    var annualDiscountText by remember(initialPlan) {
        mutableStateOf((initialPlan?.annualDiscountPct ?: 15L).toString())
    }
    var apiLimitKText by remember(initialPlan) {
        mutableStateOf((initialPlan?.apiCallsLimitK ?: 500L).toString())
    }
    var slaSupport by remember(initialPlan) {
        mutableStateOf(if (initialPlan != null) SlaSupport.fromRaw(initialPlan.slaSupport) else SlaSupport.PRIORITY)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = if (initialPlan == null) "Create SaaS Pricing Tier" else "Edit Pricing Tier",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Quick preset chips for fast SaaS tier configuration
                if (initialPlan == null) {
                    Text(
                        text = "Quick Templates",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = "Starter Team"
                                tierCode = TierCode.STARTER
                                baseMonthlyText = "149"
                                perSeatText = "15"
                                includedSeatsText = "5"
                                annualDiscountText = "15"
                                apiLimitKText = "100"
                                slaSupport = SlaSupport.STANDARD
                            },
                            label = { Text("Starter $149") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = "Growth Scale"
                                tierCode = TierCode.GROWTH
                                baseMonthlyText = "499"
                                perSeatText = "25"
                                includedSeatsText = "15"
                                annualDiscountText = "20"
                                apiLimitKText = "1000"
                                slaSupport = SlaSupport.PRIORITY
                            },
                            label = { Text("Growth $499") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = "Enterprise Cloud"
                                tierCode = TierCode.ENTERPRISE
                                baseMonthlyText = "1499"
                                perSeatText = "35"
                                includedSeatsText = "40"
                                annualDiscountText = "25"
                                apiLimitKText = "10000"
                                slaSupport = SlaSupport.DEDICATED_24_7
                            },
                            label = { Text("Enterprise $1,499") }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Tier Name *") },
                    placeholder = { Text("e.g., Enterprise Cloud") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan_name_input")
                )

                Text(
                    text = "Tier Classification",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TierCode.entries.forEach { tier ->
                        FilterChip(
                            selected = tierCode == tier,
                            onClick = { tierCode = tier },
                            label = { Text(tier.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseMonthlyText,
                        onValueChange = { baseMonthlyText = it },
                        label = { Text("Base / mo") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("plan_base_price_input")
                    )
                    OutlinedTextField(
                        value = perSeatText,
                        onValueChange = { perSeatText = it },
                        label = { Text("Per Seat / mo") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = includedSeatsText,
                        onValueChange = { includedSeatsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Included Seats") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = annualDiscountText,
                        onValueChange = { annualDiscountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Annual Disc %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = apiLimitKText,
                    onValueChange = { apiLimitKText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Monthly API Quota (in thousands)") },
                    suffix = { Text("k req/mo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Support SLA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SlaSupport.entries.forEach { sla ->
                        FilterChip(
                            selected = slaSupport == sla,
                            onClick = { slaSupport = sla },
                            label = { Text(sla.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val baseCents = parseDollarsToCents(baseMonthlyText)
                    val seatCents = parseDollarsToCents(perSeatText)
                    val incSeats = includedSeatsText.toLongOrNull()
                    val disc = annualDiscountText.toLongOrNull()
                    val apiK = apiLimitKText.toLongOrNull()
                    when {
                        name.trim().length < 2 ->
                            validationError = "Tier name must be at least 2 characters."
                        baseCents == null || baseCents < 0L ->
                            validationError = "Enter a valid base monthly price."
                        seatCents == null || seatCents < 0L ->
                            validationError = "Enter a valid per-seat monthly price."
                        incSeats == null || incSeats !in 1L..10_000L ->
                            validationError = "Included seats must be 1–10,000."
                        disc == null || disc !in 0L..60L ->
                            validationError = "Annual discount must be 0–60%."
                        apiK == null || apiK !in 1L..1_000_000L ->
                            validationError = "API quota must be at least 1k calls."
                        else -> {
                            onSave(
                                SaasPlan(
                                    id = initialPlan?.id ?: "",
                                    userId = initialPlan?.userId ?: "",
                                    name = name.trim(),
                                    tierCode = tierCode.name,
                                    baseMonthlyCents = baseCents,
                                    perSeatMonthlyCents = seatCents,
                                    includedSeats = incSeats,
                                    annualDiscountPct = disc,
                                    apiCallsLimitK = apiK,
                                    slaSupport = slaSupport.name,
                                    active = initialPlan?.active ?: true
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_plan_button")
            ) {
                Text(if (isBusy) "Saving..." else "Save Tier")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}
