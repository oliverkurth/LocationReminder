package com.example.locationreminder.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddEditReminder : Screen("add_edit_reminder/{reminderId}") {
        fun createRoute(reminderId: Long = -1L) = "add_edit_reminder/$reminderId"
    }
    object LocationsList : Screen("locations_list")
    object MapScreen : Screen("map_screen/{locationId}") {
        fun createRoute(locationId: Long = -1L) = "map_screen/$locationId"
    }
}
