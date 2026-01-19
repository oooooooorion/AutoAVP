package com.example.autoavp.ui.office

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.autoavp.data.local.entities.InstanceOfficeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeScreen(
    navController: NavController,
    viewModel: OfficeViewModel = hiltViewModel()
) {
    val offices by viewModel.offices.collectAsState()
    val editingOffice by viewModel.editingOffice.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bureaux d'Instance") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAddOffice() },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un bureau")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(offices, key = { it.officeId }) { office ->
                OfficeCard(
                    office = office,
                    onEdit = { viewModel.onEditOffice(office) },
                    onDelete = { viewModel.onDeleteOffice(office) }
                )
            }
        }
    }

    // Dialogue d'édition
    if (editingOffice != null) {
        OfficeEditDialog(
            office = editingOffice!!,
            onDismiss = { viewModel.onCancelEdit() },
            onSave = { viewModel.onSaveOffice(it) }
        )
    }
}

@Composable
fun OfficeCard(
    office: InstanceOfficeEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = try { Color(android.graphics.Color.parseColor(office.colorHex)) } catch (e: Exception) { Color.Gray }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pastille de couleur
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Black, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = office.name, style = MaterialTheme.typography.titleMedium)
                Text(text = office.address, style = MaterialTheme.typography.bodySmall)
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun OfficeEditDialog(
    office: InstanceOfficeEntity,
    onDismiss: () -> Unit,
    onSave: (InstanceOfficeEntity) -> Unit
) {
    var name by remember { mutableStateOf(office.name) }
    var address by remember { mutableStateOf(office.address) }
    var hours by remember { mutableStateOf(office.openingHours) }
    var colorHex by remember { mutableStateOf(office.colorHex) }

    // Liste de couleurs prédéfinies
    val colors = listOf(
        "#FFCE00" to "Jaune",
        "#E60000" to "Rouge",
        "#009900" to "Vert",
        "#004899" to "Bleu",
        "#FFFFFF" to "Blanc"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (office.officeId == 0L) "Nouveau Bureau" else "Modifier Bureau") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du Bureau") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse") }
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Horaires") }
                )
                
                Text("Couleur AVP :", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { (hex, _) ->
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (colorHex == hex) 3.dp else 1.dp,
                                    color = if (colorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(office.copy(
                    name = name,
                    address = address,
                    openingHours = hours,
                    colorHex = colorHex
                ))
            }) {
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
