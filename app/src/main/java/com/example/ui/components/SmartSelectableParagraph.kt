package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HighlightAnnotation
import com.example.model.ParagraphStyle
import com.example.utils.PersianArabicSearchEngine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom RTL-aware Selectable Text Paragraph that completely solves the Android/Web cursor jumping bug!
 *
 * Why standard SelectionContainer jumps to the top of the screen:
 * 1. When dragging a selection handle in RTL text, if the finger moves slightly outside the line's bounding box
 *    or crosses a paragraph boundary, Android's global SelectionContainer recalculates the offset against the root
 *    scrollable container (0,0) and selects everything from the top of the page.
 *
 * How [SmartSelectableParagraph] solves this permanently:
 * 1. Selection coordinates are strictly clamped to the local paragraph's [TextLayoutResult] dimensions (`0f..width`, `0f..height`).
 * 2. Both the Start Handle and End Handle track incremental finger drag deltas (`dragAmount`) from their exact anchor
 *    screen position, moving smoothly character-by-character in the exact direction the user guides their finger.
 * 3. Includes fine-tune buttons (Word/Char +/-) and a floating action bar (Copy, Highlight, Share, Select Paragraph).
 */
@Composable
fun SmartSelectableParagraph(
    paragraphIndex: Int,
    text: String,
    style: ParagraphStyle,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    accentColor: Color,
    searchMatches: List<PersianArabicSearchEngine.SearchMatch>,
    activeMatch: PersianArabicSearchEngine.SearchMatch?,
    annotations: List<HighlightAnnotation>,
    isActiveSelectionParagraph: Boolean,
    selectionRange: TextRange?,
    onSelectionChange: (TextRange?) -> Unit,
    onCopySelection: (String) -> Unit,
    onHighlightSelection: (Int, Int, String, Long) -> Unit,
    onShareSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    // Track drag positions in local pixel coordinates of the Text composable for rock-solid handle movement
    var startHandleTouchPos by remember { mutableStateOf<Offset?>(null) }
    var endHandleTouchPos by remember { mutableStateOf<Offset?>(null) }

    // Build AnnotatedString with Search Highlights & User Annotations
    val annotatedText = remember(text, searchMatches, activeMatch, annotations, style) {
        buildAnnotatedString {
            append(text)

            // 1. Saved user highlights
            for (ann in annotations) {
                val s = ann.startOffset.coerceIn(0, text.length)
                val e = ann.endOffset.coerceIn(0, text.length)
                if (s < e) {
                    addStyle(
                        SpanStyle(
                            background = Color(ann.colorHex).copy(alpha = 0.42f),
                            color = Color.Unspecified
                        ),
                        start = s,
                        end = e
                    )
                }
            }

            // 2. Search matches in this paragraph
            for (match in searchMatches) {
                val s = match.startOffset.coerceIn(0, text.length)
                val e = match.endOffset.coerceIn(0, text.length)
                if (s < e) {
                    val isCurrentActive = activeMatch != null &&
                        activeMatch.paragraphIndex == match.paragraphIndex &&
                        activeMatch.startOffset == match.startOffset &&
                        activeMatch.endOffset == match.endOffset

                    addStyle(
                        SpanStyle(
                            background = if (isCurrentActive) Color(0xFFF97316) else Color(0xFFFACC15).copy(alpha = 0.55f),
                            color = if (isCurrentActive) Color.White else Color(0xFF0F172A),
                            fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Normal
                        ),
                        start = s,
                        end = e
                    )
                }
            }
        }
    }

    // Compute typography styling based on paragraph role
    val effectiveFontSize: TextUnit = when (style) {
        ParagraphStyle.TITLE -> (fontSizeSp + 5f).sp
        ParagraphStyle.HEADING -> (fontSizeSp + 2.5f).sp
        ParagraphStyle.SUBHEADING -> (fontSizeSp + 1f).sp
        ParagraphStyle.QUOTE -> fontSizeSp.sp
        ParagraphStyle.LIST_ITEM -> fontSizeSp.sp
        ParagraphStyle.BODY -> fontSizeSp.sp
    }

    val effectiveFontWeight = when (style) {
        ParagraphStyle.TITLE -> FontWeight.ExtraBold
        ParagraphStyle.HEADING -> FontWeight.Bold
        ParagraphStyle.SUBHEADING -> FontWeight.SemiBold
        ParagraphStyle.QUOTE -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val effectiveLineHeight = (effectiveFontSize.value * lineHeightMultiplier).sp

    // Helper function: safely convert any local (x, y) point to a character offset within [0, text.length]
    // Clamping (x, y) to the paragraph's actual layout bounds prevents the cursor from EVER jumping out!
    fun getClampedOffsetForPosition(layout: TextLayoutResult, rawOffset: Offset): Int {
        val w = layout.size.width.toFloat().coerceAtLeast(1f)
        val h = layout.size.height.toFloat().coerceAtLeast(1f)
        val clampedX = rawOffset.x.coerceIn(2f, w - 2f)
        val clampedY = rawOffset.y.coerceIn(2f, h - 2f)
        return layout.getOffsetForPosition(Offset(clampedX, clampedY)).coerceIn(0, text.length)
    }

    // Helper function: find the word boundaries around a character offset
    fun getWordRangeAtOffset(offset: Int): TextRange {
        if (text.isEmpty()) return TextRange.Zero
        val safeIdx = offset.coerceIn(0, text.length - 1)
        var start = safeIdx
        var end = safeIdx + 1

        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }
        while (end < text.length && !text[end].isWhitespace()) {
            end++
        }
        if (start == end && end < text.length) end++
        return TextRange(start, end)
    }

    // Helper function: get pixel coordinate for a given character offset inside the layout
    fun getCursorTopBottomCenter(layout: TextLayoutResult, charOffset: Int, isStart: Boolean): Pair<Offset, Float> {
        if (text.isEmpty()) return Offset.Zero to 20f
        val maxIdx = (text.length - 1).coerceAtLeast(0)
        val boxIdx = if (isStart) {
            charOffset.coerceIn(0, maxIdx)
        } else {
            (charOffset - 1).coerceIn(0, maxIdx)
        }
        val box = layout.getBoundingBox(boxIdx)
        val dir = layout.getBidiRunDirection(boxIdx)
        val isRtl = dir == ResolvedTextDirection.Rtl

        // In RTL text, the start of a character is its right edge, and the end of a character is its left edge.
        val xCoord = when {
            isStart && isRtl -> box.right
            isStart && !isRtl -> box.left
            !isStart && isRtl -> box.left
            else -> box.right
        }.coerceIn(0f, layout.size.width.toFloat())

        val lineIndex = layout.getLineForOffset(boxIdx)
        val lineTop = layout.getLineTop(lineIndex)
        val lineBottom = layout.getLineBottom(lineIndex)
        val lineHeight = (lineBottom - lineTop).coerceAtLeast(16f)

        return Offset(xCoord, lineBottom) to lineHeight
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isActiveSelectionParagraph && selectionRange != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.05f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                } else if (style == ParagraphStyle.QUOTE) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.06f))
                        .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                } else {
                    Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                }
            )
    ) {
        // Floating Smart Selection Toolbar when this paragraph has an active selection
        if (isActiveSelectionParagraph && selectionRange != null && !selectionRange.collapsed) {
            val minSel = min(selectionRange.start, selectionRange.end).coerceIn(0, text.length)
            val maxSel = max(selectionRange.start, selectionRange.end).coerceIn(0, text.length)
            val selectedString = text.substring(minSel, maxSel)

            SmartSelectionControlBar(
                selectedText = selectedString,
                selectionStart = minSel,
                selectionEnd = maxSel,
                maxTextLength = text.length,
                accentColor = accentColor,
                onAdjustSelection = { newStart, newEnd ->
                    val s = newStart.coerceIn(0, text.length)
                    val e = newEnd.coerceIn(0, text.length)
                    if (s != e) {
                        onSelectionChange(TextRange(min(s, e), max(s, e)))
                    }
                },
                onSelectWholeParagraph = {
                    onSelectionChange(TextRange(0, text.length))
                },
                onCopy = { onCopySelection(selectedString) },
                onHighlight = { colorHex ->
                    onHighlightSelection(minSel, maxSel, selectedString, colorHex)
                },
                onShare = { onShareSelection(selectedString) },
                onDismiss = { onSelectionChange(null) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Main Text Canvas with Custom Highlight Drawing & Precision Drag Handles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Bottom padding gives room for the teardrop drag handles on the last line
                .padding(
                    bottom = if (isActiveSelectionParagraph && selectionRange != null) 34.dp else 4.dp,
                    top = 4.dp
                )
        ) {
            // 1. Background Selection Highlight Canvas
            if (isActiveSelectionParagraph && selectionRange != null && !selectionRange.collapsed) {
                val layout = textLayoutResult
                if (layout != null) {
                    val selStart = min(selectionRange.start, selectionRange.end).coerceIn(0, text.length)
                    val selEnd = max(selectionRange.start, selectionRange.end).coerceIn(0, text.length)

                    Canvas(modifier = Modifier.matchParentSize()) {
                        // Draw selection rectangles per line so RTL/BiDi lines never glitch or invert
                        val startLine = layout.getLineForOffset(selStart)
                        val endLine = layout.getLineForOffset((selEnd - 1).coerceAtLeast(selStart))

                        for (lineIdx in startLine..endLine) {
                            val lineStartOffset = if (lineIdx == startLine) selStart else layout.getLineStart(lineIdx)
                            val lineEndOffset = if (lineIdx == endLine) selEnd else layout.getLineEnd(lineIdx, visibleEnd = true)

                            if (lineStartOffset < lineEndOffset) {
                                var minX = Float.MAX_VALUE
                                var maxX = Float.MIN_VALUE
                                for (charIdx in lineStartOffset until lineEndOffset) {
                                    if (charIdx in 0 until text.length) {
                                        val box = layout.getBoundingBox(charIdx)
                                        if (box.left < minX) minX = box.left
                                        if (box.right > maxX) maxX = box.right
                                    }
                                }
                                if (minX < maxX) {
                                    val top = layout.getLineTop(lineIdx)
                                    val bottom = layout.getLineBottom(lineIdx)
                                    drawRect(
                                        color = accentColor.copy(alpha = 0.28f),
                                        topLeft = Offset(minX, top),
                                        size = Size(maxX - minX, bottom - top)
                                    )
                                }
                            } else {
                                // Fallback to path if single empty line
                                val path: Path = layout.getPathForRange(selStart, selEnd)
                                drawPath(path = path, color = accentColor.copy(alpha = 0.28f))
                            }
                        }
                    }
                }
            }

            // 2. The Text Composable with Long-Press Word Selection & Tap Dismissal
            Text(
                text = annotatedText,
                fontSize = effectiveFontSize,
                fontWeight = effectiveFontWeight,
                lineHeight = effectiveLineHeight,
                color = textColor,
                textAlign = TextAlign.Start,
                onTextLayout = { result ->
                    textLayoutResult = result
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(text) {
                        detectTapGestures(
                            onLongPress = { tapOffset ->
                                val layout = textLayoutResult ?: return@detectTapGestures
                                val charOffset = getClampedOffsetForPosition(layout, tapOffset)
                                val wordRange = getWordRangeAtOffset(charOffset)
                                onSelectionChange(wordRange)
                            },
                            onDoubleTap = { tapOffset ->
                                val layout = textLayoutResult ?: return@detectTapGestures
                                val charOffset = getClampedOffsetForPosition(layout, tapOffset)
                                val wordRange = getWordRangeAtOffset(charOffset)
                                onSelectionChange(wordRange)
                            },
                            onTap = { tapOffset ->
                                // If selection is already active in this paragraph, tapping moves the nearest handle
                                // or dismisses if tapped outside
                                if (isActiveSelectionParagraph && selectionRange != null) {
                                    val layout = textLayoutResult ?: return@detectTapGestures
                                    val tappedChar = getClampedOffsetForPosition(layout, tapOffset)
                                    val s = min(selectionRange.start, selectionRange.end)
                                    val e = max(selectionRange.start, selectionRange.end)
                                    val distToStart = kotlin.math.abs(tappedChar - s)
                                    val distToEnd = kotlin.math.abs(tappedChar - e)
                                    if (distToStart <= distToEnd) {
                                        val newStart = min(tappedChar, e - 1).coerceAtLeast(0)
                                        onSelectionChange(TextRange(newStart, e))
                                    } else {
                                        val newEnd = max(tappedChar, s + 1).coerceAtMost(text.length)
                                        onSelectionChange(TextRange(s, newEnd))
                                    }
                                }
                            }
                        )
                    }
            )

            // 3. Custom Precision Drag Handles (Start Handle & End Handle)
            val layout = textLayoutResult
            if (isActiveSelectionParagraph && selectionRange != null && !selectionRange.collapsed && layout != null) {
                val selStart = min(selectionRange.start, selectionRange.end).coerceIn(0, text.length)
                val selEnd = max(selectionRange.start, selectionRange.end).coerceIn(0, text.length)

                val (startBottomPos, startLineHeight) = getCursorTopBottomCenter(layout, selStart, isStart = true)
                val (endBottomPos, endLineHeight) = getCursorTopBottomCenter(layout, selEnd, isStart = false)

                // Draw vertical cursor bars at start and end edges
                Canvas(modifier = Modifier.matchParentSize()) {
                    // Start vertical cursor bar
                    drawLine(
                        color = accentColor,
                        start = Offset(startBottomPos.x, startBottomPos.y - startLineHeight),
                        end = Offset(startBottomPos.x, startBottomPos.y),
                        strokeWidth = 3.dp.toPx()
                    )
                    // End vertical cursor bar
                    drawLine(
                        color = Color(0xFF0284C7),
                        start = Offset(endBottomPos.x, endBottomPos.y - endLineHeight),
                        end = Offset(endBottomPos.x, endBottomPos.y),
                        strokeWidth = 3.dp.toPx()
                    )
                }

                val handleSizeDp = 32.dp
                val handleRadiusPx = with(density) { (handleSizeDp / 2).toPx() }

                // START HANDLE (Right handle in RTL)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (startBottomPos.x - handleRadiusPx).roundToInt(),
                                y = startBottomPos.y.roundToInt()
                            )
                        }
                        .size(handleSizeDp)
                        .pointerInput(layout, text) {
                            detectDragGestures(
                                onDragStart = {
                                    // Anchor at the exact center of the character line
                                    val (pos, lh) = getCursorTopBottomCenter(layout, selStart, isStart = true)
                                    startHandleTouchPos = Offset(pos.x, pos.y - lh / 2f)
                                },
                                onDragEnd = {
                                    startHandleTouchPos = null
                                },
                                onDragCancel = {
                                    startHandleTouchPos = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentPos = startHandleTouchPos ?: run {
                                        val (pos, lh) = getCursorTopBottomCenter(layout, selStart, isStart = true)
                                        Offset(pos.x, pos.y - lh / 2f)
                                    }
                                    // Add exact finger delta!
                                    val nextPos = Offset(
                                        x = (currentPos.x + dragAmount.x).coerceIn(1f, layout.size.width.toFloat() - 1f),
                                        y = (currentPos.y + dragAmount.y).coerceIn(1f, layout.size.height.toFloat() - 1f)
                                    )
                                    startHandleTouchPos = nextPos

                                    val newOffset = getClampedOffsetForPosition(layout, nextPos)
                                    val currentEnd = max(selectionRange.start, selectionRange.end)
                                    if (newOffset != selStart && newOffset != currentEnd) {
                                        onSelectionChange(
                                            TextRange(
                                                min(newOffset, currentEnd),
                                                max(newOffset, currentEnd)
                                            )
                                        )
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    TeardropHandleIcon(
                        color = accentColor,
                        isStart = true
                    )
                }

                // END HANDLE (Left handle in RTL)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (endBottomPos.x - handleRadiusPx).roundToInt(),
                                y = endBottomPos.y.roundToInt()
                            )
                        }
                        .size(handleSizeDp)
                        .pointerInput(layout, text) {
                            detectDragGestures(
                                onDragStart = {
                                    val (pos, lh) = getCursorTopBottomCenter(layout, selEnd, isStart = false)
                                    endHandleTouchPos = Offset(pos.x, pos.y - lh / 2f)
                                },
                                onDragEnd = {
                                    endHandleTouchPos = null
                                },
                                onDragCancel = {
                                    endHandleTouchPos = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentPos = endHandleTouchPos ?: run {
                                        val (pos, lh) = getCursorTopBottomCenter(layout, selEnd, isStart = false)
                                        Offset(pos.x, pos.y - lh / 2f)
                                    }
                                    val nextPos = Offset(
                                        x = (currentPos.x + dragAmount.x).coerceIn(1f, layout.size.width.toFloat() - 1f),
                                        y = (currentPos.y + dragAmount.y).coerceIn(1f, layout.size.height.toFloat() - 1f)
                                    )
                                    endHandleTouchPos = nextPos

                                    val newOffset = getClampedOffsetForPosition(layout, nextPos)
                                    val currentStart = min(selectionRange.start, selectionRange.end)
                                    if (newOffset != selEnd && newOffset != currentStart) {
                                        onSelectionChange(
                                            TextRange(
                                                min(currentStart, newOffset),
                                                max(currentStart, newOffset)
                                            )
                                        )
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    TeardropHandleIcon(
                        color = Color(0xFF0284C7),
                        isStart = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TeardropHandleIcon(
    color: Color,
    isStart: Boolean
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val radius = w * 0.42f
        val centerX = w / 2f
        val centerY = h * 0.58f

        // Teardrop pointer triangle pointing up to the baseline
        val path = Path().apply {
            moveTo(centerX, 0f)
            lineTo(centerX - radius * 0.75f, centerY - radius * 0.3f)
            lineTo(centerX + radius * 0.75f, centerY - radius * 0.3f)
            close()
        }
        drawPath(path, color = color)

        // Outer white ring for high contrast
        drawCircle(
            color = Color.White,
            radius = radius + 2.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        // Main colored circle
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(centerX, centerY)
        )
        // Inner precision dot
        drawCircle(
            color = Color.White,
            radius = radius * 0.32f,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Floating control bar above the selected paragraph with:
 * - Instant Copy, Highlight (Yellow/Green/Blue/Pink), Share, Select All Paragraph
 * - Precision +/-1 char/word nudge buttons so the user has 100% control even without dragging!
 */
@Composable
private fun SmartSelectionControlBar(
    selectedText: String,
    selectionStart: Int,
    selectionEnd: Int,
    maxTextLength: Int,
    accentColor: Color,
    onAdjustSelection: (Int, Int) -> Unit,
    onSelectWholeParagraph: () -> Unit,
    onCopy: () -> Unit,
    onHighlight: (Long) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Primary Actions (Copy, Highlight colors, Select All, Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy Button
                    Button(
                        onClick = onCopy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی متن",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "کپی متن (${selectedText.length} حرف)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Select Entire Paragraph Button
                    OutlinedButton(
                        onClick = onSelectWholeParagraph,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "کل بند",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("کل بند", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Highlight Color Dots & Share / Dismiss
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val highlightColors = listOf(
                        0xFFFACC15L, // Yellow
                        0xFF4ADE80L, // Green
                        0xFF38BDF8L, // Sky Blue
                        0xFFF472B6L  // Pink
                    )
                    highlightColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                .pointerInput(hex) {
                                    detectTapGestures(onTap = { onHighlight(hex) })
                                }
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "اشتراک‌گذاری",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن انتخاب",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Row: Fine-Tune Cursor Nudge Controls (for ultra-precise start/end adjustment)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start Handle Fine Tuning (Right side in RTL)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ابتدا:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    SmallNudgeButton(label = "-۱", onClick = {
                        if (selectionStart > 0) onAdjustSelection(selectionStart - 1, selectionEnd)
                    })
                    SmallNudgeButton(label = "+۱", onClick = {
                        if (selectionStart + 1 < selectionEnd) onAdjustSelection(selectionStart + 1, selectionEnd)
                    })
                }

                Text(
                    text = "بازه: $selectionStart تا $selectionEnd (قفل هوشمند ضدپرش فعال)",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp
                )

                // End Handle Fine Tuning (Left side in RTL)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "انتها:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    SmallNudgeButton(label = "-۱", onClick = {
                        if (selectionEnd - 1 > selectionStart) onAdjustSelection(selectionStart, selectionEnd - 1)
                    })
                    SmallNudgeButton(label = "+۱", onClick = {
                        if (selectionEnd < maxTextLength) onAdjustSelection(selectionStart, selectionEnd + 1)
                    })
                }
            }
        }
    }
}

@Composable
private fun SmallNudgeButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF334155),
        contentColor = Color.White,
        modifier = Modifier.height(24.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
