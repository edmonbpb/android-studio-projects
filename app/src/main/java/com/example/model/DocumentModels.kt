package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class DocumentFormat(val labelFa: String, val badgeText: String, val extension: String) {
    PDF("سند PDF", "PDF", "pdf"),
    WORD("فایل Word", "DOCX", "docx"),
    IMAGE("تصویر و عکس", "PIC", "jpg"),
    TEXT("متن ساده", "TXT", "txt")
}

enum class ParagraphStyle {
    TITLE,
    HEADING,
    SUBHEADING,
    BODY,
    QUOTE,
    LIST_ITEM
}

data class DocumentParagraph(
    val index: Int,
    val text: String,
    val style: ParagraphStyle = ParagraphStyle.BODY,
    val pageNumber: Int = 1
)

data class HighlightAnnotation(
    val id: String,
    val docId: String,
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val colorHex: Long = 0xFFFDE047, // Warm Yellow default
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ReaderDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val format: DocumentFormat,
    val dateAdded: String,
    val fileSize: String,
    val pageCount: Int,
    val paragraphs: List<DocumentParagraph>,
    val uri: Uri? = null,
    val isSample: Boolean = true,
    val isBookmarked: Boolean = false,
    val lastReadParagraph: Int = 0,
    val pdfBitmaps: List<Bitmap> = emptyList(),
    val imageBitmap: Bitmap? = null
)
