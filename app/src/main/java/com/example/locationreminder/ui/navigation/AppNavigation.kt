package com.example.locationreminder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.locationreminder.ui.screens.AddEditReminderScreen
import com.example.locationreminder.ui.screens.HomeScreen
import com.example.locationreminder.ui.screens.LocationsListScreen
import com.example.locationreminder.ui.screens.MapScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

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
            arguments = listOf(navArgument("locationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getLong("locationId") ?: -1L
            MapScreen(
                locationId = locationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
