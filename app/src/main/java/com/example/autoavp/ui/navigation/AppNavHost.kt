package com.example.autoavp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.autoavp.ui.scan.ScanScreen
import com.example.autoavp.ui.scan.ScanViewModel
import com.example.autoavp.ui.session.SessionDetailsScreen
import com.example.autoavp.ui.office.OfficeScreen
import com.example.autoavp.ui.home.HomeScreen
import com.example.autoavp.ui.history.HistoryScreen
import com.example.autoavp.ui.preview.PrintPreviewScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Scan.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode")
            val viewModel: ScanViewModel = hiltViewModel()
            viewModel.setScanMode(mode)

            ScanScreen(
                viewModel = viewModel,
                onFinishScan = {
                    navController.popBackStack()
                },
                onOpenSettings = {
                    navController.navigate(Screen.Offices.route)
                }
            )
        }

        composable(
            route = Screen.SessionDetails.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { 
            SessionDetailsScreen(navController = navController)
        }
        
        composable(Screen.Offices.route) {
            OfficeScreen(navController = navController)
        }

        composable(Screen.History.route) {
            HistoryScreen(navController = navController)
        }

        composable(
            route = Screen.PrintPreview.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("officeId") { type = NavType.LongType }
            )
        ) {
            PrintPreviewScreen(navController = navController)
        }
    }
}
