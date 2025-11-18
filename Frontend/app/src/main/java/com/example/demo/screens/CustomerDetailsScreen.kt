package com.example.demo.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.example.demo.api.AuthRepository
import com.example.demo.accessibility.ChatMessage
import com.example.demo.api.ApiResponse
import com.example.demo.viewmodels.CustomerViewModel
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import com.example.demo.accessibility.GeminiApiService
import com.example.demo.accessibility.ChatAnalysisListener
import com.example.demo.config.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailsScreen(
    customerName: String,
    onBackPress: () -> Unit,
    onNavigateToAnalysis: (customerName: String, analysisResult: String, hasCallRecording: Boolean) -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val geminiService = remember { GeminiApiService(ApiConfig.GEMINI_API_KEY, context) }
    val scope = rememberCoroutineScope()
    
    // Debug logging when screen loads
    LaunchedEffect(Unit) {
        Log.d("CustomerDetailsScreen", "Customer details screen loaded for: $customerName")
    }
    
    // Helper function to get filename from URI
    fun getFileName(uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex) ?: "audio_file.mp3"
            } else "audio_file.mp3"
        } ?: "audio_file.mp3"
    }
    
    // State for chat messages
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var showMessagesDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for call recording upload
    var isUploadingRecording by remember { mutableStateOf(false) }
    var selectedRecordingUri by remember { mutableStateOf<Uri?>(null) }
    var recordingUploadStatus by remember { mutableStateOf<String?>(null) }
    
    // State for performance analysis
    var isAnalyzing by remember { mutableStateOf(false) }
    
    // File picker launcher for call recordings
    val recordingPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedRecordingUri = uri
        uri?.let {
            isUploadingRecording = true
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(it)
                    Log.d("CustomerDetails", "Selected file - URI: $it, MIME: $mimeType")
                    
                    if (mimeType?.startsWith("audio/") == true) {
                        val fileName = getFileName(it)
                        recordingUploadStatus = "Audio file ready: $fileName"
                        Toast.makeText(context, "✅ Audio file ready for analysis!", Toast.LENGTH_SHORT).show()
                        Log.d("CustomerDetails", "Audio file validated successfully: $fileName")
                    } else {
                        recordingUploadStatus = "Please select a valid audio file (MP3, WAV, etc.)"
                        Toast.makeText(context, "❌ Please select a valid audio file", Toast.LENGTH_SHORT).show()
                        selectedRecordingUri = null
                        Log.w("CustomerDetails", "Invalid file type selected: $mimeType")
                    }
                } catch (e: Exception) {
                    recordingUploadStatus = "Failed to process recording: ${e.message}"
                    Toast.makeText(context, "❌ Failed to process file: ${e.message}", Toast.LENGTH_LONG).show()
                    selectedRecordingUri = null
                    Log.e("CustomerDetails", "Error processing selected file", e)
                } finally {
                    isUploadingRecording = false
                }
            }
        }
    }
    
    // Function to load messages
    fun loadMessages() {
        isLoadingMessages = true
        scope.launch {
            when (val response = authRepository.getChatMessages(customerName, null)) {
                is ApiResponse.Success -> {
                    messages = response.data
                    showMessagesDialog = true
                    isLoadingMessages = false
                    Log.d("CustomerDetails", "Loaded ${messages.size} chat messages")
                }
                is ApiResponse.Error -> {
                    errorMessage = response.message
                    Toast.makeText(context, "Error loading messages: ${response.message}", Toast.LENGTH_LONG).show()
                    isLoadingMessages = false
                    Log.e("CustomerDetails", "Failed to load messages: ${response.message}")
                }
                else -> { /* keep loading state */ }
            }
        }
    }
    
    // Function to analyze performance
    fun analyzePerformance() {
        if (messages.isEmpty()) {
            Toast.makeText(context, "Please load chat history first", Toast.LENGTH_SHORT).show()
            return
        }
        
        isAnalyzing = true
        Log.d("CustomerDetails", "Analyze button clicked")
        Log.d("CustomerDetails", "Starting analysis with recording URI: $selectedRecordingUri")
        Log.d("CustomerDetails", "Chat messages count: ${messages.size}")
        
        geminiService.analyzeCustomerInteraction(
            customerName = customerName,
            messages = messages,
            callRecordingUri = selectedRecordingUri,
            listener = object : ChatAnalysisListener {
                override fun onAnalysisReceived(analysis: String) {
                    Log.d("CustomerDetails", "Analysis received successfully")
                    isAnalyzing = false
                    Toast.makeText(context, "✅ Analysis completed!", Toast.LENGTH_SHORT).show()
                    
                    // Navigate to analysis results screen
                    onNavigateToAnalysis(customerName, analysis, selectedRecordingUri != null)
                }

                override fun onError(error: String) {
                    Log.e("CustomerDetails", "Analysis failed: $error")
                    Toast.makeText(context, "❌ Analysis failed: $error", Toast.LENGTH_LONG).show()
                    isAnalyzing = false
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Customer Details",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        Log.d("CustomerDetailsScreen", "Back button clicked for customer: $customerName")
                        onBackPress()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E40AF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F7FF))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Customer profile header
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEBF8FF)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = "WhatsApp Contact",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
                
            // Chat History Button
            ActionCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Chat History",
                description = "View WhatsApp conversation history with this customer",
                gradientColors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6)),
                isLoading = isLoadingMessages,
                onClick = { loadMessages() }
            )
                
            Spacer(modifier = Modifier.height(6.dp))
                
            // Upload Call Recording Button
            ActionCard(
                icon = Icons.Default.FileUpload,
                title = "Call Recording",
                description = when {
                    selectedRecordingUri != null -> "✅ Audio file ready - ${getFileName(selectedRecordingUri!!)}"
                    isUploadingRecording -> "Processing audio file..."
                    else -> "Upload MP3/audio file for comprehensive analysis"
                },
                gradientColors = if (selectedRecordingUri != null)
                    listOf(Color(0xFF059669), Color(0xFF10B981))
                else
                    listOf(Color(0xFF0F766E), Color(0xFF14B8A6)),
                isLoading = isUploadingRecording,
                onClick = { 
                    Log.d("CustomerDetails", "Launching file picker for audio files")
                    recordingPickerLauncher.launch("audio/*") 
                }
            )
            
            // Show upload status if available
            recordingUploadStatus?.let { status ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedRecordingUri != null) Color(0xFF059669) else Color(0xFFDC2626),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
                
            Spacer(modifier = Modifier.height(16.dp))
            
            // Analysis Heading
            Text(
                text = "Analyze Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
                
            // Analyze button with enhanced status
            Button(
                onClick = { 
                    Log.d("CustomerDetails", "Analyze button clicked")
                    analyzePerformance() 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnalyzing && messages.isNotEmpty(),
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
                                if (messages.isNotEmpty())
                                    listOf(Color(0xFF7C3AED), Color(0xFF9333EA))
                                else
                                    listOf(Color(0xFF6B7280), Color(0xFF9CA3AF))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAnalyzing) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedRecordingUri != null) "Analyzing Chat + Audio..." else "Analyzing Chat...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = when {
                                messages.isEmpty() -> "Load Chat History First"
                                selectedRecordingUri != null -> "Analyze Chat + Call Recording"
                                else -> "Analyze Chat Only"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Chat Messages Dialog
        if (showMessagesDialog) {
            MessagesDialog(
                customerName = customerName,
                messages = messages,
                onDismiss = { showMessagesDialog = false }
            )
        }
    }
}

@Composable
fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    gradientColors: List<Color>,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                
                Spacer(modifier = Modifier.width(14.dp))
                
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
                        color = Color(0xFF6B7280)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = when (icon) {
                                Icons.AutoMirrored.Filled.Chat -> "View/Load Messages"
                                Icons.Default.FileUpload -> "Upload Audio File"
                                Icons.Default.Assessment -> "Analyze Now"
                                else -> "Action"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesDialog(
    customerName: String,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = Color(0xFF1E40AF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chat History - $customerName",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No chat history available",
                            color = Color(0xFF6B7280)
                        )
                    }
                } else {
                    messages.forEachIndexed { index, message ->
                        MessageItemCard(message = message)
                        if (index < messages.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = Color(0xFF1E40AF)
                )
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun MessageItemCard(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isIncoming) 
                Color(0xFFF3F4F6)
            else 
                Color(0xFFDBEAFE)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (message.isIncoming) "From: ${message.contactName}" else "You",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
                
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151)
            )
        }
    }
}