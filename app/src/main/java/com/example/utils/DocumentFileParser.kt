package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.DocumentFormat
import com.example.model.DocumentParagraph
import com.example.model.ParagraphStyle
import com.example.model.ReaderDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Parses real local files (PDF, DOCX, TXT/MD, JPG/PNG/WEBP) opened via Android Storage Access Framework.
 */
object DocumentFileParser {

    suspend fun parseUri(context: Context, uri: Uri): ReaderDocument? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)?.lowercase() ?: ""
            var fileName = "سند وارد شده"
            var fileSizeStr = "نامشخص"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) {
                        val bytes = cursor.getLong(sizeIdx)
                        fileSizeStr = formatFileSize(bytes)
                    }
                }
            }

            val lowerName = fileName.lowercase()
            val format = when {
                mimeType.contains("pdf") || lowerName.endsWith(".pdf") -> DocumentFormat.PDF
                mimeType.contains("word") || mimeType.contains("officedocument") ||
                    lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> DocumentFormat.WORD
                mimeType.startsWith("image/") || lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
                    lowerName.endsWith(".webp") -> DocumentFormat.IMAGE
                else -> DocumentFormat.TEXT
            }

            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())

            when (format) {
                DocumentFormat.WORD -> {
                    val paragraphs = contentResolver.openInputStream(uri)?.use { stream ->
                        parseDocxStream(stream)
                    } ?: emptyList()

                    val finalParagraphs = paragraphs.ifEmpty {
                        listOf(
                            DocumentParagraph(0, fileName, ParagraphStyle.TITLE, 1),
                            DocumentParagraph(
                                1,
                                "متن قابل استخراجی در این فایل Word یافت نشد یا فایل با فرمت قدیمی .doc ذخیره شده است.",
                                ParagraphStyle.BODY,
                                1
                            )
                        )
                    }
                    ReaderDocument(
                        id = UUID.randomUUID().toString(),
                        title = fileName.substringBeforeLast('.'),
                        subtitle = "فایل Word باز شده از حافظه دستگاه",
                        author = "کاربر دستگاه",
                        format = DocumentFormat.WORD,
                        dateAdded = dateStr,
                        fileSize = fileSizeStr,
                        pageCount = (finalParagraphs.size / 6).coerceAtLeast(1),
                        paragraphs = finalParagraphs,
                        uri = uri,
                        isSample = false
                    )
                }

                DocumentFormat.PDF -> {
                    val pdfBitmaps = mutableListOf<Bitmap>()
                    val extractedParagraphs = mutableListOf<DocumentParagraph>()

                    // 1. Render PDF pages via Android native PdfRenderer
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        val pageCount = renderer.pageCount.coerceAtMost(25) // Cap for memory safety
                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                val width = (page.width * 1.5f).toInt().coerceAtLeast(600)
                                val height = (page.height * 1.5f).toInt().coerceAtLeast(800)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pdfBitmaps.add(bmp)
                            }
                        }
                        renderer.close()
                    }

                    // 2. Also attempt lightweight text stream extraction for searchable text view
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val rawBytes = stream.readBytes()
                        val extracted = extractBasicTextFromPdfBytes(rawBytes)
                        if (extracted.isNotEmpty()) {
                            extracted.forEachIndexed { idx, line ->
                                extractedParagraphs.add(
                                    DocumentParagraph(
                                        index = idx,
                                        text = line,
                                        style = if (idx == 0) ParagraphStyle.TITLE else ParagraphStyle.BODY,
                                        pageNumber = (idx / 5) + 1
                                    )
                                )
                            }
                        }
                    }

                    if (extractedParagraphs.isEmpty()) {
                        extractedParagraphs.add(
                            DocumentParagraph(
                                index = 0,
                                text = "فایل PDF: ${fileName.substringBeforeLast('.')}",
                                style = ParagraphStyle.TITLE,
                                pageNumber = 1
                            )
                        )
                        extractedParagraphs.add(
                            DocumentParagraph(
                                index = 1,
                                text = "صفحات گرافیکی این سند PDF با کیفیت بالا بارگذاری شده‌اند. می‌توانید از زوم دو انگشتی برای بررسی صفحات استفاده کنید و یا یادداشت‌های متنی خود را به سند اضافه نمایید.",
                                style = ParagraphStyle.BODY,
                                pageNumber = 1
                            )
                        )
                    }

                    ReaderDocument(
                        id = UUID.randomUUID().toString(),
                        title = fileName.substringBeforeLast('.'),
                        subtitle = "سند PDF بارگذاری شده (${pdfBitmaps.size.coerceAtLeast(1)} صفحه)",
                        author = "فایل محلی",
                        format = DocumentFormat.PDF,
                        dateAdded = dateStr,
                        fileSize = fileSizeStr,
                        pageCount = pdfBitmaps.size.coerceAtLeast(1),
                        paragraphs = extractedParagraphs,
                        uri = uri,
                        isSample = false,
                        pdfBitmaps = pdfBitmaps
                    )
                }

                DocumentFormat.IMAGE -> {
                    val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    val paragraphs = listOf(
                        DocumentParagraph(
                            index = 0,
                            text = "تصویر: ${fileName.substringBeforeLast('.')}",
                            style = ParagraphStyle.TITLE,
                            pageNumber = 1
                        ),
                        DocumentParagraph(
                            index = 1,
                            text = "تصویر از حافظه دستگاه بارگذاری شد. در حالت مشاهده تصویر می‌توانید با دو انگشت زوم و جابجایی (Pan & Zoom) انجام دهید و متن یا توضیحات مرتبط با تصویر را در بخش متن هوشمند جستجو و کپی کنید.",
                            style = ParagraphStyle.BODY,
                            pageNumber = 1
                        )
                    )
                    ReaderDocument(
                        id = UUID.randomUUID().toString(),
                        title = fileName.substringBeforeLast('.'),
                        subtitle = "تصویر باز شده از گالری / حافظه",
                        author = "گالری دستگاه",
                        format = DocumentFormat.IMAGE,
                        dateAdded = dateStr,
                        fileSize = fileSizeStr,
                        pageCount = 1,
                        paragraphs = paragraphs,
                        uri = uri,
                        isSample = false,
                        imageBitmap = bitmap
                    )
                }

                DocumentFormat.TEXT -> {
                    val paragraphs = mutableListOf<DocumentParagraph>()
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                            val rawText = reader.readText()
                            val blocks = rawText.split(Regex("\\r?\\n\\s*\\r?\\n|\\r?\\n"))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            blocks.forEachIndexed { idx, block ->
                                val style = when {
                                    idx == 0 && block.length < 100 -> ParagraphStyle.TITLE
                                    block.startsWith("# ") -> ParagraphStyle.HEADING
                                    block.startsWith("## ") -> ParagraphStyle.SUBHEADING
                                    block.startsWith("- ") || block.startsWith("• ") -> ParagraphStyle.LIST_ITEM
                                    else -> ParagraphStyle.BODY
                                }
                                val cleanBlock = block.removePrefix("# ").removePrefix("## ").trim()
                                paragraphs.add(
                                    DocumentParagraph(
                                        index = idx,
                                        text = cleanBlock,
                                        style = style,
                                        pageNumber = (idx / 8) + 1
                                    )
                                )
                            }
                        }
                    }

                    if (paragraphs.isEmpty()) {
                        paragraphs.add(
                            DocumentParagraph(0, "فایل متنی خالی است.", ParagraphStyle.BODY, 1)
                        )
                    }

                    ReaderDocument(
                        id = UUID.randomUUID().toString(),
                        title = fileName.substringBeforeLast('.'),
                        subtitle = "فایل متنی (UTF-8)",
                        author = "فایل محلی",
                        format = DocumentFormat.TEXT,
                        dateAdded = dateStr,
                        fileSize = fileSizeStr,
                        pageCount = ((paragraphs.size / 8) + 1).coerceAtLeast(1),
                        paragraphs = paragraphs,
                        uri = uri,
                        isSample = false
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts paragraphs from a .docx (ZIP containing word/document.xml) using Android's XmlPullParser.
     * Preserves paragraph breaks (<w:p>) and runs (<w:t>).
     */
    private fun parseDocxStream(inputStream: InputStream): List<DocumentParagraph> {
        val result = mutableListOf<DocumentParagraph>()
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(zipStream, "UTF-8")

                var eventType = parser.eventType
                val currentParagraph = StringBuilder()
                var pIndex = 0

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val name = parser.name
                            if (name == "t") {
                                val text = parser.nextText()
                                currentParagraph.append(text)
                            } else if (name == "tab") {
                                currentParagraph.append("    ")
                            } else if (name == "br") {
                                currentParagraph.append("\n")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "p") {
                                val paraText = currentParagraph.toString().trim()
                                if (paraText.isNotEmpty()) {
                                    val style = when {
                                        pIndex == 0 && paraText.length < 120 -> ParagraphStyle.TITLE
                                        paraText.length < 65 && !paraText.endsWith(".") && !paraText.endsWith("۔") -> ParagraphStyle.HEADING
                                        else -> ParagraphStyle.BODY
                                    }
                                    result.add(
                                        DocumentParagraph(
                                            index = pIndex,
                                            text = paraText,
                                            style = style,
                                            pageNumber = (pIndex / 6) + 1
                                        )
                                    )
                                    pIndex++
                                }
                                currentParagraph.clear()
                            }
                        }
                    }
                    eventType = parser.next()
                }
                break
            }
            entry = zipStream.nextEntry
        }
        return result
    }

    /**
     * Extracts readable UTF-8/Unicode strings from uncompressed PDF text blocks if present.
     */
    private fun extractBasicTextFromPdfBytes(bytes: ByteArray): List<String> {
        return try {
            val content = String(bytes, Charsets.ISO_8859_1)
            val matches = Regex("\\(([^()]{4,200})\\)\\s*Tj").findAll(content)
            val lines = matches.map { it.groupValues[1].trim() }
                .filter { line -> line.any { it.isLetterOrDigit() } }
                .toList()
            lines
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
