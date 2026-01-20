package com.example.autoavp.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan/{mode}") {
        fun createRoute(mode: String) = "scan/$mode"
        const val MODE_SINGLE = "single"
        const val MODE_BULK = "bulk"
    }
    object SessionDetails : Screen("session_details/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_details/$sessionId"
    }
            object Offices : Screen("offices")
            object History : Screen("history")
            object PrintPreview : Screen("print_preview/{sessionId}/{officeId}") {
                fun createRoute(sessionId: Long, officeId: Long) = "print_preview/$sessionId/$officeId"
            }
            object Settings : Screen("settings")
        }
        