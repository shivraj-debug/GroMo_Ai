package com.example.demo.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.api.ApiResponse
import com.example.demo.api.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a customer who has accepted terms
 */
data class Customer(
    val chatName: String,
    val acceptedAt: String,
    val lastUpdated: String
)

/**
 * Represents the state of the customer list
 */
sealed class CustomerListState {
    object Loading : CustomerListState()
    data class Success(val customers: List<Customer>) : CustomerListState()
    data class Error(val message: String) : CustomerListState()
    object Empty : CustomerListState()
}

/**
 * Represents the state of adding a new customer
 */
sealed class AddCustomerState {
    object Idle : AddCustomerState()
    object Loading : AddCustomerState()
    object Success : AddCustomerState()
    data class Error(val message: String) : AddCustomerState()
}

/**
 * ViewModel for managing the list of customers who accepted terms
 */
class CustomerViewModel(private val authRepository: AuthRepository) : ViewModel() {
    
    private val _customerListState = MutableStateFlow<CustomerListState>(CustomerListState.Empty)
    val customerListState: StateFlow<CustomerListState> = _customerListState
    
    private val _addCustomerState = MutableStateFlow<AddCustomerState>(AddCustomerState.Idle)
    val addCustomerState: StateFlow<AddCustomerState> = _addCustomerState
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    
    /**
     * Load customers who have accepted terms
     */
    fun loadAcceptedCustomers() {
        viewModelScope.launch {
            _customerListState.value = CustomerListState.Loading
            
            try {
                when (val response = authRepository.getAcceptedChats()) {
                    is ApiResponse.Success -> {
                        val customers = response.data.map { chat ->
                            Customer(
                                chatName = chat.chatName,
                                acceptedAt = formatDate(chat.acceptedAt),
                                lastUpdated = formatDate(chat.lastUpdated)
                            )
                        }
                        
                        // Sort customers by lastUpdated (most recent first)
                        val sortedCustomers = customers.sortedByDescending { customer ->
                            try {
                                dateFormat.parse(customer.lastUpdated)?.time ?: 0
                            } catch (e: Exception) {
                                0L
                            }
                        }
                        
                        if (sortedCustomers.isEmpty()) {
                            _customerListState.value = CustomerListState.Empty
                        } else {
                            _customerListState.value = CustomerListState.Success(sortedCustomers)
                        }
                    }
                    is ApiResponse.Error -> {
                        _customerListState.value = CustomerListState.Error(response.message)
                    }
                    is ApiResponse.Loading -> {
                        _customerListState.value = CustomerListState.Loading
                    }
                }
            } catch (e: Exception) {
                _customerListState.value = CustomerListState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Add a new customer manually
     */
    fun addCustomer(chatName: String) {
        viewModelScope.launch {
            _addCustomerState.value = AddCustomerState.Loading
            
            try {
                when (val response = authRepository.storeChatAcceptance(chatName)) {
                    is ApiResponse.Success -> {
                        _addCustomerState.value = AddCustomerState.Success
                        // Reload the customer list
                        loadAcceptedCustomers()
                    }
                    is ApiResponse.Error -> {
                        _addCustomerState.value = AddCustomerState.Error(response.message)
                    }
                    is ApiResponse.Loading -> {
                        _addCustomerState.value = AddCustomerState.Loading
                    }
                }
            } catch (e: Exception) {
                _addCustomerState.value = AddCustomerState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Reset the add customer state
     */
    fun resetAddCustomerState() {
        _addCustomerState.value = AddCustomerState.Idle
    }
    
    /**
     * Format date string to readable string
     */
    private fun formatDate(dateString: String): String {
        return try {
            val date = apiDateFormat.parse(dateString)
            date?.let { dateFormat.format(it) } ?: "N/A"
        } catch (e: Exception) {
            // If parsing fails, just return the original string
            dateString
        }
    }
    
    /**
     * Refresh customer data
     */
    fun refreshCustomers() {
        loadAcceptedCustomers()
    }
} 