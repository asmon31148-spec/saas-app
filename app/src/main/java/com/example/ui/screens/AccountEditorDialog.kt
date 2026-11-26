package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AccountHealth
import com.example.data.AccountStatus
import com.example.data.B2bAccount
import com.example.data.BillingCycle
import com.example.data.SaasPlan
import com.example.data.TierCode
import com.example.ui.components.formatUsdFromCents
import com.example.ui.components.parseDollarsToCents

@Composable
fun AccountEditorDialog(
    initialAccount: B2bAccount?,
    availablePlans: List<SaasPlan>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (B2bAccount) -> Unit
) {
    var companyName by remember(initialAccount) { mutableStateOf(initialAccount?.companyName ?: "") }
    var industry by remember(initialAccount) { mutableStateOf(initialAccount?.industry ?: "") }
    var contactName by remember(initialAccount) { mutableStateOf(initialAccount?.contactName ?: "") }
    var contactEmail by remember(initialAccount) { mutableStateOf(initialAccount?.contactEmail ?: "") }

    val activePlans = remember(availablePlans) { availablePlans.filter { it.active } }
    var selectedPlanId by remember(initialAccount) {
        mutableStateOf(initialAccount?.planId?.takeIf { it.isNotBlank() } ?: activePlans.firstOrNull()?.id ?: "")
    }
    var customPlanName by remember(initialAccount) {
        mutableStateOf(initialAccount?.planName ?: activePlans.firstOrNull()?.name ?: "Growth Plan")
    }
    var tierCode by remember(initialAccount) {
        mutableStateOf(
            if (initialAccount != null) TierCode.fromRaw(initialAccount.tierCode)
            else activePlans.firstOrNull()?.let { TierCode.fromRaw(it.tierCode) } ?: TierCode.GROWTH
        )
    }
    var billingCycle by remember(initialAccount) {
        mutableStateOf(
            if (initialAccount != null) BillingCycle.fromRaw(initialAccount.billingCycle)
            else BillingCycle.ANNUAL
        )
    }
    var licensedSeatsText by remember(initialAccount) {
        mutableStateOf((initialAccount?.licensedSeats ?: 15L).toString())
    }
    var activeSeatsText by remember(initialAccount) {
        mutableStateOf((initialAccount?.activeSeats ?: 12L).toString())
    }
    var mrrDollarsText by remember(initialAccount) {
        val initialMrr = initialAccount?.mrrCents
            ?: activePlans.firstOrNull()?.computeMonthlyMrrCents(15L, BillingCycle.ANNUAL)
            ?: 49900L
        mutableStateOf((initialMrr / 100.0).toInt().toString())
    }
    var status by remember(initialAccount) {
        mutableStateOf(if (initialAccount != null) AccountStatus.fromRaw(initialAccount.status) else AccountStatus.ACTIVE)
    }
    var health by remember(initialAccount) {
        mutableStateOf(if (initialAccount != null) AccountHealth.fromRaw(initialAccount.health) else AccountHealth.HEALTHY)
    }
    var notes by remember(initialAccount) { mutableStateOf(initialAccount?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun recalculateFromPlan(plan: SaasPlan?, seats: Long, cycle: BillingCycle) {
        if (plan != null) {
            val computed = plan.computeMonthlyMrrCents(seats, cycle)
            mrrDollarsText = (computed / 100.0).toInt().toString()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = if (initialAccount == null) "Add B2B Account" else "Edit B2B Account",
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

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it; validationError = null },
                    label = { Text("Company Name *") },
                    placeholder = { Text("e.g., Acme Robotics Inc.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_company_input")
                )

                OutlinedTextField(
                    value = industry,
                    onValueChange = { industry = it; validationError = null },
                    label = { Text("Industry Vertical *") },
                    placeholder = { Text("e.g., FinTech, Logistics, Healthcare") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_industry_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it; validationError = null },
                        label = { Text("Primary Contact *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("account_contact_name_input")
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it; validationError = null },
                        label = { Text("Work Email *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("account_contact_email_input")
                    )
                }

                if (activePlans.isNotEmpty()) {
                    Text(
                        text = "Subscription Plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activePlans.forEach { plan ->
                            FilterChip(
                                selected = selectedPlanId == plan.id,
                                onClick = {
                                    selectedPlanId = plan.id
                                    customPlanName = plan.name
                                    tierCode = TierCode.fromRaw(plan.tierCode)
                                    val seats = licensedSeatsText.toLongOrNull() ?: plan.includedSeats
                                    recalculateFromPlan(plan, seats, billingCycle)
                                },
                                label = { Text("${plan.name} (${formatUsdFromCents(plan.baseMonthlyCents)})") }
                            )
                        }
                        FilterChip(
                            selected = selectedPlanId.isBlank(),
                            onClick = { selectedPlanId = "" },
                            label = { Text("Custom") }
                        )
                    }
                }

                if (selectedPlanId.isBlank()) {
                    OutlinedTextField(
                        value = customPlanName,
                        onValueChange = { customPlanName = it },
                        label = { Text("Plan / Contract Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                }

                Text(
                    text = "Billing Cycle",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingCycle.entries.forEach { cycle ->
                        FilterChip(
                            selected = billingCycle == cycle,
                            onClick = {
                                billingCycle = cycle
                                val plan = activePlans.firstOrNull { it.id == selectedPlanId }
                                val seats = licensedSeatsText.toLongOrNull() ?: 1L
                                recalculateFromPlan(plan, seats, cycle)
                            },
                            label = { Text(cycle.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = licensedSeatsText,
                        onValueChange = { input ->
                            licensedSeatsText = input.filter { it.isDigit() }
                            val plan = activePlans.firstOrNull { it.id == selectedPlanId }
                            val seats = licensedSeatsText.toLongOrNull() ?: 1L
                            recalculateFromPlan(plan, seats, billingCycle)
                        },
                        label = { Text("Licensed Seats") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("account_seats_input")
                    )
                    OutlinedTextField(
                        value = activeSeatsText,
                        onValueChange = { activeSeatsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Active Seats") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = mrrDollarsText,
                    onValueChange = { mrrDollarsText = it },
                    label = { Text("Monthly Recurring Revenue (USD)") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_mrr_input")
                )

                Text(
                    text = "Lifecycle & Customer Health",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountHealth.entries.forEach { hl ->
                        FilterChip(
                            selected = health == hl,
                            onClick = { health = hl },
                            label = { Text(hl.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Executive Notes (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val seats = licensedSeatsText.toLongOrNull()
                    val active = activeSeatsText.toLongOrNull() ?: 0L
                    val mrrCents = parseDollarsToCents(mrrDollarsText)
                    when {
                        companyName.trim().length < 2 ->
                            validationError = "Enter a company name (at least 2 characters)."
                        industry.trim().length < 2 ->
                            validationError = "Enter an industry vertical."
                        contactName.trim().length < 2 ->
                            validationError = "Enter a primary contact name."
                        contactEmail.trim().length < 3 ->
                            validationError = "Enter a valid work email."
                        seats == null || seats < 1L ->
                            validationError = "Licensed seats must be at least 1."
                        mrrCents == null || mrrCents < 0L ->
                            validationError = "Enter a valid monthly MRR amount."
                        else -> {
                            onSave(
                                B2bAccount(
                                    id = initialAccount?.id ?: "",
                                    userId = initialAccount?.userId ?: "",
                                    companyName = companyName.trim(),
                                    industry = industry.trim(),
                                    contactName = contactName.trim(),
                                    contactEmail = contactEmail.trim(),
                                    planId = selectedPlanId,
                                    planName = customPlanName.trim().ifBlank { tierCode.label },
                                    tierCode = tierCode.name,
                                    billingCycle = billingCycle.name,
                                    licensedSeats = seats,
                                    activeSeats = active.coerceIn(0L, seats),
                                    mrrCents = mrrCents,
                                    status = status.name,
                                    health = health.name,
                                    notes = notes.trim()
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_account_button")
            ) {
                Text(if (isBusy) "Saving..." else "Save Account")
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
