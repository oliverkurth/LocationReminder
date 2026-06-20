package com.example.locationreminder.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

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

    var hasInitializedCenter = false

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
                    hasInitializedCenter = false // Reset so the map will center on this loaded point
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

    fun setInitialSharedLocation(lat: Double, lon: Double, name: String?) {
        val point = GeoPoint(lat, lon)
        _selectedPoint.value = point
        _mapCenter.value = point
        hasInitializedCenter = false // Reset so the map will center on this new point
        if (name != null) {
            _locationName.value = name
        }
    }

    fun centerOnSelectedPoint(mapView: MapView) {
        _selectedPoint.value?.let { point ->
            mapView.controller.animateTo(point)
            _mapCenter.value = point
            hasInitializedCenter = true
        }
    }

    fun centerOnCurrentLocation(mapView: MapView) {
        val locationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        locationOverlay?.myLocation?.let { point ->
            mapView.controller.animateTo(point)
            _mapCenter.value = point
            hasInitializedCenter = true
        } ?: run {
            // Fallback if myLocation isn't available yet
            mapView.controller.animateTo(_mapCenter.value)
            hasInitializedCenter = true
        }
    }

    fun saveLocation(onSaved: () -> Unit) {
        val point = _selectedPoint.value ?: return
        val name = _locationName.value.ifBlank { "Unnamed Location" }
        
        viewModelScope.launch {
            val allLocationsList = repository.allLocations.first()
            val nextOrder = allLocationsList.maxOfOrNull { it.displayOrder }?.plus(1) ?: 0
            val currentOrder = _currentLocation.value?.displayOrder ?: nextOrder

            val entity = LocationEntity(
                id = _currentLocation.value?.id ?: 0,
                name = name,
                latitude = point.latitude,
                longitude = point.longitude,
                radius = _radius.value,
                displayOrder = currentOrder
            )
            if (entity.id == 0L) {
                repository.insertLocation(entity)
            } else {
                repository.updateLocation(entity)
            }
            onSaved()
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            // Remove the location reference from any reminders that use it
            // We need to fetch the reminders directly from the DB here since we don't have a StateFlow of them in this ViewModel
            val allReminders = repository.getLocationBasedReminders() 
            val remindersWithLocation = allReminders.filter { it.locationId == location.id }
            remindersWithLocation.forEach { 
                repository.updateReminder(it.copy(locationId = null))
            }
            repository.deleteLocation(location)
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
    initialLat: Double? = null,
    initialLon: Double? = null,
    initialName: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: LocationViewModel = viewModel(factory = LocationViewModelFactory(LocalContext.current.applicationContext))
) {
    val context = LocalContext.current
    val selectedPoint by viewModel.selectedPoint.collectAsState()
    val radius by viewModel.radius.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Location") },
            text = { Text("Are you sure you want to delete '${locationName}'? Reminders using this location will no longer trigger based on it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.currentLocation.value?.let { viewModel.deleteLocation(it) }
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(locationId) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.loadLocation(locationId)
        
        if (locationId == -1L && initialLat != null && initialLon != null) {
            viewModel.setInitialSharedLocation(initialLat, initialLon, initialName)
        }
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
                    if (locationId != -1L) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(
                        onClick = { mapViewRef?.let { viewModel.centerOnSelectedPoint(it) } },
                        enabled = selectedPoint != null
                    ) {
                        Icon(Icons.Default.Place, contentDescription = "Center on Selected Location")
                    }
                    IconButton(
                        onClick = { mapViewRef?.let { viewModel.centerOnCurrentLocation(it) } }
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Slider(
                value = radius,
                onValueChange = viewModel::updateRadius,
                valueRange = 50f..1000f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // We use a key based on the locationId so the AndroidView is recreated if we switch locations,
                // but it won't be recreated just because we pan.
                key(locationId) {
                    AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapViewRef = this
                            setMultiTouchControls(true)
                            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                            controller.setZoom(15.0)
                            
                            // Force layout to ensure it draws
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setClipToOutline(true)
                            
                            val mapEventsReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    viewModel.updateSelectedPoint(p)
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            }
                            overlays.add(MapEventsOverlay(mapEventsReceiver))

                            val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            myLocationOverlay.enableMyLocation()
                            overlays.add(myLocationOverlay)
                        }
                    },
                    update = { mapView ->
                        // Center the map if it hasn't been initialized yet for this view model instance
                        if (!viewModel.hasInitializedCenter) {
                            mapView.controller.setCenter(mapCenter)
                            viewModel.hasInitializedCenter = true
                        }
                        
                        // Clear previous markers/polygons except the events overlay and location overlay
                        val overlaysToRemove = mapView.overlays.filter { it !is MapEventsOverlay && it !is MyLocationNewOverlay }
                        mapView.overlays.removeAll(overlaysToRemove)
                        
                        selectedPoint?.let { point ->
                            val circle = Polygon(mapView).apply {
                                points = Polygon.pointsAsCircle(point, radius.toDouble())
                                fillColor = 0x440000FF // Semi-transparent blue (increased opacity)
                                outlinePaint.color = 0xFF0000FF.toInt() // Solid blue border
                                outlinePaint.strokeWidth = 5f // Thicker border
                            }
                            mapView.overlays.add(circle)

                            val marker = Marker(mapView).apply {
                                position = point
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Selected Location"
                            }
                            mapView.overlays.add(marker)
                        }
                        mapView.invalidate()
                    }
                )
                }
            }
        }
    }
}
