package com.example.demo.api

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages authentication tokens for API calls
 */
object TokenManager {
    private const val PREF_NAME = "AuthPreferences"
    private const val KEY_AUTH_TOKEN = "auth_token"
    
    /**
     * Store authentication token
     * 
     * @param context Application context
     * @param token JWT token to store
     */
    fun saveToken(context: Context, token: String) {
        getSharedPreferences(context)
            .edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }
    
    /**
     * Retrieve the stored authentication token
     * 
     * @param context Application context
     * @return JWT token string or null if not found
     */
    fun getToken(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_AUTH_TOKEN, null)
    }
    
    /**
     * Clear the stored authentication token (for logout)
     * 
     * @param context Application context
     */
    fun clearToken(context: Context) {
        getSharedPreferences(context)
            .edit()
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }
    
    /**
     * Check if a token exists
     * 
     * @param context Application context
     * @return true if token exists, false otherwise
     */
    fun hasToken(context: Context): Boolean {
        return getToken(context) != null
    }
    
    /**
     * Get shared preferences instance
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
} 