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
import com.example.data.B2bAccount
import com.example.data.BillingCycle
import com.example.data.InvoiceStatus
import com.example.data.SaasInvoice
import com.example.ui.components.parseDollarsToCents

@Composable
fun InvoiceEditorDialog(
    accounts: List<B2bAccount>,
    preselectedAccount: B2bAccount?,
    existingInvoiceCount: Int,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (SaasInvoice) -> Unit
) {
    val initialAccount = preselectedAccount ?: accounts.firstOrNull()
    var selectedAccount by remember(initialAccount) { mutableStateOf(initialAccount) }
    var invoiceNumber by remember {
        mutableStateOf("INV-2026-${1001 + existingInvoiceCount}")
    }
    var billingPeriod by remember { mutableStateOf("Nov 2026") }
    var dueDateLabel by remember { mutableStateOf("2026-12-15") }
    var amountDollarsText by remember(selectedAccount) {
        val defaultCents = selectedAccount?.let { acc ->
            if (BillingCycle.fromRaw(acc.billingCycle) == BillingCycle.ANNUAL) acc.mrrCents * 12L else acc.mrrCents
        } ?: 49900L
        mutableStateOf((defaultCents / 100.0).toInt().toString())
    }
    var status by remember { mutableStateOf(InvoiceStatus.SENT) }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = "Issue B2B Invoice",
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

                if (accounts.isEmpty()) {
                    Text(
                        text = "Add a B2B client account first before issuing an invoice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Client Account *",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccount?.id == acc.id,
                                onClick = {
                                    selectedAccount = acc
                                    val cents = if (BillingCycle.fromRaw(acc.billingCycle) == BillingCycle.ANNUAL) {
                                        acc.mrrCents * 12L
                                    } else {
                                        acc.mrrCents
                                    }
                                    amountDollarsText = (cents / 100.0).toInt().toString()
                                },
                                label = { Text(acc.companyName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it; validationError = null },
                        label = { Text("Invoice Number *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_number_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = billingPeriod,
                            onValueChange = { billingPeriod = it },
                            label = { Text("Billing Period *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dueDateLabel,
                            onValueChange = { dueDateLabel = it },
                            label = { Text("Due Date *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = amountDollarsText,
                        onValueChange = { amountDollarsText = it },
                        label = { Text("Invoice Total (USD) *") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_amount_input")
                    )

                    Text(
                        text = "Initial Status",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(InvoiceStatus.DRAFT, InvoiceStatus.SENT, InvoiceStatus.PAID).forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(300) },
                        label = { Text("Line-item Memo (Optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy && selectedAccount != null,
                onClick = {
                    val account = selectedAccount
                    val amountCents = parseDollarsToCents(amountDollarsText)
                    when {
                        account == null -> validationError = "Select a B2B client account."
                        invoiceNumber.trim().length < 3 -> validationError = "Invoice number must be at least 3 chars."
                        billingPeriod.trim().length < 2 -> validationError = "Enter a billing period."
                        dueDateLabel.trim().length < 4 -> validationError = "Enter a valid due date."
                        amountCents == null || amountCents < 0L -> validationError = "Enter a valid invoice amount."
                        else -> {
                            onCreate(
                                SaasInvoice(
                                    accountId = account.id,
                                    companyName = account.companyName,
                                    invoiceNumber = invoiceNumber.trim(),
                                    billingPeriod = billingPeriod.trim(),
                                    amountCents = amountCents,
                                    status = status.name,
                                    dueDateLabel = dueDateLabel.trim(),
                                    notes = notes.trim()
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_invoice_button")
            ) {
                Text(if (isBusy) "Issuing..." else "Issue Invoice")
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
