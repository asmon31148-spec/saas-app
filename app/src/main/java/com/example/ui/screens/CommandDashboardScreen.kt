package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccountHealth
import com.example.data.AccountStatus
import com.example.data.B2bAccount
import com.example.data.InvoiceStatus
import com.example.data.SaasInvoice
import com.example.data.SaasPlan
import com.example.ui.components.EmptyWorkspaceState
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TierMixRevenueChart
import com.example.ui.components.accountHealthColor
import com.example.ui.components.formatCompactUsdFromCents
import com.example.ui.components.formatUsdFromCents
import com.example.ui.components.invoiceStatusColors
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.IndigoTertiary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.viewmodel.DashboardMetrics
import com.example.viewmodel.UiState

@Composable
fun CommandDashboardScreen(
    metrics: DashboardMetrics,
    accountsState: UiState<List<B2bAccount>>,
    plansState: UiState<List<SaasPlan>>,
    invoicesState: UiState<List<SaasInvoice>>,
    onNavigateToAccounts: () -> Unit,
    onNavigateToModel: () -> Unit,
    onMarkInvoicePaid: (SaasInvoice) -> Unit,
    onQuickAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = accountsState is UiState.Loading || plansState is UiState.Loading
    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val plans = (plansState as? UiState.Success)?.data.orEmpty()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero ARR / MRR Banner
            item {
                ExecutiveRevenueHeroCard(
                    metrics = metrics,
                    planCount = plans.count { it.active },
                    onAddAccount = onQuickAddAccount,
                    onConfigureModel = onNavigateToModel
                )
            }

            // Error States
            if (accountsState is UiState.Error) {
                item { ErrorBannerCard(message = accountsState.message) }
            }
            if (invoicesState is UiState.Error) {
                item { ErrorBannerCard(message = invoicesState.message) }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (accounts.isEmpty()) {
                item {
                    EmptyWorkspaceState(
                        title = stringResource(R.string.empty_accounts_title),
                        description = stringResource(R.string.empty_accounts_desc),
                        primaryLabel = "Add B2B Account",
                        onPrimaryClick = onQuickAddAccount,
                        secondaryLabel = "Define Tiers",
                        onSecondaryClick = onNavigateToModel,
                        primaryTestTag = "dashboard_empty_add_account_button"
                    )
                }
            } else {
                // 2. KPI 2x2 Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiMetricCard(
                                label = "Active Accounts",
                                value = "${metrics.activeAccountsCount}",
                                subtitle = "${metrics.trialAccountsCount} in trial",
                                icon = Icons.Default.Apartment,
                                accentColor = CobaltPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Seat Utilization",
                                value = if (metrics.totalLicensedSeats > 0) {
                                    "${((metrics.totalActiveSeats * 100) / metrics.totalLicensedSeats)}%"
                                } else "0%",
                                subtitle = "${metrics.totalActiveSeats}/${metrics.totalLicensedSeats} seats",
                                icon = Icons.Default.Group,
                                accentColor = EmeraldMint,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiMetricCard(
                                label = "ARPA (Monthly)",
                                value = formatCompactUsdFromCents(metrics.arpaCents),
                                subtitle = "Avg rev / account",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                accentColor = IndigoTertiary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Open Receivables",
                                value = formatCompactUsdFromCents(
                                    metrics.outstandingInvoicesCents + metrics.overdueInvoicesCents
                                ),
                                subtitle = "${formatCompactUsdFromCents(metrics.overdueInvoicesCents)} overdue",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                accentColor = if (metrics.overdueInvoicesCents > 0L) AmberWarning else EmeraldMint,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Canvas Tier Mix Breakdown
                item {
                    TierMixRevenueChart(
                        tierBreakdown = metrics.tierMrrBreakdown,
                        totalMrrCents = metrics.totalMrrCents
                    )
                }

                // 4. Priority Receivables
                if (metrics.priorityInvoices.isNotEmpty()) {
                    item {
                        Text(
                            text = "Open & Overdue Invoices",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(metrics.priorityInvoices, key = { "dash_inv_${it.id}" }) { invoice ->
                        DashboardInvoiceRow(
                            invoice = invoice,
                            onMarkPaid = { onMarkInvoicePaid(invoice) }
                        )
                    }
                }

                // 5. At-Risk / Past-Due Accounts
                if (metrics.attentionAccounts.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Retention Watchlist",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            FilledTonalButton(
                                onClick = onNavigateToAccounts,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("View All")
                            }
                        }
                    }
                    items(metrics.attentionAccounts, key = { "dash_acc_${it.id}" }) { account ->
                        AttentionAccountCard(account = account, onClick = onNavigateToAccounts)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ExecutiveRevenueHeroCard(
    metrics: DashboardMetrics,
    planCount: Int,
    onAddAccount: () -> Unit,
    onConfigureModel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_saas_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.88f),
                                ObsidianSurface.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldMint.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "LIVE SAAS RUN RATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldMint,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Text(
                        text = "$planCount active tiers",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Monthly Recurring (MRR)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = formatUsdFromCents(metrics.totalMrrCents),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Annualized (ARR)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = formatCompactUsdFromCents(metrics.totalArrCents),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldMint
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAddAccount,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("hero_add_account_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("New Account", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onConfigureModel,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("hero_saas_model_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("SaaS Model", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardInvoiceRow(
    invoice: SaasInvoice,
    onMarkPaid: () -> Unit
) {
    val status = InvoiceStatus.fromRaw(invoice.status)
    val (bg, fg) = invoiceStatusColors(status)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = invoice.companyName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
                }
                Text(
                    text = "${invoice.invoiceNumber} • Due ${invoice.dueDateLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatUsdFromCents(invoice.amountCents),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = onMarkPaid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dash_mark_paid_${invoice.id}")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Mark Paid", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AttentionAccountCard(
    account: B2bAccount,
    onClick: () -> Unit
) {
    val health = AccountHealth.fromRaw(account.health)
    val status = AccountStatus.fromRaw(account.status)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmberWarning.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = accountHealthColor(health),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = account.companyName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${status.label} • ${health.label} • ${account.activeSeats}/${account.licensedSeats} seats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatUsdFromCents(account.mrrCents),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
