package com.example.demo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    customerName: String,
    analysisResult: String,
    hasCallRecording: Boolean,
    onBackPress: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "Analysis Results",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            text = customerName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6B7280)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E40AF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F7FF))
                .padding(paddingValues)
        ) {
            // Compact Analysis Type Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                if (hasCallRecording)
                                    listOf(Color(0xFF7C3AED), Color(0xFF9333EA))
                                else
                                    listOf(Color(0xFF1E40AF), Color(0xFF3B82F6))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasCallRecording) "Chat + Call Analysis" else "Chat Analysis",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            
            // Full Screen Analysis Content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                // Full screen scrollable analysis content without header
                SelectionContainer {
                    MarkdownText(
                        markdown = analysisResult,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val lines = markdown.split("\n")
    
    Column(modifier = modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                // Table detection (lines with |)
                line.contains("|") && line.trim().startsWith("|") && line.trim().endsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    var j = i
                    // Collect all consecutive table lines
                    while (j < lines.size && lines[j].contains("|") && 
                           (lines[j].trim().startsWith("|") || lines[j].trim().contains("---"))) {
                        if (!lines[j].trim().contains("---")) { // Skip separator lines
                            tableLines.add(lines[j])
                        }
                        j++
                    }
                    
                    if (tableLines.isNotEmpty()) {
                        RenderTable(tableLines)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    i = j
                }
                // Headers (###, ##, #)
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    i++
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        ),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    i++
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        ),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    i++
                }
                // Horizontal rules (--- or ***)
                line.trim() == "---" || line.trim() == "***" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFFE5E7EB)
                    )
                    i++
                }
                // Code blocks (```)
                line.trim().startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++ // Skip opening ```
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // Skip closing ```
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF3F4F6)
                        )
                    ) {
                        Text(
                            text = codeLines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF374151)
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                // Empty lines
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    i++
                }
                // Regular text with inline formatting
                else -> {
                    RenderFormattedText(line)
                    i++
                }
            }
        }
    }
}

@Composable
fun RenderTable(tableLines: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAFAFA)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            tableLines.forEachIndexed { index, line ->
                val cells = line.split("|").filter { it.isNotBlank() }.map { it.trim() }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell,
                            style = if (index == 0) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937)
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF374151)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                
                if (index == 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color(0xFFE5E7EB)
                    )
                }
            }
        }
    }
}

@Composable
fun RenderFormattedText(text: String) {
    val annotatedString = buildAnnotatedString {
        var currentText = text
        
        // Handle bullet points and numbered lists first
        when {
            currentText.trimStart().startsWith("- ") -> {
                val indent = currentText.length - currentText.trimStart().length
                append(" ".repeat(indent))
                append("• ")
                currentText = currentText.trimStart().removePrefix("- ")
            }
            // Handle asterisk bullets with any number of spaces (*, *  , *   , etc.)
            currentText.trimStart().matches(Regex("^\\*\\s+.*")) -> {
                val indent = currentText.length - currentText.trimStart().length
                append(" ".repeat(indent))
                append("• ")
                // Remove asterisk and any following spaces
                currentText = currentText.trimStart().replaceFirst(Regex("^\\*\\s+"), "")
            }
            currentText.trimStart().matches(Regex("^\\d+\\. .*")) -> {
                val indent = currentText.length - currentText.trimStart().length
                append(" ".repeat(indent))
                val match = Regex("^(\\d+\\. )(.*)").find(currentText.trimStart())
                if (match != null) {
                    append(match.groupValues[1])
                    currentText = match.groupValues[2]
                }
            }
        }
        
        // Process inline formatting
        var i = 0
        while (i < currentText.length) {
            when {
                // Bold text (**text**)
                currentText.substring(i).startsWith("**") -> {
                    val endIndex = currentText.indexOf("**", i + 2)
                    if (endIndex != -1) {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                        ) {
                            append(currentText.substring(i + 2, endIndex))
                        }
                        i = endIndex + 2
                    } else {
                        append(currentText[i])
                        i++
                    }
                }
                // Italic text (*text*) - but not if it starts the string or after space (to avoid bullet conflicts)
                currentText.substring(i).startsWith("*") && 
                !currentText.substring(i).startsWith("**") &&
                i > 0 && currentText[i-1] != ' ' -> {
                    val endIndex = currentText.indexOf("*", i + 1)
                    if (endIndex != -1 && endIndex < currentText.length - 1 && 
                        !currentText.substring(i+1, endIndex).contains(" ")) {
                        withStyle(
                            style = SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF374151)
                            )
                        ) {
                            append(currentText.substring(i + 1, endIndex))
                        }
                        i = endIndex + 1
                    } else {
                        append(currentText[i])
                        i++
                    }
                }
                // Inline code (`code`)
                currentText.substring(i).startsWith("`") -> {
                    val endIndex = currentText.indexOf("`", i + 1)
                    if (endIndex != -1) {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFFF3F4F6),
                                color = Color(0xFFDC2626)
                            )
                        ) {
                            append(currentText.substring(i + 1, endIndex))
                        }
                        i = endIndex + 1
                    } else {
                        append(currentText[i])
                        i++
                    }
                }
                // Regular character
                else -> {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF374151)
                        )
                    ) {
                        append(currentText[i])
                    }
                    i++
                }
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}