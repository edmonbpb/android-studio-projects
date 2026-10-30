package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocumentFormat
import com.example.model.ReaderDocument

/**
 * Interactive Visual Canvas for viewing PDF pages and Images (PIC) with pinch-to-zoom,
 * pan, rotation, and double-tap zoom reset.
 */
@Composable
fun VisualPageViewer(
    document: ReaderDocument,
    isDarkSurface: Boolean,
    onSwitchToTextLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var currentPageIndex by remember {
        mutableIntStateOf(0)
    }

    val totalPages = when {
        document.pdfBitmaps.isNotEmpty() -> document.pdfBitmaps.size
        else -> document.pageCount.coerceAtLeast(1)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Visual Control Strip (Zoom In/Out, Rotate, Reset, Switch to OCR/Text Layer)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDarkSurface) Color(0xFF1E293B) else Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(4f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "بزرگنمایی")
                    }
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            scale = (scale - 0.25f).coerceAtLeast(0.75f)
                            if (scale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "کوچک‌نمایی")
                    }
                    IconButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "چرخش صفحه")
                    }
                    if (scale != 1f || offsetX != 0f || offsetY != 0f || rotationDegrees != 0f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                rotationDegrees = 0f
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("بازنشانی دید", fontSize = 11.sp)
                        }
                    }
                }

                // Switch to Smart Text / Search / Copy Layer
                Button(
                    onClick = onSwitchToTextLayer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D9488),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "لایه متن و کپی",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("لایه متن و کپی دقیق", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Page Navigation Bar if multiple pages exist
        if (totalPages > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "صفحه قبل")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "صفحه ${currentPageIndex + 1} از $totalPages",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = { if (currentPageIndex + 1 < totalPages) currentPageIndex++ },
                    enabled = currentPageIndex + 1 < totalPages,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "صفحه بعد")
                }
            }
        }

        // Interactive Pan & Zoom Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkSurface) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                .border(
                    1.dp,
                    if (isDarkSurface) Color(0xFF334155) else Color(0xFFCBD5E1),
                    RoundedCornerShape(16.dp)
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.75f, 4.5f)
                        scale = newScale
                        if (newScale > 1f) {
                            offsetX = (offsetX + pan.x).coerceIn(-800f, 800f)
                            offsetY = (offsetY + pan.y).coerceIn(-800f, 800f)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = rotationDegrees
                    ),
                contentAlignment = Alignment.Center
            ) {
                val realBitmap: Bitmap? = when {
                    document.imageBitmap != null -> document.imageBitmap
                    document.pdfBitmaps.isNotEmpty() ->
                        document.pdfBitmaps.getOrNull(currentPageIndex.coerceIn(0, document.pdfBitmaps.lastIndex))
                    else -> null
                }

                if (realBitmap != null) {
                    Image(
                        bitmap = realBitmap.asImageBitmap(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    // Render an authentic high-detail visual plate for sample PDF / Illuminated Manuscript PIC
                    SampleIlluminatedPlate(
                        document = document,
                        pageNumber = currentPageIndex + 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleIlluminatedPlate(
    document: ReaderDocument,
    pageNumber: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFAF6EC),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.92f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ornamental Persian geometric border canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val margin = 18.dp.toPx()

                // Outer golden-teal frame
                drawRoundRect(
                    color = Color(0xFF0D9488),
                    topLeft = Offset(margin, margin),
                    size = Size(w - margin * 2, h - margin * 2),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Inner amber hairline frame
                val innerMargin = 26.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFD97706),
                    topLeft = Offset(innerMargin, innerMargin),
                    size = Size(w - innerMargin * 2, h - innerMargin * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Header medallion arch
                val archPath = Path().apply {
                    moveTo(w * 0.25f, innerMargin)
                    lineTo(w * 0.75f, innerMargin)
                    lineTo(w * 0.68f, innerMargin + 38.dp.toPx())
                    lineTo(w * 0.32f, innerMargin + 38.dp.toPx())
                    close()
                }
                drawPath(
                    path = archPath,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF0F766E), Color(0xFF0D9488), Color(0xFF0F766E))
                    )
                )
            }

            // Content inside the illuminated frame
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (document.format == DocumentFormat.IMAGE) "لوح تصویری اسکن‌شده (OCR)" else "نمای گرافیکی صفحه $pageNumber",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        text = document.title,
                        color = Color(0xFF0F172A),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    HorizontalDivider(
                        modifier = Modifier.width(120.dp),
                        thickness = 1.5.dp,
                        color = Color(0xFFD97706)
                    )

                    val pageParagraphs = document.paragraphs.filter { it.pageNumber == pageNumber }
                        .ifEmpty { document.paragraphs.take(3) }

                    pageParagraphs.forEach { para ->
                        Text(
                            text = para.text,
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp,
                            lineHeight = 26.sp,
                            fontWeight = if (para.index == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "پشتیبانی از زوم دو انگشتی (Pinch to Zoom)",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "صفحه $pageNumber",
                        color = Color(0xFF0F766E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
