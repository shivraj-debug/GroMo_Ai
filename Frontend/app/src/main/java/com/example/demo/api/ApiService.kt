package com.example.demo.api

// Remove the import for ApiResponse since we're defining it in this file
// import com.example.demo.api.models.ApiResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.demo.config.ApiConfig

/**
 * API endpoints for authentication
 */
interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @GET("users/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): Response<UserResponse>
    
    // Chat acceptance endpoints
    @GET("chats/accept/{chatName}")
    suspend fun checkChatAcceptance(
        @Header("Authorization") token: String,
        @Path("chatName") chatName: String
    ): Response<ChatAcceptanceResponse>
    
    @POST("chats/accept")
    suspend fun storeChatAcceptance(
        @Header("Authorization") token: String,
        @Body request: ChatAcceptanceRequest
    ): Response<ChatAcceptanceResponse>
    
    // Chat messages endpoints
    @POST("chats/messages")
    suspend fun storeChatMessages(
        @Header("Authorization") token: String,
        @Body request: ChatMessagesRequest
    ): Response<ChatMessagesResponse>
    
    @GET("chats/messages/{chatName}")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("chatName") chatName: String,
        @Query("limit") limit: Int? = null
    ): Response<ChatMessagesListResponse>
    
    @GET("chats/accepted")
    suspend fun getAcceptedChats(
        @Header("Authorization") token: String
    ): Response<AcceptedChatsResponse>
    
    companion object {
        fun create(): ApiService {
            val logger = HttpLoggingInterceptor().apply { 
                level = HttpLoggingInterceptor.Level.BODY 
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()
                
            return Retrofit.Builder()
                .baseUrl(ApiConfig.BACKEND_API_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

/**
 * Generic class to represent API responses
 */
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String) : ApiResponse<Nothing>()
    object Loading : ApiResponse<Nothing>()
    
    fun isSuccessful(): Boolean = this is Success
}

/**
 * Data classes for requests and responses
 */
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String,
    val user: User,
    val message: String? = null
)

data class UserResponse(
    val success: Boolean,
    val user: User,
    val message: String? = null
)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: String? = null
)

/**
 * Request model for accepting terms for a chat
 */
data class ChatAcceptanceRequest(
    val chatName: String
)

/**
 * Response model for chat acceptance
 */
data class ChatAcceptanceResponse(
    val success: Boolean,
    val message: String? = null,
    val accepted: Boolean = false,
    val data: ChatAcceptanceData? = null
)

/**
 * Data contained in the response
 */
data class ChatAcceptanceData(
    val chatName: String,
    val acceptedAt: String
)

/**
 * Request model for storing chat messages
 */
data class ChatMessagesRequest(
    val chatName: String,
    val messages: List<ChatMessageDto>
)

/**
 * DTO for sending chat messages
 */
data class ChatMessageDto(
    val text: String,
    val timestamp: String,
    val timestampMillis: Long,
    val isIncoming: Boolean
)

/**
 * Response model for storing chat messages
 */
data class ChatMessagesResponse(
    val success: Boolean,
    val message: String? = null,
    val savedCount: Int = 0
)

/**
 * Response model for retrieving chat messages
 */
data class ChatMessagesListResponse(
    val success: Boolean,
    val message: String? = null,
    val count: Int = 0,
    val data: List<ChatMessageDto> = emptyList()
)

/**
 * Response model for getting accepted chats
 */
data class AcceptedChatsResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<AcceptedChatDto> = emptyList()
)

/**
 * Data class for an accepted chat
 */
data class AcceptedChatDto(
    val chatName: String,
    val acceptedAt: String,
    val lastUpdated: String,
    val messageCount: Int
) 