package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SaasPlan
import com.example.data.SlaSupport
import com.example.data.TierCode
import com.example.data.UnitEconomicsInput
import com.example.ui.components.EmptyWorkspaceState
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCompactUsdDouble
import com.example.ui.components.formatUsdDouble
import com.example.ui.components.formatUsdFromCents
import com.example.ui.components.tierColors
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldMint
import com.example.viewmodel.UiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SaasModelScreen(
    plansState: UiState<List<SaasPlan>>,
    economicsInput: UnitEconomicsInput,
    isBusy: Boolean,
    onEconomicsChange: (UnitEconomicsInput) -> Unit,
    onSavePlan: (SaasPlan, () -> Unit) -> Unit,
    onTogglePlanActive: (String, Boolean) -> Unit,
    onDeletePlan: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Pricing Tiers, 1 = Unit Economics
    var editingPlan by remember { mutableStateOf<SaasPlan?>(null) }
    var showPlanEditor by remember { mutableStateOf(false) }
    var planPendingDelete by remember { mutableStateOf<SaasPlan?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("tab_pricing_tiers")
                    ) {
                        Text("Pricing Tiers")
                    }
                    SegmentedButton(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("tab_unit_economics")
                    ) {
                        Text("Unit Economics")
                    }
                }
            }

            if (selectedSection == 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "B2B Subscription Tiers",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Define hybrid base + per-seat SaaS pricing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingPlan = null
                                showPlanEditor = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("create_plan_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("New Tier")
                        }
                    }
                }

                when (plansState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item { ErrorBannerCard(message = plansState.message) }
                    }
                    is UiState.Success -> {
                        if (plansState.data.isEmpty()) {
                            item {
                                EmptyWorkspaceState(
                                    title = stringResource(R.string.empty_plans_title),
                                    description = stringResource(R.string.empty_plans_desc),
                                    primaryLabel = "Create First SaaS Tier",
                                    onPrimaryClick = {
                                        editingPlan = null
                                        showPlanEditor = true
                                    },
                                    primaryTestTag = "plans_empty_create_button"
                                )
                            }
                        } else {
                            items(plansState.data, key = { it.id }) { plan ->
                                SaasPlanCard(
                                    plan = plan,
                                    onToggleActive = { active -> onTogglePlanActive(plan.id, active) },
                                    onEdit = {
                                        editingPlan = plan
                                        showPlanEditor = true
                                    },
                                    onDelete = { planPendingDelete = plan }
                                )
                            }
                        }
                    }
                }
            } else {
                // Unit Economics & ARR Simulator
                item {
                    UnitEconomicsSimulatorSection(
                        input = economicsInput,
                        onInputChange = onEconomicsChange
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showPlanEditor) {
        PlanEditorDialog(
            initialPlan = editingPlan,
            isBusy = isBusy,
            onDismiss = { showPlanEditor = false },
            onSave = { plan ->
                onSavePlan(plan) { showPlanEditor = false }
            }
        )
    }

    planPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { planPendingDelete = null },
            title = { Text("Delete SaaS Tier") },
            text = { Text("Remove '${target.name}' from your pricing catalog?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlan(target.id, target.name)
                        planPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { planPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SaasPlanCard(
    plan: SaasPlan,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tier = TierCode.fromRaw(plan.tierCode)
    val sla = SlaSupport.fromRaw(plan.slaSupport)
    val (tierBg, tierFg) = tierColors(tier)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_card_${plan.id}")
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
                verticalAlignment = Alignment.CenterVertically
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
                            text = plan.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusPill(text = tier.label, containerColor = tierBg, contentColor = tierFg)
                    }
                    Text(
                        text = "${sla.label} • ${plan.apiCallsLimitK}k API req/mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = plan.active,
                    onCheckedChange = onToggleActive
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBox(
                    label = "Base / mo",
                    value = formatUsdFromCents(plan.baseMonthlyCents),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "+ Seat / mo",
                    value = formatUsdFromCents(plan.perSeatMonthlyCents),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Incl. Seats",
                    value = "${plan.includedSeats}",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Annual Disc",
                    value = "${plan.annualDiscountPct}%",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit tier")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete tier",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UnitEconomicsSimulatorSection(
    input: UnitEconomicsInput,
    onInputChange: (UnitEconomicsInput) -> Unit
) {
    val ltvCac = input.ltvToCacRatio
    val healthBadge = when {
        ltvCac >= 5.0 -> "Elite (>=5x)" to (MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer)
        ltvCac >= 3.0 -> "Healthy (3x+)" to (MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer)
        else -> "Optimize (<3x)" to (AmberWarningBg to AmberWarning)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Output Summary Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Simulated B2B ARR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCompactUsdDouble(input.projectedArrUsd),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    StatusPill(
                        text = "LTV:CAC ${String.format(Locale.US, "%.1fx", ltvCac)} • ${healthBadge.first}",
                        containerColor = healthBadge.second.first,
                        contentColor = healthBadge.second.second
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBox(
                        label = "ARPA / mo",
                        value = formatUsdDouble(input.arpaMonthlyUsd),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Customer LTV",
                        value = formatCompactUsdDouble(input.ltvUsd),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Payback",
                        value = "${String.format(Locale.US, "%.1f", input.cacPaybackMonths)} mo",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "NRR",
                        value = "${input.netRevenueRetentionPct.roundToInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                }

                // 12-Month Compounding Cohort Projection Bar Chart
                Text(
                    text = "12-Month Cohort ARR Trajectory (NRR ${input.netRevenueRetentionPct.roundToInt()}%)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val barColor = CobaltPrimary
                val highlightColor = EmeraldMint
                val nrrFactor = (input.netRevenueRetentionPct / 100.0).coerceIn(0.4, 2.0)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val months = 12
                    val gap = 6.dp.toPx()
                    val barWidth = (size.width - gap * (months - 1)) / months
                    val values = (1..months).map { m ->
                        val t = (m - 1) / 11.0
                        1.0 + (nrrFactor - 1.0) * t
                    }
                    val maxVal = (values.maxOrNull() ?: 1.2).coerceAtLeast(1.1)
                    values.forEachIndexed { idx, v ->
                        val ratio = (v / maxVal).toFloat().coerceIn(0.08f, 1f)
                        val h = size.height * ratio
                        val x = idx * (barWidth + gap)
                        drawRoundRect(
                            color = if (idx == months - 1) highlightColor else barColor.copy(alpha = 0.45f + 0.5f * (idx / 11f)),
                            topLeft = Offset(x, size.height - h),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        // Interactive Sliders
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Model Assumptions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                EconomicsSliderRow(
                    label = "Target B2B Accounts",
                    valueText = "${input.targetAccounts}",
                    value = input.targetAccounts.toFloat(),
                    range = 5f..300f,
                    onValueChange = { onInputChange(input.copy(targetAccounts = it.roundToInt())) }
                )
                EconomicsSliderRow(
                    label = "Avg Seats / Account",
                    valueText = "${input.avgSeatsPerAccount} seats",
                    value = input.avgSeatsPerAccount.toFloat(),
                    range = 2f..120f,
                    onValueChange = { onInputChange(input.copy(avgSeatsPerAccount = it.roundToInt())) }
                )
                EconomicsSliderRow(
                    label = "Base Monthly Fee",
                    valueText = "$${input.basePriceMonthlyUsd}",
                    value = input.basePriceMonthlyUsd.toFloat(),
                    range = 49f..2500f,
                    onValueChange = { onInputChange(input.copy(basePriceMonthlyUsd = it.roundToInt())) }
                )
                EconomicsSliderRow(
                    label = "Monthly Logo Churn",
                    valueText = "${String.format(Locale.US, "%.1f", input.monthlyChurnPct)}%",
                    value = input.monthlyChurnPct,
                    range = 0.5f..8.0f,
                    onValueChange = { onInputChange(input.copy(monthlyChurnPct = it)) }
                )
                EconomicsSliderRow(
                    label = "Annual Seat Expansion",
                    valueText = "${input.annualExpansionPct.roundToInt()}%",
                    value = input.annualExpansionPct,
                    range = 0f..50f,
                    onValueChange = { onInputChange(input.copy(annualExpansionPct = it)) }
                )
                EconomicsSliderRow(
                    label = "Enterprise CAC",
                    valueText = "$${input.cacUsd}",
                    value = input.cacUsd.toFloat(),
                    range = 500f..15000f,
                    onValueChange = { onInputChange(input.copy(cacUsd = (it / 100f).roundToInt() * 100)) }
                )
            }
        }
    }
}

@Composable
private fun EconomicsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
