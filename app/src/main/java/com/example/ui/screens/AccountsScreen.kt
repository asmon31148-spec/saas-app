package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccountHealth
import com.example.data.AccountStatus
import com.example.data.B2bAccount
import com.example.data.BillingCycle
import com.example.data.SaasPlan
import com.example.data.TierCode
import com.example.ui.components.EmptyWorkspaceState
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.StatusPill
import com.example.ui.components.accountHealthColor
import com.example.ui.components.accountStatusColors
import com.example.ui.components.formatUsdFromCents
import com.example.ui.components.tierColors
import com.example.viewmodel.UiState

@Composable
fun AccountsScreen(
    accountsState: UiState<List<B2bAccount>>,
    plansState: UiState<List<SaasPlan>>,
    isBusy: Boolean,
    onSaveAccount: (B2bAccount, () -> Unit) -> Unit,
    onDeleteAccount: (String, String) -> Unit,
    onIssueInvoiceForAccount: (B2bAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String>("ALL") }
    var editingAccount by remember { mutableStateOf<B2bAccount?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var accountPendingDelete by remember { mutableStateOf<B2bAccount?>(null) }

    val plans = (plansState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAccount = null
                    showEditor = true
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_account_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add B2B Account")
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
                // Search & Filter Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search B2B accounts, contacts, industry...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accounts_search_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            "ALL" to "All",
                            "ACTIVE" to "Active",
                            "TRIAL" to "Trial",
                            "AT_RISK" to "At Risk",
                            "ENTERPRISE" to "Enterprise",
                            "GROWTH" to "Growth",
                            "STARTER" to "Starter"
                        )
                        filters.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                when (accountsState) {
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
                            ErrorBannerCard(message = accountsState.message)
                        }
                    }
                    is UiState.Success -> {
                        val filtered = remember(accountsState.data, searchQuery, selectedFilter) {
                            accountsState.data.filter { acc ->
                                val matchesQuery = searchQuery.isBlank() ||
                                    acc.companyName.contains(searchQuery, ignoreCase = true) ||
                                    acc.industry.contains(searchQuery, ignoreCase = true) ||
                                    acc.contactName.contains(searchQuery, ignoreCase = true)
                                val matchesFilter = when (selectedFilter) {
                                    "ALL" -> true
                                    "ACTIVE" -> AccountStatus.fromRaw(acc.status) == AccountStatus.ACTIVE
                                    "TRIAL" -> AccountStatus.fromRaw(acc.status) == AccountStatus.TRIAL
                                    "AT_RISK" -> AccountHealth.fromRaw(acc.health) == AccountHealth.AT_RISK ||
                                        AccountStatus.fromRaw(acc.status) == AccountStatus.PAST_DUE
                                    else -> acc.tierCode == selectedFilter
                                }
                                matchesQuery && matchesFilter
                            }
                        }

                        if (accountsState.data.isEmpty()) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyWorkspaceState(
                                    title = stringResource(R.string.empty_accounts_title),
                                    description = stringResource(R.string.empty_accounts_desc),
                                    primaryLabel = "Add First B2B Client",
                                    onPrimaryClick = {
                                        editingAccount = null
                                        showEditor = true
                                    },
                                    primaryTestTag = "accounts_empty_add_button"
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
                                    text = "No B2B accounts match your current search or filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { it.id }) { account ->
                                    B2bAccountCard(
                                        account = account,
                                        onEdit = {
                                            editingAccount = account
                                            showEditor = true
                                        },
                                        onIssueInvoice = { onIssueInvoiceForAccount(account) },
                                        onDelete = { accountPendingDelete = account }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AccountEditorDialog(
            initialAccount = editingAccount,
            availablePlans = plans,
            isBusy = isBusy,
            onDismiss = { showEditor = false },
            onSave = { account ->
                onSaveAccount(account) { showEditor = false }
            }
        )
    }

    accountPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { accountPendingDelete = null },
            title = { Text("Remove B2B Account") },
            text = {
                Text("Delete ${target.companyName} from your active SaaS portfolio? Existing invoices remain in your billing ledger.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount(target.id, target.companyName)
                        accountPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun B2bAccountCard(
    account: B2bAccount,
    onEdit: () -> Unit,
    onIssueInvoice: () -> Unit,
    onDelete: () -> Unit
) {
    val tier = TierCode.fromRaw(account.tierCode)
    val status = AccountStatus.fromRaw(account.status)
    val health = AccountHealth.fromRaw(account.health)
    val cycle = BillingCycle.fromRaw(account.billingCycle)
    val (tierBg, tierFg) = tierColors(tier)
    val (stBg, stFg) = accountStatusColors(status)
    val utilization = if (account.licensedSeats > 0L) {
        (account.activeSeats.toFloat() / account.licensedSeats.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.companyName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = account.companyName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${account.industry} • ${account.contactName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatUsdFromCents(account.mrrCents),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${cycle.label} / MRR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(text = account.planName, containerColor = tierBg, contentColor = tierFg)
                StatusPill(text = status.label, containerColor = stBg, contentColor = stFg)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accountHealthColor(health))
                    )
                    Text(
                        text = health.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Seat Utilization Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Seat Utilization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${account.activeSeats} / ${account.licensedSeats} seats (${(utilization * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { utilization },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onIssueInvoice,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("issue_invoice_btn_${account.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Issue Invoice")
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit account")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete account",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
