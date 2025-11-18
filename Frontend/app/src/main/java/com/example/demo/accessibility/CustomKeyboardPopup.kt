package com.example.demo.accessibility

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.example.demo.R

/**
 * Interface for text selection callback
 */
interface TextSelectionListener {
    fun onTextSelected(text: String)
}

/**
 * CustomKeyboardPopup - Enhanced version with loading animations and improved UI
 */
class CustomKeyboardPopup(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var isShowing = false
    
    // Current active tab index
    private var activeTabIndex = 1
    
    // Tab content containers
    private var tabContentContainers: Array<LinearLayout>? = null
    
    // Text selection listener
    private var textSelectionListener: TextSelectionListener? = null
    
    // Chat history provider
    private var chatHistoryProvider: ChatHistoryProvider? = null
    
    // Chat history container (for updating)
    private var chatHistoryContainer: LinearLayout? = null

    // Categories and Products data (for backward compatibility)
    private val categories = listOf(
        "Banking Services",
        "Insurance",
        "Investment", 
        "Loans",
        "Credit Services",
        "Digital Services"
    )
    
    private val products = mapOf(
        "Banking Services" to listOf("HDFC Bank Account", "Current Account", "Savings Account", "NRI Account"),
        "Insurance" to listOf("Life Insurance", "Health Insurance", "Vehicle Insurance", "Travel Insurance"),
        "Investment" to listOf("Mutual Funds", "Fixed Deposits", "SIP", "Portfolio Management"),
        "Loans" to listOf("Home Loan", "Personal Loan", "Car Loan", "Education Loan"),
        "Credit Services" to listOf("Credit Card", "HDFC Credit Card", "Business Credit Card", "Platinum Card"),
        "Digital Services" to listOf("Mobile Banking", "Net Banking", "UPI Services", "Digital Wallet")
    )

    // Selected items tracking
    private var selectedCategory: String? = null
    private var selectedProduct: String? = null

    // Initialize
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    /**
     * Set text selection listener
     */
    fun setTextSelectionListener(listener: TextSelectionListener) {
        this.textSelectionListener = listener
    }
    
    /**
     * Set chat history provider
     */
    fun setChatHistoryProvider(provider: ChatHistoryProvider) {
        this.chatHistoryProvider = provider
    }

    /**
     * Show the custom keyboard popup with the specified dimensions
     */
    fun show(width: Int, height: Int, yOffset: Int, initialTabIndex: Int = 1) {
        if (isShowing) {
            updateSize(width, height, yOffset)
            return
        }

        activeTabIndex = initialTabIndex
        val popupLayout = createPopupLayout()

        val windowParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowParams.gravity = Gravity.BOTTOM
        windowParams.y = yOffset

        try {
            windowManager?.addView(popupLayout, windowParams)
            popupView = popupLayout
            isShowing = true
            
            if (activeTabIndex == 2) {
                showCustomerDetailsContent()
            }
            
            if (activeTabIndex == 1 && chatHistoryProvider is WhatsAppAccessibilityService) {
                val service = chatHistoryProvider as WhatsAppAccessibilityService
                val inputText = service.currentInputText.trim()
                
                if (inputText.isNotBlank() && inputText.length >= 5 && !service.isImprovementRequested) {
                    android.util.Log.d("CustomKeyboardPopup", "Auto-triggering API call for input: '$inputText'")
                    service.requestMessageImprovement(inputText)
                }
            }
            
            if (activeTabIndex == 0 && chatHistoryProvider is WhatsAppAccessibilityService) {
                val service = chatHistoryProvider as WhatsAppAccessibilityService
                if (!service.isRepliesRequested()) {
                    service.requestAiSuggestedReplies()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Create the popup layout with all UI elements
     */
    private fun createPopupLayout(): View {
        val popupLayout = LinearLayout(context)
        popupLayout.orientation = LinearLayout.VERTICAL
        
        // Create gradient background for the entire popup
        val popupBg = GradientDrawable()
        popupBg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        popupBg.colors = intArrayOf(0xFFFAFAFA.toInt(), 0xFFF0F0F0.toInt())
        popupLayout.background = popupBg

        addTopBarWithCloseButton(popupLayout)
        addTabLayout(popupLayout)
        addTabContentContainer(popupLayout)

        return popupLayout
    }

    /**
     * Create an enhanced cross icon view
     */
    private fun createCrossIcon(): View {
        val crossContainer = FrameLayout(context)
        crossContainer.setPadding(4, 4, 4, 4)
        
        // Create cross shape using Path
        val crossView = View(context)
        val crossDrawable = ShapeDrawable()
        
        // Create cross path
        val crossPath = Path()
        val size = 24f
        val strokeWidth = 3f
        
        // First line of cross (top-left to bottom-right)
        crossPath.moveTo(strokeWidth, strokeWidth)
        crossPath.lineTo(size - strokeWidth, size - strokeWidth)
        crossPath.lineTo(size - strokeWidth, size - strokeWidth + strokeWidth)
        crossPath.lineTo(strokeWidth + strokeWidth, size)
        crossPath.lineTo(strokeWidth, size - strokeWidth)
        crossPath.lineTo(size - strokeWidth - strokeWidth, strokeWidth)
        crossPath.close()
        
        // Second line of cross (top-right to bottom-left)
        crossPath.moveTo(size - strokeWidth, strokeWidth)
        crossPath.lineTo(size, strokeWidth + strokeWidth)
        crossPath.lineTo(strokeWidth + strokeWidth, size - strokeWidth)
        crossPath.lineTo(strokeWidth, size)
        crossPath.lineTo(0f, size - strokeWidth)
        crossPath.lineTo(size - strokeWidth - strokeWidth, strokeWidth + strokeWidth)
        crossPath.close()
        
        crossDrawable.shape = PathShape(crossPath, size, size)
        crossDrawable.paint.color = 0xFFE57373.toInt() // Light red color for visibility
        crossDrawable.paint.style = Paint.Style.FILL
        
        crossView.background = crossDrawable
        
        // Set size for the cross
        val crossParams = FrameLayout.LayoutParams(48, 48)
        crossParams.gravity = Gravity.CENTER
        crossContainer.addView(crossView, crossParams)
        
        return crossContainer
    }

    /**
     * Create animated loading spinner
     */
    private fun createLoadingSpinner(): FrameLayout {
        val container = FrameLayout(context)
        
        // Create progress bar
        val progressBar = ProgressBar(context)
        progressBar.isIndeterminate = true
        
        // Style the progress bar
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(0xFF3F51B5.toInt())
        
        val params = FrameLayout.LayoutParams(32, 32)
        params.gravity = Gravity.CENTER
        container.addView(progressBar, params)
        
        return container
    }

    /**
     * Create pulsing dot animation for loading
     */
    private fun createPulsingDots(): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER
        
        // Create 3 dots
        for (i in 0..2) {
            val dot = View(context)
            val dotBg = GradientDrawable()
            dotBg.shape = GradientDrawable.OVAL
            dotBg.setColor(0xFF3F51B5.toInt())
            dot.background = dotBg
            
            val dotParams = LinearLayout.LayoutParams(12, 12)
            dotParams.setMargins(4, 0, 4, 0)
            container.addView(dot, dotParams)
            
            // Add pulsing animation with delay
            val animator = ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f)
            animator.duration = 1200
            animator.repeatCount = ValueAnimator.INFINITE
            animator.startDelay = i * 200L
            animator.start()
        }
        
        return container
    }

    /**
     * Add a top bar with enhanced close button
     */
    private fun addTopBarWithCloseButton(parent: LinearLayout) {
        val topBar = LinearLayout(context)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setBackgroundColor(0xFF3F51B5.toInt())
        topBar.setPadding(16, 12, 16, 12)

        // Enhanced close button with clearer icon
        val closeButton = FrameLayout(context)
        
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(0xFFFFFFFF.toInt())
        drawable.setStroke(2, 0xFFE57373.toInt()) // Light red border to indicate close
        closeButton.background = drawable

        closeButton.setPadding(12, 12, 12, 12)
        closeButton.elevation = 4f

        // Use the enhanced cross icon
        val crossIcon = createCrossIcon()
        val iconParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        iconParams.gravity = Gravity.CENTER
        closeButton.addView(crossIcon, iconParams)

        // Enhanced click effect
        closeButton.setOnClickListener {
            // Scale down animation
            closeButton.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    closeButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction {
                            hide()
                        }
                        .start()
                }
                .start()
        }

        val titleText = TextView(context)
        titleText.text = "GroMo AI Copilot"
        titleText.textSize = 18f
        titleText.setTextColor(Color.WHITE)
        titleText.gravity = Gravity.CENTER
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)

        val titleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleParams.gravity = Gravity.CENTER
        topBar.addView(titleText, 0, titleParams)

        val buttonParams = LinearLayout.LayoutParams(56, 56)
        buttonParams.setMargins(8, 8, 8, 8)
        topBar.addView(closeButton, buttonParams)

        parent.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
    
    /**
     * Add tabs layout with improved colors
     */
    private fun addTabLayout(parent: LinearLayout) {
        val tabLayout = LinearLayout(context)
        tabLayout.orientation = LinearLayout.HORIZONTAL
        
        // Create gradient background for tabs
        val tabBg = GradientDrawable()
        tabBg.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        tabBg.colors = intArrayOf(0xFF3F51B5.toInt(), 0xFF5C6BC0.toInt())
        tabLayout.background = tabBg
        
        val tabTitles = arrayOf("Auto Reply", "Suggestion", "Settings")
        val tabButtons = arrayOfNulls<TextView>(tabTitles.size)
        
        val tabClickListener = View.OnClickListener { view ->
            val clickedTabIndex = view.tag as Int
            
            if (clickedTabIndex != activeTabIndex) {
                activeTabIndex = clickedTabIndex
                
                for (i in tabButtons.indices) {
                    val isActive = i == activeTabIndex
                    tabButtons[i]?.let { button ->
                        button.setTextColor(if (isActive) Color.WHITE else 0xBBFFFFFF.toInt())
                        button.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                        
                        // Enhanced active tab styling
                        if (isActive) {
                            val activeBg = GradientDrawable()
                            activeBg.shape = GradientDrawable.RECTANGLE
                            activeBg.cornerRadius = 25f
                            activeBg.setColor(0x33FFFFFF.toInt())
                            button.background = activeBg
                        } else {
                            button.background = null
                        }
                    }
                }
                
                showTabContent(activeTabIndex)
                
                if (activeTabIndex == 0 && chatHistoryProvider is WhatsAppAccessibilityService) {
                    val service = chatHistoryProvider as WhatsAppAccessibilityService
                    
                    if (!service.isRepliesRequested() || service.getAiSuggestedReplies().isEmpty()) {
                        android.util.Log.d("CustomKeyboardPopup", "Reply tab clicked, requesting suggestions")
                        requestAiSuggestedReplies()
                    } else {
                        android.util.Log.d("CustomKeyboardPopup", "Reply tab clicked, suggestions already requested")
                    }
                }
                
                if (activeTabIndex == 1 && chatHistoryProvider is WhatsAppAccessibilityService) {
                    val service = chatHistoryProvider as WhatsAppAccessibilityService
                    val inputText = service.currentInputText.trim()
                    
                    android.util.Log.d("CustomKeyboardPopup", "Suggestion tab clicked, current input: '$inputText'")
                    
                    if (inputText.isNotBlank() && inputText.length >= 5) {
                        service.checkInputAndRequestImprovement()
                    } else {
                        if (service.improvedMessageSuggestions.isNotEmpty()) {
                            service.improvedMessageSuggestions.clear()
                            refreshSuggestionTab()
                        }
                        
                        android.util.Log.d("CustomKeyboardPopup", "Not requesting improvements - input is empty or too short: '$inputText'")
                    }
                }
                
                if (activeTabIndex == 2) {
                    showCustomerDetailsContent()
                }
                
                android.util.Log.d("CustomKeyboardPopup", "Switched to tab $activeTabIndex: ${tabTitles[activeTabIndex]}")
            }
        }
        
        for (i in tabTitles.indices) {
            val tabButton = TextView(context)
            tabButton.text = tabTitles[i]
            tabButton.gravity = Gravity.CENTER
            tabButton.textSize = 16f
            tabButton.tag = i
            
            val isActive = i == activeTabIndex
            tabButton.setTextColor(if (isActive) Color.WHITE else 0xBBFFFFFF.toInt())
            tabButton.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            
            if (isActive) {
                val activeBg = GradientDrawable()
                activeBg.shape = GradientDrawable.RECTANGLE
                activeBg.cornerRadius = 25f
                activeBg.setColor(0x33FFFFFF.toInt())
                tabButton.background = activeBg
            }
            
            tabButton.setOnClickListener(tabClickListener)
            
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(8, 8, 8, 8)
            tabButton.setPadding(16, 16, 16, 16)
            
            tabButtons[i] = tabButton
            tabLayout.addView(tabButton, params)
        }
        
        parent.addView(tabLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
    
    /**
     * Show content for the selected tab
     */
    private fun showTabContent(index: Int) {
        if (tabContentContainers == null) return
        
        for (i in tabContentContainers!!.indices) {
            tabContentContainers!![i].visibility = if (i == index) View.VISIBLE else View.GONE
        }
        
        when (index) {
            0 -> refreshReplyTab()
            2 -> showCustomerDetailsContent()
        }
    }
    
    /**
     * Add tab content container
     */
    private fun addTabContentContainer(parent: LinearLayout) {
        val contentContainer = FrameLayout(context)
        
        // Create subtle gradient background for content area
        val contentBg = GradientDrawable()
        contentBg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        contentBg.colors = intArrayOf(0xFFFAFAFA.toInt(), 0xFFF5F5F5.toInt())
        contentContainer.background = contentBg
        
        val numTabs = 3
        val containers = Array(numTabs) { LinearLayout(context) }
        
        containers.forEachIndexed { index, container ->
            container.orientation = LinearLayout.VERTICAL
            container.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            container.visibility = if (index == activeTabIndex) View.VISIBLE else View.GONE
            
            when (index) {
                0 -> populateReplyTab(container)
                1 -> populateSuggestionTab(container)
                2 -> populateChatHistoryTab(container)
            }
            
            contentContainer.addView(container)
        }
        
        tabContentContainers = containers
        
        parent.addView(contentContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        
        android.util.Log.d("CustomKeyboardPopup", "Tab content containers initialized, active tab: $activeTabIndex")
    }
    
    /**
     * Request AI-suggested replies
     */
    private fun requestAiSuggestedReplies() {
        if (chatHistoryProvider is WhatsAppAccessibilityService) {
            (chatHistoryProvider as WhatsAppAccessibilityService).requestAiSuggestedReplies()
        }
    }
    
    /**
     * Create enhanced loading layout with animation
     */
    private fun createLoadingLayout(message: String): LinearLayout {
        val loadingLayout = LinearLayout(context)
        loadingLayout.orientation = LinearLayout.VERTICAL
        loadingLayout.gravity = Gravity.CENTER
        loadingLayout.setPadding(32, 32, 32, 32)
        
        // Add pulsing dots
        val dotsContainer = createPulsingDots()
        loadingLayout.addView(dotsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add loading message
        val loadingText = TextView(context)
        loadingText.text = message
        loadingText.textSize = 16f
        loadingText.setTextColor(0xFF3F51B5.toInt())
        loadingText.gravity = Gravity.CENTER
        loadingText.setPadding(0, 16, 0, 0)
        loadingText.setTypeface(loadingText.typeface, Typeface.BOLD)
        
        loadingLayout.addView(loadingText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        return loadingLayout
    }
    
    /**
     * Refresh the Reply tab content with AI-suggested replies
     */
    fun refreshReplyTab() {
        if (isShowing && tabContentContainers != null) {
            val replyContainer = tabContentContainers!![0]
            
            replyContainer.removeAllViews()
            populateReplyTab(replyContainer)
            
            if (activeTabIndex == 0) {
                replyContainer.requestLayout()
                android.util.Log.d("CustomKeyboardPopup", "Reply tab refreshed with new content")
            }
        }
    }
    
    /**
     * Populate Reply tab with enhanced loading states
     */
    private fun populateReplyTab(container: LinearLayout) {
        val scrollView = ScrollView(context)
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(16, 16, 16, 16)
        
        val titleText = TextView(context)
        titleText.text = "AI-Suggested Replies"
        titleText.textSize = 16f
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)
        titleText.setPadding(0, 0, 0, 16)
        contentLayout.addView(titleText)
        
        val suggestedReplies = if (chatHistoryProvider is WhatsAppAccessibilityService) {
            val service = chatHistoryProvider as WhatsAppAccessibilityService
            val replies = service.getAiSuggestedReplies()
            
            val repliesInProgress = service.isRepliesRequested() && replies.isEmpty()
            
            android.util.Log.d("CustomKeyboardPopup", "Got ${replies.size} suggested replies for UI update, inProgress: $repliesInProgress")
            
            replies
        } else {
            emptyList<String>()
        }
        
        if (suggestedReplies.isNotEmpty()) {
            android.util.Log.d("CustomKeyboardPopup", "Showing ${suggestedReplies.size} AI replies in UI")
            
            titleText.text = "AI-Suggested Replies"
            
            for (reply in suggestedReplies) {
                val replyButton = createButtonWithRipple(reply)
                contentLayout.addView(replyButton)
                
                android.util.Log.d("CustomKeyboardPopup", "Added reply to UI: $reply")
            }
        } else {
            val isLoading = chatHistoryProvider is WhatsAppAccessibilityService && 
                           (chatHistoryProvider as WhatsAppAccessibilityService).isRepliesRequested()
            
            if (isLoading) {
                val loadingLayout = createLoadingLayout("Generating smart replies...")
                contentLayout.addView(loadingLayout)
                
                val refreshingInfo = TextView(context)
                refreshingInfo.text = "Analyzing conversation context for personalized suggestions"
                refreshingInfo.textSize = 14f
                refreshingInfo.gravity = Gravity.CENTER
                refreshingInfo.setPadding(16, 8, 16, 32)
                refreshingInfo.setTextColor(0xFF666666.toInt())
                contentLayout.addView(refreshingInfo)
                
                android.util.Log.d("CustomKeyboardPopup", "Showing enhanced loading state for AI replies")
            } else {
                val emptyText = TextView(context)
                emptyText.text = "No AI replies available"
                emptyText.textSize = 14f
                emptyText.gravity = Gravity.CENTER
                emptyText.setPadding(16, 32, 16, 32)
                contentLayout.addView(emptyText)
                
                android.util.Log.d("CustomKeyboardPopup", "No AI replies available, showing message")
            }
        }
        
        scrollView.addView(contentLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }
    
    /**
     * Populate Suggestion tab
     */
    private fun populateSuggestionTab(container: LinearLayout) {
        updateSuggestionTabContent(container)
    }
    
    /**
     * Populate Chat History tab
     */
    private fun populateChatHistoryTab(container: LinearLayout) {
        val scrollView = ScrollView(context)
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(16, 16, 16, 16)
        
        chatHistoryContainer = contentLayout
        showCustomerDetailsContent(contentLayout)
        
        scrollView.addView(contentLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }
    
    /**
     * Show customer details content with action buttons
     */
    private fun showCustomerDetailsContent(container: LinearLayout? = null) {
        val targetContainer = container ?: chatHistoryContainer ?: return
        
        targetContainer.removeAllViews()
        
        val chatName = chatHistoryProvider?.getCurrentChatName() ?: "Unknown Contact"
        
        val selectionRow = LinearLayout(context)
        selectionRow.orientation = LinearLayout.HORIZONTAL
        selectionRow.gravity = Gravity.CENTER_HORIZONTAL
        
        val selectCategoryButton = createCompactActionButton(
            "Category",
            selectedCategory ?: "None Selected",
            "📁"
        ) {
            showCategorySelection(targetContainer)
        }
        
        val selectProductButton = createCompactActionButton(
            "Product",
            if (selectedCategory == null) {
                "Tap to select"
            } else {
                selectedProduct ?: "None Selected"
            },
            "📦"
        ) {
            showProductSelection(targetContainer)
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        buttonParams.setMargins(12, 0, 12, 0)
        
        selectionRow.addView(selectCategoryButton, buttonParams)
        selectionRow.addView(selectProductButton, buttonParams)
        
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.setMargins(0, 20, 0, 32)
        targetContainer.addView(selectionRow, rowParams)
        
        val chatHistoryButton = createCompactActionButton(
            "Chat History",
            "Customer: $chatName",
            "💬"
        ) {
            showActualChatMessages(targetContainer)
        }
        targetContainer.addView(chatHistoryButton)
    }
    
    /**
     * Show category selection grid
     */
    private fun showCategorySelection(container: LinearLayout) {
        container.removeAllViews()
        
        // Create header row with back arrow and title
        val headerRow = LinearLayout(context)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.setPadding(16, 8, 16, 8)
        
        // Clean back arrow button without background
        val backArrow = TextView(context)
        backArrow.text = "←"
        backArrow.textSize = 28f
        backArrow.setTextColor(0xFF3F51B5.toInt())
        backArrow.setPadding(8, 8, 16, 8)
        backArrow.setTypeface(backArrow.typeface, Typeface.BOLD)
        backArrow.gravity = Gravity.CENTER
        
        backArrow.setOnClickListener {
            // Add press animation
            backArrow.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    backArrow.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    showCustomerDetailsContent(container)
                }
                .start()
        }
        
        val categoriesTitle = TextView(context)
        categoriesTitle.text = "Select Category"
        categoriesTitle.textSize = 18f
        categoriesTitle.setTypeface(categoriesTitle.typeface, Typeface.BOLD)
        categoriesTitle.setTextColor(0xFF333333.toInt())
        categoriesTitle.gravity = Gravity.CENTER
        
        // Add back arrow
        headerRow.addView(backArrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add title with weight to center it better
        val titleParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        headerRow.addView(categoriesTitle, titleParams)
        
        container.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        val gridLayout = LinearLayout(context)
        gridLayout.orientation = LinearLayout.VERTICAL
        gridLayout.setPadding(8, 4, 8, 4)
        
        val categories = listOf("Banking", "Insurance", "Investment", "Credit", "Loan")
        
        val categoriesPerRow = 2
        for (i in categories.indices step categoriesPerRow) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.gravity = Gravity.CENTER_HORIZONTAL
            
            for (j in 0 until categoriesPerRow) {
                val categoryIndex = i + j
                if (categoryIndex < categories.size) {
                    val category = categories[categoryIndex]
                    val categoryItem = createGridSelectionItem(category, "Category") { selectedCategoryName ->
                        android.util.Log.d("CustomKeyboardPopup", "Selected category: $selectedCategoryName")
                        this.selectedCategory = selectedCategoryName
                        this.selectedProduct = null
                        showCustomerDetailsContent(container)
                    }
                    
                    val itemParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    itemParams.setMargins(4, 4, 4, 4)
                    rowLayout.addView(categoryItem, itemParams)
                } else {
                    val emptySpace = View(context)
                    val emptyParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    rowLayout.addView(emptySpace, emptyParams)
                }
            }
            
            gridLayout.addView(rowLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        
        container.addView(gridLayout)
    }

    /**
     * Show product selection grid
     */
    private fun showProductSelection(container: LinearLayout) {
        android.util.Log.d("CustomKeyboardPopup", "=== PRODUCT SELECTION STARTED ===")
        android.util.Log.d("CustomKeyboardPopup", "Selected category: $selectedCategory")
        
        container.removeAllViews()
        
        if (selectedCategory == null) {
            val headerRow = LinearLayout(context)
            headerRow.orientation = LinearLayout.HORIZONTAL
            headerRow.gravity = Gravity.CENTER_VERTICAL
            headerRow.setPadding(16, 8, 16, 8)
            
            val backArrow = TextView(context)
            backArrow.text = "←"
            backArrow.textSize = 28f
            backArrow.setTextColor(0xFF3F51B5.toInt())
            backArrow.setPadding(8, 8, 16, 8)
            backArrow.setTypeface(backArrow.typeface, Typeface.BOLD)
            backArrow.gravity = Gravity.CENTER
            
            backArrow.setOnClickListener {
                showCustomerDetailsContent(container)
            }
            
            headerRow.addView(backArrow)
            container.addView(headerRow)
            
            val warningText = TextView(context)
            warningText.text = "⚠️ Please select a category first!"
            warningText.textSize = 18f
            warningText.setTextColor(0xFFFF9500.toInt())
            warningText.setPadding(16, 32, 16, 32)
            warningText.gravity = Gravity.CENTER
            container.addView(warningText)
            return
        }
        
        val headerRow = LinearLayout(context)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.setPadding(16, 8, 16, 8)
        
        val backArrow = TextView(context)
        backArrow.text = "←"
        backArrow.textSize = 28f
        backArrow.setTextColor(0xFF3F51B5.toInt())
        backArrow.setPadding(8, 8, 16, 8)
        backArrow.setTypeface(backArrow.typeface, Typeface.BOLD)
        backArrow.gravity = Gravity.CENTER
        
        backArrow.setOnClickListener {
            showCustomerDetailsContent(container)
        }
        
        val titleText = TextView(context)
        titleText.text = "Products for: $selectedCategory"
        titleText.textSize = 18f
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)
        titleText.setTextColor(0xFF333333.toInt())
        titleText.gravity = Gravity.CENTER
        
        // Add back arrow
        headerRow.addView(backArrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add title with weight to center it better
        val titleParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        headerRow.addView(titleText, titleParams)
        
        container.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        val productsForCategory = when (selectedCategory) {
            "Banking" -> {
                android.util.Log.d("CustomKeyboardPopup", "Showing Banking products")
                listOf("Current Account", "Savings Account", "Banking Credit Card")
            }
            "Insurance" -> {
                android.util.Log.d("CustomKeyboardPopup", "Showing Insurance products")
                listOf("Life Insurance", "Health Insurance", "Car Insurance")
            }
            "Investment" -> {
                android.util.Log.d("CustomKeyboardPopup", "Showing Investment products")
                listOf("Mutual Fund", "SIP", "Fixed Deposit")
            }
            "Credit" -> {
                android.util.Log.d("CustomKeyboardPopup", "Showing Credit products")
                listOf("Credit Card", "Credit Score", "Credit Report")
            }
            "Loan" -> {
                android.util.Log.d("CustomKeyboardPopup", "Showing Loan products")
                listOf("Personal Loan", "Home Loan", "Car Loan")
            }
            else -> {
                android.util.Log.e("CustomKeyboardPopup", "Unknown category: $selectedCategory")
                emptyList<String>()
            }
        }
        
        android.util.Log.d("CustomKeyboardPopup", "Products to show: $productsForCategory")
        
        if (productsForCategory.isEmpty()) {
            val noProductsText = TextView(context)
            noProductsText.text = "No products available"
            noProductsText.textSize = 16f
            noProductsText.setTextColor(0xFF666666.toInt())
            noProductsText.setPadding(16, 32, 16, 32)
            noProductsText.gravity = Gravity.CENTER
            container.addView(noProductsText)
            return
        }
        
        val gridLayout = LinearLayout(context)
        gridLayout.orientation = LinearLayout.VERTICAL
        gridLayout.setPadding(8, 4, 8, 4)
        
        val productsPerRow = 2
        for (i in productsForCategory.indices step productsPerRow) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.gravity = Gravity.CENTER_HORIZONTAL
            
            for (j in 0 until productsPerRow) {
                val productIndex = i + j
                if (productIndex < productsForCategory.size) {
                    val productName = productsForCategory[productIndex]
                    
                    android.util.Log.d("CustomKeyboardPopup", "Adding product button: $productName")
                    
                    val productButton = createGridSelectionItem(productName, "Product") { selectedProductName ->
                        android.util.Log.d("CustomKeyboardPopup", "=== PRODUCT SELECTED ===")
                        android.util.Log.d("CustomKeyboardPopup", "Selected product: $selectedProductName")
                        android.util.Log.d("CustomKeyboardPopup", "For category: $selectedCategory")
                        
                        this.selectedProduct = selectedProductName
                        showCustomerDetailsContent(container)
                    }
                    
                    val itemParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    itemParams.setMargins(4, 4, 4, 4)
                    rowLayout.addView(productButton, itemParams)
                } else {
                    val emptySpace = View(context)
                    val emptyParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    rowLayout.addView(emptySpace, emptyParams)
                }
            }
            
            gridLayout.addView(rowLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        
        container.addView(gridLayout)
        android.util.Log.d("CustomKeyboardPopup", "=== PRODUCT SELECTION COMPLETED ===")
    }

    /**
     * Show actual chat messages
     */
    private fun showActualChatMessages(container: LinearLayout) {
        container.removeAllViews()
        
        val headerRow = LinearLayout(context)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.setPadding(16, 8, 16, 8)
        
        val backArrow = TextView(context)
        backArrow.text = "←"
        backArrow.textSize = 28f
        backArrow.setTextColor(0xFF3F51B5.toInt())
        backArrow.setPadding(8, 8, 16, 8)
        backArrow.setTypeface(backArrow.typeface, Typeface.BOLD)
        backArrow.gravity = Gravity.CENTER
        
        backArrow.setOnClickListener {
            showCustomerDetailsContent(container)
        }
        
        val messagesTitle = TextView(context)
        messagesTitle.text = "Chat History"
        messagesTitle.textSize = 18f
        messagesTitle.setTypeface(messagesTitle.typeface, Typeface.BOLD)
        messagesTitle.setTextColor(0xFF333333.toInt())
        messagesTitle.gravity = Gravity.CENTER
        
        headerRow.addView(backArrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // Add title with weight to center it better
        val titleParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        headerRow.addView(messagesTitle, titleParams)
        
        container.addView(headerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        val messages = try {
            chatHistoryProvider?.getCurrentChatHistory() ?: emptyList<ChatMessage>()
        } catch (e: Exception) {
            emptyList<ChatMessage>()
        }
        
        val stringMessages = if (messages.isEmpty()) {
            try {
                val method = chatHistoryProvider?.javaClass?.getMethod("getChatHistory")
                @Suppress("UNCHECKED_CAST")
                method?.invoke(chatHistoryProvider) as? List<String> ?: emptyList<String>()
            } catch (e: Exception) {
                emptyList<String>()
            }
        } else {
            emptyList<String>()
        }
        
        if (messages.isEmpty() && stringMessages.isEmpty()) {
            val defaultMessages = listOf(
                "Hello! How can I help you today?",
                "I'm interested in your banking services.",
                "Great! Let me show you our account options.",
                "What documents do I need?",
                "You'll need ID proof and address proof."
            )
            
            val messagesScrollView = ScrollView(context)
            val messagesContainer = LinearLayout(context)
            messagesContainer.orientation = LinearLayout.VERTICAL
            messagesContainer.setPadding(16, 8, 16, 16)
            
            defaultMessages.forEach { message ->
                val messageView = createMessageBubble(message)
                messagesContainer.addView(messageView)
            }
            
            messagesScrollView.addView(messagesContainer)
            
            val scrollParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
            container.addView(messagesScrollView, scrollParams)
        } else if (messages.isNotEmpty()) {
            refreshChatHistoryContent(container)
        } else {
            val messagesScrollView = ScrollView(context)
            val messagesContainer = LinearLayout(context)
            messagesContainer.orientation = LinearLayout.VERTICAL
            messagesContainer.setPadding(16, 8, 16, 16)
            
            stringMessages.forEach { message ->
                val messageView = createMessageBubble(message)
                messagesContainer.addView(messageView)
            }
            
            messagesScrollView.addView(messagesContainer)
            
            val scrollParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
            container.addView(messagesScrollView, scrollParams)
        }
    }

    /**
     * Refresh chat history content
     */
    private fun refreshChatHistoryContent(container: LinearLayout? = null) {
        val targetContainer = container ?: chatHistoryContainer ?: return
        val chatHistoryProvider = this.chatHistoryProvider ?: return
        
        val chatName = chatHistoryProvider.getCurrentChatName() ?: "Unknown Contact"
        val chatHistory = try {
            chatHistoryProvider.getCurrentChatHistory()
        } catch (e: Exception) {
            emptyList<ChatMessage>()
        }
        
        if (chatHistory.isEmpty()) {
            val emptyText = TextView(context)
            emptyText.text = "No chat history available for $chatName"
            emptyText.textSize = 16f
            emptyText.gravity = Gravity.CENTER
            emptyText.setPadding(16, 32, 16, 32)
            emptyText.setTextColor(0xFF757575.toInt())
            
            targetContainer.addView(emptyText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            return
        }
        
        val groupedMessages = groupMessagesByTime(chatHistory)
        
        groupedMessages.forEach { (timeGroup: String, groupMessages: List<ChatMessage>) ->
            val headerText = TextView(context)
            headerText.text = timeGroup
            headerText.textSize = 14f
            headerText.setTypeface(headerText.typeface, Typeface.BOLD)
            headerText.setTextColor(0xFF3F51B5.toInt())
            headerText.setPadding(8, 16, 8, 8)
            
            val headerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            targetContainer.addView(headerText, headerParams)
            
            groupMessages.forEach { chatMessage ->
                val historyItem = createHistoryItemFromMessage(chatMessage)
                targetContainer.addView(historyItem)
            }
        }
        
        val paddingView = View(context)
        val paddingParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            100
        )
        targetContainer.addView(paddingView, paddingParams)
    }
    
    /**
     * Group chat messages by time periods
     */
    private fun groupMessagesByTime(messages: List<ChatMessage>): Map<String, List<ChatMessage>> {
        val currentTime = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val threeDays = 3 * oneDay
        val oneWeek = 7 * oneDay
        
        return messages.groupBy { chatMessage ->
            val messageTime = chatMessage.timestampMillis
            val timeDiff = currentTime - messageTime

            when {
                timeDiff < oneDay -> "Today"
                timeDiff < threeDays -> "Recent"
                timeDiff < oneWeek -> "This Week"
                else -> "Older"
            }
        }
    }
    
    /**
     * Create a history item from a ChatMessage
     */
    private fun createHistoryItemFromMessage(chatMessage: ChatMessage): LinearLayout {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(16, 12, 16, 12)
        
        val itemBg = GradientDrawable()
        itemBg.shape = GradientDrawable.RECTANGLE
        itemBg.cornerRadius = 8f
        
        if (chatMessage.isIncoming) {
            itemBg.setColor(0xFFFFFFFF.toInt())
        } else {
            itemBg.setColor(0xFFE1F5FE.toInt())
        }
        
        item.background = itemBg
        
        val headerLayout = LinearLayout(context)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.gravity = Gravity.CENTER_VERTICAL
        
        val senderText = TextView(context)
        senderText.text = if (chatMessage.isIncoming) chatMessage.contactName else "You"
        senderText.textSize = 12f
        senderText.setTypeface(senderText.typeface, Typeface.BOLD)
        senderText.setTextColor(if (chatMessage.isIncoming) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt())
        
        val timeText = TextView(context)
        timeText.text = chatMessage.timestamp
        timeText.textSize = 12f
        timeText.setTextColor(0xFF757575.toInt())
        
        val senderParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        headerLayout.addView(senderText, senderParams)
        
        headerLayout.addView(timeText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        val headerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        headerParams.bottomMargin = 4
        item.addView(headerLayout, headerParams)
        
        val messageText = TextView(context)
        messageText.text = chatMessage.text
        messageText.textSize = 14f
        messageText.setTextColor(0xFF333333.toInt())
        
        item.addView(messageText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        item.setOnClickListener {
            val originalColor = if (chatMessage.isIncoming) 0xFFFFFFFF.toInt() else 0xFFE1F5FE.toInt()
            val highlightColor = 0xFFE3F2FD.toInt()
            
            itemBg.setColor(highlightColor)
            
            item.postDelayed({
                itemBg.setColor(originalColor)
            }, 200)
        }
        
        val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        if (chatMessage.isIncoming) {
            itemParams.setMargins(0, 0, 48, 12)
        } else {
            itemParams.setMargins(48, 0, 0, 12)
        }
        
        item.layoutParams = itemParams
        
        return item
    }

    /**
     * Create message bubble
     */
    private fun createMessageBubble(message: String): View {
        val bubble = TextView(context)
        bubble.text = message
        bubble.textSize = 14f
        bubble.setTextColor(0xFF333333.toInt())
        bubble.setPadding(12, 8, 12, 8)
        
        val bubbleBg = GradientDrawable()
        bubbleBg.shape = GradientDrawable.RECTANGLE
        bubbleBg.cornerRadius = 8f
        bubbleBg.setColor(Color.WHITE)
        bubbleBg.setStroke(1, 0xFFE0E0E0.toInt())
        bubble.background = bubbleBg
        
        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bubbleParams.setMargins(0, 4, 0, 4)
        bubble.layoutParams = bubbleParams
        
        return bubble
    }

    /**
     * Create enhanced compact action button with animations
     */
    private fun createCompactActionButton(
        title: String, 
        selectedValue: String,
        icon: String,
        onClick: () -> Unit
    ): LinearLayout {
        val button = LinearLayout(context)
        button.orientation = LinearLayout.HORIZONTAL
        button.setPadding(20, 36, 20, 36)
        button.gravity = Gravity.CENTER
        
        val buttonBg = GradientDrawable()
        buttonBg.shape = GradientDrawable.RECTANGLE
        buttonBg.cornerRadius = 16f
        buttonBg.setColor(0xFFF0F8FF.toInt())
        buttonBg.setStroke(2, 0xFF9E9E9E.toInt())
        button.background = buttonBg
        
        button.elevation = 2f
        
        val iconText = TextView(context)
        iconText.text = icon
        iconText.textSize = 24f
        iconText.gravity = Gravity.CENTER
        iconText.setTextColor(0xFF3F51B5.toInt())
        iconText.setPadding(0, 0, 16, 0)
        
        val textLayout = LinearLayout(context)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.gravity = Gravity.CENTER
        
        val titleText = TextView(context)
        titleText.text = title
        titleText.textSize = 16f
        titleText.setTypeface(titleText.typeface, Typeface.BOLD)
        titleText.setTextColor(0xFF2C2C2C.toInt())
        titleText.gravity = Gravity.CENTER
        
        val selectedText = TextView(context)
        when {
            selectedValue.isNotEmpty() && selectedValue != "None Selected" -> {
                selectedText.text = selectedValue
                selectedText.textSize = 13f
                selectedText.setTextColor(0xFF3F51B5.toInt())
                selectedText.setPadding(0, 4, 0, 0)
                selectedText.maxLines = 2
                selectedText.gravity = Gravity.CENTER
                selectedText.setTypeface(selectedText.typeface, Typeface.NORMAL)
            }
            else -> {
                selectedText.text = "Tap to select"
                selectedText.textSize = 13f
                selectedText.setTextColor(0xFF3F51B5.toInt())
                selectedText.setPadding(0, 4, 0, 0)
                selectedText.gravity = Gravity.CENTER
            }
        }
        
        textLayout.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        textLayout.addView(selectedText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        button.addView(iconText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        button.addView(textLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        button.setOnClickListener {
            // Enhanced click animation
            button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    buttonBg.setColor(0xFFF0F4FF.toInt())
                    buttonBg.setStroke(2, 0xFF3F51B5.toInt())
                    button.elevation = 4f
                    onClick()
                    
                    button.postDelayed({
                        buttonBg.setColor(0xFFF0F8FF.toInt())
                        buttonBg.setStroke(2, 0xFF9E9E9E.toInt())
                        button.elevation = 2f
                    }, 150)
                }
                .start()
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(0, 0, 0, 24)
        button.layoutParams = buttonParams
        
        return button
    }

    /**
     * Create enhanced grid selection item with better animations
     */
    private fun createGridSelectionItem(
        itemName: String,
        itemType: String,
        onSelect: (String) -> Unit
    ): LinearLayout {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.HORIZONTAL
        item.setPadding(20, 20, 20, 20)
        item.gravity = Gravity.CENTER_VERTICAL
        
        val itemBg = GradientDrawable()
        itemBg.shape = GradientDrawable.RECTANGLE
        itemBg.cornerRadius = 12f
        itemBg.setColor(Color.WHITE)
        itemBg.setStroke(3, 0xFF999999.toInt())
        item.background = itemBg
        
        val iconText = TextView(context)
        iconText.text = if (itemType == "Category") "📁" else "📦"
        iconText.textSize = 22f
        iconText.gravity = Gravity.CENTER
        iconText.setPadding(0, 0, 12, 0)
        
        val nameText = TextView(context)
        nameText.text = itemName
        nameText.textSize = 15f
        nameText.setTextColor(0xFF333333.toInt())
        nameText.gravity = Gravity.CENTER_VERTICAL
        nameText.maxLines = 2
        nameText.setTypeface(nameText.typeface, Typeface.BOLD)
        
        val iconParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        item.addView(iconText, iconParams)
        
        val textParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        item.addView(nameText, textParams)
        
        item.setOnClickListener {
            // Enhanced selection animation
            item.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    item.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    itemBg.setColor(0xFFE3F2FD.toInt())
                    itemBg.setStroke(3, 0xFF3F51B5.toInt())
                    
                    onSelect(itemName)
                    
                    item.postDelayed({
                        itemBg.setColor(Color.WHITE)
                        itemBg.setStroke(3, 0xFF999999.toInt())
                    }, 200)
                }
                .start()
        }
        
        return item
    }

    /**
     * Create a button with enhanced ripple-like effect and light blue background
     */
    private fun createButtonWithRipple(text: String): LinearLayout {
        val button = LinearLayout(context)
        button.orientation = LinearLayout.HORIZONTAL
        button.gravity = Gravity.CENTER_VERTICAL
        button.setPadding(16, 20, 16, 20) // Increased vertical padding
        
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 12f
        bg.setColor(0xFFE3F2FD.toInt()) // Light blue background
        bg.setStroke(2, 0xFF2196F3.toInt()) // Blue border
        button.background = bg
        button.elevation = 2f
        
        val buttonText = TextView(context)
        buttonText.text = text
        buttonText.textSize = 15f
        buttonText.setTextColor(0xFF1565C0.toInt()) // Darker blue text for better readability
        buttonText.lineHeight = (buttonText.textSize * 1.4f).toInt()
        
        val textParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        button.addView(buttonText, textParams)
        
        button.setOnClickListener {
            // Enhanced button press animation
            button.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .withEndAction {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    bg.setColor(0xFFBBDEFB.toInt()) // Darker blue on press
                    bg.setStroke(2, 0xFF1976D2.toInt())
                    button.elevation = 4f
                    
                    textSelectionListener?.onTextSelected(text)
                    hide()
                    
                    button.postDelayed({
                        bg.setColor(0xFFE3F2FD.toInt())
                        bg.setStroke(2, 0xFF2196F3.toInt())
                        button.elevation = 2f
                    }, 200)
                }
                .start()
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(0, 0, 0, 16) // Increased bottom margin
        button.layoutParams = buttonParams
        
        return button
    }
    
    /**
     * Create a card-like layout with enhanced animations and light blue styling
     */
    private fun createCardLayout(text: String): LinearLayout {
        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(16, 20, 16, 20) // Increased padding
        
        val cardBg = GradientDrawable()
        cardBg.shape = GradientDrawable.RECTANGLE
        cardBg.cornerRadius = 12f
        cardBg.setColor(0xFFE3F2FD.toInt()) // Light blue background
        cardBg.setStroke(2, 0xFF2196F3.toInt()) // Blue border
        card.background = cardBg
        card.elevation = 2f
        
        val cardText = TextView(context)
        cardText.text = text
        cardText.textSize = 15f
        cardText.setTextColor(0xFF1565C0.toInt()) // Darker blue text
        cardText.lineHeight = (cardText.textSize * 1.4f).toInt()
        
        card.addView(cardText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        card.setOnClickListener {
            // Enhanced card selection animation
            card.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .withEndAction {
                    card.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    cardBg.setColor(0xFFBBDEFB.toInt()) // Darker blue on press
                    cardBg.setStroke(2, 0xFF1976D2.toInt())
                    card.elevation = 4f
                    
                    textSelectionListener?.onTextSelected(text)
                    hide()
                    
                    card.postDelayed({
                        cardBg.setColor(0xFFE3F2FD.toInt())
                        cardBg.setStroke(2, 0xFF2196F3.toInt())
                        card.elevation = 2f
                    }, 200)
                }
                .start()
        }
        
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 0, 0, 16)
        card.layoutParams = cardParams
        
        return card
    }

    /**
     * Update the popup size and position
     */
    fun updateSize(width: Int, height: Int, yOffset: Int) {
        if (!isShowing || popupView == null) return

        try {
            val params = popupView?.layoutParams as WindowManager.LayoutParams
            params.width = width
            params.height = height
            params.y = yOffset
            windowManager?.updateViewLayout(popupView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hide and remove the popup
     */
    fun hide() {
        if (isShowing && popupView != null) {
            try {
                windowManager?.removeView(popupView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                popupView = null
                isShowing = false
                activeTabIndex = 1
            }
        }
    }

    fun handleBackPress() {
        hide()
    }

    fun isVisible(): Boolean {
        return isShowing
    }

    fun refreshChatHistory() {
        if (isShowing && activeTabIndex == 2) {
            showCustomerDetailsContent()
        }
    }

    fun getCurrentTabIndex(): Int {
        return activeTabIndex
    }
    
    fun refreshSuggestionTab() {
        if (!isShowing || tabContentContainers == null) return
        
        val suggestionContainer = tabContentContainers?.get(1) ?: return
        updateSuggestionTabContent(suggestionContainer)
        
        android.util.Log.d("CustomKeyboardPopup", "Refreshed suggestion tab with new content")
    }
    
    /**
     * Update the content of the suggestion tab with enhanced loading states
     */
    private fun updateSuggestionTabContent(container: LinearLayout) {
        container.removeAllViews()
        
        val scrollView = ScrollView(context)
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(16, 16, 16, 16)
        
        val currentInput = if (chatHistoryProvider != null && chatHistoryProvider!!::class.simpleName == "WhatsAppAccessibilityService") {
            try {
                val field = chatHistoryProvider!!::class.java.getDeclaredField("currentInputText")
                field.isAccessible = true
                (field.get(chatHistoryProvider!!) as? String)?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        
        val hasValidInput = currentInput.isNotBlank() && currentInput.length >= 5
        
        val improvedSuggestions = if (chatHistoryProvider != null && chatHistoryProvider!!::class.simpleName == "WhatsAppAccessibilityService") {
            try {
                val field = chatHistoryProvider!!::class.java.getDeclaredField("improvedMessageSuggestions")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (field.get(chatHistoryProvider!!) as? MutableList<String>)?.toList() ?: emptyList<String>()
            } catch (e: Exception) {
                emptyList<String>()
            }
        } else {
            emptyList<String>()
        }
        
        if (improvedSuggestions.isNotEmpty()) {
            for (suggestion in improvedSuggestions) {
                val suggestionCard = createCardLayout(suggestion)
                contentLayout.addView(suggestionCard)
            }
        } 
        else if (hasValidInput) {
            val isRequesting = if (chatHistoryProvider is WhatsAppAccessibilityService) {
                (chatHistoryProvider as WhatsAppAccessibilityService).isImprovementRequested
            } else {
                false
            }
            
            if (isRequesting) {
                val loadingLayout = createLoadingLayout("Crafting better suggestions...")
                contentLayout.addView(loadingLayout)
            } else {
                val readyText = TextView(context)
                readyText.text = "Ready to improve your draft message."
                readyText.textSize = 14f
                readyText.gravity = Gravity.CENTER
                readyText.setPadding(16, 32, 16, 32)
                contentLayout.addView(readyText)
            }
            
            val inputPreview = TextView(context)
            inputPreview.text = "Your draft: \"$currentInput\""
            inputPreview.textSize = 14f
            inputPreview.setTextColor(0xFF666666.toInt())
            inputPreview.setPadding(16, 16, 16, 16)
            
            val previewBg = GradientDrawable()
            previewBg.shape = GradientDrawable.RECTANGLE
            previewBg.cornerRadius = 8f
            previewBg.setColor(0xFFF8F9FA.toInt())
            previewBg.setStroke(1, 0xFFE0E0E0.toInt())
            inputPreview.background = previewBg
            
            contentLayout.addView(inputPreview)
        }
        else {
            val infoText = TextView(context)
            infoText.text = "Start typing in the WhatsApp input field to get improved message suggestions."
            infoText.textSize = 14f
            infoText.gravity = Gravity.CENTER
            infoText.setPadding(16, 32, 16, 32)
            contentLayout.addView(infoText)
        }
        
        scrollView.addView(contentLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }
}