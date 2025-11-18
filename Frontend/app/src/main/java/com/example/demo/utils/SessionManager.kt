package com.example.demo.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.demo.api.User

/**
 * SessionManager handles storing and retrieving authentication data
 */
class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private var editor: SharedPreferences.Editor = prefs.edit()

    companion object {
        const val PREF_NAME = "gromo_copilot_prefs"
        const val USER_TOKEN = "user_token"
        const val USER_ID = "user_id"
        const val USER_NAME = "user_name"
        const val USER_EMAIL = "user_email"
        const val IS_LOGGED_IN = "is_logged_in"
    }

    /**
     * Save auth token and user info
     */
    fun saveAuthToken(token: String) {
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    /**
     * Get saved auth token
     */
    fun getToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    /**
     * Save user info
     */
    fun saveUser(user: User) {
        editor.putString(USER_ID, user.id)
        editor.putString(USER_NAME, user.name)
        editor.putString(USER_EMAIL, user.email)
        editor.apply()
    }

    /**
     * Get saved user data
     */
    fun getUser(): User? {
        val id = prefs.getString(USER_ID, null) ?: return null
        val name = prefs.getString(USER_NAME, "")!!
        val email = prefs.getString(USER_EMAIL, "")!!
        
        return User(id, name, email)
    }

    /**
     * Set logged in status
     */
    fun setLoggedIn(isLoggedIn: Boolean) {
        editor.putBoolean(IS_LOGGED_IN, isLoggedIn)
        editor.apply()
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGGED_IN, false)
    }

    /**
     * Clear all saved data when user logs out
     */
    fun clearSession() {
        editor.clear()
        editor.apply()
    }
} 