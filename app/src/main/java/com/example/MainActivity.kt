package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.signOutUser
import com.example.data.B2bAccount
import com.example.data.SaasRepository
import com.example.ui.screens.AccountEditorDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.CommandDashboardScreen
import com.example.ui.screens.SaasModelScreen
import com.example.ui.theme.StrataSaaSTheme
import com.example.viewmodel.SaasViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_COMMAND = "command"
const val ROUTE_ACCOUNTS = "accounts"
const val ROUTE_MODEL = "saas_model"
const val ROUTE_BILLING = "billing"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navTabs = listOf(
    NavTab(ROUTE_COMMAND, R.string.nav_command, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_command"),
    NavTab(ROUTE_ACCOUNTS, R.string.nav_accounts, Icons.Filled.Apartment, Icons.Outlined.Apartment, "nav_tab_accounts"),
    NavTab(ROUTE_MODEL, R.string.nav_model, Icons.Filled.Layers, Icons.Outlined.Layers, "nav_tab_model"),
    NavTab(ROUTE_BILLING, R.string.nav_billing, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_tab_billing")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StrataSaaSTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            StrataWorkspaceShell(currentUser = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrataWorkspaceShell(currentUser: FirebaseUser) {
    val uid = currentUser.uid
    val viewModel: SaasViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SaasViewModel(SaasRepository(db), uid)
            }
        }
    )

    val plansState by viewModel.plansState.collectAsStateWithLifecycle()
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoicesState.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val economicsInput by viewModel.economicsInput.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showQuickAddAccountDialog by remember { mutableStateOf(false) }
    var showBillingInvoiceDialog by remember { mutableStateOf(false) }
    var preselectedInvoiceAccount by remember { mutableStateOf<B2bAccount?>(null) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("workspace_profile_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = currentUser.displayName
                                    ?.split(" ")
                                    ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                    ?.take(2)
                                    ?.joinToString("")
                                    ?.ifBlank { null }
                                if (initials != null) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Workspace Profile",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_COMMAND,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_COMMAND) {
                        CommandDashboardScreen(
                            metrics = metrics,
                            accountsState = accountsState,
                            plansState = plansState,
                            invoicesState = invoicesState,
                            onNavigateToAccounts = { navigateToTab(ROUTE_ACCOUNTS) },
                            onNavigateToModel = { navigateToTab(ROUTE_MODEL) },
                            onMarkInvoicePaid = { inv ->
                                viewModel.updateInvoiceStatus(inv.id, com.example.data.InvoiceStatus.PAID)
                            },
                            onQuickAddAccount = { showQuickAddAccountDialog = true }
                        )
                    }
                    composable(ROUTE_ACCOUNTS) {
                        AccountsScreen(
                            accountsState = accountsState,
                            plansState = plansState,
                            isBusy = isBusy,
                            onSaveAccount = { acc, onDone -> viewModel.saveAccount(acc, onDone) },
                            onDeleteAccount = { id, name -> viewModel.deleteAccount(id, name) },
                            onIssueInvoiceForAccount = { acc ->
                                preselectedInvoiceAccount = acc
                                showBillingInvoiceDialog = true
                                navigateToTab(ROUTE_BILLING)
                            }
                        )
                    }
                    composable(ROUTE_MODEL) {
                        SaasModelScreen(
                            plansState = plansState,
                            economicsInput = economicsInput,
                            isBusy = isBusy,
                            onEconomicsChange = { viewModel.setEconomicsInput(it) },
                            onSavePlan = { plan, onDone -> viewModel.savePlan(plan, onDone) },
                            onTogglePlanActive = { id, active -> viewModel.togglePlanActive(id, active) },
                            onDeletePlan = { id, name -> viewModel.deletePlan(id, name) }
                        )
                    }
                    composable(ROUTE_BILLING) {
                        BillingScreen(
                            invoicesState = invoicesState,
                            accountsState = accountsState,
                            metrics = metrics,
                            isBusy = isBusy,
                            preselectedAccount = preselectedInvoiceAccount,
                            showInvoiceDialog = showBillingInvoiceDialog,
                            onOpenInvoiceDialog = { acc ->
                                preselectedInvoiceAccount = acc
                                showBillingInvoiceDialog = true
                            },
                            onDismissInvoiceDialog = {
                                showBillingInvoiceDialog = false
                                preselectedInvoiceAccount = null
                            },
                            onCreateInvoice = { inv, onDone -> viewModel.createInvoice(inv, onDone) },
                            onUpdateInvoiceStatus = { id, st -> viewModel.updateInvoiceStatus(id, st) },
                            onDeleteInvoice = { id, num -> viewModel.deleteInvoice(id, num) },
                            onNavigateToAccounts = { navigateToTab(ROUTE_ACCOUNTS) }
                        )
                    }
                }
            }
        }
    }

    if (showQuickAddAccountDialog) {
        val plans = (plansState as? UiState.Success)?.data.orEmpty()
        AccountEditorDialog(
            initialAccount = null,
            availablePlans = plans,
            isBusy = isBusy,
            onDismiss = { showQuickAddAccountDialog = false },
            onSave = { account ->
                viewModel.saveAccount(account) { showQuickAddAccountDialog = false }
            }
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Executive Workspace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = currentUser.displayName ?: "Workspace Owner",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = currentUser.email ?: "Signed in with Google",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "All B2B accounts, SaaS tiers, and billing records sync securely to your isolated Cloud Firestore workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showProfileDialog = false
                        signOutUser(credentialManager, scope)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showProfileDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Close")
                }
            }
        )
    }
}
