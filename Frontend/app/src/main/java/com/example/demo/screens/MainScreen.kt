package com.example.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.demo.api.User
import com.example.demo.ui.theme.DemoTheme
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.example.demo.viewmodels.CustomerViewModel
import com.example.demo.viewmodels.Customer
import com.example.demo.viewmodels.CustomerListState
import com.example.demo.viewmodels.AddCustomerState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.demo.api.AuthRepository
import android.widget.Toast
import android.util.Log

/**
 * Navigation state holder to persist tab selection across navigation
 */
object MainScreenState {
    private var _selectedTabIndex = mutableStateOf(0)
    val selectedTabIndex: androidx.compose.runtime.State<Int> = _selectedTabIndex
    
    fun setSelectedTab(index: Int) {
        Log.d("MainScreenState", "Setting tab to: $index")
        _selectedTabIndex.value = index
    }
    
    fun getSelectedTab(): Int = _selectedTabIndex.value
}

/**
 * Main screen that shows application instructions with bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit = {},
    user: User? = null,
    onFetchProfile: () -> Unit = {},
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onNavigateToCustomerDetails: (String) -> Unit = {}
) {
    // Use persistent tab state
    val selectedTabIndex by MainScreenState.selectedTabIndex
    
    // Fetch user profile when screen loads
    LaunchedEffect(Unit) {
        onFetchProfile()
        Log.d("MainScreen", "MainScreen loaded with tab: $selectedTabIndex")
    }
    
    // Define tab items
    val items = listOf("Home", "Learning", "Customers", "Settings")
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Star,
        Icons.Filled.Person,
        Icons.Filled.Settings
    )
    
    // Show error message if any
    errorMessage?.let {
        ErrorAlert(
            message = it,
            onDismiss = onClearError
        )
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedTabIndex) {
                                0 -> "GroMo AI Mentor"
                                1 -> "Learning"
                                2 -> "Customers"
                                else -> "Settings"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },

                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E40AF), // Deep Blue
                            Color(0xFF3B82F6), // Blue
                            Color(0xFF60A5FA)  // Light Blue
                        )
                    )
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF0F7FF), // Light blue
                contentColor = Color(0xFF1F2937)
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                icons[index], 
                                contentDescription = item,
                                tint = if (selectedTabIndex == index) Color(0xFF1E40AF) else Color(0xFF9CA3AF)
                            ) 
                        },
                        label = { 
                            Text(
                                item,
                                color = if (selectedTabIndex == index) Color(0xFF1E40AF) else Color(0xFF9CA3AF)
                            ) 
                        },
                        selected = selectedTabIndex == index,
                        onClick = { 
                            Log.d("MainScreen", "Tab clicked: $index")
                            MainScreenState.setSelectedTab(index)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1E40AF),
                            selectedTextColor = Color(0xFF1E40AF),
                            unselectedIconColor = Color(0xFF9CA3AF),
                            unselectedTextColor = Color(0xFF9CA3AF),
                            indicatorColor = Color(0xFFDBEAFE)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFFF0F7FF), // Light blue // White background
        modifier = Modifier.systemBarsPadding()
    ) { innerPadding ->
        // Display the content for the selected tab
        when (selectedTabIndex) {
            0 -> HomeTabContent(
                modifier = Modifier.padding(innerPadding)
            )
            1 -> FeaturesTabContent(Modifier.padding(innerPadding))
            2 -> CustomersTabContent(
                modifier = Modifier.padding(innerPadding),
                onCustomerClick = { customerName ->
                    Log.d("MainScreen", "Customer clicked: $customerName, ensuring customers tab state")
                    // Ensure we remember we're on customers tab before navigating
                    MainScreenState.setSelectedTab(2)
                    onNavigateToCustomerDetails(customerName)
                }
            )
            3 -> SettingsTabContent(user, onLogout, Modifier.padding(innerPadding))
        }
    }
}

/**
 * Home tab content with improved UI and features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // Main Features Section
        Text(
            text = "Key Features",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E40AF),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Feature 1: GroMo AI Copilot
        EnhancedFeatureCard(
            icon = Icons.Default.Chat,
            title = "GroMo AI Copilot",
            description = "AI assistance directly in WhatsApp chat with smart suggestions and auto-replies.",
            gradientColors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6)),
            showButton = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Feature 2: Performance Analysis
        EnhancedFeatureCard(
            icon = Icons.Default.Analytics,
            title = "Performance Analysis",
            description = "Track conversation metrics and response times.",
            gradientColors = listOf(Color(0xFF0F766E), Color(0xFF14B8A6))
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Feature 3: Improvement Suggestions
        EnhancedFeatureCard(
            icon = Icons.Default.TrendingUp,
            title = "Improvement Suggestions",
            description = "Get personalized recommendations to enhance messaging.",
            gradientColors = listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA))
        )
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Enhanced feature card component with gradients and better styling
 */
@Composable
fun EnhancedFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    gradientColors: List<Color>,
    showButton: Boolean = false
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F7FF) // Light blue
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                // Icon with gradient background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.linearGradient(gradientColors),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280),
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }
            
            // Show button if needed
            if (showButton) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { 
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(gradientColors),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enable Now",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feature card component (kept for backward compatibility)
 */
@Composable
fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    iconColor: androidx.compose.ui.graphics.Color,
    showButton: Boolean = false
) {
    // Delegate to enhanced version
    EnhancedFeatureCard(
        icon = icon,
        title = title,
        description = description,
        gradientColors = listOf(iconColor, iconColor.copy(alpha = 0.8f)),
        showButton = showButton
    )
}

/**
 * Learning tab content
 */
@Composable
fun FeaturesTabContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Learning Features",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF1F2937),
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Coming Soon!",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF6B7280)
        )
    }
}

/**
 * Customers tab content - ADD CLIENT FEATURE RESTORED
 */
@Composable
fun CustomersTabContent(
    modifier: Modifier = Modifier,
    onCustomerClick: (String) -> Unit = {}
) {
    // Create repository and factory for CustomerViewModel
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val customerViewModel: CustomerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CustomerViewModel(authRepository) as T
            }
        }
    )
    val customerListState by customerViewModel.customerListState.collectAsState()
    val addCustomerState by customerViewModel.addCustomerState.collectAsState()
    
    // State for showing add customer dialog
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    
    // Load customers when screen is first composed
    LaunchedEffect(Unit) {
        customerViewModel.loadAcceptedCustomers()
    }
    
    // Show add customer result message
    when (addCustomerState) {
        is AddCustomerState.Success -> {
            LaunchedEffect(addCustomerState) {
                Toast.makeText(context, "Customer added successfully", Toast.LENGTH_SHORT).show()
                customerViewModel.resetAddCustomerState()
            }
        }
        is AddCustomerState.Error -> {
            val errorMessage = (addCustomerState as AddCustomerState.Error).message
            LaunchedEffect(addCustomerState) {
                Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                customerViewModel.resetAddCustomerState()
            }
        }
        else -> { /* do nothing */ }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content based on state
            when (customerListState) {
                is CustomerListState.Loading -> {
                    // Loading UI
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Loading customers...",
                            color = Color(0xFF1F2937),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                is CustomerListState.Success -> {
                    val customers = (customerListState as CustomerListState.Success).customers
                    
                    // Display customer list
                    Text(
                        text = "${customers.size} Customers",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Start,
                        color = Color(0xFF1E40AF),
                        fontWeight = FontWeight.Bold
                    )
                    
                    // List of customers
                    customers.forEach { customer ->
                        EnhancedCustomerCard(customer = customer, onCustomerClick = onCustomerClick)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (customers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9CA3AF)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No customers found",
                                    color = Color(0xFF6B7280),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
                is CustomerListState.Error -> {
                    // Error UI
                    val errorMessage = (customerListState as CustomerListState.Error).message
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error: $errorMessage",
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { customerViewModel.refreshCustomers() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E40AF)
                                )
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
                is CustomerListState.Empty -> {
                    // Empty UI
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No customers have accepted terms yet",
                                color = Color(0xFF6B7280),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { customerViewModel.refreshCustomers() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E40AF)
                                )
                            ) {
                                Text("Refresh", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        
        // Add customer FAB - RESTORED with gradient
        FloatingActionButton(
            onClick = { showAddCustomerDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Transparent,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Customer",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    
    // Add Customer Dialog - RESTORED with light theme
    if (showAddCustomerDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddCustomerDialog = false
                newCustomerName = ""
            },
            title = { 
                Text(
                    "Add New Customer",
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column {
                    Text(
                        "Enter the WhatsApp name of the customer to manually add them to the database.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = Color(0xFF6B7280)
                    )
                    
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("WhatsApp Name", color = Color(0xFF1E40AF)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedBorderColor = Color(0xFF1E40AF),
                            unfocusedBorderColor = Color(0xFF9CA3AF)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomerName.isNotBlank()) {
                            customerViewModel.addCustomer(newCustomerName.trim())
                            showAddCustomerDialog = false
                            newCustomerName = ""
                        }
                    },
                    enabled = newCustomerName.isNotBlank() && addCustomerState !is AddCustomerState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E40AF)
                    )
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showAddCustomerDialog = false
                        newCustomerName = ""
                    }
                ) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            },
            containerColor = Color.White,
            textContentColor = Color(0xFF1F2937)
        )
    }
}

/**
 * Enhanced customer card component
 */
@Composable
fun EnhancedCustomerCard(
    customer: Customer,
    onCustomerClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCustomerClick(customer.chatName) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F7FF) // Light blue
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Customer icon/avatar with gradient
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Customer details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.chatName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF1E40AF)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Start Date: ${customer.acceptedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF10B981)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Last chat: ${customer.lastUpdated}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

/**
 * Customer card component (kept for backward compatibility)
 */
@Composable
fun CustomerCard(
    customer: Customer,
    onCustomerClick: (String) -> Unit = {}
) {
    // Delegate to enhanced version
    EnhancedCustomerCard(customer = customer, onCustomerClick = onCustomerClick)
}

/**
 * Settings tab content
 */
@Composable
fun SettingsTabContent(
    user: User?,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // User Profile Card with gradient
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0F7FF) // Light blue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Icon with gradient background
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF1E40AF), Color(0xFF60A5FA))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(50.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // User Name
                Text(
                    text = user?.name ?: "Loading...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
                
                // User Email
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF6B7280)
                )
                
                // Account Created Date
                user?.createdAt?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Member since: ${formatDate(it)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9CA3AF)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color(0xFFE5E7EB))
                Spacer(modifier = Modifier.height(20.dp))
                
                // User ID section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Account ID",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )
                    Text(
                        text = user?.id?.take(8)?.plus("...") ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Logout button with gradient
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFF87171))
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Logout",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Format a date string from ISO format to a readable date
 */
private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date!!)
    } catch (e: Exception) {
        dateString
    }
}

@Composable
fun ErrorAlert(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Error",
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.Bold
            ) 
        },
        text = { 
            Text(
                message,
                color = Color(0xFF6B7280)
            ) 
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "OK",
                    color = Color(0xFF1E40AF)
                )
            }
        },
        containerColor = Color.White,
        titleContentColor = Color(0xFF1F2937),
        textContentColor = Color(0xFF6B7280)
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    DemoTheme {
        MainScreen()
    }
}