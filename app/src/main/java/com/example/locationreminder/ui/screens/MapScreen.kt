package com.example.locationreminder.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.data.LocationEntity
import com.example.locationreminder.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class LocationViewModel(private val repository: AppRepository) : ViewModel() {
    private val _currentLocation = MutableStateFlow<LocationEntity?>(null)
    val currentLocation: StateFlow<LocationEntity?> = _currentLocation.asStateFlow()

    private val _mapCenter = MutableStateFlow(GeoPoint(51.5074, -0.1278)) // Default London
    val mapCenter: StateFlow<GeoPoint> = _mapCenter.asStateFlow()

    private val _selectedPoint = MutableStateFlow<GeoPoint?>(null)
    val selectedPoint: StateFlow<GeoPoint?> = _selectedPoint.asStateFlow()

    private val _radius = MutableStateFlow(100f) // Default 100m
    val radius: StateFlow<Float> = _radius.asStateFlow()

    private val _locationName = MutableStateFlow("")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    fun loadLocation(id: Long) {
        if (id != -1L) {
            viewModelScope.launch {
                val loc = repository.getLocationById(id)
                if (loc != null) {
                    _currentLocation.value = loc
                    _locationName.value = loc.name
                    _radius.value = loc.radius
                    val point = GeoPoint(loc.latitude, loc.longitude)
                    _selectedPoint.value = point
                    _mapCenter.value = point
                }
            }
        }
    }

    fun updateSelectedPoint(point: GeoPoint) {
        _selectedPoint.value = point
    }

    fun updateRadius(newRadius: Float) {
        _radius.value = newRadius
    }

    fun updateName(name: String) {
        _locationName.value = name
    }

    fun saveLocation(onSaved: () -> Unit) {
        val point = _selectedPoint.value ?: return
        val name = _locationName.value.ifBlank { "Unnamed Location" }
        
        viewModelScope.launch {
            val entity = LocationEntity(
                id = _currentLocation.value?.id ?: 0,
                name = name,
                latitude = point.latitude,
                longitude = point.longitude,
                radius = _radius.value
            )
            if (entity.id == 0L) {
                repository.insertLocation(entity)
            } else {
                repository.updateLocation(entity)
            }
            onSaved()
        }
    }
}

class LocationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val repository = AppRepository(database.locationDao(), database.reminderDao())
        @Suppress("UNCHECKED_CAST")
        return LocationViewModel(repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    locationId: Long,
    onNavigateBack: () -> Unit,
    viewModel: LocationViewModel = viewModel(factory = LocationViewModelFactory(LocalContext.current.applicationContext))
) {
    val context = LocalContext.current
    val selectedPoint by viewModel.selectedPoint.collectAsState()
    val radius by viewModel.radius.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()

    LaunchedEffect(locationId) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.loadLocation(locationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (locationId == -1L) "Add Location" else "Edit Location") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveLocation(onNavigateBack) },
                        enabled = selectedPoint != null && locationName.isNotBlank()
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
        ) {
            OutlinedTextField(
                value = locationName,
                onValueChange = viewModel::updateName,
                label = { Text("Location Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Text(
                text = "Radius: ${radius.toInt()}m",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Slider(
                value = radius,
                onValueChange = viewModel::updateRadius,
                valueRange = 50f..1000f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                            controller.setZoom(15.0)
                            
                            // Force layout to ensure it draws
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            
                            val mapEventsReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    viewModel.updateSelectedPoint(p)
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            }
                            overlays.add(MapEventsOverlay(mapEventsReceiver))
                        }
                    },
                    update = { mapView ->
                        mapView.controller.setCenter(mapCenter)
                        
                        // Clear previous markers/polygons except the events overlay
                        mapView.overlays.removeAll { it !is MapEventsOverlay }
                        
                        selectedPoint?.let { point ->
                            val marker = Marker(mapView).apply {
                                position = point
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Selected Location"
                            }
                            mapView.overlays.add(marker)

                            val circle = Polygon().apply {
                                points = Polygon.pointsAsCircle(point, radius.toDouble())
                                fillColor = 0x12121212
                                strokeColor = 0xFF121212.toInt()
                                strokeWidth = 2f
                            }
                            mapView.overlays.add(circle)
                        }
                        mapView.invalidate()
                    }
                )
            }
        }
    }
}
