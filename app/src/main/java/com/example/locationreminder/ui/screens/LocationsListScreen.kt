package com.example.locationreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.locationreminder.data.LocationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddLocation: () -> Unit,
    onNavigateToEditLocation: (Long) -> Unit,
    viewModel: ReminderViewModel = viewModel(factory = ReminderViewModelFactory(LocalContext.current.applicationContext))
) {
    val locations by viewModel.allLocations.collectAsState()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onLocationMoved(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddLocation) {
                Icon(Icons.Default.Add, contentDescription = "Add Location")
            }
        }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(locations, key = { it.id }) { location ->
                ReorderableItem(reorderableState, key = location.id) { isDragging ->
                    LocationItem(
                        location = location,
                        isDragging = isDragging,
                        onClick = { onNavigateToEditLocation(location.id) },
                        dragModifier = Modifier.draggableHandle(
                            onDragStarted = { viewModel.onLocationDragStarted() },
                            onDragStopped = { viewModel.onLocationDragStopped() }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LocationItem(
    location: LocationEntity,
    isDragging: Boolean,
    onClick: () -> Unit,
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
                Text(text = location.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Radius: ${location.radius.toInt()}m", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                IconButton(onClick = {}, modifier = dragModifier.size(24.dp)) {
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder")
                }
            }
        }
    }
}
