package com.example.locationreminder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.locationreminder.R
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.notifications.NotificationHelper
import com.example.locationreminder.repository.AppRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AppRepository
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val database = AppDatabase.getDatabase(applicationContext)
        repository = AppRepository(database.locationDao(), database.reminderDao())
        notificationHelper = NotificationHelper(applicationContext)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.hasAccuracy() && location.accuracy <= 100f) {
                        checkGeofences(location)
                    } else if (!location.hasAccuracy()) {
                        // If no accuracy is provided, we might still want to check, 
                        // but usually FusedLocationProviderClient provides accuracy.
                        // For now, we'll be conservative and only accept locations with known good accuracy.
                        Log.d("LocationService", "Ignoring location with no accuracy")
                    } else {
                        Log.d("LocationService", "Ignoring location with poor accuracy: ${location.accuracy}m")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Reminder Active")
            .setContentText("Tracking location for your reminders")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        requestLocationUpdates()

        return START_STICKY
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60000) // 1 minute
            .setMinUpdateDistanceMeters(50f) // 50 meters
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService", "Missing location permission", e)
        }
    }

    private fun checkGeofences(currentLocation: Location) {
        serviceScope.launch {
            val reminders = repository.getLocationBasedReminders()
            for (reminder in reminders) {
                if (reminder.isDeferred && reminder.deferredUntil != null && System.currentTimeMillis() < reminder.deferredUntil) {
                    continue // Skip deferred reminders
                }

                reminder.locationId?.let { locId ->
                    val locationEntity = repository.getLocationById(locId)
                    if (locationEntity != null) {
                        val targetLocation = Location("").apply {
                            latitude = locationEntity.latitude
                            longitude = locationEntity.longitude
                        }
                        val distance = currentLocation.distanceTo(targetLocation)
                        if (distance <= locationEntity.radius) {

                            // Check recurring days condition
                            if (reminder.recurringDays != null && reminder.recurringDays.isNotEmpty()) {
                                val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                if (!reminder.recurringDays.contains(currentDay)) {
                                    return@let // Skip if today is not a selected recurring day
                                }
                            }

                            // Check time condition if set
                            if (reminder.timeOfDay != null) {
                                val parts = reminder.timeOfDay.split(":")
                                if (parts.size == 2) {
                                    val hour = parts[0].toIntOrNull() ?: 0
                                    val minute = parts[1].toIntOrNull() ?: 0
                                    val calendar = java.util.Calendar.getInstance()
                                    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                                    val currentMinute = calendar.get(java.util.Calendar.MINUTE)

                                    val currentTimeInMinutes = currentHour * 60 + currentMinute
                                    val targetTimeInMinutes = hour * 60 + minute

                                    if (currentTimeInMinutes < targetTimeInMinutes) {
                                        // It's too early, don't trigger yet
                                        return@let
                                    }
                                }
                            }

                            // Trigger notification
                            notificationHelper.showReminderNotification(reminder)
                            
                            // Mark as deferred/done or we might keep triggering it. 
                            // For simplicity, we'll auto-defer it for a long time or just let the user dismiss it.
                            // To prevent spamming, we can set a temporary deferred state.
                            val updatedReminder = reminder.copy(
                                isDeferred = true,
                                deferredUntil = System.currentTimeMillis() + 60 * 60 * 1000 // 1 hour cooldown
                            )
                            repository.updateReminder(updatedReminder)
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for background location tracking"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }
}
