package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SaasRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun checkMatchingUser(expectedUserId: String): String {
        val uid = requireUserId()
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    private fun <T> observeUserCollection(
        userId: String,
        collectionName: String,
        mapper: (List<T>) -> List<T> = { it },
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = checkMatchingUser(userId)
        val path = "users/$uid/$collectionName"
        emitAll(
            db.collection("users").document(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    mapper(
                        snapshot.toObjects(
                            clazz,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlans(userId: String): Flow<List<SaasPlan>> =
        observeUserCollection(
            userId = userId,
            collectionName = "plans",
            clazz = SaasPlan::class.java,
            mapper = { list -> list.sortedBy { it.baseMonthlyCents } }
        )

    fun observeAccounts(userId: String): Flow<List<B2bAccount>> =
        observeUserCollection(
            userId = userId,
            collectionName = "accounts",
            clazz = B2bAccount::class.java,
            mapper = { list -> list.sortedByDescending { it.mrrCents } }
        )

    fun observeInvoices(userId: String): Flow<List<SaasInvoice>> =
        observeUserCollection(
            userId = userId,
            collectionName = "invoices",
            clazz = SaasInvoice::class.java,
            mapper = { list ->
                list.sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
        )

    // --- PLANS ---

    suspend fun savePlan(userId: String, plan: SaasPlan): Result<String> {
        var docPath = "users/$userId/plans"
        return try {
            val uid = checkMatchingUser(userId)
            val cleanName = plan.name.trim()
            require(cleanName.length in 2..60) { "Plan name must be 2–60 characters." }
            require(plan.baseMonthlyCents in 0L..100_000_000L) { "Base monthly price out of bounds." }
            require(plan.perSeatMonthlyCents in 0L..10_000_000L) { "Per-seat price out of bounds." }
            require(plan.includedSeats in 1L..10_000L) { "Included seats must be 1–10,000." }
            require(plan.annualDiscountPct in 0L..60L) { "Annual discount must be 0–60%." }
            require(plan.apiCallsLimitK in 1L..1_000_000L) { "API quota must be at least 1k calls." }

            val plansCol = db.collection("users").document(uid).collection("plans")
            if (plan.id.isBlank()) {
                val docRef = plansCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "tierCode" to TierCode.fromRaw(plan.tierCode).name,
                    "baseMonthlyCents" to plan.baseMonthlyCents,
                    "perSeatMonthlyCents" to plan.perSeatMonthlyCents,
                    "includedSeats" to plan.includedSeats,
                    "annualDiscountPct" to plan.annualDiscountPct,
                    "apiCallsLimitK" to plan.apiCallsLimitK,
                    "slaSupport" to SlaSupport.fromRaw(plan.slaSupport).name,
                    "active" to plan.active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = plansCol.document(plan.id)
                docPath = docRef.path
                val updates = mapOf(
                    "name" to cleanName,
                    "tierCode" to TierCode.fromRaw(plan.tierCode).name,
                    "baseMonthlyCents" to plan.baseMonthlyCents,
                    "perSeatMonthlyCents" to plan.perSeatMonthlyCents,
                    "includedSeats" to plan.includedSeats,
                    "annualDiscountPct" to plan.annualDiscountPct,
                    "apiCallsLimitK" to plan.apiCallsLimitK,
                    "slaSupport" to SlaSupport.fromRaw(plan.slaSupport).name,
                    "active" to plan.active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                Result.success(plan.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (plan.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePlanActive(userId: String, planId: String, active: Boolean): Result<Unit> {
        var docPath = "users/$userId/plans/$planId"
        return try {
            val uid = checkMatchingUser(userId)
            require(planId.isNotBlank()) { "Plan ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("plans").document(planId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePlan(userId: String, planId: String): Result<Unit> {
        var docPath = "users/$userId/plans/$planId"
        return try {
            val uid = checkMatchingUser(userId)
            require(planId.isNotBlank()) { "Plan ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("plans").document(planId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- B2B ACCOUNTS ---

    suspend fun saveAccount(userId: String, account: B2bAccount): Result<String> {
        var docPath = "users/$userId/accounts"
        return try {
            val uid = checkMatchingUser(userId)
            val company = account.companyName.trim()
            val industry = account.industry.trim()
            val contactName = account.contactName.trim()
            val contactEmail = account.contactEmail.trim()
            val planName = account.planName.trim()
            val notes = account.notes.trim().take(500)

            require(company.length in 2..100) { "Company name must be 2–100 characters." }
            require(industry.length in 2..60) { "Industry must be 2–60 characters." }
            require(contactName.length in 2..80) { "Contact name must be 2–80 characters." }
            require(contactEmail.length in 3..120) { "Contact email must be 3–120 characters." }
            require(planName.length in 1..60) { "Plan name must be 1–60 characters." }
            require(account.licensedSeats in 1L..100_000L) { "Licensed seats must be 1–100,000." }
            require(account.activeSeats in 0L..100_000L) { "Active seats must be 0–100,000." }
            require(account.mrrCents in 0L..500_000_000L) { "MRR out of valid bounds." }

            val accountsCol = db.collection("users").document(uid).collection("accounts")
            if (account.id.isBlank()) {
                val docRef = accountsCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "companyName" to company,
                    "industry" to industry,
                    "contactName" to contactName,
                    "contactEmail" to contactEmail,
                    "planId" to account.planId.trim().takeIf { it.isNotEmpty() },
                    "planName" to planName,
                    "tierCode" to TierCode.fromRaw(account.tierCode).name,
                    "billingCycle" to BillingCycle.fromRaw(account.billingCycle).name,
                    "licensedSeats" to account.licensedSeats,
                    "activeSeats" to account.activeSeats.coerceAtMost(account.licensedSeats),
                    "mrrCents" to account.mrrCents,
                    "status" to AccountStatus.fromRaw(account.status).name,
                    "health" to AccountHealth.fromRaw(account.health).name,
                    "notes" to notes.takeIf { it.isNotEmpty() },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = accountsCol.document(account.id)
                docPath = docRef.path
                val updates = buildMap<String, Any> {
                    put("companyName", company)
                    put("industry", industry)
                    put("contactName", contactName)
                    put("contactEmail", contactEmail)
                    if (account.planId.trim().isNotEmpty()) put("planId", account.planId.trim())
                    put("planName", planName)
                    put("tierCode", TierCode.fromRaw(account.tierCode).name)
                    put("billingCycle", BillingCycle.fromRaw(account.billingCycle).name)
                    put("licensedSeats", account.licensedSeats)
                    put("activeSeats", account.activeSeats.coerceAtMost(account.licensedSeats))
                    put("mrrCents", account.mrrCents)
                    put("status", AccountStatus.fromRaw(account.status).name)
                    put("health", AccountHealth.fromRaw(account.health).name)
                    if (notes.isNotEmpty()) put("notes", notes)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
                Result.success(account.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (account.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAccountStatusAndHealth(
        userId: String,
        accountId: String,
        status: AccountStatus,
        health: AccountHealth
    ): Result<Unit> {
        var docPath = "users/$userId/accounts/$accountId"
        return try {
            val uid = checkMatchingUser(userId)
            require(accountId.isNotBlank()) { "Account ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("accounts").document(accountId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "health" to health.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(userId: String, accountId: String): Result<Unit> {
        var docPath = "users/$userId/accounts/$accountId"
        return try {
            val uid = checkMatchingUser(userId)
            require(accountId.isNotBlank()) { "Account ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("accounts").document(accountId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- INVOICES ---

    suspend fun createInvoice(userId: String, invoice: SaasInvoice): Result<String> {
        var docPath = "users/$userId/invoices"
        return try {
            val uid = checkMatchingUser(userId)
            val accountId = invoice.accountId.trim()
            val company = invoice.companyName.trim()
            val invNum = invoice.invoiceNumber.trim()
            val period = invoice.billingPeriod.trim()
            val dueDate = invoice.dueDateLabel.trim()
            val notes = invoice.notes.trim().take(300)

            require(accountId.isNotBlank()) { "Please select a B2B client account." }
            require(company.length in 2..100) { "Company name must be 2–100 characters." }
            require(invNum.length in 3..40) { "Invoice number must be 3–40 characters." }
            require(period.length in 2..50) { "Billing period must be 2–50 characters." }
            require(dueDate.length in 4..30) { "Due date must be 4–30 characters." }
            require(invoice.amountCents in 0L..500_000_000L) { "Invoice amount out of bounds." }

            val docRef = db.collection("users").document(uid).collection("invoices").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "accountId" to accountId,
                "companyName" to company,
                "invoiceNumber" to invNum,
                "billingPeriod" to period,
                "amountCents" to invoice.amountCents,
                "status" to InvoiceStatus.fromRaw(invoice.status).name,
                "dueDateLabel" to dueDate,
                "notes" to notes.takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInvoiceStatus(
        userId: String,
        invoiceId: String,
        newStatus: InvoiceStatus
    ): Result<Unit> {
        var docPath = "users/$userId/invoices/$invoiceId"
        return try {
            val uid = checkMatchingUser(userId)
            require(invoiceId.isNotBlank()) { "Invoice ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("invoices").document(invoiceId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        var docPath = "users/$userId/invoices/$invoiceId"
        return try {
            val uid = checkMatchingUser(userId)
            require(invoiceId.isNotBlank()) { "Invoice ID cannot be blank." }
            val docRef = db.collection("users").document(uid).collection("invoices").document(invoiceId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
