package com.example.autoavp.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.ui.navigation.Screen
import com.example.autoavp.ui.print.AvpPdfGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    navController: NavController,
    viewModel: SessionDetailsViewModel = hiltViewModel()
) {
    val items: List<MailItemEntity> by viewModel.mailItems.collectAsState(initial = emptyList())
    val offices: List<InstanceOfficeEntity> by viewModel.offices.collectAsState(initial = emptyList())
    val selectedOffice: InstanceOfficeEntity? by viewModel.selectedOffice.collectAsState(initial = null)
    val context = LocalContext.current

    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vérification (${items.size})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { 
                        val officeId = selectedOffice?.officeId
                        val sId = viewModel.getSessionId()
                        if (officeId != null) {
                            navController.navigate(Screen.PrintPreview.createRoute(sId, officeId))
                        }
                    }, enabled = selectedOffice != null && items.isNotEmpty()) {
                        Icon(Icons.Default.Print, contentDescription = "Aperçu avant impression")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Sélecteur de Bureau
            if (offices.isEmpty()) {
                Text(
                    "Aucun bureau configuré. Allez dans Paramètres.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedOffice?.name ?: "Choisir un bureau d'instance",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        label = { Text("Bureau d'Instance") }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        offices.forEach { office: InstanceOfficeEntity ->
                            DropdownMenuItem(
                                text = { Text(office.name) },
                                onClick = {
                                    viewModel.selectOffice(office)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.mailId }) { item ->
                    MailItemCard(
                        item = item,
                        onUpdate = { updatedItem -> viewModel.updateItem(updatedItem) },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun MailItemCard(
    item: MailItemEntity,
    onUpdate: (MailItemEntity) -> Unit,
    onDelete: () -> Unit
) {
    // État local pour l'édition fluide
    var trackingNumber by remember(item.trackingNumber) { mutableStateOf(item.trackingNumber ?: "") }
    var address by remember(item.recipientAddress) { mutableStateOf(item.recipientAddress ?: "") }
    var showDetails by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Courrier #${item.mailId}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(Icons.Default.Info, contentDescription = "Détails techniques")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (showDetails) {
                // Détails techniques
                val statusColor = when (item.validationStatus) {
                    "VERIFIED" -> Color.Green
                    "WARNING" -> Color.Yellow
                    else -> Color.Blue
                }
                val statusIcon = when (item.validationStatus) {
                    "VERIFIED" -> Icons.Default.CheckCircle
                    "WARNING" -> Icons.Default.Warning
                    else -> Icons.Default.Calculate
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Statut : ${item.validationStatus}", style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                        
                        // Bloc de diagnostic des clés
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DiagnosticKey("ISO", item.isoKey)
                            DiagnosticKey("OCR", item.ocrKey, isBold = true)
                        }

                        if (!item.rawOcrText.isNullOrBlank()) {
                            Text("OCR Brut (Extrait) :", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            Text(item.rawOcrText.take(50).replace("\n", " ") + "...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = trackingNumber,
                onValueChange = { 
                    trackingNumber = it
                    onUpdate(item.copy(trackingNumber = it))
                },
                label = { Text("N° Suivi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (item.validationStatus == "WARNING") {
                        Icon(Icons.Default.Warning, contentDescription = "Non vérifié", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { 
                    address = it
                    onUpdate(item.copy(recipientAddress = it))
                },
                label = { Text("Adresse Destinataire") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
        }
    }
}

@Composable
fun DiagnosticKey(label: String, value: String?, isBold: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            value ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            color = if (isBold && value != null) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}
