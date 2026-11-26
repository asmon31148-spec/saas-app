package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountHealth
import com.example.data.AccountStatus
import com.example.data.B2bAccount
import com.example.data.InvoiceStatus
import com.example.data.SaasInvoice
import com.example.data.SaasPlan
import com.example.data.SaasRepository
import com.example.data.TierCode
import com.example.data.UnitEconomicsInput
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DashboardMetrics(
    val totalMrrCents: Long = 0L,
    val totalArrCents: Long = 0L,
    val activeAccountsCount: Int = 0,
    val trialAccountsCount: Int = 0,
    val atRiskOrPastDueCount: Int = 0,
    val totalLicensedSeats: Long = 0L,
    val totalActiveSeats: Long = 0L,
    val arpaCents: Long = 0L,
    val collectedInvoicesCents: Long = 0L,
    val outstandingInvoicesCents: Long = 0L,
    val overdueInvoicesCents: Long = 0L,
    val tierMrrBreakdown: Map<TierCode, Long> = emptyMap(),
    val priorityInvoices: List<SaasInvoice> = emptyList(),
    val attentionAccounts: List<B2bAccount> = emptyList()
)

class SaasViewModel(
    private val repository: SaasRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _economicsInput = MutableStateFlow(UnitEconomicsInput())
    val economicsInput: StateFlow<UnitEconomicsInput> = _economicsInput.asStateFlow()

    val plansState: StateFlow<UiState<List<SaasPlan>>> = repository.observePlans(currentUserId)
        .map<List<SaasPlan>, UiState<List<SaasPlan>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "plans")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val accountsState: StateFlow<UiState<List<B2bAccount>>> = repository.observeAccounts(currentUserId)
        .map<List<B2bAccount>, UiState<List<B2bAccount>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "accounts")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val invoicesState: StateFlow<UiState<List<SaasInvoice>>> = repository.observeInvoices(currentUserId)
        .map<List<SaasInvoice>, UiState<List<SaasInvoice>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "invoices")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        accountsState,
        invoicesState
    ) { accState, invState ->
        val accounts = (accState as? UiState.Success)?.data ?: emptyList()
        val invoices = (invState as? UiState.Success)?.data ?: emptyList()

        val billableAccounts = accounts.filter {
            val st = AccountStatus.fromRaw(it.status)
            st == AccountStatus.ACTIVE || st == AccountStatus.PAST_DUE
        }
        val totalMrr = billableAccounts.sumOf { it.mrrCents }
        val activeCount = accounts.count { AccountStatus.fromRaw(it.status) == AccountStatus.ACTIVE }
        val trialCount = accounts.count { AccountStatus.fromRaw(it.status) == AccountStatus.TRIAL }
        val attentionAccounts = accounts.filter {
            AccountStatus.fromRaw(it.status) == AccountStatus.PAST_DUE ||
                AccountHealth.fromRaw(it.health) == AccountHealth.AT_RISK
        }
        val licensedSeats = accounts.filter {
            AccountStatus.fromRaw(it.status) != AccountStatus.CHURNED
        }.sumOf { it.licensedSeats }
        val activeSeats = accounts.filter {
            AccountStatus.fromRaw(it.status) != AccountStatus.CHURNED
        }.sumOf { it.activeSeats }

        val arpa = if (billableAccounts.isNotEmpty()) totalMrr / billableAccounts.size else 0L

        val tierBreakdown = TierCode.entries.associateWith { tier ->
            billableAccounts
                .filter { TierCode.fromRaw(it.tierCode) == tier }
                .sumOf { it.mrrCents }
        }

        val collected = invoices
            .filter { InvoiceStatus.fromRaw(it.status) == InvoiceStatus.PAID }
            .sumOf { it.amountCents }
        val outstanding = invoices
            .filter { InvoiceStatus.fromRaw(it.status) == InvoiceStatus.SENT }
            .sumOf { it.amountCents }
        val overdue = invoices
            .filter { InvoiceStatus.fromRaw(it.status) == InvoiceStatus.OVERDUE }
            .sumOf { it.amountCents }

        val priorityInvoices = invoices.filter {
            val st = InvoiceStatus.fromRaw(it.status)
            st == InvoiceStatus.OVERDUE || st == InvoiceStatus.SENT
        }.take(4)

        DashboardMetrics(
            totalMrrCents = totalMrr,
            totalArrCents = totalMrr * 12L,
            activeAccountsCount = activeCount,
            trialAccountsCount = trialCount,
            atRiskOrPastDueCount = attentionAccounts.size,
            totalLicensedSeats = licensedSeats,
            totalActiveSeats = activeSeats,
            arpaCents = arpa,
            collectedInvoicesCents = collected,
            outstandingInvoicesCents = outstanding,
            overdueInvoicesCents = overdue,
            tierMrrBreakdown = tierBreakdown,
            priorityInvoices = priorityInvoices,
            attentionAccounts = attentionAccounts.take(4)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardMetrics()
    )

    private fun unwrapFirestoreError(error: Throwable, resource: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $resource", rootError)
        return rootError.localizedMessage ?: "Failed to load $resource"
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun updateEconomics(transform: (UnitEconomicsInput) -> Unit) {
        _economicsInput.update { current ->
            var next = current
            // Helper wrapper handled via copy below
            next
        }
    }

    fun setEconomicsInput(input: UnitEconomicsInput) {
        _economicsInput.value = input
    }

    fun savePlan(plan: SaasPlan, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.savePlan(currentUserId, plan)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _snackbarMessage.value = if (plan.id.isBlank()) {
                        "SaaS tier '${plan.name.trim()}' created"
                    } else {
                        "SaaS tier '${plan.name.trim()}' updated"
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not save SaaS plan"
                }
            )
        }
    }

    fun togglePlanActive(planId: String, active: Boolean) {
        viewModelScope.launch {
            val result = repository.togglePlanActive(currentUserId, planId, active)
            result.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update plan status"
            }
        }
    }

    fun deletePlan(planId: String, planName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deletePlan(currentUserId, planId)
            _isBusy.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "Removed tier '$planName'" },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not delete tier"
                }
            )
        }
    }

    fun saveAccount(account: B2bAccount, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveAccount(currentUserId, account)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _snackbarMessage.value = if (account.id.isBlank()) {
                        "Added B2B client '${account.companyName.trim()}'"
                    } else {
                        "Updated '${account.companyName.trim()}'"
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not save B2B account"
                }
            )
        }
    }

    fun updateAccountStatusAndHealth(
        accountId: String,
        status: AccountStatus,
        health: AccountHealth
    ) {
        viewModelScope.launch {
            val result = repository.updateAccountStatusAndHealth(currentUserId, accountId, status, health)
            result.fold(
                onSuccess = { _snackbarMessage.value = "Account status updated" },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to update status"
                }
            )
        }
    }

    fun deleteAccount(accountId: String, companyName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAccount(currentUserId, accountId)
            _isBusy.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "Removed account '$companyName'" },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not delete account"
                }
            )
        }
    }

    fun createInvoice(invoice: SaasInvoice, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createInvoice(currentUserId, invoice)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _snackbarMessage.value = "Invoice ${invoice.invoiceNumber.trim()} issued"
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not issue invoice"
                }
            )
        }
    }

    fun updateInvoiceStatus(invoiceId: String, status: InvoiceStatus) {
        viewModelScope.launch {
            val result = repository.updateInvoiceStatus(currentUserId, invoiceId, status)
            result.fold(
                onSuccess = { _snackbarMessage.value = "Invoice marked ${status.label}" },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to update invoice"
                }
            )
        }
    }

    fun deleteInvoice(invoiceId: String, invoiceNumber: String) {
        viewModelScope.launch {
            val result = repository.deleteInvoice(currentUserId, invoiceId)
            result.fold(
                onSuccess = { _snackbarMessage.value = "Deleted invoice $invoiceNumber" },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to delete invoice"
                }
            )
        }
    }

    private companion object {
        const val TAG = "SaasViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
