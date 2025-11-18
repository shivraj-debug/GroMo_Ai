package com.example.demo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.demo.api.User
import com.example.demo.screens.LoginScreen
import com.example.demo.screens.MainScreen
import com.example.demo.screens.RegisterScreen
import com.example.demo.screens.StartScreen
import com.example.demo.screens.CustomerDetailsScreen
import com.example.demo.screens.AnalysisResultScreen
import com.example.demo.viewmodels.AuthState
import com.example.demo.viewmodels.AuthViewModel
import com.example.demo.viewmodels.CustomerViewModel

/**
 * Navigation routes used in the app
 */
object NavRoutes {
    const val START_SCREEN = "start_screen"
    const val LOGIN_SCREEN = "login_screen"
    const val REGISTER_SCREEN = "register_screen"
    const val MAIN_SCREEN = "main_screen"
    const val CUSTOMER_DETAILS_SCREEN = "customer_details_screen/{customerName}"
    const val ANALYSIS_RESULTS_SCREEN = "analysis_results_screen/{customerName}"
    
    // Helper function to create customer details route with parameter
    fun customerDetailsRoute(customerName: String): String {
        return "customer_details_screen/$customerName"
    }
    
    // Helper function to create analysis results route with parameter
    fun analysisResultsRoute(customerName: String): String {
        return "analysis_results_screen/$customerName"
    }
}

/**
 * Data holder for analysis results (temporary storage between screens)
 * In production, consider using a ViewModel or proper state management
 */
object AnalysisDataHolder {
    private var _analysisData: AnalysisData? = null
    
    data class AnalysisData(
        val customerName: String,
        val result: String,
        val hasCallRecording: Boolean
    )
    
    fun setAnalysisData(customerName: String, result: String, hasCallRecording: Boolean) {
        _analysisData = AnalysisData(customerName, result, hasCallRecording)
    }
    
    fun getAnalysisData(): AnalysisData? = _analysisData
    
    fun clearAnalysisData() {
        _analysisData = null
    }
}

/**
 * Main navigation component that sets up the navigation graph
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState(initial = AuthState.UNAUTHENTICATED)
    val user by authViewModel.user.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    
    // Observe authentication state changes
    LaunchedEffect(authState) {
        when (authState) {
            AuthState.AUTHENTICATED -> {
                navController.navigate(NavRoutes.MAIN_SCREEN) {
                    popUpTo(NavRoutes.START_SCREEN) { inclusive = true }
                }
            }
            else -> { /* No action needed */ }
        }
    }
    
    NavHost(navController = navController, startDestination = NavRoutes.START_SCREEN) {
        composable(NavRoutes.START_SCREEN) {
            StartScreen(
                onContinueClick = {
                    // If already logged in, go to main screen, otherwise to login
                    if (authState == AuthState.AUTHENTICATED) {
                        navController.navigate(NavRoutes.MAIN_SCREEN) {
                            popUpTo(NavRoutes.START_SCREEN) { inclusive = true }
                        }
                    } else {
                        navController.navigate(NavRoutes.LOGIN_SCREEN)
                    }
                }
            )
        }
        
        composable(NavRoutes.LOGIN_SCREEN) {
            LoginScreen(
                onLoginClick = { email, password ->
                    authViewModel.login(email, password)
                },
                onRegisterClick = {
                    navController.navigate(NavRoutes.REGISTER_SCREEN)
                },
                errorMessage = if (authState == AuthState.ERROR) errorMessage else null,
                onClearError = { authViewModel.resetError() },
                isLoading = authState == AuthState.LOADING
            )
        }
        
        composable(NavRoutes.REGISTER_SCREEN) {
            RegisterScreen(
                onRegisterClick = { name, email, password ->
                    authViewModel.register(name, email, password)
                },
                onLoginClick = {
                    navController.navigateUp()
                },
                errorMessage = if (authState == AuthState.ERROR) errorMessage else null,
                onClearError = { authViewModel.resetError() },
                isLoading = authState == AuthState.LOADING
            )
        }
        
        composable(NavRoutes.MAIN_SCREEN) {
            MainScreen(
                user = user,
                onFetchProfile = {
                    authViewModel.fetchUserProfile()
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(NavRoutes.LOGIN_SCREEN) {
                        popUpTo(NavRoutes.MAIN_SCREEN) { inclusive = true }
                    }
                },
                errorMessage = errorMessage,
                onClearError = { authViewModel.clearErrorMessage() },
                onNavigateToCustomerDetails = { customerName ->
                    // Navigate to customer details without any popUpTo to maintain stack
                    navController.navigate(NavRoutes.customerDetailsRoute(customerName))
                }
            )
        }
        
        composable(
            route = NavRoutes.CUSTOMER_DETAILS_SCREEN,
            arguments = listOf(
                navArgument("customerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val customerName = backStackEntry.arguments?.getString("customerName") ?: ""
            CustomerDetailsScreen(
                customerName = customerName,
                onBackPress = { 
                    navController.popBackStack() 
                },
                onNavigateToAnalysis = { customerName: String, analysisResult: String, hasCallRecording: Boolean ->
                    // Store analysis data temporarily
                    AnalysisDataHolder.setAnalysisData(customerName, analysisResult, hasCallRecording)
                    
                    // Navigate to analysis results screen with single top
                    navController.navigate(NavRoutes.analysisResultsRoute(customerName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable(
            route = NavRoutes.ANALYSIS_RESULTS_SCREEN,
            arguments = listOf(
                navArgument("customerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val routeCustomerName = backStackEntry.arguments?.getString("customerName") ?: ""
            
            // Get analysis data from temporary storage
            val analysisData = AnalysisDataHolder.getAnalysisData()
            
            if (analysisData != null) {
                AnalysisResultScreen(
                    customerName = routeCustomerName,
                    analysisResult = analysisData.result,
                    hasCallRecording = analysisData.hasCallRecording,
                    onBackPress = { 
                        // Clear analysis data
                        AnalysisDataHolder.clearAnalysisData()
                        
                        // Use the simplest possible back navigation
                        navController.popBackStack()
                    }
                )
            } else {
                // Fallback if no analysis data is found
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}

/**
 * Simple user data class for authentication
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = ""
)