package com.example.locationreminder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.locationreminder.SharedLocationData
import com.example.locationreminder.ui.screens.AddEditReminderScreen
import com.example.locationreminder.ui.screens.HomeScreen
import com.example.locationreminder.ui.screens.LocationsListScreen
import com.example.locationreminder.ui.screens.MapScreen

@Composable
fun AppNavigation(
    sharedLocationData: SharedLocationData? = null,
    onSharedLocationHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedLocationData) {
        if (sharedLocationData != null) {
            navController.navigate(
                Screen.MapScreen.createRoute(
                    lat = sharedLocationData.lat.toFloat(),
                    lon = sharedLocationData.lon.toFloat(),
                    name = sharedLocationData.name
                )
            )
            onSharedLocationHandled()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAddReminder = { navController.navigate(Screen.AddEditReminder.createRoute()) },
                onNavigateToEditReminder = { id -> navController.navigate(Screen.AddEditReminder.createRoute(id)) },
                onNavigateToLocations = { navController.navigate(Screen.LocationsList.route) }
            )
        }
        
        composable(
            route = Screen.AddEditReminder.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: -1L
            AddEditReminderScreen(
                reminderId = reminderId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLocations = { navController.navigate(Screen.LocationsList.route) }
            )
        }
        
        composable(Screen.LocationsList.route) {
            LocationsListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddLocation = { navController.navigate(Screen.MapScreen.createRoute()) },
                onNavigateToEditLocation = { id -> navController.navigate(Screen.MapScreen.createRoute(id)) }
            )
        }
        
        composable(
            route = Screen.MapScreen.route,
            arguments = listOf(
                navArgument("locationId") { type = NavType.LongType },
                navArgument("lat") { type = NavType.FloatType; defaultValue = Float.NaN },
                navArgument("lon") { type = NavType.FloatType; defaultValue = Float.NaN },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getLong("locationId") ?: -1L
            val lat = backStackEntry.arguments?.getFloat("lat")?.takeIf { !it.isNaN() }
            val lon = backStackEntry.arguments?.getFloat("lon")?.takeIf { !it.isNaN() }
            val name = backStackEntry.arguments?.getString("name")
            
            MapScreen(
                locationId = locationId,
                initialLat = lat?.toDouble(),
                initialLon = lon?.toDouble(),
                initialName = name,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
