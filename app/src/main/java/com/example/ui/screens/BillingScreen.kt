package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.B2bAccount
import com.example.data.InvoiceStatus
import com.example.data.SaasInvoice
import com.example.ui.components.EmptyWorkspaceState
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCompactUsdFromCents
import com.example.ui.components.formatUsdFromCents
import com.example.ui.components.invoiceStatusColors
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.DashboardMetrics
import com.example.viewmodel.UiState

@Composable
fun BillingScreen(
    invoicesState: UiState<List<SaasInvoice>>,
    accountsState: UiState<List<B2bAccount>>,
    metrics: DashboardMetrics,
    isBusy: Boolean,
    preselectedAccount: B2bAccount?,
    showInvoiceDialog: Boolean,
    onOpenInvoiceDialog: (B2bAccount?) -> Unit,
    onDismissInvoiceDialog: () -> Unit,
    onCreateInvoice: (SaasInvoice, () -> Unit) -> Unit,
    onUpdateInvoiceStatus: (String, InvoiceStatus) -> Unit,
    onDeleteInvoice: (String, String) -> Unit,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStatusFilter by remember { mutableStateOf<InvoiceStatus?>(null) }
    var invoicePendingDelete by remember { mutableStateOf<SaasInvoice?>(null) }
    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val allInvoices = (invoicesState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (accounts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onOpenInvoiceDialog(null) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_invoice_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Issue Invoice")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                // Collection Summary Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BillingSummaryChip(
                        label = "Collected",
                        value = formatCompactUsdFromCents(metrics.collectedInvoicesCents),
                        accent = EmeraldMint,
                        modifier = Modifier.weight(1f)
                    )
                    BillingSummaryChip(
                        label = "Pending",
                        value = formatCompactUsdFromCents(metrics.outstandingInvoicesCents),
                        accent = CobaltPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    BillingSummaryChip(
                        label = "Overdue",
                        value = formatCompactUsdFromCents(metrics.overdueInvoicesCents),
                        accent = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { selectedStatusFilter = null },
                        label = { Text("All (${allInvoices.size})") }
                    )
                    InvoiceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatusFilter == st,
                            onClick = { selectedStatusFilter = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                when (invoicesState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.padding(16.dp)) {
                            ErrorBannerCard(message = invoicesState.message)
                        }
                    }
                    is UiState.Success -> {
                        val filtered = remember(invoicesState.data, selectedStatusFilter) {
                            if (selectedStatusFilter == null) invoicesState.data
                            else invoicesState.data.filter { InvoiceStatus.fromRaw(it.status) == selectedStatusFilter }
                        }

                        if (invoicesState.data.isEmpty()) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyWorkspaceState(
                                    title = stringResource(R.string.empty_invoices_title),
                                    description = stringResource(R.string.empty_invoices_desc),
                                    primaryLabel = if (accounts.isNotEmpty()) "Issue First Invoice" else "Add B2B Account First",
                                    onPrimaryClick = {
                                        if (accounts.isNotEmpty()) onOpenInvoiceDialog(null)
                                        else onNavigateToAccounts()
                                    },
                                    primaryTestTag = "billing_empty_cta_button"
                                )
                            }
                        } else if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No invoices in this status.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { it.id }) { invoice ->
                                    InvoiceCard(
                                        invoice = invoice,
                                        onStatusChange = { next -> onUpdateInvoiceStatus(invoice.id, next) },
                                        onDelete = { invoicePendingDelete = invoice }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInvoiceDialog) {
        InvoiceEditorDialog(
            accounts = accounts,
            preselectedAccount = preselectedAccount,
            existingInvoiceCount = allInvoices.size,
            isBusy = isBusy,
            onDismiss = onDismissInvoiceDialog,
            onCreate = { invoice ->
                onCreateInvoice(invoice, onDismissInvoiceDialog)
            }
        )
    }

    invoicePendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { invoicePendingDelete = null },
            title = { Text("Delete Invoice") },
            text = { Text("Remove ${target.invoiceNumber} for ${target.companyName} from your ledger?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteInvoice(target.id, target.invoiceNumber)
                        invoicePendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { invoicePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BillingSummaryChip(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: SaasInvoice,
    onStatusChange: (InvoiceStatus) -> Unit,
    onDelete: () -> Unit
) {
    val status = InvoiceStatus.fromRaw(invoice.status)
    val (bg, fg) = invoiceStatusColors(status)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invoice_card_${invoice.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = invoice.invoiceNumber,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatusPill(text = status.label, containerColor = bg, contentColor = fg)
                    }
                    Text(
                        text = invoice.companyName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${invoice.billingPeriod} • Due ${invoice.dueDateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatUsdFromCents(invoice.amountCents),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (invoice.notes.isNotBlank()) {
                Text(
                    text = invoice.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status.isTerminal) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Settled ledger record (locked)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    if (status == InvoiceStatus.DRAFT) {
                        OutlinedButton(
                            onClick = { onStatusChange(InvoiceStatus.SENT) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Send")
                        }
                    }
                    if (status == InvoiceStatus.SENT) {
                        OutlinedButton(
                            onClick = { onStatusChange(InvoiceStatus.OVERDUE) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Overdue")
                        }
                    }
                    FilledTonalButton(
                        onClick = { onStatusChange(InvoiceStatus.PAID) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mark_paid_btn_${invoice.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Mark Paid")
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete invoice",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
