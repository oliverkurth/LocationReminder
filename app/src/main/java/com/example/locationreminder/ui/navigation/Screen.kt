package com.example.locationreminder.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddEditReminder : Screen("add_edit_reminder/{reminderId}") {
        fun createRoute(reminderId: Long = -1L) = "add_edit_reminder/$reminderId"
    }
    object LocationsList : Screen("locations_list")
    object MapScreen : Screen("map_screen/{locationId}?lat={lat}&lon={lon}&name={name}") {
        fun createRoute(locationId: Long = -1L, lat: Float? = null, lon: Float? = null, name: String? = null): String {
            var route = "map_screen/$locationId"
            val params = mutableListOf<String>()
            if (lat != null) params.add("lat=$lat")
            if (lon != null) params.add("lon=$lon")
            if (name != null) params.add("name=${android.net.Uri.encode(name)}")
            if (params.isNotEmpty()) {
                route += "?" + params.joinToString("&")
            }
            return route
        }
    }
}
