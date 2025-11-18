package com.example.demo.accessibility

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityUtil {
    /**
     * Check if the WhatsApp Accessibility Service is enabled
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val expectedServiceName = "${context.packageName}/.accessibility.WhatsAppAccessibilityService"
        return enabledServices.split(':').any { it.contains(expectedServiceName) }
    }
} 