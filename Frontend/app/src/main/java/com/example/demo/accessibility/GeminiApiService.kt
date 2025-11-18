package com.example.demo.accessibility

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Interface for handling Gemini API responses with suggested replies
 */
interface GeminiResponseListener {
    fun onSuggestedRepliesReceived(replies: List<String>)
    fun onError(error: String)
}

/**
 * Interface for handling chat analysis responses
 */
interface ChatAnalysisListener {
    fun onAnalysisReceived(analysis: String)
    fun onError(error: String)
}

/**
 * Service class for interacting with the Gemini API
 */
class GeminiApiService(private val apiKey: String, private val context: Context) {
    
    // Gemini model name - using specifically Gemini 2.0 Flash-Lite
    private val MODEL_NAME = "gemini-2.0-flash-lite"
    
    // Create model instance
    private val generativeModel = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey
    )
    
    /**
     * Improve a partially typed message and suggest better versions
     * @param partialMessage The user's partial/draft message
     * @param conversationContext The recent chat history for context
     * @param listener Callback for receiving improved message suggestions
     */
    fun getImprovedMessageSuggestions(partialMessage: String, conversationContext: String, listener: GeminiResponseListener) {
        // Only proceed if the partial message has some content
        if (partialMessage.isBlank()) {
            listener.onError("Partial message is empty")
            return
        }
        
        val prompt = """
Help improve this message the user is typing.

CHAT CONTEXT: $conversationContext

USER'S DRAFT: "$partialMessage"

TASK: Give 3-5 better versions of their message.

IMPROVE BY:
- Fix typos/grammar naturally
- Make it clearer and easier to read
- Complete it if it seems unfinished
- Keep their original tone and meaning
- Make it flow better

KEEP IT NATURAL:
- Don't make it too formal unless they were being formal
- Don't change their style completely 
- If it's casual chat, keep it casual
- If it's business, make it professional
- Match the conversation context

FOCUS ON:
- What they actually want to say
- Making it sound natural for this conversation
- Keeping their personality in the message
- Making sure it makes sense in context

Give practical improvements that actually help them communicate better.

Return ONLY a JSON array with your improved message options:
{"replies": ["First improved version", "Second improved version", "Third improved version"]}
""".trimIndent()
        
        // Log the model and prompt being used
        Log.d("GeminiAPI", "Using model: $MODEL_NAME for message improvement")
        Log.d("GeminiAPI", "Improving partial message: \"$partialMessage\"")
        Log.d("GeminiAPI", "===== COMPLETE PROMPT FOR MESSAGE IMPROVEMENT =====")
        Log.d("GeminiAPI", prompt)
        Log.d("GeminiAPI", "===== END OF PROMPT =====")
        
        // Create a coroutine scope for async operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Make the API call to Gemini
                val response = generativeModel.generateContent(
                    content {
                        text(prompt)
                    }
                )
                
                // Get response text
                val responseText = response.text?.trim() ?: ""
                Log.d("GeminiAPI", "===== COMPLETE RAW RESPONSE FROM GEMINI =====")
                Log.d("GeminiAPI", responseText)
                Log.d("GeminiAPI", "===== END OF RAW RESPONSE =====")
                
                // Parse the response to extract suggested replies
                val improvements = parseGeminiResponse(responseText)
                
                // Log parsed improvements
                Log.d("GeminiAPI", "Parsed ${improvements.size} improved suggestions:")
                improvements.forEachIndexed { index, suggestion ->
                    Log.d("GeminiAPI", "Improvement ${index+1}: $suggestion")
                }
                
                // Return results on the main thread
                withContext(Dispatchers.Main) {
                    listener.onSuggestedRepliesReceived(improvements)
                }
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Error getting message improvements", e)
                withContext(Dispatchers.Main) {
                    listener.onError("Failed to improve message: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send chat history to Gemini API and get suggested replies
     * @param conversationText The chat history in conversation format
     */
    fun getSuggestedReplies(conversationText: String, listener: GeminiResponseListener) {
        // Log that we're starting the API request
        Log.d("GeminiAPI", "Starting API request for suggested replies")
        
        // Create the prompt for Gemini
       val prompt = """
You are a GroMo Partner chatting with a customer on WhatsApp. About GroMo: It is a fintech platform in India that empowers individuals to earn money by selling financial products like insurance, loans, and credit cards. It aims to create a network of micro-entrepreneurs, providing them with the tools to succeed in the financial services sector. and you are gromo patner

CONVERSATION: $conversationText

Your job: Generate 5 natural reply options for what the customer just said.

HOW TO RESPOND:
- If they're worried/skeptical → reassure with facts, don't oversell
- If they're asking technical questions → give clear, simple answers  
- If they want safer options → suggest alternatives, explain benefits
- If they're ready to proceed → guide next steps, keep momentum
- If they have doubts → address directly, share relevant examples

KEEP IT REAL:
- Talk like a helpful local advisor who knows GroMo products well
- Use normal WhatsApp language (short, friendly, mix hindi-english naturally)
- Answer their actual question first, then mention 1-2 GroMo benefits if relevant
- Don't sound like you're reading from a script

GROMO BENEFITS TO MENTION WHEN RELEVANT:
- Mutual Funds: Historical performance, balanced approach, no lock-in
- Savings Account: 7% interest, zero balance, instant access
- Credit Cards: No fees, good rewards, easy approval
- General: Transparent pricing, SEBI regulated, good customer ratings

BE NATURAL:
- Some replies can be short and direct
- Some can share more details if needed
- Match the customer's energy, concern level and in user curretly message tone
- Sound genuinely helpful, not pushy, 

Generate replies that feel like what you would actually say to help this specific customer with their specific concern right now.

{"replies": ["Reply 1", "Reply 2", "Reply 3", "Reply 4", "Reply 5"]}
""".trimIndent()
        
        // Log the model and prompt being used
        Log.d("GeminiAPI", "Using model: $MODEL_NAME")
        Log.d("GeminiAPI", "===== COMPLETE PROMPT BEING SENT TO GEMINI =====")
        Log.d("GeminiAPI", prompt)
        Log.d("GeminiAPI", "===== END OF PROMPT =====")
        
        // Create a coroutine scope for async operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log that we're making the API call
                Log.d("GeminiAPI", "Making API call to Gemini for suggested replies")
                
                // Make the API call to Gemini
                val response = generativeModel.generateContent(
                    content {
                        text(prompt)
                    }
                )
                
                // Get response text
                val responseText = response.text?.trim() ?: ""
                Log.d("GeminiAPI", "===== COMPLETE RAW RESPONSE FROM GEMINI =====")
                Log.d("GeminiAPI", responseText)
                Log.d("GeminiAPI", "===== END OF RAW RESPONSE =====")
                
                // Parse the response to extract suggested replies
                val replies = parseGeminiResponse(responseText)
                
                // Log parsed replies
                Log.d("GeminiAPI", "Parsed ${replies.size} suggested replies:")
                replies.forEachIndexed { index, reply ->
                    Log.d("GeminiAPI", "Reply ${index+1}: $reply")
                }
                
                // Return results on the main thread
                withContext(Dispatchers.Main) {
                    Log.d("GeminiAPI", "API request complete - returning ${replies.size} replies to UI")
                    listener.onSuggestedRepliesReceived(replies)
                }
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Error getting suggestions with model $MODEL_NAME", e)
                withContext(Dispatchers.Main) {
                    Log.e("GeminiAPI", "API request failed with error: ${e.message}")
                    listener.onError("Failed to get suggestions: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Reset model selection - no longer needed but kept for compatibility
     */
    fun resetModelSelection() {
        // No need to reset as we're using a fixed model
    }
    
    /**
     * Parse Gemini response to extract suggested replies
     */
    private fun parseGeminiResponse(responseText: String): List<String> {
        val replies = mutableListOf<String>()
        
        try {
            // Clean up response to ensure it's valid JSON
            var jsonText = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Log.d("GeminiAPI", "Cleaned JSON: $jsonText")
            
            // Handle case where response might have extra text before or after JSON
            val jsonStartIndex = jsonText.indexOf("{")
            val jsonEndIndex = jsonText.lastIndexOf("}") + 1
            
            if (jsonStartIndex >= 0 && jsonEndIndex > jsonStartIndex) {
                jsonText = jsonText.substring(jsonStartIndex, jsonEndIndex)
                Log.d("GeminiAPI", "Extracted JSON object: $jsonText")
            }
            
            // Parse JSON
            val jsonObject = JSONObject(jsonText)
            
            // Extract replies array
            if (jsonObject.has("replies")) {
                val repliesArray = jsonObject.getJSONArray("replies")
                Log.d("GeminiAPI", "Found replies array with ${repliesArray.length()} items")
                
                // Add each reply to the list
                for (i in 0 until repliesArray.length()) {
                    val reply = repliesArray.getString(i)
                    replies.add(reply)
                    Log.d("GeminiAPI", "Added reply $i: $reply")
                }
            } else {
                Log.w("GeminiAPI", "JSON doesn't contain 'replies' key")
            }
        } catch (e: Exception) {
            // Fallback parsing if the response format is unexpected
            Log.e("GeminiAPI", "Error parsing response", e)
            
            // Try to find any JSON array in the text
            val arrayPattern = Regex("\\[(.*)\\]")
            val arrayMatch = arrayPattern.find(responseText)
            
            if (arrayMatch != null) {
                try {
                    val jsonArray = JSONArray("[${arrayMatch.groupValues[1]}]")
                    Log.d("GeminiAPI", "Fallback: Found JSON array with ${jsonArray.length()} items")
                    
                    for (i in 0 until jsonArray.length()) {
                        val reply = jsonArray.getString(i)
                        replies.add(reply)
                        Log.d("GeminiAPI", "Fallback: Added reply $i: $reply")
                    }
                } catch (e: Exception) {
                    Log.e("GeminiAPI", "Fallback parsing failed", e)
                }
            }
        }
        
        // If we still don't have replies, add some defaults
        if (replies.isEmpty()) {
            Log.w("GeminiAPI", "No replies parsed, using defaults")
            
            replies.addAll(listOf(
                "I'll get back to you soon.",
                "Let me think about that.",
                "Thanks for sharing that with me.",
                "I understand what you're saying.",
                "Let's discuss this more later."
            ))
        }
        
        // Log the final replies we're returning
        Log.d("GeminiAPI", "Returning ${replies.size} replies")
        
        return replies
    }

    /**
     * Read audio file from URI and prepare for Gemini API
     */
    private suspend fun prepareAudioForGemini(uri: Uri): Pair<String, ByteArray>? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("GeminiAPI", "Preparing audio file for Gemini API from URI: $uri")
                
                // Read file data
                val fileData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                } ?: return@withContext null
                
                // Get MIME type
                val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
                val fileName = getFileName(uri) ?: "recording.mp3"
                
                Log.d("GeminiAPI", "Audio file prepared - Name: $fileName, MIME: $mimeType, Size: ${fileData.size} bytes")
                
                Pair(mimeType, fileData)
            }
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Error preparing audio file for Gemini", e)
            null
        }
    }

    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    /**
     * Analyze chat history and call recording to provide insights
     * @param customerName The name of the customer
     * @param messages List of chat messages
     * @param callRecordingUri URI of the call recording (optional)
     * @param listener Callback for receiving analysis results
     */
    fun analyzeCustomerInteraction(
        customerName: String,
        messages: List<ChatMessage>,
        callRecordingUri: Uri? = null,
        listener: ChatAnalysisListener
    ) {
        // Format messages for the prompt
        val formattedMessages = messages.joinToString("\n") { msg ->
            "${if (msg.isIncoming) customerName else "Agent"}: ${msg.text}"
        }

        val prompt = """
        You are a helpful sales coaching assistant for financial advisors. Analyze for gromo patner who want to gain insights form his interaction and provide brief, supportive feedback to help improve sales performance. 

        ## CONVERSATION:
        $formattedMessages

        ${if (callRecordingUri != null) "## CALL RECORDING: Voice conversation included" else ""}
        "and be lenient while giving score"
        ### 📊 PERFORMANCE SCORES
        | Metric | Score | 
        |--------|-------|
        | Sales Conversation Quality | [X/10] |
        | Customer Engagement | [X/10] |
        | Product Explanation | [X/10] |

        ### 💼 WHAT WENT WELL
        [2-3 specific positive points about the interaction]

        ### 🎯 GROWTH OPPORTUNITIES  
        [2-3 specific areas to improve for better sales results]

        ${if (callRecordingUri != null) """
        ### 🎙️ VOICE INSIGHTS
        **Communication Style:** [Brief assessment of tone, clarity, confidence]
        **Improvement Tip:** [One specific suggestion for voice communication]
        """ else ""}

        ### 📈 QUICK RECOMMENDATIONS
        1. [One immediate action to try next time]
        2. [One skill to practice]
        3. [One way to better engage customers]

        ### 👤 CUSTOMER INSIGHTS
        - **Interest Level**: [High/Medium/Low]
        - **Main Concerns**: [Brief summary]
        - **Next Best Action**: [Suggested follow-up approach]

        Keep responses concise and focus on actionable insights for financial product sales, give respose to the point no extra thing or explainination like "Okay, here...."
        """.trimIndent()

        // Log the model and prompt being used
        Log.d("GeminiAPI", "Using model: $MODEL_NAME for customer interaction analysis")
        Log.d("GeminiAPI", "Call recording provided: ${callRecordingUri != null}")
        Log.d("GeminiAPI", "===== COMPLETE PROMPT FOR INTERACTION ANALYSIS =====")
        Log.d("GeminiAPI", prompt)
        Log.d("GeminiAPI", "===== END OF PROMPT =====")

        // Create a coroutine scope for async operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Prepare call recording for Gemini if provided
                var audioData: Pair<String, ByteArray>? = null
                callRecordingUri?.let { uri ->
                    Log.d("GeminiAPI", "Preparing call recording for Gemini from URI: $uri")
                    audioData = prepareAudioForGemini(uri)
                    if (audioData != null) {
                        Log.d("GeminiAPI", "Call recording prepared successfully for Gemini API")
                    } else {
                        Log.e("GeminiAPI", "Failed to prepare call recording for Gemini")
                    }
                }

                // Create content with audio file if available
                val content = content {
                    text(prompt)
                    
                    // If we have audio data, include it in the request
                    audioData?.let { (mimeType, data) ->
                        Log.d("GeminiAPI", "Adding audio blob to Gemini request - MIME: $mimeType, Size: ${data.size} bytes")
                        
                        // Add the audio file as blob data
                        blob(mimeType, data)
                        
                        text("\n\n## 🎙️ AUDIO ANALYSIS INSTRUCTIONS:")
                        text("IMPORTANT: First determine what type of audio this is:")
                        text("• If it contains human conversation/dialogue → Analyze as customer service call")
                        text("• If it's music/songs → Note this and explain it's not suitable for customer service analysis")
                        text("• If it's ambient/environmental sound → Note this and explain what was detected")
                        text("")
                        text("FOR CUSTOMER SERVICE CALLS, ANALYZE:")
                        text("🎯 Voice Quality & Professionalism")
                        text("🎯 Communication Clarity & Pace")
                        text("🎯 Empathy & Active Listening")
                        text("🎯 Problem-Solving Effectiveness")
                        text("🎯 Customer Engagement Level")
                        text("🎯 Audio Technical Quality")
                        text("🎯 Background Environment Assessment")
                        text("")
                        text("FOR NON-CONVERSATION AUDIO:")
                        text("⚠️ Clearly state what type of audio was provided")
                        text("⚠️ Explain why it cannot be analyzed for customer service quality")
                        text("⚠️ Recommend uploading actual call recordings for meaningful analysis")
                    }
                }

                // Make the API call to Gemini
                Log.d("GeminiAPI", "Making API call to Gemini for interaction analysis${if (audioData != null) " with audio" else " (text only)"}")
                val response = generativeModel.generateContent(content)

                // Get response text
                val responseText = response.text?.trim() ?: ""
                Log.d("GeminiAPI", "===== COMPLETE RAW RESPONSE FROM GEMINI =====")
                Log.d("GeminiAPI", responseText)
                Log.d("GeminiAPI", "===== END OF RAW RESPONSE =====")

                // Return results on the main thread
                withContext(Dispatchers.Main) {
                    Log.d("GeminiAPI", "Analysis complete, returning results to UI")
                    listener.onAnalysisReceived(responseText)
                }
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Error analyzing customer interaction", e)
                withContext(Dispatchers.Main) {
                    listener.onError("Failed to analyze customer interaction: ${e.message}")
                }
            }
        }
    }
}