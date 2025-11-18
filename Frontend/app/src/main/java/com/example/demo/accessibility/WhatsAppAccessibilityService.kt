package com.example.demo.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.graphics.Typeface
import com.example.demo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.demo.api.ApiService
import com.example.demo.api.AuthRepository
import com.example.demo.api.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.demo.config.ApiConfig

// Enum to track chat state
enum class ChatState {
    UNKNOWN,             // Initial state, haven't checked terms
    TERMS_CHECKING,      // Currently checking if terms are accepted
    TERMS_NOT_ACCEPTED,  // Terms not accepted yet
    TERMS_ACCEPTED       // Terms accepted, features enabled
}

// Data class to store chat message information
data class ChatMessage(
    val text: String,           // Message content
    val timestamp: String,      // Formatted message timestamp
    val timestampMillis: Long,  // Raw timestamp in milliseconds
    val isIncoming: Boolean,    // Whether message is incoming or outgoing
    val contactName: String     // The name of the contact/group in the chat
)

// Interface to provide chat history to the popup
interface ChatHistoryProvider {
    fun getCurrentChatHistory(): List<ChatMessage>
    fun getCurrentChatName(): String
}

// New data class for debugging message detection
data class MessageDebugInfo(
    val text: String,
    var isOutgoing: Boolean = false,
    var hasCheckmarks: Boolean = false,
    var isRightAligned: Boolean = false,
    var bounds: String = "",
    val timestamp: String
)

class WhatsAppAccessibilityService : AccessibilityService(), TextSelectionListener, ChatHistoryProvider, GeminiResponseListener, TermsAcceptanceListener {
    private var windowManager: WindowManager? = null
    private var customIconView: View? = null
    private var isOverlayShown = false
    
    // Keep track of the last found input field
    private var lastInputNode: AccessibilityNodeInfo? = null
    
    // Store chat history
    private val chatHistory = mutableListOf<ChatMessage>()
    private var currentChatName: String = "Unknown Contact"

    // Custom keyboard popup component
    private lateinit var customKeyboardPopup: CustomKeyboardPopup
    
    // Terms and conditions popup component
    private lateinit var termsAndConditionsPopup: TermsAndConditionsPopup
    
    // Shared preferences key
    private val PREFS_NAME = "GromoAIPreferences"
    
    // Store the input rect for use after terms acceptance
    private var pendingInputRect: Rect? = null

    // Icon size and margin values
    private var iconSizePx: Int = 0
    private var rightMarginPx: Int = 0
    private var bottomMarginPx: Int = 0

    // Default keyboard height (will be updated dynamically)
    private var keyboardHeight: Int = 0
    private var lastVisibleHeight: Int = 0
    private var isKeyboardVisible: Boolean = false

    // Periodic check handler and runnable
    private val verificationHandler = Handler(Looper.getMainLooper())
    private lateinit var periodicVerificationRunnable: Runnable
    
    // Track layout changes to detect keyboard visibility
    private val globalLayoutListener = View.OnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val navigationBarHeight = getNavigationBarHeight()

        // If the height changed and the new bottom is significantly less than screen height,
        // it's likely that the keyboard is now visible
        if (oldBottom != 0 && bottom != oldBottom) {
            val heightDiff = screenHeight - bottom

            // Check if the height difference is significant (likely keyboard)
            if (heightDiff > screenHeight * 0.15) {
                // Account for navigation bar height if present
                val keyboardHeightEstimate = heightDiff - navigationBarHeight
                if (keyboardHeightEstimate > 100) {
                    keyboardHeight = keyboardHeightEstimate
                    isKeyboardVisible = true
                    updatePopupSize()
                }
            } else {
                // Keyboard is no longer visible
                val wasKeyboardVisible = isKeyboardVisible
                isKeyboardVisible = false

                // Hide popup when keyboard disappears
                if (wasKeyboardVisible && customKeyboardPopup.isVisible()) {
                    customKeyboardPopup.hide()
                }
            }

            // Store last visible height for future comparisons
            lastVisibleHeight = bottom
        }
    }

    private var lastPackageName: String? = null

    // Add debug info list
    private val messageDebugInfo = mutableListOf<MessageDebugInfo>()

    // Store the latest chat JSON for external access
    private var latestChatJson: String = "{}"
    
    // Store AI-suggested replies
    private val suggestedReplies = mutableListOf<String>()
    
    // Gemini API service
    private lateinit var geminiApiService: GeminiApiService
    
    // Flag to track if suggestions have been requested for the current chat
    private var suggestionsRequested = false
    private var repliesRequested = false // Add this new flag for replies
    
    // For debouncing API requests
    private val debounceDelay = 3000L // 3 seconds delay between API calls
    private var lastRequestTime = 0L
    private val apiRequestHandler = Handler(Looper.getMainLooper())
    private var pendingApiRequest: Runnable? = null

    // Add the following property to the class
    var currentInputText: String = ""
        private set(value) {
            field = value
        }
    private var lastImprovedText: String = ""
    var isImprovementRequested = false
        private set

    // Add this property to store improved message suggestions
    val improvedMessageSuggestions = mutableListOf<String>()

    // API related properties
    private lateinit var authRepository: AuthRepository
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Map to track state of each chat
    private val chatStateMap = mutableMapOf<String, ChatState>()
    
    // Flag to determine if we should capture messages for current chat
    private var shouldCaptureMessages = false
    
    // Add a flag to track when we need to sync messages with the backend
    private var needsMessageSync = false

    // Add a timestamp for the last sync
    private var lastMessageSyncTime = 0L

    // Add debounce time for message syncing (5 seconds)
    private val messageSyncDebounceTime = 5000L
    
    // Method to clear all local state - called when user logs out
    fun clearAllChatState() {
        android.util.Log.d("WhatsAppService", "Clearing all chat state")
        chatStateMap.clear()
        shouldCaptureMessages = false
        chatHistory.clear()
        suggestedReplies.clear()
        improvedMessageSuggestions.clear()
        currentChatName = "Unknown Contact"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Initialize window manager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Initialize API repositories
        authRepository = AuthRepository(this)
        
        // Load saved preferences
        loadPreferences()
        
        // Initialize custom keyboard popup
        customKeyboardPopup = CustomKeyboardPopup(this)
        
        // Initialize terms and conditions popup
        termsAndConditionsPopup = TermsAndConditionsPopup(this)
        termsAndConditionsPopup.setTermsAcceptanceListener(this)
        
        // Set text selection listener
        customKeyboardPopup.setTextSelectionListener(this)
        
        // Set chat history provider
        customKeyboardPopup.setChatHistoryProvider(this)

        // Initialize size values based on device density
        val displayMetrics = Resources.getSystem().displayMetrics
        val iconSizeDp = 24f
        iconSizePx = (iconSizeDp * displayMetrics.density).toInt()

        val rightMarginDp = 1f
        rightMarginPx = (rightMarginDp * displayMetrics.density).toInt()

        val bottomMarginDp = 4f
        bottomMarginPx = (bottomMarginDp * displayMetrics.density).toInt()

        // Initialize default keyboard height (about 30% of screen height)
        keyboardHeight = (displayMetrics.heightPixels * 0.3).toInt()
         
        // Initialize periodic verification
        setupPeriodicVerification()
        
        // Initialize Gemini API Service with API key
        geminiApiService = GeminiApiService(ApiConfig.GEMINI_API_KEY, this)
        
        // Register for events to detect app changes
        registerAppChangeDetection()
    }
    
    /**
     * Load saved preferences
     */
    private fun loadPreferences() {
        // This method is kept for potential future use with other preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Note: We no longer use local terms acceptance storage
    }
    
    /**
     * Save preferences
     */
    private fun savePreferences() {
        // This method is kept for potential future use with other preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Note: We no longer use local terms acceptance storage
        editor.apply()
    }
    
    /**
     * Implementation of ChatHistoryProvider interface
     */
    override fun getCurrentChatHistory(): List<ChatMessage> {
        // Log the conversation format to console
        android.util.Log.d("WhatsAppDebug", "Chat Conversation: ${getChatHistoryAsConversation()}")
        
        // Return a copy of the current chat history
        return chatHistory.toList()
    }
    
    /**
     * Get the current chat name
     */
    override fun getCurrentChatName(): String {
        return currentChatName
    }
    
    /**
     * Implementation of TextSelectionListener interface
     * This is called when user selects text from the custom keyboard popup
     */
    override fun onTextSelected(text: String) {
        pasteTextToWhatsAppInput(text)
    }
    
    /**
     * Paste text to WhatsApp input field
     */
    private fun pasteTextToWhatsAppInput(text: String) {
        // Try to use the last found input node if available
        var inputNode = lastInputNode
        
        // If no input node available, try to find it
        if (inputNode == null || !inputNode.isVisibleToUser) {
            val rootNode = rootInActiveWindow ?: return
            
            // Try to find by ID first
            val inputFieldIds = listOf("entry", "conversation_entry", "message_input")
            for (id in inputFieldIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
                if (nodes.isNotEmpty() && nodes[0] != null) {
                    inputNode = nodes[0]
                    break
                }
            }
            
            // If not found by ID, try to find by class name
            if (inputNode == null) {
                val editTextNodes = findNodesByClassName(rootNode, "android.widget.EditText")
                if (editTextNodes.isNotEmpty()) {
                    inputNode = editTextNodes[0]
                }
            }
        }
        
        // Set text in the input field
        inputNode?.let { node ->
            // Focus on the input field first
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            
            // Create a bundle with the text
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            
            // Set the text
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Store reference to the input node for future use
            lastInputNode = node
        }
    }
    
    /**
     * Register for events that help detect when WhatsApp is closed or backgrounded
     */
    private fun registerAppChangeDetection() {
        // We're already handling this in onAccessibilityEvent and through periodic verification
        // This is just a placeholder for future enhancements if needed
    }

    /**
     * Set up periodic verification to ensure overlay is removed when WhatsApp is closed
     */
    private fun setupPeriodicVerification() {
        periodicVerificationRunnable = object : Runnable {
            override fun run() {
                verifyWhatsAppActiveAndInputVisible()
                
                // Also check if we need to sync messages
                if (needsMessageSync) {
                    syncChatMessagesWithBackend()
                }
                
                // Run check frequently to catch app switches and sync needs
                verificationHandler.postDelayed(this, 200)
            }
        }
        
        // Start the periodic check
        verificationHandler.post(periodicVerificationRunnable)
    }

    /**
     * Verify WhatsApp is active and input field is visible
     */
    private fun verifyWhatsAppActiveAndInputVisible() {
        // 1. First check if the current foreground app is WhatsApp
        val currentPackage = getCurrentForegroundPackage()
        if (currentPackage != null && currentPackage != "com.whatsapp") {
            android.util.Log.d("WhatsAppService", "Current foreground app is not WhatsApp: $currentPackage")
            removeOverlay()
            customKeyboardPopup.hide()
            return
        }
        
        // 2. Check if root node exists
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            android.util.Log.d("WhatsAppService", "Root node is null, removing overlay")
            removeOverlay()
            customKeyboardPopup.hide()
            return
        }
        
        // 3. Check package name from root node
        val packageName = rootNode.packageName?.toString()
        if (packageName != "com.whatsapp") {
            android.util.Log.d("WhatsAppService", "Not in WhatsApp (package: $packageName), removing overlay")
            removeOverlay()
            customKeyboardPopup.hide()
            return
        }
        
        // 4. Only keep overlay if we can find an input field
        if (isOverlayShown && !hasWhatsAppInputField(rootNode)) {
            android.util.Log.d("WhatsAppService", "No input field found in WhatsApp, removing overlay")
            removeOverlay()
            customKeyboardPopup.hide()
        }
    }

    /**
     * Get the current foreground package name
     */
    private fun getCurrentForegroundPackage(): String? {
        try {
            // If we have a root node, use it to determine the foreground package
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                return rootNode.packageName?.toString()
            }
        } catch (e: Exception) {
            // If there's an error, log it and continue
            android.util.Log.e("WhatsAppService", "Error getting foreground package", e)
        }
        
        // Return last known package as fallback
        return lastPackageName
    }

    private fun getNavigationBarHeight(): Int {
        val resources = Resources.getSystem()
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // More aggressively check if we've left WhatsApp
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            // If we've switched to a non-WhatsApp package, remove overlays
            if (packageName != "com.whatsapp") {
                android.util.Log.d("WhatsAppService", "Left WhatsApp: $packageName")
                removeOverlay()
                customKeyboardPopup.hide()
                lastPackageName = packageName
                return
            }
        }

        // Check if the package has changed since the last event
        if (lastPackageName != packageName) {
            // If we switched from WhatsApp to another app
            if (lastPackageName == "com.whatsapp" && packageName != "com.whatsapp") {
                android.util.Log.d("WhatsAppService", "Package changed: $lastPackageName -> $packageName")
                // Make sure to clean up all overlays
                removeOverlay()
                customKeyboardPopup.hide()
            }
            lastPackageName = packageName
        }

        // Check if the current foreground app is WhatsApp
        if (packageName != "com.whatsapp") {
            // If we're not in WhatsApp, remove overlays
            removeOverlay()
            customKeyboardPopup.hide()
            return
        }

        // Track window state changes to detect when WhatsApp is being closed or minimized
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // When window state changes, check if we still have a valid input field
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                // No active window, remove overlays
                removeOverlay()
                customKeyboardPopup.hide()
                return
            }
            
            // Detect current chat name when we switch to a new chat
            detectCurrentChat(rootNode)
            
            // Check if we can find the input field
            val hasInputField = hasWhatsAppInputField(rootNode)
            if (!hasInputField) {
                // No input field found, remove overlays
                removeOverlay()
                customKeyboardPopup.hide()
                return
            }
            
            // If we have an input field, process it
            findAndProcessInputField(rootNode)
            
            // Only capture chat messages if terms are accepted and GM mentor was clicked
            if (shouldCaptureMessages) {
                captureWhatsAppMessages(rootNode)
            }
            
            // Back button was likely pressed, hide the custom keyboard
            if (customKeyboardPopup.isVisible()) {
                customKeyboardPopup.handleBackPress()
            }

            checkKeyboardVisibility()
        }

        // Process content changes for when we're in a WhatsApp chat
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {  // Add scrolling event to catch newly visible messages

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                removeOverlay()
                customKeyboardPopup.hide()
                return
            }

            // Check if input field exists
            findAndProcessInputField(rootNode)
            
            // Only process content changes in the conversation view if terms are accepted
            if (hasWhatsAppInputField(rootNode) && shouldCaptureMessages) {
                // Update messages when content changes (new messages, scrolling)
                captureWhatsAppMessages(rootNode)
            }

            // If popup is shown, constantly check keyboard dimensions
            if (customKeyboardPopup.isVisible()) {
                estimateKeyboardHeightAndUpdatePopup()
            }
        }

        // Add this section inside the TYPE_WINDOW_CONTENT_CHANGED handling
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            
            // Check if we're in a WhatsApp chat
            if (packageName == "com.whatsapp") {
                // Check for input field changes
                checkForInputChanges()
            }
        }
    }
    
    /**
     * Detect the current chat we're in
     */
    private fun detectCurrentChat(rootNode: AccessibilityNodeInfo) {
        try {
            // First try to find the conversation contact name
            val contactNameIds = listOf(
                "conversation_contact_name",
                "contact_name",
                "action_bar_title",
                "toolbar_title",
                "chat_contact_name"
            )
            
            // Log that we're detecting the chat
            android.util.Log.d("WhatsAppService", "Detecting current chat...")
            
            for (id in contactNameIds) {
                val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
                if (titleNodes.isNotEmpty() && titleNodes[0] != null) {
                    val newChatName = titleNodes[0].text?.toString()
                    
                    if (newChatName != null && newChatName.isNotBlank()) {
                        // Log the detected chat name
                        android.util.Log.d("WhatsAppService", "Found chat name: $newChatName")
                        
                        // If we switched to a new chat, reset state
                        if (newChatName != currentChatName) {
                            android.util.Log.d("WhatsAppService", "Switched to new chat: $newChatName (from $currentChatName)")
                            
                            // Before changing chat, sync current messages if needed
                            if (needsMessageSync) {
                                syncChatMessagesWithBackend()
                            }
                            
                            // Store old chat name for reference
                            val oldChatName = currentChatName
                            
                            // Update current chat name
                            currentChatName = newChatName
                            
                            // Clear history and suggestions for new chat
                            chatHistory.clear()
                            suggestedReplies.clear()
                            improvedMessageSuggestions.clear()
                            
                            // Reset flags for API requests
                            resetSuggestionRequestFlag()
                            
                            // Reset capture state for new chat - don't capture until terms checked
                            shouldCaptureMessages = false
                            
                            // If we don't have a state for this chat yet, set to UNKNOWN
                            if (!chatStateMap.containsKey(newChatName)) {
                                chatStateMap[newChatName] = ChatState.UNKNOWN
                            } else if (chatStateMap[newChatName] == ChatState.TERMS_ACCEPTED) {
                                // If this chat has already accepted terms, set flag and load messages
                                shouldCaptureMessages = true
                                loadChatMessagesFromBackend()
                            }
                            
                            android.util.Log.d("WhatsAppService", "Chat state for $newChatName: ${chatStateMap[newChatName]}")
                        }
                        return
                    }
                }
            }
            
            // If we couldn't find the contact name using IDs, try to find by looking at text views in action bar
            val actionBarNodes = findNodesInActionBar(rootNode)
            for (node in actionBarNodes) {
                val text = node.text?.toString()
                if (text != null && text.isNotBlank() && text.length <= 30) { // Reasonable length for a chat name
                    android.util.Log.d("WhatsAppService", "Found chat name in action bar: $text")
                    
                    // If we switched to a new chat, reset state
                    if (text != currentChatName) {
                        android.util.Log.d("WhatsAppService", "Switched to new chat: $text (from $currentChatName)")
                        
                        // Store old chat name for reference
                        val oldChatName = currentChatName
                        
                        // Update current chat name
                        currentChatName = text
                        
                        // Clear history and suggestions for new chat
                        chatHistory.clear()
                        suggestedReplies.clear()
                        improvedMessageSuggestions.clear()
                        
                        // Reset flags for API requests
                        resetSuggestionRequestFlag()
                        
                        // Reset capture state for new chat - don't capture until terms checked
                        shouldCaptureMessages = false
                        
                        // If we don't have a state for this chat yet, set to UNKNOWN
                        if (!chatStateMap.containsKey(text)) {
                            chatStateMap[text] = ChatState.UNKNOWN
                        }
                        
                        android.util.Log.d("WhatsAppService", "Chat state for $text: ${chatStateMap[text]}")
                    }
                    return
                }
            }
            
            // If we still haven't found a chat name, check the conversation header
            val headerNodes = findConversationHeader(rootNode)
            for (node in headerNodes) {
                val text = node.text?.toString()
                if (text != null && text.isNotBlank() && text.length <= 30) {
                    android.util.Log.d("WhatsAppService", "Found chat name in header: $text")
                    
                    // If we switched to a new chat, reset state
                    if (text != currentChatName) {
                        android.util.Log.d("WhatsAppService", "Switched to new chat: $text (from $currentChatName)")
                        
                        // Store old chat name for reference
                        val oldChatName = currentChatName
                        
                        // Update current chat name
                        currentChatName = text
                        
                        // Clear history and suggestions for new chat
                        chatHistory.clear()
                        suggestedReplies.clear()
                        improvedMessageSuggestions.clear()
                        
                        // Reset flags for API requests
                        resetSuggestionRequestFlag()
                        
                        // Reset capture state for new chat - don't capture until terms checked
                        shouldCaptureMessages = false
                        
                        // If we don't have a state for this chat yet, set to UNKNOWN
                        if (!chatStateMap.containsKey(text)) {
                            chatStateMap[text] = ChatState.UNKNOWN
                        }
                        
                        android.util.Log.d("WhatsAppService", "Chat state for $text: ${chatStateMap[text]}")
                    }
                    return
                }
            }
            
            android.util.Log.d("WhatsAppService", "Could not detect chat name, using existing: $currentChatName")
        } catch (e: Exception) {
            android.util.Log.e("WhatsAppService", "Error detecting current chat", e)
        }
    }
    
    /**
     * Find conversation header nodes that might contain the chat name
     */
    private fun findConversationHeader(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        // Try to find header containers
        val headerContainers = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_header") ?: 
                              rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_header")
        
        if (headerContainers != null && headerContainers.isNotEmpty()) {
            // Find text views inside the header container
            for (container in headerContainers) {
                results.addAll(findNodesByClassName(container, "android.widget.TextView"))
            }
        }
        
        return results
    }
    
    /**
     * Find nodes that might be in the action bar (for contact name)
     */
    private fun findNodesInActionBar(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        // Look for parent nodes that might be an action bar or toolbar
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        // Try to find the action bar by ID first
        val actionBarContainers = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_toolbar") ?:
                                 rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_toolbar")
        
        if (actionBarContainers != null && actionBarContainers.isNotEmpty()) {
            // Find text views inside the action bar
            for (container in actionBarContainers) {
                results.addAll(findNodesByClassName(container, "android.widget.TextView"))
            }
            return results
        }
        
        // Fall back to checking class names
        return findActionBarNodesByClassName(rootNode)
    }
    
    /**
     * Find action bar nodes by class name
     */
    private fun findActionBarNodesByClassName(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val childCount = rootNode.childCount
        
        // Check if this node looks like an action bar
        val className = rootNode.className?.toString() ?: ""
        if (className.contains("Toolbar") || 
            className.contains("ActionBar") || 
            className.contains("AppBar")) {
            
            // If it is, find text views inside it
            results.addAll(findNodesByClassName(rootNode, "android.widget.TextView"))
        }
        
        // Continue searching in child nodes
        for (i in 0 until childCount) {
            val child = rootNode.getChild(i) ?: continue
            results.addAll(findActionBarNodesByClassName(child))
        }
        
        return results
    }
    
    /**
     * Find message containers in the WhatsApp UI
     */
    private fun findMessageContainers(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        // Only get messages that are visible on screen
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        // Try multiple approaches to ensure we find all message containers
        
        // APPROACH 1: Find by message bubble container IDs
        val bubbleIds = listOf(
            "message_text",
            "conversation_text",
            "message_container",
            "bubble_layout",
            "conversation_row_text",
            "message_bubble"
        )
        
        for (id in bubbleIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
                if (nodes.isNotEmpty()) {
                    results.addAll(nodes)
                }
            } catch (e: Exception) {
                // Continue with the next ID if this one fails
            }
        }
        
        // APPROACH 2: Find the message list container first
        if (results.isEmpty()) {
            val messageListContainers = findMessageListContainer(rootNode)
            
            // Process each container to find actual messages
            for (container in messageListContainers) {
                // Find text views inside the message list - these are likely messages
                val textViews = findNodesByClassName(container, "android.widget.TextView")
                
                // Filter to keep only nodes that are likely message content
                val messageNodes = textViews.filter { node ->
                    val text = node.text?.toString() ?: ""
                    
                    // Message text should be non-empty and visible
                    text.isNotBlank() && 
                    node.isVisibleToUser && 
                    // Exclude very short texts that might be timestamps
                    text.length > 2 &&
                    // Exclude likely status texts
                    !isTimestampOrStatus(text)
                }
                
                results.addAll(messageNodes)
            }
        }
        
        // APPROACH 3: Most aggressive - find ALL TextViews with substantial content
        if (results.isEmpty()) {
            // Get all TextViews in the hierarchy
            val allTextViews = findNodesByClassName(rootNode, "android.widget.TextView")
            
            // Filter for likely message content
            val likelyMessages = allTextViews.filter { node ->
                val text = node.text?.toString() ?: ""
                
                // More liberal criteria to catch all possible messages:
                // 1. Must have text
                // 2. Must be visible
                // 3. Text must be of reasonable length (not timestamps or status indicators)
                // 4. Not contained within obvious non-message containers (headers, etc.)
                text.isNotBlank() && 
                node.isVisibleToUser && 
                text.length >= 2 && 
                !isTimestampOrStatus(text) &&
                !isInNonMessageContainer(node)
            }
            
            results.addAll(likelyMessages)
        }
        
        // Log how many message containers we found
        android.util.Log.d("WhatsAppService", "Found ${results.size} message containers")
        
        return results
    }
    
    /**
     * Check if text is likely a timestamp or status indication
     */
    private fun isTimestampOrStatus(text: String): Boolean {
        // Check if it matches timestamp formats
        val timestampPatterns = listOf(
            Regex("\\d{1,2}:\\d{2}(\\s?[AaPp][Mm])?"), // HH:MM AM/PM
            Regex("\\d{1,2}:\\d{2}"),                  // 24-hour format
            Regex("Yesterday"),
            Regex("Today"),
            Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}")       // date format
        )
        
        // Check for status indicators
        val statusTexts = listOf(
            "typing…", "online", "last seen", "delivered", "read", "sending", "sent"
        )
        
        // Return true if it's a timestamp or status
        return timestampPatterns.any { it.matches(text) } || 
               statusTexts.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * Find the message list container in WhatsApp
     */
    private fun findMessageListContainer(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        // Possible container IDs for the message list
        val containerIds = listOf(
            "conversation_list",
            "messages_list", 
            "message_list",
            "conversation_text_list"
        )
        
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        // Try searching by ID first
        for (id in containerIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
                if (nodes.isNotEmpty()) {
                    return nodes.toList()
                }
            } catch (e: Exception) {
                // Continue with the next ID if this one fails
            }
        }
        
        // If no IDs found, try to find RecyclerView or ListView which typically holds messages
        val listContainers = findNodesByClassName(rootNode, "androidx.recyclerview.widget.RecyclerView")
        if (listContainers.isNotEmpty()) {
            return listContainers
        }
        
        // Try ListView as fallback
        return findNodesByClassName(rootNode, "android.widget.ListView")
    }
    
    /**
     * Check if a node is inside a non-message container (like app bar, bottom bar)
     */
    private fun isInNonMessageContainer(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        
        // Check up to 5 parent levels
        for (i in 0 until 5) {
            current = current?.parent ?: return false
            
            // Check if this container has an ID that suggests it's not a message container
            val idString = current.viewIdResourceName ?: ""
            
            // These are common non-message container IDs
            val nonMessageContainerIds = listOf(
                "app_bar", "toolbar", "action_bar", "navigation", "bottom_bar", 
                "footer", "header", "status_bar", "tab_layout", "input_panel"
            )
            
            if (nonMessageContainerIds.any { id -> idString.contains(id) }) {
                return true
            }
            
            // Check className for non-message containers
            val className = current.className?.toString() ?: ""
            if (className.contains("Toolbar") || 
                className.contains("ActionBar") || 
                className.contains("AppBar") ||
                className.contains("NavigationView") ||
                className.contains("BottomNavigationView")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * FIXED: Capture and store WhatsApp messages with corrected logic
     */
    private fun captureWhatsAppMessages(rootNode: AccessibilityNodeInfo) {
        try {
            // Check if we're in a chat by looking for input field
            if (!hasWhatsAppInputField(rootNode)) {
                return
            }
            
            // Only capture messages if terms have been accepted for this chat
            if (!shouldCaptureMessages) {
                android.util.Log.d("WhatsAppService", "Not capturing messages - terms not accepted or GM mentor not clicked")
                return
            }
            
            // Check if the chat is scrolled to the bottom - only capture messages when at bottom
            val isAtBottom = isChatScrolledToBottom(rootNode)
            if (!isAtBottom) {
                android.util.Log.d("WhatsAppService", "Not capturing messages - user has scrolled up to view history")
                return
            }
            
            android.util.Log.d("WhatsAppService", "Capturing messages for chat: $currentChatName (terms accepted)")
            
            // Store current chat history size to detect changes
            val previousSize = chatHistory.size
            
            // Look for message containers - just the visible ones
            val messageContainers = findMessageContainers(rootNode)
            
            // Track any new messages we find and if they're incoming
            var foundNewMessages = false
            var foundNewIncomingMessage = false
            
            // Process each container to extract messages
            for (container in messageContainers) {
                try {
                    // Extract text
                    val text = container.text?.toString() ?: continue
                    if (text.isBlank()) continue
                    
                    // Log the raw message for debugging
                    android.util.Log.d("WhatsAppService", "Processing potential message: $text")
                    
                    // More sophisticated duplicate check based on content
                    // Only consider it a duplicate if the exact same text is found in recent messages
                    val isDuplicate = chatHistory.any { it.text == text }
                    if (isDuplicate) {
                        android.util.Log.d("WhatsAppService", "Skipping duplicate message: $text")
                        continue
                    }
                    
                    // Try to get timestamp (this will vary based on WhatsApp's UI structure)
                    var timestamp = ""
                    
                    // APPROACH 1: Check sibling nodes for timestamp
                    val parent = container.parent
                    if (parent != null) {
                        for (i in 0 until parent.childCount) {
                            val child = parent.getChild(i) ?: continue
                            
                            // Check if child might be timestamp (small text, usually time only)
                            val childText = child.text?.toString() ?: ""
                            if (childText.matches(Regex("\\d{1,2}:\\d{2}(\\s?[AaPp][Mm])?")) || // HH:MM AM/PM format
                                childText.matches(Regex("\\d{1,2}:\\d{2}"))) {                  // 24-hour format
                                timestamp = childText
                                break
                            }
                        }
                    }
                    
                    // APPROACH 2: Check nearby nodes for timestamp
                    if (timestamp.isBlank()) {
                        val nearbyTimestamps = findNearbyTimestamps(container)
                        if (nearbyTimestamps.isNotEmpty()) {
                            timestamp = nearbyTimestamps[0]
                        }
                    }
                    
                    // If no explicit timestamp, use current time
                    val currentTime = System.currentTimeMillis()
                    val formattedTime = if (timestamp.isBlank()) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(currentTime))
                    } else {
                        timestamp
                    }
                    
                    // Use detection to determine if message is incoming or outgoing
                    // Create debug info for the message
                    val debugInfo = MessageDebugInfo(
                        text = text.take(20),
                        isOutgoing = false, // Will be updated
                        hasCheckmarks = false, // Will be updated
                        isRightAligned = false, // Will be updated
                        bounds = "", // Will be updated
                        timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    )

                    // Get bounds for position analysis
                    val bounds = Rect()
                    container.getBoundsInScreen(bounds)
                    debugInfo.bounds = "${bounds.left},${bounds.top} - ${bounds.right},${bounds.bottom}"

                    // Screen width for position determination
                    val displayMetrics = Resources.getSystem().displayMetrics
                    val screenWidth = displayMetrics.widthPixels

                    // Look for indicators of outgoing messages

                    // 1. Check for tick marks (most reliable)
                    val hasCheckmarks = findCheckmarksInNode(container)
                    debugInfo.hasCheckmarks = hasCheckmarks

                    // 2. Check position on screen (right side of screen usually means outgoing)
                    val isRightAligned = bounds.left > (screenWidth * 0.6) // More than 60% to the right
                    debugInfo.isRightAligned = isRightAligned

                    // 3. Check background color (can look for container view)
                    val hasOutgoingBackground = hasOutgoingMessageBackground(container)

                    // 4. Check for content description mentioning "sent", "delivered", etc.
                    val contentDesc = container.contentDescription?.toString() ?: ""
                    val hasSentStatus = contentDesc.contains("sent", ignoreCase = true) || 
                                      contentDesc.contains("delivered", ignoreCase = true) ||
                                      contentDesc.contains("read", ignoreCase = true)

                    // Combine signals for more accurate detection
                    // Checkmarks are still the strongest signal, but now we use multiple factors
                    val isOutgoing = hasCheckmarks || 
                                  (isRightAligned && (hasOutgoingBackground || hasSentStatus)) ||
                                  (hasSentStatus && hasOutgoingBackground)

                    debugInfo.isOutgoing = isOutgoing
                    
                    // Determine if this message is incoming (from the other person)
                    val isIncoming = !isOutgoing
                    
                    // Update our tracking flags
                    foundNewMessages = true
                    if (isIncoming) {
                        foundNewIncomingMessage = true
                    }
                    
                    // Add to debug info list
                    messageDebugInfo.add(0, debugInfo)
                    if (messageDebugInfo.size > 20) {
                        messageDebugInfo.removeAt(messageDebugInfo.size - 1)
                    }
                    
                    // For debugging, log the detection results
                    android.util.Log.d("WhatsAppService", "Message: ${debugInfo.text}, Outgoing: $isOutgoing, " +
                          "HasCheckmarks: $hasCheckmarks, RightAligned: $isRightAligned, HasOutgoingBg: $hasOutgoingBackground, " + 
                          "HasSentStatus: $hasSentStatus")
                    
                    // Create and add chat message - note that isIncoming is inverse of isOutgoing
                    val chatMessage = ChatMessage(
                        text = text,
                        timestamp = formattedTime,
                        timestampMillis = currentTime,
                        isIncoming = isIncoming,
                        contactName = currentChatName
                    )
                    
                    // Log that we found a new message
                    android.util.Log.d("WhatsAppService", "New message detected: ${if (isIncoming) "Incoming" else "Outgoing"} - $text")
                    
                    // Add to history, adding to end of list (newest messages at the end)
                    chatHistory.add(chatMessage) // Add to end of list
                } catch (e: Exception) {
                    android.util.Log.e("WhatsAppService", "Error processing message container", e)
                }
            }
            
            // Clean up and limit history size
            if (chatHistory.size > 30) {
                chatHistory.subList(0, chatHistory.size - 30).clear() // Remove oldest messages (from beginning of list)
            }
            
            // Check if chat history has changed
            val hasNewMessages = foundNewMessages || chatHistory.size != previousSize
            
            // FIXED: If the chat history has changed, reset flags and potentially request new suggestions
            if (hasNewMessages && chatHistory.isNotEmpty()) {
                android.util.Log.d("WhatsAppService", "Chat history changed, resetting suggestion flags")
                
                // Log new chat history in conversation format
                android.util.Log.d("WhatsAppService", "New chat history:\n${getChatHistoryAsConversation()}")
                
                // FIXED: Check if the last message is from the user using correct indexing
                val lastMessage = chatHistory.last()
                val isLastMessageFromUser = !lastMessage.isIncoming
                
                android.util.Log.d("WhatsAppService", "Last message analysis:")
                android.util.Log.d("WhatsAppService", "  Text: '${lastMessage.text}'")
                android.util.Log.d("WhatsAppService", "  Is incoming: ${lastMessage.isIncoming}")
                android.util.Log.d("WhatsAppService", "  Is from user: $isLastMessageFromUser")
                
                if (isLastMessageFromUser) {
                    // Clear previous suggestions when the last message is from the user
                    android.util.Log.d("WhatsAppService", "Last message is from user, clearing suggested replies")
                    suggestedReplies.clear()
                    
                    // Update UI if reply tab is visible
                    if (customKeyboardPopup.isVisible() && customKeyboardPopup.getCurrentTabIndex() == 0) {
                        customKeyboardPopup.refreshReplyTab()
                    }
                    
                    // Reset flags without requesting new suggestions
                    resetSuggestionRequestFlag()
                } else {
                    // Last message is from the other person, automatically request suggestions
                    android.util.Log.d("WhatsAppService", "🚀 Last message is from other person, auto-requesting suggestions")
                    
                    // Reset the flags first
                    resetSuggestionRequestFlag()
                    
                    // Auto-request suggestions
                    requestAiSuggestedReplies()
                }
                
                // Mark for syncing with backend
                needsMessageSync = true
                
                // Try to sync immediately with debounce protection
                syncChatMessagesWithBackend()
            }
            
            // Refresh the chat history in popup if it's showing
            refreshChatHistoryInPopup()
        } catch (e: Exception) {
            android.util.Log.e("WhatsAppService", "Error in captureWhatsAppMessages", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Get chat history in a plain conversation format for the AI
     */
    fun getChatHistoryAsConversation(): String {
        val stringBuilder = StringBuilder()
        
        // Add header
        stringBuilder.append("CHAT HISTORY WITH: ${currentChatName}\n\n")
        
        // Add each message in chronological order (oldest first)
        // Since messages are now stored with oldest first, we can just iterate normally
        chatHistory.forEach { message ->
            // Format based on sender
            val senderName = if (message.isIncoming) currentChatName else "YOU"
            
            // Add to builder
            stringBuilder.append("($senderName ${message.timestamp}) ${message.text}\n\n")
        }
        
        return stringBuilder.toString()
    }
    
    /**
     * Check if WhatsApp's input field exists in the current view
     */
    private fun hasWhatsAppInputField(rootNode: AccessibilityNodeInfo): Boolean {
        // Common IDs for WhatsApp input field
        val inputFieldIds = listOf(
            "entry", // Common WhatsApp input field ID
            "conversation_entry",
            "message_input"
        )

        // Look for the input field by ID
        for (id in inputFieldIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
            if (nodes.isNotEmpty() && nodes[0] != null) {
                return true
            }
        }

        // If no input field found by ID, try to find by class name in a WhatsApp context
        val editTextNodes = findNodesByClassName(rootNode, "android.widget.EditText")
        return editTextNodes.isNotEmpty()
    }

    private fun findAndProcessInputField(rootNode: AccessibilityNodeInfo?) {
        rootNode ?: return

        // Common IDs for WhatsApp input field
        val inputFieldIds = listOf(
            "entry", // Common WhatsApp input field ID
            "conversation_entry",
            "message_input"
        )

        // Look for the input field
        for (id in inputFieldIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
            if (nodes.isNotEmpty() && nodes[0] != null) {
                val inputNode = nodes[0]
                // Store reference to the input field
                lastInputNode = inputNode
                positionIconOnInputField(inputNode)
                return
            }
        }

        // If no input field found, try to find by class name
        val editTextNodes = findNodesByClassName(rootNode, "android.widget.EditText")
        if (editTextNodes.isNotEmpty()) {
            // Store reference to the input field
            lastInputNode = editTextNodes[0]
            positionIconOnInputField(editTextNodes[0])
            return
        }

        // If we can't find the edit text, remove any existing overlay
        lastInputNode = null
        removeOverlay()
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val childCount = root.childCount

        if (root.className?.toString() == className) {
            results.add(root)
        }

        for (i in 0 until childCount) {
            val child = root.getChild(i) ?: continue
            results.addAll(findNodesByClassName(child, className))
        }

        return results
    }

    private fun positionIconOnInputField(inputNode: AccessibilityNodeInfo) {
        // Get the bounds of the input field
        val inputRect = Rect()
        inputNode.getBoundsInScreen(inputRect)

        // Get screen dimensions
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Ensure the input field bounds are valid
        if (inputRect.left >= 0 &&
           inputRect.right <= screenWidth &&
           inputRect.top >= 0 &&
           inputRect.bottom <= screenHeight) {

            // Create or update the icon
            if (customIconView == null) {
                createCustomIcon(inputRect)
            } else {
                updateCustomIconPosition(inputRect)
            }
        }
    }

    private fun createCustomIcon(inputRect: Rect) {
        if (customIconView != null) return

        // Create a frame layout container
        val iconLayout = FrameLayout(this)
        iconLayout.setBackgroundResource(android.R.color.transparent)

        // Create the image view for our icon
        val iconView = ImageView(this)
        iconView.setImageResource(R.drawable.gromo_copilot)

        // Add the icon to the frame layout
        val params = FrameLayout.LayoutParams(iconSizePx, iconSizePx)
        params.gravity = Gravity.CENTER
        iconLayout.addView(iconView, params)

        // Setup the window parameters
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Position inside the input field at the bottom right
        windowParams.gravity = Gravity.TOP or Gravity.LEFT
        windowParams.x = inputRect.right - iconSizePx - rightMarginPx
        windowParams.y = inputRect.bottom - iconSizePx - bottomMarginPx

        // Set up click listener for the icon
        iconView.setOnClickListener {
            // Toggle the custom popup keyboard
            toggleCustomPopup(inputRect)
        }

        // Add layout change listener to detect keyboard visibility
        iconLayout.addOnLayoutChangeListener(globalLayoutListener)

        try {
            windowManager?.addView(iconLayout, windowParams)
            customIconView = iconLayout
            isOverlayShown = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCustomIconPosition(inputRect: Rect) {
        val iconView = customIconView ?: return

        try {
            val params = iconView.layoutParams as WindowManager.LayoutParams

            // Update position inside input field at bottom right
            params.x = inputRect.right - iconSizePx - rightMarginPx
            params.y = inputRect.bottom - iconSizePx - bottomMarginPx

            // Ensure the icon is visible
            iconView.visibility = View.VISIBLE

            // Update the icon position
            windowManager?.updateViewLayout(iconView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Toggle custom popup when GM mentor icon is clicked
     */
    private fun toggleCustomPopup(inputRect: Rect) {
        if (customKeyboardPopup.isVisible()) {
            customKeyboardPopup.hide()
        } else if (termsAndConditionsPopup.isVisible()) {
            termsAndConditionsPopup.hide()
        } else {
            // First simulate click to bring up keyboard
            simulateInputFieldClick(inputRect)
            
            // Delay popup creation to allow keyboard to appear first
            Handler(Looper.getMainLooper()).postDelayed({
                // Store input rect for possible later use
                pendingInputRect = inputRect
                
                // Always force a check with the backend - ignore local state
                checkChatTermsAcceptance(forceCheck = true)
                
                // Automatically check for text in the input field
                val currentText = fetchCurrentInputText()
                if (currentText.isNotBlank() && currentText.length >= 5) {
                    // Pre-fetch text for suggestions tab
                    currentInputText = currentText
                    
                    // Schedule a delayed request for suggestions
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Only request if popup is visible and the suggestion tab is active
                        if (customKeyboardPopup.isVisible() && customKeyboardPopup.getCurrentTabIndex() == 1) {
                            requestMessageImprovement(currentText)
                        }
                    }, 500) // Small delay to ensure popup is fully shown
                }
            }, 300)
        }
    }

    private fun simulateInputFieldClick(inputRect: Rect) {
        // Try to focus the input field to bring up the keyboard
        try {
            // Find the input field again
            val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
            if (nodes != null && nodes.isNotEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCustomPopup() {
        // Get screen dimensions
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // Calculate navigation bar height
        val navBarHeight = getNavigationBarHeight()

        // Check if there's text in the input field to determine which tab to show
        val hasInputText = currentInputText.isNotBlank() && currentInputText.length >= 5
        val initialTabIndex = if (hasInputText) 1 else 0 // Set to 1 (suggestion tab) if text exists

        // FIXED: Show the popup with correct dimensions and initial tab
        // We want the keyboard to be above the navigation bar, not covering it
        customKeyboardPopup.show(
            width = screenWidth,
            height = keyboardHeight,  // Don't subtract navBarHeight here
            yOffset = navBarHeight,   // This will position it above the nav bar
            initialTabIndex = initialTabIndex // Set initial tab based on input text
        )

        // Start continuously monitoring keyboard height
        startKeyboardHeightMonitoring()
    }

    private fun startKeyboardHeightMonitoring() {
        // Setup a repeating check to keep popup synchronized with keyboard
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (customKeyboardPopup.isVisible()) {
                    estimateKeyboardHeightAndUpdatePopup()
                    handler.postDelayed(this, 100) // Continue checking every 100ms
                }
            }
        }

        // Start the monitoring
        handler.post(runnable)
    }

    private fun estimateKeyboardHeightAndUpdatePopup() {
        // Get the usable screen height and total height including system bars
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // Check for a visible root node
        val rootNode = rootInActiveWindow ?: return

        // Get bounds of the root node (visible screen area)
        val visibleRect = Rect()
        rootNode.getBoundsInScreen(visibleRect)

        // Get navigation bar height
        val navBarHeight = getNavigationBarHeight()

        // FIXED: Calculate keyboard height by comparing visible content area with screen size
        val contentHeight = visibleRect.height()
        if (contentHeight < screenHeight) {
            // Calculate the difference between screen height and visible content area
            val calculatedKeyboardHeight = screenHeight - visibleRect.bottom

            // Check if keyboard is actually visible
            val previousKeyboardVisible = isKeyboardVisible
            isKeyboardVisible = calculatedKeyboardHeight > screenHeight * 0.15

            // If keyboard disappeared, hide our custom keyboard too
            if (previousKeyboardVisible && !isKeyboardVisible && customKeyboardPopup.isVisible()) {
                customKeyboardPopup.hide()
                return
            }

            // Only update if there's a significant change and keyboard is visible
            if (isKeyboardVisible && Math.abs(calculatedKeyboardHeight - keyboardHeight) > 50) {
                keyboardHeight = calculatedKeyboardHeight
                updatePopupSize()
            }
        } else {
            // If full screen is visible, keyboard is not visible
            if (isKeyboardVisible && customKeyboardPopup.isVisible()) {
                isKeyboardVisible = false
                customKeyboardPopup.hide()
            }
        }
    }

    private fun updatePopupSize() {
        // Get navigation bar height
        val navBarHeight = getNavigationBarHeight()

        // FIXED: Update popup with correct dimensions
        customKeyboardPopup.updateSize(
            width = Resources.getSystem().displayMetrics.widthPixels,
            height = keyboardHeight,  // Don't subtract navBarHeight here
            yOffset = navBarHeight    // This will position it above the nav bar
        )
    }

    private fun removeOverlay() {
        try {
            if (customIconView != null) {
                // Remove the layout change listener first
                customIconView?.removeOnLayoutChangeListener(globalLayoutListener)
                
                // Then remove the view from window manager
                windowManager?.removeView(customIconView)
                
                android.util.Log.d("WhatsAppService", "Overlay removed successfully")
            }
        } catch (e: Exception) {
            android.util.Log.e("WhatsAppService", "Error removing overlay", e)
        } finally {
            // Always set these to ensure cleanup happens
            customIconView = null
            isOverlayShown = false
        }
    }

    override fun onInterrupt() {
        // Service interrupted, clean up resources
        removeOverlay()
        customKeyboardPopup.hide()
        lastPackageName = null
        
        // Cancel any pending API requests
        pendingApiRequest?.let { apiRequestHandler.removeCallbacks(it) }
        pendingApiRequest = null
        
        // Stop verification handler
        verificationHandler.removeCallbacks(periodicVerificationRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        customKeyboardPopup.hide()
        lastPackageName = null
        
        // Cancel any pending API requests
        pendingApiRequest?.let { apiRequestHandler.removeCallbacks(it) }
        pendingApiRequest = null
        
        // Stop verification handler
        verificationHandler.removeCallbacks(periodicVerificationRunnable)
    }

    private fun checkKeyboardVisibility() {
        // Get the usable screen height and total height
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // Check for a visible root node
        val rootNode = rootInActiveWindow ?: return

        // Get bounds of the root node (visible screen area)
        val visibleRect = Rect()
        rootNode.getBoundsInScreen(visibleRect)

        // Compare with last known visible height
        val heightDiff = screenHeight - visibleRect.bottom
        val wasKeyboardVisible = isKeyboardVisible

        // Update keyboard visibility state
        isKeyboardVisible = heightDiff > screenHeight * 0.15

        // If keyboard was visible but is no longer visible, hide our custom keyboard too
        if (wasKeyboardVisible && !isKeyboardVisible && customKeyboardPopup.isVisible()) {
            customKeyboardPopup.hide()
        }
    }

    /**
     * Get the latest chat as conversation format
     */
    fun getLatestChatConversation(): String {
        return getChatHistoryAsConversation()
    }

    /**
     * Get full debug information for testing
     */
    fun getFullDebugInfoAsString(): String {
        return getChatHistoryAsConversation()
    }

    /**
     * Refresh the chat history in the popup
     */
    private fun refreshChatHistoryInPopup() {
        // If the popup is visible and showing the chat history tab
        if (customKeyboardPopup.isVisible()) {
            customKeyboardPopup.refreshChatHistory()
        }
    }

    /**
     * Get the latest chat JSON
     */
    fun getLatestChatJson(): String {
        return latestChatJson
    }

    /**
     * Get full debug information including messages and detection info
     */
    fun getFullDebugInfoAsJson(): String {
        // Just return the chat history in conversation format
        return getChatHistoryAsConversation()
    }

    /**
     * FIXED: Enhanced reset method with better logging
     */
    private fun resetSuggestionRequestFlag() {
        // Cancel any pending API requests
        pendingApiRequest?.let { apiRequestHandler.removeCallbacks(it) }
        pendingApiRequest = null
        
        // Log reset with more detail
        android.util.Log.d("WhatsAppService", "=== RESETTING SUGGESTION FLAGS ===")
        android.util.Log.d("WhatsAppService", "Previous state - suggestionsRequested: $suggestionsRequested, repliesRequested: $repliesRequested")
        
        // Reset all suggestion flags
        suggestionsRequested = false
        repliesRequested = false
        
        // Clear improved message suggestions
        improvedMessageSuggestions.clear()
        
        android.util.Log.d("WhatsAppService", "New state - suggestionsRequested: $suggestionsRequested, repliesRequested: $repliesRequested")
        android.util.Log.d("WhatsAppService", "Chat history size: ${chatHistory.size}")
        if (chatHistory.isNotEmpty()) {
            android.util.Log.d("WhatsAppService", "Last message: '${chatHistory.last().text}' (incoming: ${chatHistory.last().isIncoming})")
        }
        android.util.Log.d("WhatsAppService", "=== END RESET ===")
    }

    /**
     * Get AI-suggested replies for the current chat
     */
    fun getAiSuggestedReplies(): List<String> {
        return suggestedReplies.toList()
    }
    
    /**
     * FIXED: Request AI-suggested replies with corrected logic and enhanced debugging
     */
    fun requestAiSuggestedReplies() {
        // Check if terms have been accepted for this chat
        if (!shouldCaptureMessages) {
            android.util.Log.d("WhatsAppService", "Not requesting suggestions: Terms not accepted for this chat")
            return
        }
        
        // Only request suggestions if chat history is not empty
        if (chatHistory.isEmpty()) {
            android.util.Log.d("WhatsAppService", "Not requesting suggestions: Chat history is empty")
            return
        }
        
        // FIXED: Access the last message correctly (newest message is at the end of list)
        val lastMessage = chatHistory.last()
        android.util.Log.d("WhatsAppService", "Last message: '${lastMessage.text}' - isIncoming: ${lastMessage.isIncoming}")
        
        // Only request suggestions if the last message is from the other person (incoming)
        if (!lastMessage.isIncoming) {
            android.util.Log.d("WhatsAppService", "Not requesting suggestions: Last message is from user, not recipient")
            return
        }
        
        // Check if we've already requested for this chat state
        if (repliesRequested) {
            android.util.Log.d("WhatsAppService", "Not requesting suggestions: Already requested for current chat state")
            return
        }
        
        android.util.Log.d("WhatsAppService", "✅ All conditions met - requesting AI suggestions for last message: '${lastMessage.text}'")
        
        // Set flag BEFORE making request to prevent duplicate calls
        repliesRequested = true
        
        // IMPORTANT: Always refresh UI to show loading state before making API call
        if (customKeyboardPopup.isVisible()) {
            customKeyboardPopup.refreshReplyTab()
            android.util.Log.d("WhatsAppService", "Showing loading indicator before API request")
        }
        
        // Cancel any pending requests
        pendingApiRequest?.let { apiRequestHandler.removeCallbacks(it) }
        
        // Create a new runnable for this request
        pendingApiRequest = Runnable {
            sendApiRequestWithDebug()
        }
        
        // Get current time
        val currentTime = System.currentTimeMillis()
        
        // Check if we need to delay this request
        val timeElapsed = currentTime - lastRequestTime
        if (timeElapsed < debounceDelay) {
            // Not enough time has passed since last request, schedule for later
            val delayTime = debounceDelay - timeElapsed
            android.util.Log.d("WhatsAppService", "Debouncing API request. Will send in ${delayTime}ms")
            apiRequestHandler.postDelayed(pendingApiRequest!!, delayTime)
        } else {
            // Enough time has passed, make request now
            sendApiRequestWithDebug()
        }
    }
    
    /**
     * FIXED: Enhanced API request method with better debugging
     */
    private fun sendApiRequestWithDebug() {
        // Update last request time
        lastRequestTime = System.currentTimeMillis()
        
        // Generate chat history in conversation format
        val chatConversation = getChatHistoryAsConversation()
        
        // Enhanced logging
        android.util.Log.d("WhatsAppService", "=== SENDING API REQUEST ===")
        android.util.Log.d("WhatsAppService", "Chat: $currentChatName")
        android.util.Log.d("WhatsAppService", "Messages count: ${chatHistory.size}")
        android.util.Log.d("WhatsAppService", "Last message: '${chatHistory.last().text}' (incoming: ${chatHistory.last().isIncoming})")
        android.util.Log.d("WhatsAppService", "Full conversation:\n$chatConversation")
        android.util.Log.d("WhatsAppService", "=== END REQUEST INFO ===")
        
        // Make sure to show loading state right before the actual API call
        Handler(Looper.getMainLooper()).post {
            if (customKeyboardPopup.isVisible()) {
                customKeyboardPopup.refreshReplyTab()
            }
        }
        
        // Request suggested replies from Gemini API
        geminiApiService.getSuggestedReplies(chatConversation, object : GeminiResponseListener {
            override fun onSuggestedRepliesReceived(replies: List<String>) {
                android.util.Log.d("WhatsAppService", "=== API RESPONSE RECEIVED ===")
                android.util.Log.d("WhatsAppService", "Received ${replies.size} suggested replies")
                for (i in replies.indices) {
                    android.util.Log.d("WhatsAppService", "Reply ${i + 1}: '${replies[i]}'")
                }
                
                // FIXED: Double-check if last message is still incoming before storing replies
                if (chatHistory.isNotEmpty() && chatHistory.last().isIncoming) {
                    // Store the suggested replies
                    suggestedReplies.clear()
                    suggestedReplies.addAll(replies)
                    android.util.Log.d("WhatsAppService", "✅ Stored ${replies.size} suggestions")
                } else {
                    android.util.Log.d("WhatsAppService", "⚠️ Last message changed - not storing suggestions")
                    suggestedReplies.clear()
                }
                
                // Reset flag
                repliesRequested = false
                
                // Update UI on main thread
                Handler(Looper.getMainLooper()).post {
                    if (::customKeyboardPopup.isInitialized) {
                        customKeyboardPopup.refreshReplyTab()
                        android.util.Log.d("WhatsAppService", "✅ UI refreshed with suggestions")
                    }
                }
                android.util.Log.d("WhatsAppService", "=== END API RESPONSE ===")
            }
            
            override fun onError(error: String) {
                android.util.Log.e("WhatsAppService", "=== API ERROR ===")
                android.util.Log.e("WhatsAppService", "Error: $error")
                android.util.Log.e("WhatsAppService", "Chat: $currentChatName")
                android.util.Log.e("WhatsAppService", "Messages: ${chatHistory.size}")
                
                // Reset flag
                repliesRequested = false
                
                // Add fallback reply only if last message is still incoming
                if (chatHistory.isNotEmpty() && chatHistory.last().isIncoming) {
                    suggestedReplies.clear()
                    suggestedReplies.add("I'll get back to you soon.")
                    android.util.Log.d("WhatsAppService", "Added fallback reply due to error")
                } else {
                    suggestedReplies.clear()
                }
                
                // Update UI
                Handler(Looper.getMainLooper()).post {
                    customKeyboardPopup.refreshReplyTab()
                }
                android.util.Log.e("WhatsAppService", "=== END API ERROR ===")
            }
        })
    }
    
    /**
     * Check if suggestions for replies are currently being requested
     */
    fun isRepliesRequested(): Boolean {
        return repliesRequested
    }

    /**
     * Implementation of GeminiResponseListener
     */
    override fun onSuggestedRepliesReceived(replies: List<String>) {
        // Log the suggested replies with full details
        android.util.Log.d("WhatsAppService", "Received ${replies.size} suggested replies from Gemini API")
        for (i in replies.indices) {
            android.util.Log.d("WhatsAppService", "Reply $i: ${replies[i]}")
        }
        
        // Check if the last message is from the user - if so, don't update suggestions
        if (chatHistory.isNotEmpty() && !chatHistory.last().isIncoming) {
            android.util.Log.d("WhatsAppService", "Ignoring received suggestions as last message is from user")
            // Clear suggestions instead of storing the new ones
            suggestedReplies.clear()
        } else {
            // Store the suggested replies only if last message is from other person
            suggestedReplies.clear()
            suggestedReplies.addAll(replies)
        }
        
        // Set flag to false so we can request again if needed
        repliesRequested = false
        
        // Ensure UI update happens on main thread
        Handler(Looper.getMainLooper()).post {
            // Notify the custom keyboard popup to refresh
            if (::customKeyboardPopup.isInitialized) {
                android.util.Log.d("WhatsAppService", "Refreshing UI with ${suggestedReplies.size} suggested replies")
                customKeyboardPopup.refreshReplyTab()
                
                // Force UI update if the popup is visible
                if (customKeyboardPopup.isVisible()) {
                    android.util.Log.d("WhatsAppService", "Popup is visible, forcing update")
                }
            } else {
                android.util.Log.e("WhatsAppService", "CustomKeyboardPopup not initialized yet")
            }
        }
    }

    override fun onError(error: String) {
        // Log the error
        android.util.Log.e("WhatsAppService", "Error getting suggested replies: $error")
        
        // Check if the last message is from the user
        if (chatHistory.isNotEmpty() && !chatHistory.last().isIncoming) {
            // If user's message is last, just clear suggestions without showing a default
            suggestedReplies.clear()
        } else {
            // Add a default reply in case of error only if last message is from other person
            suggestedReplies.clear()
            suggestedReplies.add("I'll get back to you soon.")
        }
        
        // Reset flag to allow future requests
        repliesRequested = false
        
        // Notify the custom keyboard popup to refresh
        customKeyboardPopup.refreshReplyTab()
    }

    /**
     * Find timestamps near a message container
     */
    private fun findNearbyTimestamps(container: AccessibilityNodeInfo): List<String> {
        val timestamps = mutableListOf<String>()
        val parent = container.parent ?: return timestamps
        
        // Check siblings and their children
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            
            // Check direct sibling text
            val siblingText = sibling.text?.toString() ?: ""
            if (isTimestamp(siblingText)) {
                timestamps.add(siblingText)
                continue
            }
            
            // Check sibling's children
            for (j in 0 until sibling.childCount) {
                val child = sibling.getChild(j) ?: continue
                val childText = child.text?.toString() ?: ""
                if (isTimestamp(childText)) {
                    timestamps.add(childText)
                }
            }
        }
        
        return timestamps
    }

    /**
     * Check if a string is likely a timestamp
     */
    private fun isTimestamp(text: String): Boolean {
        // Common timestamp patterns
        val timestampPatterns = listOf(
            Regex("\\d{1,2}:\\d{2}(\\s?[AaPp][Mm])?"), // HH:MM AM/PM
            Regex("\\d{1,2}:\\d{2}"),                  // 24-hour format
            Regex("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}") // date format
        )
        
        return timestampPatterns.any { it.matches(text) }
    }

    /**
     * Check if a node likely has the background for outgoing messages
     */
    private fun hasOutgoingMessageBackground(node: AccessibilityNodeInfo): Boolean {
        // Look for parent containers that might be the message bubble
        var current: AccessibilityNodeInfo? = node
        
        // Check up to 3 parent levels
        for (i in 0 until 3) {
            current = current?.parent ?: return false
            
            // Check for background drawable or content description 
            // that might indicate an outgoing message bubble
            val contentDesc = current.contentDescription?.toString()?.lowercase() ?: ""
            
            // WhatsApp often puts background color info in content description
            if (contentDesc.contains("green") || 
                contentDesc.contains("light green") ||
                contentDesc.contains("teal") ||
                contentDesc.contains("outgoing")) {
                return true
            }
            
            // Check class name for bubble layouts
            val className = current.className?.toString() ?: ""
            if (className.contains("Bubble") || className.contains("Message")) {
                // If it's a bubble container, check if it's on the right side
                val bubbleBounds = Rect()
                current.getBoundsInScreen(bubbleBounds)
                
                val displayMetrics = Resources.getSystem().displayMetrics
                val screenWidth = displayMetrics.widthPixels
                
                // Right-aligned bubbles are usually outgoing
                if (bubbleBounds.left > (screenWidth * 0.5)) {
                    return true
                }
            }
        }
        
        return false
    }

    /**
     * Find checkmarks in a node or its descendants - the most reliable indicator of outgoing messages
     */
    private fun findCheckmarksInNode(node: AccessibilityNodeInfo): Boolean {
        // 1. Check this node for checkmark characters
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains("✓") || nodeText.contains("✔")) {
            return true
        }
        
        // 2. Check content description for status indicators
        val contentDesc = node.contentDescription?.toString() ?: ""
        val statusKeywords = listOf("delivered", "read", "sent", "seen")
        if (statusKeywords.any { contentDesc.contains(it, ignoreCase = true) }) {
            return true
        }
        
        // 3. Check immediate children (just one level) for checkmarks/status
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            
            // Check child's text
            val childText = child.text?.toString() ?: ""
            if (childText.contains("✓") || childText.contains("✔")) {
                return true
            }
            
            // Check child's content description
            val childContentDesc = child.contentDescription?.toString() ?: ""
            if (statusKeywords.any { childContentDesc.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        
        // 4. Check siblings for checkmarks or status indicators
        val parent = node.parent ?: return false
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling == node) continue // Skip self
            
            // Check sibling text
            val siblingText = sibling.text?.toString() ?: ""
            if (siblingText.contains("✓") || siblingText.contains("✔")) {
                return true
            }
            
            // Check sibling content description
            val siblingContentDesc = sibling.contentDescription?.toString() ?: ""
            if (statusKeywords.any { siblingContentDesc.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        
        return false
    }

    /**
     * Check for bubble parameters that indicate outgoing messages
     * Returns a Pair<Boolean, String> where:
     * - Boolean indicates if it's likely an outgoing message bubble
     * - String provides debug info about the bubble
     */
    private fun checkBubbleParameters(node: AccessibilityNodeInfo): Pair<Boolean, String> {
        var current: AccessibilityNodeInfo? = node
        val bubbleDebugInfo = StringBuilder()
        
        // Check up to 4 parent levels to find bubble container
        for (i in 0 until 4) {
            current = current?.parent ?: return Pair(false, "No parent found")
            
            val className = current.className?.toString() ?: ""
            val contentDesc = current.contentDescription?.toString()?.lowercase() ?: ""
            
            // Check if this looks like a message bubble container
            if (className.contains("FrameLayout") || 
                className.contains("LinearLayout") || 
                className.contains("Bubble") || 
                className.contains("Message")) {
                
                // Check if bubble has indicators of being an outgoing message
                val outgoingIndicators = listOf("outgoing", "sent", "delivered", "read", "green", "teal")
                
                // Check content description for outgoing indicators
                val hasOutgoingIndicator = outgoingIndicators.any { contentDesc.contains(it) }
                
                // Check position (WhatsApp typically puts outgoing messages on the right)
                val bubbleBounds = Rect()
                current.getBoundsInScreen(bubbleBounds)
                val displayMetrics = Resources.getSystem().displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val isRightAligned = bubbleBounds.left > (screenWidth * 0.5) // More than 50% to the right
                
                // Add debug info
                bubbleDebugInfo.append("Class: $className, ")
                    .append("ContentDesc: ${contentDesc.take(20)}, ")
                    .append("RightAligned: $isRightAligned, ")
                    .append("HasOutgoingIndicator: $hasOutgoingIndicator")
                
                // If we have strong indicators it's an outgoing message bubble
                if (hasOutgoingIndicator || (isRightAligned && (className.contains("Bubble") || className.contains("Message")))) {
                    return Pair(true, bubbleDebugInfo.toString())
                }
            }
        }
        
        return Pair(false, bubbleDebugInfo.toString())
    }

    /**
     * Get current text from WhatsApp input field
     */
    private fun fetchCurrentInputText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        
        // Try to find the input field
        val inputNode = lastInputNode ?: findWhatsAppInputField(rootNode) ?: return ""
        
        // Get the text from the input field
        val inputText = inputNode.text?.toString() ?: ""
        
        // Check if this is just a placeholder text
        if (isPlaceholderText(inputText)) {
            return "" // Return empty string for placeholder text
        }
        
        return inputText
    }
    
    /**
     * Check if the given text is just a placeholder like "Message"
     */
    private fun isPlaceholderText(text: String): Boolean {
        val placeholders = listOf("Message", "Type a message", "Enter message", "メッセージ")
        val trimmedText = text.trim().lowercase()
        
        // Log the exact text for debugging
        android.util.Log.d("WhatsAppService", "Input field text: '$text', trimmed: '$trimmedText'")
        
        // Check if it matches any known placeholder
        return placeholders.any { placeholder -> 
            trimmedText == placeholder.lowercase() || 
            trimmedText.isEmpty()
        }
    }

    /**
     * Get current input text and request improvement if appropriate
     */
    fun checkInputAndRequestImprovement() {
        // Get current input text
        val inputText = currentInputText.trim()
        
        // Log the exact text we're checking
        android.util.Log.d("WhatsAppService", "Checking input text for improvements: '$inputText'")
        
        // Check if this is a placeholder or empty text
        val isEmptyOrPlaceholder = inputText.isBlank() || 
                                  inputText.length < 5 || 
                                  isPlaceholderText(inputText)
        
        // Clear suggestions if input is empty or just a placeholder
        if (isEmptyOrPlaceholder) {
            // Clear any existing suggestions
            if (improvedMessageSuggestions.isNotEmpty()) {
                improvedMessageSuggestions.clear()
                
                // Update UI if suggestion tab is active
                if (customKeyboardPopup.isVisible() && customKeyboardPopup.getCurrentTabIndex() == 1) {
                    customKeyboardPopup.refreshSuggestionTab()
                }
                
                android.util.Log.d("WhatsAppService", "Cleared suggestions because input is empty/placeholder: '$inputText'")
            }
            return
        }
        
        // If we reached here, we have real text input
        // Only request if not already requesting
        if (!isImprovementRequested) {
            // Check if this text is different from last improved text
            if (shouldRequestImprovement(inputText, lastImprovedText)) {
                // Update last improved text
                lastImprovedText = inputText
                
                // Request improvement
                requestMessageImprovement(inputText)
            } else {
                android.util.Log.d("WhatsAppService", "Not requesting improvement: No significant changes to text")
            }
        } else {
            android.util.Log.d("WhatsAppService", "Not requesting improvement: Already requesting")
        }
    }

    /**
     * Monitor changes in the input field and request improvements when needed
     */
    private fun checkForInputChanges() {
        // Get current text from input field
        val inputText = fetchCurrentInputText().trim()
        
        // Check if this might be placeholder text
        val effectiveText = if (isPlaceholderText(inputText)) "" else inputText
        
        // Store current input text for later use, but don't request improvements automatically
        if (effectiveText != currentInputText) {
            // Update the stored text regardless
            currentInputText = effectiveText
            
            // If input became empty, clear suggestions
            if (effectiveText.isBlank() && improvedMessageSuggestions.isNotEmpty()) {
                improvedMessageSuggestions.clear()
                
                // Update UI if suggestion tab is active
                if (customKeyboardPopup.isVisible() && customKeyboardPopup.getCurrentTabIndex() == 1) {
                    customKeyboardPopup.refreshSuggestionTab()
                }
                
                android.util.Log.d("WhatsAppService", "Cleared suggestions because input became empty")
            }
            
            // Log the input change but don't request improvement automatically
            android.util.Log.d("WhatsAppService", "Input field text changed: '$effectiveText'")
        }
    }

    /**
     * Decide if we should request improvement for the current input text
     */
    private fun shouldRequestImprovement(newText: String, previousText: String): Boolean {
        // Don't improve if text is too short
        if (newText.length < 5) return false
        
        // Always improve if this is the first improvement
        if (previousText.isBlank()) return true
        
        // Calculate how different the new text is from the previous
        val commonPrefix = newText.commonPrefixWith(previousText).length
        val textDifference = newText.length - commonPrefix
        
        // If text has changed significantly (added at least 5 new characters)
        return textDifference >= 5
    }

    /**
     * Request message improvement suggestions from Gemini API
     * Only called when the Suggestion tab is clicked and there's text
     */
    fun requestMessageImprovement(inputText: String) {
        // Check if terms have been accepted for this chat
        if (!shouldCaptureMessages) {
            android.util.Log.d("WhatsAppService", "Not requesting message improvements: Terms not accepted for this chat")
            return
        }
        
        // Check if we should improve this text
        if (inputText.isBlank() || inputText.length < 5) {
            // No text or too short to improve, log and return
            android.util.Log.d("WhatsAppService", "Not requesting improvements: Input text is empty or too short")
            return
        }
        
        // Set flag to prevent multiple requests for same text
        isImprovementRequested = true
        
        // Get conversation context for better improvements
        val conversationContext = getChatHistoryAsConversation()
        
        // Log what we're doing
        android.util.Log.d("WhatsAppService", "Requesting improvement for input text: $inputText")
        
        // Make API request for improvements
        geminiApiService.getImprovedMessageSuggestions(inputText, conversationContext, object : GeminiResponseListener {
            override fun onSuggestedRepliesReceived(replies: List<String>) {
                // Store the improved suggestions
                improvedMessageSuggestions.clear()
                improvedMessageSuggestions.addAll(replies)
                
                // Update UI if suggestion tab is active
                if (customKeyboardPopup.isVisible() && customKeyboardPopup.getCurrentTabIndex() == 1) {
                    customKeyboardPopup.refreshSuggestionTab()
                }
                
                // Reset flag
                isImprovementRequested = false
                
                // Log results
                android.util.Log.d("WhatsAppService", "Received ${replies.size} improved message suggestions")
            }
            
            override fun onError(error: String) {
                android.util.Log.e("WhatsAppService", "Error getting message improvements: $error")
                // Reset flag
                isImprovementRequested = false
            }
        })
    }
    
    /**
     * Get improved message suggestions for UI display
     */
    fun fetchImprovedMessageSuggestions(): List<String> {
        return improvedMessageSuggestions.toList()
    }

    /**
     * Find WhatsApp input field in the UI hierarchy
     */
    private fun findWhatsAppInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Common IDs for WhatsApp input field
        val inputFieldIds = listOf(
            "entry", // Common WhatsApp input field ID
            "conversation_entry",
            "message_input"
        )

        // Look for the input field by ID
        for (id in inputFieldIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
            if (nodes.isNotEmpty() && nodes[0] != null) {
                return nodes[0]
            }
        }
        
        // If no input field found by ID, try to find by class name
        val editTextNodes = findNodesByClassName(rootNode, "android.widget.EditText")
        if (editTextNodes.isNotEmpty()) {
            return editTextNodes[0]
        }

        return null
    }

    /**
     * Show the terms and conditions popup
     */
    private fun showTermsAndConditionsPopup() {
        // Get screen dimensions
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        
        // Calculate navigation bar height
        val navBarHeight = getNavigationBarHeight()
        
        // Show terms popup with appropriate size
        termsAndConditionsPopup.show(
            width = screenWidth,
            height = keyboardHeight,
            yOffset = navBarHeight
        )
    }

    /**
     * Implementation of TermsAcceptanceListener interface
     */
    override fun onTermsAccepted() {
        // Mark terms as accepted for this chat
        val chatName = currentChatName
        
        // First check if user is logged in
        if (!authRepository.isLoggedIn()) {
            // User not logged in, show error message
            android.util.Log.e("WhatsAppService", "Cannot proceed: User not logged in")
            showAuthenticationError("Please log in to the GM Mentor app first")
            return
        }
        
        // Update state
        chatStateMap[chatName] = ChatState.TERMS_ACCEPTED
        shouldCaptureMessages = true
        
        // Save to backend
        coroutineScope.launch {
            try {
                android.util.Log.d("WhatsAppService", "Storing terms acceptance for chat: $chatName")
                val response = authRepository.storeChatAcceptance(chatName)
                when (response) {
                    is ApiResponse.Success -> {
                        android.util.Log.d("WhatsAppService", "Successfully stored terms acceptance for: $chatName")
                        
                        // Try to load existing messages first
                        loadChatMessagesFromBackend()
                        
                        // Start capturing messages now that terms are accepted
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            captureWhatsAppMessages(rootNode)
                        }
                        
                        // Show the main popup
                        showCustomPopup()
                    }
                    is ApiResponse.Error -> {
                        android.util.Log.e("WhatsAppService", "Failed to store terms acceptance: ${response.message}")
                        showAuthenticationError("Error: ${response.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("WhatsAppService", "Error storing terms acceptance: ${e.message}")
                showAuthenticationError("Error: ${e.message ?: "Unknown error occurred"}")
            }
        }
    }
    
    /**
     * Show authentication error message to the user
     */
    private fun showAuthenticationError(message: String) {
        // Create a popup to show the error message
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val navBarHeight = getNavigationBarHeight()
        
        // Use the terms popup to show the error, but customize the message
        termsAndConditionsPopup.showErrorMessage(message, screenWidth, keyboardHeight, navBarHeight)
    }

    override fun onTermsDeclined() {
        // Terms were declined, update state
        chatStateMap[currentChatName] = ChatState.TERMS_NOT_ACCEPTED
        shouldCaptureMessages = false
        pendingInputRect = null
    }

    /**
     * Check if this chat has already accepted terms
     */
    private fun checkChatTermsAcceptance(forceCheck: Boolean = false) {
        val chatName = currentChatName
        
        // First check if the user is logged in
        if (!authRepository.isLoggedIn()) {
            // User not logged in, show error message
            android.util.Log.e("WhatsAppService", "Cannot check terms: User not logged in")
            showAuthenticationError("Please log in to the GM Mentor app first")
            return
        }
        
        // Update state to checking
        chatStateMap[chatName] = ChatState.TERMS_CHECKING
        
        // Show loading screen
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val navBarHeight = getNavigationBarHeight()
        
        // Create loading layout
        val loadingLayout = LinearLayout(this)
        loadingLayout.orientation = LinearLayout.VERTICAL
        loadingLayout.gravity = Gravity.CENTER
        
        // Create gradient background
        val popupBg = GradientDrawable()
        popupBg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        popupBg.colors = intArrayOf(0xFFFAFAFA.toInt(), 0xFFF0F0F0.toInt())
        loadingLayout.background = popupBg

        // Add loading spinner
        val loadingSpinner = createLoadingSpinner()
        loadingLayout.addView(loadingSpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Add loading text
        val loadingText = TextView(this)
        loadingText.text = "Checking terms acceptance..."
        loadingText.textSize = 16f
        loadingText.setTextColor(0xFF3F51B5.toInt())
        loadingText.gravity = Gravity.CENTER
        loadingText.setPadding(0, 16, 0, 0)
        loadingText.setTypeface(loadingText.typeface, Typeface.BOLD)
        loadingLayout.addView(loadingText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Show loading screen
        val windowParams = WindowManager.LayoutParams(
            screenWidth,
            keyboardHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowParams.gravity = Gravity.BOTTOM
        windowParams.y = navBarHeight

        try {
            windowManager?.addView(loadingLayout, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Check with the backend server
        coroutineScope.launch {
            try {
                android.util.Log.d("WhatsAppService", "Checking terms acceptance for chat: $chatName")
                val response = authRepository.checkChatAcceptance(chatName)
                
                // Remove loading screen
                try {
                    windowManager?.removeView(loadingLayout)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                when (response) {
                    is ApiResponse.Success -> {
                        if (response.data) {
                            // Terms already accepted, update state and show main popup
                            android.util.Log.d("WhatsAppService", "Terms already accepted for chat: $chatName")
                            chatStateMap[chatName] = ChatState.TERMS_ACCEPTED
                            shouldCaptureMessages = true
                            
                            // Load existing messages from backend
                            loadChatMessagesFromBackend()
                            
                            // Start capturing messages now that terms are accepted
                            val rootNode = rootInActiveWindow
                            if (rootNode != null) {
                                captureWhatsAppMessages(rootNode)
                            }
                            
                            // Show main popup
                            showCustomPopup()
                        } else {
                            // Terms not accepted yet, show terms popup
                            android.util.Log.d("WhatsAppService", "Terms not accepted yet for chat: $chatName")
                            chatStateMap[chatName] = ChatState.TERMS_NOT_ACCEPTED
                            shouldCaptureMessages = false
                            showTermsAndConditionsPopup()
                        }
                    }
                    is ApiResponse.Error -> {
                        // If error happens, show error message
                        android.util.Log.e("WhatsAppService", "Error checking terms: ${response.message}")
                        chatStateMap[chatName] = ChatState.TERMS_NOT_ACCEPTED
                        shouldCaptureMessages = false
                        showAuthenticationError("Error: ${response.message}")
                    }
                    else -> {
                        android.util.Log.d("WhatsAppService", "Unknown response, showing terms popup")
                        chatStateMap[chatName] = ChatState.TERMS_NOT_ACCEPTED
                        shouldCaptureMessages = false
                        showTermsAndConditionsPopup()
                    }
                }
            } catch (e: Exception) {
                // Remove loading screen
                try {
                    windowManager?.removeView(loadingLayout)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
                
                // If exception, show error message
                android.util.Log.e("WhatsAppService", "Exception checking terms: ${e.message}")
                chatStateMap[chatName] = ChatState.TERMS_NOT_ACCEPTED
                shouldCaptureMessages = false
                showAuthenticationError("Error: ${e.message ?: "Unknown error occurred"}")
            }
        }
    }

    /**
     * Check if the chat view is scrolled to the bottom
     * Returns true if at bottom, false if scrolled up
     */
    private fun isChatScrolledToBottom(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Look for ListView/RecyclerView/ScrollView that contains chat messages
            val listViewIds = listOf(
                "conversation_list",
                "messages_list",
                "chat_list",
                "messages_container"
            )
            
            // First try to find by ID
            for (id in listViewIds) {
                val listNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$id")
                if (listNodes.isNotEmpty() && listNodes[0] != null) {
                    val listView = listNodes[0]
                    
                    // Check if we're at the bottom of the list
                    if (listView.isScrollable) {
                        // If scrollable but can't scroll down, we're at the bottom
                        val canScrollForward = listView.actionList.any { action -> 
                            action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        }
                        // If we can't scroll forward (down), we're at the bottom
                        return !canScrollForward
                    }
                    return true // If not scrollable, treat as at bottom
                }
            }
            
            // If we can't find by ID, look for scrollable lists
            val scrollableLists = findNodesByClassNameList(rootNode, listOf(
                "android.widget.ListView",
                "androidx.recyclerview.widget.RecyclerView",
                "android.widget.ScrollView"
            ))
            
            for (scrollable in scrollableLists) {
                if (scrollable.isScrollable) {
                    // If scrollable but can't scroll down, we're at the bottom
                    val canScrollForward = scrollable.actionList.any { action -> 
                        action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
                    // If we can't scroll forward (down), we're at the bottom
                    return !canScrollForward
                }
            }
            
            // Default to true if we can't determine (safer option)
            return true
        } catch (e: Exception) {
            android.util.Log.e("WhatsAppService", "Error checking scroll position", e)
            return true // Default to true on error
        }
    }

    /**
     * Helper method to find nodes by multiple possible class names
     */
    private fun findNodesByClassNameList(rootNode: AccessibilityNodeInfo, classNames: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            for (className in classNames) {
                val nodes = findNodesByClassName(rootNode, className)
                result.addAll(nodes)
            }
        } catch (e: Exception) {
            android.util.Log.e("WhatsAppService", "Error finding nodes by class names", e)
        }
        
        return result
    }

    /**
     * Sync chat messages with the backend database
     */
    private fun syncChatMessagesWithBackend() {
        // Only sync if terms have been accepted and we have messages to sync
        if (!shouldCaptureMessages || chatHistory.isEmpty()) {
            return
        }
        
        // Check if enough time has passed since last sync
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMessageSyncTime < messageSyncDebounceTime) {
            // Not enough time passed, mark for future sync
            needsMessageSync = true
            return
        }
        
        // Update last sync time
        lastMessageSyncTime = currentTime
        needsMessageSync = false
        
        // Log that we're syncing
        android.util.Log.d("WhatsAppService", "Syncing ${chatHistory.size} messages for chat: $currentChatName")
        
        // Make API call to store messages
        coroutineScope.launch {
            try {
                val response = authRepository.storeChatMessages(currentChatName, chatHistory)
                when (response) {
                    is ApiResponse.Success -> {
                        android.util.Log.d("WhatsAppService", "Successfully stored ${response.data} messages")
                    }
                    is ApiResponse.Error -> {
                        android.util.Log.e("WhatsAppService", "Error storing messages: ${response.message}")
                        // If error, mark for retry
                        needsMessageSync = true
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("WhatsAppService", "Exception storing messages: ${e.message}")
                // If exception, mark for retry
                needsMessageSync = true
            }
        }
    }

    /**
     * Load chat messages from the backend database
     */
    private fun loadChatMessagesFromBackend() {
        // Only load if terms have been accepted and we need messages
        if (!shouldCaptureMessages) {
            return
        }
        
        // Don't load if we already have messages
        if (chatHistory.isNotEmpty()) {
            return
        }
        
        // Log that we're loading
        android.util.Log.d("WhatsAppService", "Loading messages for chat: $currentChatName")
        
        // Make API call to get messages
        coroutineScope.launch {
            try {
                val response = authRepository.getChatMessages(currentChatName)
                when (response) {
                    is ApiResponse.Success -> {
                        val messages = response.data
                        if (messages.isNotEmpty()) {
                            android.util.Log.d("WhatsAppService", "Loaded ${messages.size} messages from backend")
                            
                            // Clear existing history and add loaded messages
                            chatHistory.clear()
                            chatHistory.addAll(messages)
                            
                            // Refresh UI
                            refreshChatHistoryInPopup()
                        } else {
                            android.util.Log.d("WhatsAppService", "No messages found in backend for this chat")
                        }
                    }
                    is ApiResponse.Error -> {
                        android.util.Log.e("WhatsAppService", "Error loading messages: ${response.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("WhatsAppService", "Exception loading messages: ${e.message}")
            }
        }
    }

    /**
     * FIXED: Debug method to manually trigger reply request (for testing)
     */
    fun debugTriggerReplyRequest() {
        android.util.Log.d("WhatsAppService", "=== DEBUG TRIGGER REPLY REQUEST ===")
        android.util.Log.d("WhatsAppService", "Current chat: $currentChatName")
        android.util.Log.d("WhatsAppService", "Should capture messages: $shouldCaptureMessages")
        android.util.Log.d("WhatsAppService", "Chat history size: ${chatHistory.size}")
        android.util.Log.d("WhatsAppService", "Replies requested: $repliesRequested")
        
        if (chatHistory.isNotEmpty()) {
            val lastMsg = chatHistory.last()
            android.util.Log.d("WhatsAppService", "Last message: '${lastMsg.text}' (incoming: ${lastMsg.isIncoming})")
        }
        
        // Force reset flags and request
        repliesRequested = false
        requestAiSuggestedReplies()
        android.util.Log.d("WhatsAppService", "=== END DEBUG TRIGGER ===")
    }

    /**
     * Create a loading spinner view
     */
    private fun createLoadingSpinner(): ProgressBar {
        val progressBar = ProgressBar(this)
        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF3F51B5.toInt())
        return progressBar
    }
}