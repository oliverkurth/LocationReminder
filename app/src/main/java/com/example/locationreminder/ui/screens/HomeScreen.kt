package com.example.locationreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.animation.core.animateDpAsState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextDecoration
import com.example.locationreminder.data.ReminderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddReminder: () -> Unit,
    onNavigateToEditReminder: (Long) -> Unit,
    onNavigateToLocations: () -> Unit,
    viewModel: ReminderViewModel = viewModel(factory = ReminderViewModelFactory(LocalContext.current.applicationContext))
) {
    val reminders by viewModel.allReminders.collectAsState()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onReminderMoved(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                actions = {
                    IconButton(onClick = onNavigateToLocations) {
                        Icon(Icons.Default.Place, contentDescription = "Locations")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddReminder) {
                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
            }
        }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(reminders, key = { it.id }) { reminder ->
                ReorderableItem(reorderableState, key = reminder.id) { isDragging ->
                    ReminderItem(
                        reminder = reminder,
                        isDragging = isDragging,
                        onClick = { onNavigateToEditReminder(reminder.id) },
                        onToggleActive = { viewModel.toggleReminderActive(reminder) },
                        dragModifier = Modifier.draggableHandle(
                            onDragStopped = { viewModel.onReminderDragStopped() }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderItem(
    reminder: ReminderEntity,
    isDragging: Boolean,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    dragModifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 4.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title, 
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (!reminder.isActive) TextDecoration.LineThrough else null
                )
                if (reminder.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = reminder.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (reminder.timeOfDay != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Time: ${reminder.timeOfDay}", style = MaterialTheme.typography.bodySmall)
                }
                if (reminder.isRecurring) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Recurring", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "One-time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (reminder.isDeferred) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Deferred", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                IconButton(onClick = {}, modifier = dragModifier.size(24.dp)) {
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder")
                }
                Switch(
                    checked = reminder.isActive,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
