package com.example.locationreminder

import android.Manifest
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

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                AppNavigation()
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
                            AppNavigation()
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
