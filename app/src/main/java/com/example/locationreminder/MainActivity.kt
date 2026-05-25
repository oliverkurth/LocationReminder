package com.example.locationreminder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.locationreminder.service.LocationTrackingService
import com.example.locationreminder.ui.navigation.AppNavigation
import com.example.locationreminder.ui.theme.LocationReminderTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

data class SharedLocationData(val lat: Double, val lon: Double, val name: String?)

fun parseSharedText(text: String): SharedLocationData? {
    val qRegex = Regex("q=([0-9.-]+),([0-9.-]+)\\(([^)]+)\\)")
    val qMatch = qRegex.find(text)
    if (qMatch != null) {
        val lat = qMatch.groupValues[1].toDoubleOrNull()
        val lon = qMatch.groupValues[2].toDoubleOrNull()
        val name = java.net.URLDecoder.decode(qMatch.groupValues[3], "UTF-8")
        if (lat != null && lon != null) {
            return SharedLocationData(lat, lon, name)
        }
    }

    val geoRegex = Regex("geo:([0-9.-]+),([0-9.-]+)")
    val geoMatch = geoRegex.find(text)
    if (geoMatch != null) {
        val lat = geoMatch.groupValues[1].toDoubleOrNull()
        val lon = geoMatch.groupValues[2].toDoubleOrNull()
        if (lat != null && lon != null) {
            return SharedLocationData(lat, lon, null)
        }
    }
    
    val mapsRegex = Regex("maps\\.google\\.com/\\?q=([0-9.-]+),([0-9.-]+)")
    val mapsMatch = mapsRegex.find(text)
    if (mapsMatch != null) {
        val lat = mapsMatch.groupValues[1].toDoubleOrNull()
        val lon = mapsMatch.groupValues[2].toDoubleOrNull()
        if (lat != null && lon != null) {
            return SharedLocationData(lat, lon, null)
        }
    }

    return null
}

class MainActivity : ComponentActivity() {
    private var sharedLocationData by mutableStateOf<SharedLocationData?>(null)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        
        setContent {
            LocationReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val permissionState = rememberMultiplePermissionsState(permissions)

                    if (permissionState.allPermissionsGranted) {
                        // Check background location separately for Android 11+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val bgPermissionState = rememberMultiplePermissionsState(
                                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            )
                            if (bgPermissionState.allPermissionsGranted) {
                                LaunchedEffect(Unit) {
                                    LocationTrackingService.startService(this@MainActivity)
                                }
                                AppNavigation(
                                    sharedLocationData = sharedLocationData,
                                    onSharedLocationHandled = { sharedLocationData = null }
                                )
                            } else {
                                PermissionScreen(
                                    permissionState = bgPermissionState,
                                    text = "Background location permission is needed to trigger reminders when the app is closed."
                                )
                            }
                        } else {
                            LaunchedEffect(Unit) {
                                LocationTrackingService.startService(this@MainActivity)
                            }
                            AppNavigation(
                                sharedLocationData = sharedLocationData,
                                onSharedLocationHandled = { sharedLocationData = null }
                            )
                        }
                    } else {
                        PermissionScreen(
                            permissionState = permissionState,
                            text = "Location and Notification permissions are needed for this app to function properly."
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                sharedLocationData = parseSharedText(sharedText)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(permissionState: MultiplePermissionsState, text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
            Text("Grant Permissions")
        }
    }
}
