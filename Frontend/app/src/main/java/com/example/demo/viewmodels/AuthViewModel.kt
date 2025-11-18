package com.example.demo.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.api.AuthRepository
import com.example.demo.api.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)
    
    // Current authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.UNAUTHENTICATED)
    val authState: StateFlow<AuthState> = _authState
    
    // Current user
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user
    
    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    // Initialize the ViewModel
    init {
        // Check if user is already logged in
        if (repository.isLoggedIn()) {
            _user.value = repository.getCurrentUser()
            _authState.value = AuthState.AUTHENTICATED
        } else {
            _authState.value = AuthState.UNAUTHENTICATED
        }
    }
    
    /**
     * Register a new user
     */
    fun register(name: String, email: String, password: String) {
        _authState.value = AuthState.LOADING
        _errorMessage.value = null
        
        viewModelScope.launch {
            val result = repository.register(name, email, password)
            
            if (result.isSuccess) {
                _user.value = result.getOrNull()
                _authState.value = AuthState.AUTHENTICATED
            } else {
                val error = result.exceptionOrNull()?.message ?: "Registration failed"
                _errorMessage.value = error
                _authState.value = AuthState.ERROR
            }
        }
    }
    
    /**
     * Login an existing user
     */
    fun login(email: String, password: String) {
        _authState.value = AuthState.LOADING
        _errorMessage.value = null
        
        viewModelScope.launch {
            val result = repository.login(email, password)
            
            if (result.isSuccess) {
                _user.value = result.getOrNull()
                _authState.value = AuthState.AUTHENTICATED
            } else {
                val error = result.exceptionOrNull()?.message ?: "Login failed"
                _errorMessage.value = error
                _authState.value = AuthState.ERROR
            }
        }
    }
    
    /**
     * Fetch the user profile from the backend
     */
    fun fetchUserProfile() {
        viewModelScope.launch {
            val result = repository.getUserProfile()
            
            if (result.isSuccess) {
                _user.value = result.getOrNull()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to fetch profile"
                _errorMessage.value = error
            }
        }
    }
    
    /**
     * Logout the user
     */
    fun logout() {
        repository.logout()
        _user.value = null
        _authState.value = AuthState.UNAUTHENTICATED
        _errorMessage.value = null
    }
    
    /**
     * Reset error state
     */
    fun resetError() {
        if (_authState.value == AuthState.ERROR) {
            _authState.value = AuthState.UNAUTHENTICATED
            _errorMessage.value = null
        }
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

/**
 * Authentication states
 */
enum class AuthState {
    LOADING,
    AUTHENTICATED,
    UNAUTHENTICATED,
    ERROR
} 