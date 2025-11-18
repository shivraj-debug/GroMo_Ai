package com.example.demo.api

import android.content.Context
// ApiResponse is now defined directly in the api package
import com.example.demo.api.ApiResponse
import com.example.demo.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException
import java.io.IOException
// Import the ChatMessage class from the accessibility package
import com.example.demo.accessibility.ChatMessage

/**
 * Repository for handling authentication operations
 */
class AuthRepository(private val context: Context) {
    private val apiService = ApiService.create()
    private val sessionManager = SessionManager(context)
    
    /**
     * Register a new user
     */
    suspend fun register(name: String, email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val request = RegisterRequest(name, email, password)
                val response = apiService.register(request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        // Save authentication data
                        sessionManager.saveAuthToken(body.token)
                        sessionManager.saveUser(body.user)
                        sessionManager.setLoggedIn(true)
                        
                        Result.success(body.user)
                    } else {
                        Result.failure(Exception(body?.message ?: "Registration failed"))
                    }
                } else {
                    Result.failure(Exception("Registration failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(handleNetworkError(e))
            }
        }
    }
    
    /**
     * Login an existing user
     */
    suspend fun login(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(email, password)
                val response = apiService.login(request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        // Save authentication data
                        sessionManager.saveAuthToken(body.token)
                        sessionManager.saveUser(body.user)
                        sessionManager.setLoggedIn(true)
                        
                        Result.success(body.user)
                    } else {
                        Result.failure(Exception(body?.message ?: "Login failed"))
                    }
                } else {
                    Result.failure(Exception("Login failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(handleNetworkError(e))
            }
        }
    }
    
    /**
     * Get user profile from backend
     */
    suspend fun getUserProfile(): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Not authenticated"))
                }
                
                val response = apiService.getUserProfile("Bearer $token")
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        // Update stored user data
                        sessionManager.saveUser(body.user)
                        Result.success(body.user)
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to get profile"))
                    }
                } else {
                    Result.failure(Exception("Failed to get profile: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(handleNetworkError(e))
            }
        }
    }
    
    /**
     * Check if a chat has previously accepted terms
     */
    suspend fun checkChatAcceptance(chatName: String): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext ApiResponse.Error("User not authenticated")
                }
                
                val response = apiService.checkChatAcceptance("Bearer $token", chatName)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    return@withContext if (body.success) {
                        ApiResponse.Success(body.accepted)
                    } else {
                        ApiResponse.Error(body.message ?: "Unknown error")
                    }
                } else {
                    return@withContext ApiResponse.Error(response.message() ?: "Network error")
                }
            } catch (e: Exception) {
                return@withContext ApiResponse.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    /**
     * Store chat terms acceptance
     */
    suspend fun storeChatAcceptance(chatName: String): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext ApiResponse.Error("User not authenticated")
                }
                
                val request = ChatAcceptanceRequest(chatName)
                val response = apiService.storeChatAcceptance("Bearer $token", request)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    return@withContext if (body.success) {
                        ApiResponse.Success(true)
                    } else {
                        ApiResponse.Error(body.message ?: "Unknown error")
                    }
                } else {
                    return@withContext ApiResponse.Error(response.message() ?: "Network error")
                }
            } catch (e: Exception) {
                return@withContext ApiResponse.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    /**
     * Store chat messages in the backend database
     */
    suspend fun storeChatMessages(chatName: String, messages: List<ChatMessage>): ApiResponse<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext ApiResponse.Error("User not authenticated")
                }
                
                // Convert ChatMessage to ChatMessageDto
                val messageDtos = messages.map { message ->
                    ChatMessageDto(
                        text = message.text,
                        timestamp = message.timestamp,
                        timestampMillis = message.timestampMillis,
                        isIncoming = message.isIncoming
                    )
                }
                
                // Create request body
                val request = ChatMessagesRequest(chatName, messageDtos)
                
                // Make API call
                val response = apiService.storeChatMessages("Bearer $token", request)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    return@withContext if (body.success) {
                        ApiResponse.Success(body.savedCount)
                    } else {
                        ApiResponse.Error(body.message ?: "Unknown error")
                    }
                } else {
                    return@withContext ApiResponse.Error(response.message() ?: "Network error")
                }
            } catch (e: Exception) {
                return@withContext ApiResponse.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    /**
     * Get chat messages from the backend database
     */
    suspend fun getChatMessages(chatName: String, limit: Int? = null): ApiResponse<List<ChatMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext ApiResponse.Error("User not authenticated")
                }
                
                // Make API call
                val response = apiService.getChatMessages("Bearer $token", chatName, limit)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    return@withContext if (body.success) {
                        // Convert DTOs back to ChatMessage objects
                        val chatMessages = body.data.map { dto ->
                            ChatMessage(
                                text = dto.text,
                                timestamp = dto.timestamp,
                                timestampMillis = dto.timestampMillis,
                                isIncoming = dto.isIncoming,
                                contactName = chatName
                            )
                        }
                        ApiResponse.Success(chatMessages)
                    } else {
                        ApiResponse.Error(body.message ?: "Unknown error")
                    }
                } else {
                    return@withContext ApiResponse.Error(response.message() ?: "Network error")
                }
            } catch (e: Exception) {
                return@withContext ApiResponse.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    /**
     * Get all chats that have accepted terms
     */
    suspend fun getAcceptedChats(): ApiResponse<List<AcceptedChatDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext ApiResponse.Error("User not authenticated")
                }
                
                // Make API call
                val response = apiService.getAcceptedChats("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    return@withContext if (body.success) {
                        ApiResponse.Success(body.data)
                    } else {
                        ApiResponse.Error(body.message ?: "Unknown error")
                    }
                } else {
                    return@withContext ApiResponse.Error(response.message() ?: "Network error")
                }
            } catch (e: Exception) {
                return@withContext ApiResponse.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    /**
     * Handle different network errors and provide user-friendly messages
     */
    private fun handleNetworkError(error: Exception): Exception {
        return when (error) {
            is ConnectException -> Exception("Cannot connect to server. Please check if the server is running.")
            is SocketTimeoutException -> Exception("Connection timed out. Please try again.")
            is UnknownHostException -> Exception("Cannot reach server. Please check your internet connection.")
            is HttpException -> Exception("Server error: ${error.code()}")
            is IOException -> Exception("Network error: ${error.message}")
            else -> Exception("An unexpected error occurred: ${error.message}")
        }
    }
    
    /**
     * Check if the user is logged in
     */
    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }
    
    /**
     * Get the currently logged in user
     */
    fun getCurrentUser(): User? {
        return sessionManager.getUser()
    }
    
    /**
     * Logout the user
     */
    fun logout() {
        // Clear all session data
        sessionManager.clearSession()
        
        // Also clear token from TokenManager
        TokenManager.clearToken(context)
        
        // Ensure we clear any other potential cached data
        context.cacheDir.deleteRecursively()
    }
} 