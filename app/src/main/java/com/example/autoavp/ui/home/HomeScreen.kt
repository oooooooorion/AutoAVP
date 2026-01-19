package com.example.autoavp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.ui.navigation.Screen
import com.example.autoavp.ui.print.AvpPdfGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items by viewModel.mailItems.collectAsState()
    val offices by viewModel.offices.collectAsState()
    val selectedOffice by viewModel.selectedOffice.collectAsState()
    val latestSession by viewModel.latestSession.collectAsState()
    val context = LocalContext.current

    var showFabMenu by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var officeExpanded by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<MailItemEntity?>(null) }
    var showNewSessionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoAVP") },
                navigationIcon = {
                    IconButton(onClick = { 
                        navController.navigate(Screen.History.route)
                    }) {
                        Icon(Icons.Default.History, contentDescription = "Historique")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Offices.route)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                    IconButton(
                        onClick = {
                            val officeId = selectedOffice?.officeId
                            val sessionId = latestSession?.sessionId
                            if (officeId != null && sessionId != null) {
                                navController.navigate(Screen.PrintPreview.createRoute(sessionId, officeId))
                            }
                        },
                        enabled = selectedOffice != null && items.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Aperçu avant impression")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 16.dp)) {
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                showManualAddDialog = true
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Ajout manuel")
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                navController.navigate(Screen.Scan.createRoute(Screen.Scan.MODE_BULK))
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Scan multiple")
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                navController.navigate(Screen.Scan.createRoute(Screen.Scan.MODE_SINGLE))
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Scan unique")
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Ajouter"
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { /* Search logic */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Rechercher")
                    }
                    Spacer(Modifier.weight(1f))
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { showNewSessionDialog = true }) {
                            Text("Nouvelle Session", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Office Selector (Same as SessionDetails)
            if (offices.isEmpty()) {
                Text(
                    "Aucun bureau configuré.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = officeExpanded,
                    onExpandedChange = { officeExpanded = !officeExpanded },
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedOffice?.name ?: "Choisir un bureau d'instance",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        label = { Text("Bureau d'Instance") }
                    )
                    ExposedDropdownMenu(
                        expanded = officeExpanded,
                        onDismissRequest = { officeExpanded = false }
                    ) {
                        offices.forEach { office ->
                            DropdownMenuItem(
                                text = { Text(office.name) },
                                onClick = {
                                    viewModel.selectOffice(office)
                                    officeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun courrier dans la session actuelle", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.mailId }) { item ->
                        MailItemRow(
                            item = item,
                            onEdit = { itemToEdit = item },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }

    if (itemToEdit != null) {
        EditMailItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedItem ->
                viewModel.updateItem(updatedItem)
                itemToEdit = null
            }
        )
    }

    if (showManualAddDialog) {
        ManualAddDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { tracking, address ->
                viewModel.addManualItem(tracking, address)
                showManualAddDialog = false
            }
        )
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("Nouvelle Session") },
            text = { Text("Voulez-vous vraiment commencer une nouvelle session ? La liste actuelle sera archivée dans l'historique.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.createNewSession()
                    showNewSessionDialog = false
                }) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailItemRow(
    item: MailItemEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMismatch = item.validationStatus == "WARNING"
    // On considère qu'un item est "Non Vérifié" si on est en "CALCULATED" (pas d'OCR validé)
    // ET que c'est un format SmartData (86...) qui devrait normalement avoir une vérification.
    val isSmartData = item.trackingNumber?.startsWith("86") == true
    val isUnverified = item.validationStatus == "CALCULATED" && isSmartData
    
    val isIncomplete = item.trackingNumber.isNullOrBlank() || item.recipientAddress.isNullOrBlank()
    val needsAttention = isMismatch || isUnverified || isIncomplete

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (needsAttention) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (needsAttention) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Attention requise",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.trackingNumber ?: "Sans numéro",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (isIncomplete) {
                    Text(
                        text = "Données manquantes - Cliquez pour compléter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (isMismatch) {
                    Text(
                        text = "Clé incohérente - Vérifiez le numéro",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (isUnverified) {
                     Text(
                        text = "Clé non confirmée (OCR manquant)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TechnicalKeyRow(label: String, value: String, isMatch: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (isMatch && value != "-") {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Green, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun EditMailItemDialog(
    item: MailItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (MailItemEntity) -> Unit
) {
    var tracking by remember { mutableStateOf(item.trackingNumber ?: "") }
    var address by remember { mutableStateOf(item.recipientAddress ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le courrier") },
        text = {
            Column {
                // --- Section Détails Techniques ---
                val statusColor = when (item.validationStatus) {
                    "VERIFIED" -> androidx.compose.ui.graphics.Color.Green
                    "WARNING" -> androidx.compose.ui.graphics.Color.Yellow
                    else -> androidx.compose.ui.graphics.Color.Cyan
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Vérification : ${item.validationStatus}",
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        TechnicalKeyRow("Clé Luhn (Std)", item.luhnKey ?: "-", item.ocrKey == item.luhnKey)
                        TechnicalKeyRow("Clé ISO (Smart)", item.isoKey ?: "-", item.ocrKey == item.isoKey)
                        TechnicalKeyRow("Clé lue (OCR)", item.ocrKey ?: "Non trouvée", true)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        
                        val currentKey = if ((item.trackingNumber?.length ?: 0) >= 15) item.trackingNumber?.substring(14) else "-"
                        Text(
                            text = "Clé retenue : $currentKey",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // ---------------------------------

                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text("N° Suivi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse Destinataire") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(item.copy(trackingNumber = tracking, recipientAddress = address)) }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var tracking by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajout Manuel") },
        text = {
            Column {
                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text("N° Suivi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse Destinataire") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tracking, address) }, enabled = tracking.isNotBlank()) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}