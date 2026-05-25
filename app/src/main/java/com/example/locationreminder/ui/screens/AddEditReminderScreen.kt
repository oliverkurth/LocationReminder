package com.example.locationreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.locationreminder.data.ReminderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    reminderId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLocations: () -> Unit,
    viewModel: ReminderViewModel = viewModel(factory = ReminderViewModelFactory(LocalContext.current.applicationContext))
) {
    val title by viewModel.title.collectAsState()
    val description by viewModel.description.collectAsState()
    val selectedLocationId by viewModel.selectedLocationId.collectAsState()
    val timeOfDay by viewModel.timeOfDay.collectAsState()
    val isRecurring by viewModel.isRecurring.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val locations by viewModel.allLocations.collectAsState()

    LaunchedEffect(reminderId) {
        viewModel.loadReminder(reminderId)
    }

    var expandedLocationDropdown by remember { mutableStateOf(false) }

    var reminderToDelete by remember { mutableStateOf<ReminderEntity?>(null) }

    if (reminderToDelete != null) {
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Delete Reminder") },
            text = { Text("Are you sure you want to delete '${reminderToDelete?.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderToDelete?.let { 
                            viewModel.deleteReminder(it) 
                            onNavigateBack()
                        }
                        reminderToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminderId == -1L) "Add Reminder" else "Edit Reminder") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (reminderId != -1L) {
                        IconButton(onClick = { reminderToDelete = viewModel.currentReminder.value }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(
                        onClick = { viewModel.saveReminder(onNavigateBack) },
                        enabled = title.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Triggers", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Active")
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isActive,
                    onCheckedChange = viewModel::updateIsActive
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Recurring Reminder")
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isRecurring,
                    onCheckedChange = viewModel::updateIsRecurring
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Location Trigger
            ExposedDropdownMenuBox(
                expanded = expandedLocationDropdown,
                onExpandedChange = { expandedLocationDropdown = !expandedLocationDropdown }
            ) {
                OutlinedTextField(
                    value = locations.find { it.id == selectedLocationId }?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Location") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocationDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedLocationDropdown,
                    onDismissRequest = { expandedLocationDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            viewModel.updateLocation(null)
                            expandedLocationDropdown = false
                        }
                    )
                    locations.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc.name) },
                            onClick = {
                                viewModel.updateLocation(loc.id)
                                expandedLocationDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = timeOfDay ?: "",
                onValueChange = viewModel::updateTimeOfDay,
                label = { Text("Time (HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )
            // For simplicity, we just use a text field for time and specific date can be added similarly
            // A real app would use TimePickerDialog and DatePickerDialog
        }
    }
}
