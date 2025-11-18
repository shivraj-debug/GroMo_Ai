package com.example.demo.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.demo.R

/**
 * Interface for terms acceptance callback
 */
interface TermsAcceptanceListener {
    fun onTermsAccepted()
    fun onTermsDeclined()
}

/**
 * TermsAndConditionsPopup - Shows terms and conditions before displaying the main GM mentor popup
 */
class TermsAndConditionsPopup(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var isShowing = false
    
    // Terms acceptance listener
    private var termsAcceptanceListener: TermsAcceptanceListener? = null
    
    // Initialize
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    /**
     * Set terms acceptance listener
     */
    fun setTermsAcceptanceListener(listener: TermsAcceptanceListener) {
        this.termsAcceptanceListener = listener
    }
    
    /**
     * Show the terms and conditions popup
     * @param width Width of the popup (typically screen width)
     * @param height Height of the popup
     * @param yOffset Vertical offset from bottom (typically navigation bar height)
     */
    fun show(width: Int, height: Int, yOffset: Int) {
        if (isShowing) {
            // Update dimensions if already showing
            updateSize(width, height, yOffset)
            return
        }

        // Create the popup layout
        val popupLayout = createPopupLayout()

        // Setup the window parameters for popup
        val windowParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position at the bottom of the screen but ABOVE the navigation bar
        windowParams.gravity = Gravity.BOTTOM
        windowParams.y = yOffset  // This offset is the navigation bar height

        try {
            windowManager?.addView(popupLayout, windowParams)
            popupView = popupLayout
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Update the size of the popup when it's already visible
     * @param width New width for the popup
     * @param height New height for the popup
     * @param yOffset New vertical offset from bottom
     */
    fun updateSize(width: Int, height: Int, yOffset: Int) {
        if (!isShowing || popupView == null) return
        
        try {
            val params = popupView!!.layoutParams as WindowManager.LayoutParams
            
            // Update dimensions
            params.width = width
            params.height = height
            params.y = yOffset
            
            // Apply the updates
            windowManager?.updateViewLayout(popupView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Hide the popup
     */
    fun hide() {
        if (!isShowing) return
        
        try {
            windowManager?.removeView(popupView)
            popupView = null
            isShowing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Check if the popup is currently visible
     */
    fun isVisible(): Boolean {
        return isShowing
    }
    
    /**
     * Create the popup layout with all UI elements
     */
    private fun createPopupLayout(): View {
        // Create the main container
        val popupLayout = LinearLayout(context)
        popupLayout.orientation = LinearLayout.VERTICAL
        popupLayout.setBackgroundColor(0xFFF5F5F5.toInt()) // Light gray background

        // Add a top bar with title
        addTopBar(popupLayout)
        
        // Add terms and conditions content
        addTermsContent(popupLayout)
        
        // Add buttons at the bottom
        addActionButtons(popupLayout)

        return popupLayout
    }

    /**
     * Add a top bar with title
     */
    private fun addTopBar(parent: LinearLayout) {
        // Create top bar
        val topBar = LinearLayout(context)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setBackgroundColor(0xFF3F51B5.toInt()) // Material Blue
        topBar.setPadding(16, 16, 16, 16)

        // Add title text to the top bar
        val titleText = TextView(context)
        titleText.text = "GroMo AI Mentor - Terms & Conditions"
        titleText.textSize = 18f
        titleText.setTextColor(Color.WHITE)
        titleText.gravity = Gravity.CENTER
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)

        // Add the title centered
        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        titleParams.gravity = Gravity.CENTER
        topBar.addView(titleText, titleParams)

        // Add the top bar to the parent
        parent.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
    
    /**
     * Add terms and conditions content
     */
    private fun addTermsContent(parent: LinearLayout) {
        // Create scroll view for terms
        val scrollView = ScrollView(context)
        
        // Create content container
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(24, 24, 24, 24)
        
        // Add welcome text
        val welcomeText = TextView(context)
        welcomeText.text = "Welcome to GroMo AI Mentor!"
        welcomeText.textSize = 20f
        welcomeText.setTextColor(Color.BLACK)
        welcomeText.setTypeface(welcomeText.typeface, Typeface.BOLD)
        welcomeText.setPadding(0, 0, 0, 16)
        contentLayout.addView(welcomeText)
        
        // Add terms description
        val termsText = TextView(context)
        termsText.text = """
            By using GroMo AI Mentor, you agree to:

            1. Privacy: Your chat messages are processed to provide AI suggestions. Data is stored  to improve service quality.

            2. Data Usage: We collect anonymous usage data to enhance our service.

            3. Accuracy: Please review AI suggestions before sending as they may occasionally be incorrect.

            4. Responsibility: You are responsible for all messages sent using AI suggestions.

            Click 'Accept' to continue using GroMo AI Mentor.
        """.trimIndent()
        termsText.textSize = 16f
        termsText.setTextColor(Color.DKGRAY)
        termsText.setPadding(0, 8, 0, 16)
        contentLayout.addView(termsText)
        
        // Add content to scroll view
        scrollView.addView(contentLayout)
        
        // Add scroll view to parent with weight to take up available space
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f // Take remaining space
        )
        parent.addView(scrollView, params)
    }
    
    /**
     * Add action buttons (Accept/Decline)
     */
    private fun addActionButtons(parent: LinearLayout) {
        // Create button container
        val buttonLayout = LinearLayout(context)
        buttonLayout.orientation = LinearLayout.HORIZONTAL
        buttonLayout.gravity = Gravity.END
        buttonLayout.setBackgroundColor(0xFFEEEEEE.toInt()) // Light gray background
        buttonLayout.setPadding(16, 16, 16, 16)
        
        // Decline button
        val declineButton = Button(context)
        declineButton.text = "Decline"
        declineButton.setBackgroundColor(0xFFCCCCCC.toInt()) // Gray
        declineButton.setTextColor(Color.BLACK)
        declineButton.setPadding(24, 12, 24, 12)
        
        // Set click listener
        declineButton.setOnClickListener {
            hide()
            termsAcceptanceListener?.onTermsDeclined()
        }
        
        // Accept button
        val acceptButton = Button(context)
        acceptButton.text = "Accept"
        acceptButton.setBackgroundColor(0xFF3F51B5.toInt()) // Blue
        acceptButton.setTextColor(Color.WHITE)
        acceptButton.setPadding(24, 12, 24, 12)
        
        // Set click listener
        acceptButton.setOnClickListener {
            hide()
            termsAcceptanceListener?.onTermsAccepted()
        }
        
        // Add buttons to layout
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(8, 0, 8, 0)
        
        buttonLayout.addView(declineButton, buttonParams)
        buttonLayout.addView(acceptButton, buttonParams)
        
        // Add button layout to parent
        parent.addView(buttonLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
    
    /**
     * Show an error message in a similar popup
     * @param errorMessage The error message to display
     * @param width Width of the popup
     * @param height Height of the popup
     * @param yOffset Vertical offset from bottom
     */
    fun showErrorMessage(errorMessage: String, width: Int, height: Int, yOffset: Int) {
        if (isShowing) {
            hide()
        }

        // Create the popup layout
        val popupLayout = createErrorPopupLayout(errorMessage)

        // Setup the window parameters for popup
        val windowParams = WindowManager.LayoutParams(
            width,
            height / 2, // Make it half the size of the normal popup
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position at the bottom of the screen but ABOVE the navigation bar
        windowParams.gravity = Gravity.BOTTOM
        windowParams.y = yOffset  // This offset is the navigation bar height

        try {
            windowManager?.addView(popupLayout, windowParams)
            popupView = popupLayout
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Create an error popup layout
     */
    private fun createErrorPopupLayout(errorMessage: String): View {
        // Create the main container
        val popupLayout = LinearLayout(context)
        popupLayout.orientation = LinearLayout.VERTICAL
        popupLayout.setBackgroundColor(0xFFF5F5F5.toInt()) // Light gray background

        // Add a top bar with title
        val topBar = LinearLayout(context)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setBackgroundColor(0xFFD32F2F.toInt()) // Material Red
        topBar.setPadding(16, 16, 16, 16)

        // Add title text to the top bar
        val titleText = TextView(context)
        titleText.text = "Authentication Error"
        titleText.textSize = 18f
        titleText.setTextColor(Color.WHITE)
        titleText.gravity = Gravity.CENTER
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)

        // Add the title centered
        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        titleParams.gravity = Gravity.CENTER
        topBar.addView(titleText, titleParams)

        // Add the top bar to the parent
        popupLayout.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Error message container
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(24, 32, 24, 32)
        contentLayout.gravity = Gravity.CENTER
        
        // Error message text
        val errorText = TextView(context)
        errorText.text = errorMessage
        errorText.textSize = 16f
        errorText.setTextColor(Color.BLACK)
        errorText.gravity = Gravity.CENTER
        contentLayout.addView(errorText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add the content layout to parent
        popupLayout.addView(contentLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f // Take remaining space
        ))
        
        // Button container
        val buttonLayout = LinearLayout(context)
        buttonLayout.orientation = LinearLayout.HORIZONTAL
        buttonLayout.gravity = Gravity.CENTER
        buttonLayout.setBackgroundColor(0xFFEEEEEE.toInt())
        buttonLayout.setPadding(16, 16, 16, 16)
        
        // OK button
        val okButton = Button(context)
        okButton.text = "OK"
        okButton.setBackgroundColor(0xFF3F51B5.toInt()) // Blue
        okButton.setTextColor(Color.WHITE)
        okButton.setPadding(36, 12, 36, 12)
        
        // Set click listener
        okButton.setOnClickListener {
            hide()
        }
        
        // Add button to layout
        buttonLayout.addView(okButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add button layout to parent
        popupLayout.addView(buttonLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        return popupLayout
    }
} 