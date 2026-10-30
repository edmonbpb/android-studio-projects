package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SampleDocumentsRepository
import com.example.model.*
import com.example.ui.components.SmartSelectableParagraph
import com.example.ui.components.VisualPageViewer
import com.example.utils.DocumentFileParser
import com.example.utils.PersianArabicSearchEngine
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReaderThemeMode(
    val labelFa: String,
    val bgColor: Color,
    val cardColor: Color,
    val textColor: Color,
    val mutedTextColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val isDark: Boolean
) {
    PAPER_LIGHT(
        labelFa = "روشن",
        bgColor = Color(0xFFF8FAFC),
        cardColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF0F172A),
        mutedTextColor = Color(0xFF64748B),
        borderColor = Color(0xFFE2E8F0),
        accentColor = Color(0xFF0D9488),
        isDark = false
    ),
    SEPIA_WARM(
        labelFa = "کاغذی (سپیا)",
        bgColor = Color(0xFFFDF8EE),
        cardColor = Color(0xFFF5EFE0),
        textColor = Color(0xFF292524),
        mutedTextColor = Color(0xFF78716C),
        borderColor = Color(0xFFE7DEC8),
        accentColor = Color(0xFFB45309),
        isDark = false
    ),
    DARK_SLATE(
        labelFa = "تاریک",
        bgColor = Color(0xFF0F172A),
        cardColor = Color(0xFF1E293B),
        textColor = Color(0xFFF1F5F9),
        mutedTextColor = Color(0xFF94A3B8),
        borderColor = Color(0xFF334155),
        accentColor = Color(0xFF14B8A6),
        isDark = true
    ),
    AMOLED_BLACK(
        labelFa = "شب مطلق",
        bgColor = Color(0xFF020617),
        cardColor = Color(0xFF0F172A),
        textColor = Color(0xFFE2E8F0),
        mutedTextColor = Color(0xFF64748B),
        borderColor = Color(0xFF1E293B),
        accentColor = Color(0xFF38BDF8),
        isDark = true
    )
}

enum class MainTab {
    READER,
    LIBRARY,
    SEARCH_LAB,
    BOOKMARKS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParsReaderApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // App State
    var documents by remember { mutableStateOf(SampleDocumentsRepository.getInitialDocuments()) }
    var activeDocId by remember { mutableStateOf(documents.first().id) }
    val activeDocument = remember(documents, activeDocId) {
        documents.find { it.id == activeDocId } ?: documents.first()
    }

    var currentTab by remember { mutableStateOf(MainTab.READER) }
    var themeMode by remember { mutableStateOf(ReaderThemeMode.PAPER_LIGHT) }

    // Reader View Settings
    var fontSizeSp by remember { mutableFloatStateOf(16.5f) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(1.85f) }
    var showVisualMode by remember { mutableStateOf(false) } // Toggle between Smart Text & Visual Canvas for PDF/PIC
    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showNewTextDialog by remember { mutableStateOf(false) }

    // Smart Selection State (Strictly locked to one paragraph at a time to eliminate cross-screen cursor jumps!)
    var selectedParagraphIndex by remember { mutableStateOf<Int?>(0) }
    var activeSelectionRange by remember { mutableStateOf<TextRange?>(TextRange(0, 29)) }

    // Search State & Options
    var isSearchBarExpanded by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchOptionsRow by remember { mutableStateOf(false) }
    var searchOptions by remember {
        mutableStateOf(
            PersianArabicSearchEngine.SearchOptions(
                unifyPersianArabicChars = true,
                ignoreDiacritics = true,
                ignoreZwnj = true,
                unifyDigits = true,
                caseSensitive = false,
                wholeWordOnly = false
            )
        )
    }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Annotations / Highlights
    var annotations by remember {
        mutableStateOf(
            listOf(
                HighlightAnnotation(
                    id = "sample_ann_1",
                    docId = "doc_pdf_persian_arabic",
                    paragraphIndex = 2,
                    startOffset = 0,
                    endOffset = 75,
                    selectedText = "یکی از مهم‌ترین چالش‌ها در جستجوی اسناد PDF و فایل‌های Word، تفاوت کدهای یونیکد",
                    colorHex = 0xFFFACC15L
                )
            )
        )
    }

    // Compute live search matches for the active document
    val searchMatches = remember(activeDocument, searchQuery, searchOptions) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val paragraphTexts = activeDocument.paragraphs.map { it.text }
            PersianArabicSearchEngine.searchInParagraphs(paragraphTexts, searchQuery, searchOptions)
        }
    }

    // Keep currentMatchIndex valid when searchMatches change
    LaunchedEffect(searchMatches.size) {
        if (searchMatches.isEmpty()) {
            currentMatchIndex = 0
        } else if (currentMatchIndex >= searchMatches.size) {
            currentMatchIndex = 0
        }
    }

    val activeMatch = searchMatches.getOrNull(currentMatchIndex)
    val readerListState = rememberLazyListState()

    // Automatically scroll to the paragraph containing activeMatch when navigating Next/Previous match
    LaunchedEffect(activeMatch) {
        if (activeMatch != null && currentTab == MainTab.READER && !showVisualMode) {
            readerListState.animateScrollToItem(activeMatch.paragraphIndex)
        }
    }

    // File Picker Launcher (PDF, DOCX, Images, TXT)
    var isParsingFile by remember { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isParsingFile = true
            coroutineScope.launch {
                val parsedDoc = DocumentFileParser.parseUri(context, uri)
                isParsingFile = false
                if (parsedDoc != null) {
                    documents = listOf(parsedDoc) + documents
                    activeDocId = parsedDoc.id
                    selectedParagraphIndex = null
                    activeSelectionRange = null
                    showVisualMode = (parsedDoc.format == DocumentFormat.IMAGE || parsedDoc.pdfBitmaps.isNotEmpty())
                    currentTab = MainTab.READER
                    Toast.makeText(
                        context,
                        "فایل «${parsedDoc.title}» با موفقیت بارگذاری شد",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "خطا در خواندن فایل انتخاب‌شده",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Clipboard & Share Helpers
    fun copyTextToClipboard(textToCopy: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ParsReader Selection", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "کپی شد (${textToCopy.length} کاراکتر)",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun shareSelectedText(textToShare: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, textToShare)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "اشتراک‌گذاری متن")
        context.startActivity(shareIntent)
    }

    // Force Right-to-Left (RTL) layout direction for natural Persian & Arabic reading ergonomics
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = themeMode.bgColor,
            contentColor = themeMode.textColor,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeMode.cardColor,
                        titleContentColor = themeMode.textColor
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = themeMode.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "پارس‌ریدر",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "·",
                                color = themeMode.mutedTextColor,
                                fontSize = 16.sp
                            )
                            Text(
                                text = activeDocument.format.labelFa,
                                color = themeMode.mutedTextColor,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    },
                    actions = {
                        // Open Local File Button (PDF, Word, Image, Text)
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "image/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeMode.accentColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "باز کردن فایل",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "باز کردن فایل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Theme & Font Settings Trigger
                        IconButton(onClick = { showReaderSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "تنظیمات نمایش",
                                tint = themeMode.textColor
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = themeMode.cardColor,
                    contentColor = themeMode.textColor,
                    tonalElevation = 4.dp,
                    modifier = Modifier.height(68.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.READER,
                        onClick = { currentTab = MainTab.READER },
                        icon = { Icon(Icons.Default.ChromeReaderMode, contentDescription = null) },
                        label = { Text("مطالعه و کپی", fontSize = 11.sp, maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.LIBRARY,
                        onClick = { currentTab = MainTab.LIBRARY },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                        label = { Text("کتابخانه (${documents.size})", fontSize = 11.sp, maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.SEARCH_LAB,
                        onClick = { currentTab = MainTab.SEARCH_LAB },
                        icon = { Icon(Icons.Default.ManageSearch, contentDescription = null) },
                        label = { Text("جستجوی کل اسناد", fontSize = 11.sp, maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.BOOKMARKS,
                        onClick = { currentTab = MainTab.BOOKMARKS },
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        label = { Text("هایلایت‌ها (${annotations.size})", fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    MainTab.READER -> {
                        ReaderWorkspaceScreen(
                            document = activeDocument,
                            themeMode = themeMode,
                            fontSizeSp = fontSizeSp,
                            lineHeightMultiplier = lineHeightMultiplier,
                            showVisualMode = showVisualMode,
                            onToggleVisualMode = { showVisualMode = it },
                            isSearchBarExpanded = isSearchBarExpanded,
                            onToggleSearchBar = { isSearchBarExpanded = !isSearchBarExpanded },
                            searchQuery = searchQuery,
                            onSearchQueryChange = {
                                searchQuery = it
                                currentMatchIndex = 0
                            },
                            showSearchOptionsRow = showSearchOptionsRow,
                            onToggleSearchOptionsRow = { showSearchOptionsRow = !showSearchOptionsRow },
                            searchOptions = searchOptions,
                            onSearchOptionsChange = { searchOptions = it },
                            searchMatches = searchMatches,
                            currentMatchIndex = currentMatchIndex,
                            onPrevMatch = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex =
                                        (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                }
                            },
                            onNextMatch = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                }
                            },
                            activeMatch = activeMatch,
                            annotations = annotations.filter { it.docId == activeDocument.id },
                            selectedParagraphIndex = selectedParagraphIndex,
                            activeSelectionRange = activeSelectionRange,
                            onSelectionChange = { pIndex, newRange ->
                                if (newRange == null) {
                                    selectedParagraphIndex = null
                                    activeSelectionRange = null
                                } else {
                                    selectedParagraphIndex = pIndex
                                    activeSelectionRange = newRange
                                }
                            },
                            onCopySelection = ::copyTextToClipboard,
                            onHighlightSelection = { pIdx, start, end, selText, colorHex ->
                                val newAnn = HighlightAnnotation(
                                    id = UUID.randomUUID().toString(),
                                    docId = activeDocument.id,
                                    paragraphIndex = pIdx,
                                    startOffset = start,
                                    endOffset = end,
                                    selectedText = selText,
                                    colorHex = colorHex
                                )
                                annotations = listOf(newAnn) + annotations
                                Toast.makeText(context, "به لیست هایلایت‌ها اضافه شد", Toast.LENGTH_SHORT).show()
                            },
                            onShareSelection = ::shareSelectedText,
                            readerListState = readerListState,
                            onDismissKeyboard = { focusManager.clearFocus() }
                        )
                    }

                    MainTab.LIBRARY -> {
                        LibraryScreen(
                            documents = documents,
                            activeDocId = activeDocId,
                            themeMode = themeMode,
                            onSelectDocument = { doc ->
                                activeDocId = doc.id
                                selectedParagraphIndex = null
                                activeSelectionRange = null
                                showVisualMode = false
                                currentTab = MainTab.READER
                            },
                            onOpenFilePicker = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "image/*"
                                    )
                                )
                            },
                            onCreateNewTextNote = {
                                showNewTextDialog = true
                            }
                        )
                    }

                    MainTab.SEARCH_LAB -> {
                        GlobalSearchLabScreen(
                            documents = documents,
                            themeMode = themeMode,
                            searchOptions = searchOptions,
                            onSearchOptionsChange = { searchOptions = it },
                            onJumpToMatch = { docId, match, queryUsed ->
                                activeDocId = docId
                                searchQuery = queryUsed
                                isSearchBarExpanded = true
                                showVisualMode = false
                                currentTab = MainTab.READER
                                coroutineScope.launch {
                                    readerListState.scrollToItem(match.paragraphIndex)
                                }
                            }
                        )
                    }

                    MainTab.BOOKMARKS -> {
                        BookmarksAndAnnotationsScreen(
                            annotations = annotations,
                            documents = documents,
                            themeMode = themeMode,
                            onCopyText = ::copyTextToClipboard,
                            onDeleteAnnotation = { id ->
                                annotations = annotations.filterNot { it.id == id }
                            },
                            onJumpToAnnotation = { ann ->
                                activeDocId = ann.docId
                                showVisualMode = false
                                selectedParagraphIndex = ann.paragraphIndex
                                activeSelectionRange = TextRange(ann.startOffset, ann.endOffset)
                                currentTab = MainTab.READER
                                coroutineScope.launch {
                                    readerListState.scrollToItem(ann.paragraphIndex)
                                }
                            }
                        )
                    }
                }

                if (isParsingFile) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = themeMode.cardColor,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = themeMode.accentColor)
                                Text(
                                    text = "در حال پردازش و استخراج لایه‌های سند...",
                                    color = themeMode.textColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reader Display Settings Bottom Sheet
        if (showReaderSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showReaderSettingsSheet = false },
                containerColor = themeMode.cardColor,
                contentColor = themeMode.textColor
            ) {
                ReaderSettingsSheetContent(
                    themeMode = themeMode,
                    onSelectTheme = { themeMode = it },
                    fontSizeSp = fontSizeSp,
                    onFontSizeChange = { fontSizeSp = it },
                    lineHeightMultiplier = lineHeightMultiplier,
                    onLineHeightChange = { lineHeightMultiplier = it },
                    onClose = { showReaderSettingsSheet = false }
                )
            }
        }

        // Create Custom Persian/Arabic Text Document Dialog
        if (showNewTextDialog) {
            CreateCustomTextDialog(
                themeMode = themeMode,
                onDismiss = { showNewTextDialog = false },
                onCreateDocument = { title, rawContent ->
                    val blocks = rawContent.split(Regex("\\r?\\n"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val paras = blocks.mapIndexed { idx, line ->
                        DocumentParagraph(
                            index = idx,
                            text = line,
                            style = if (idx == 0) ParagraphStyle.TITLE else ParagraphStyle.BODY,
                            pageNumber = (idx / 6) + 1
                        )
                    }.ifEmpty {
                        listOf(DocumentParagraph(0, rawContent, ParagraphStyle.BODY, 1))
                    }
                    val newDoc = ReaderDocument(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "یادداشت متنی جدید" },
                        subtitle = "متن ایجاد شده توسط کاربر",
                        author = "کاربر",
                        format = DocumentFormat.TEXT,
                        dateAdded = "امروز",
                        fileSize = "${rawContent.length * 2} B",
                        pageCount = 1,
                        paragraphs = paras,
                        isSample = false
                    )
                    documents = listOf(newDoc) + documents
                    activeDocId = newDoc.id
                    showNewTextDialog = false
                    currentTab = MainTab.READER
                }
            )
        }
    }
}

@Composable
private fun ReaderWorkspaceScreen(
    document: ReaderDocument,
    themeMode: ReaderThemeMode,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    showVisualMode: Boolean,
    onToggleVisualMode: (Boolean) -> Unit,
    isSearchBarExpanded: Boolean,
    onToggleSearchBar: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearchOptionsRow: Boolean,
    onToggleSearchOptionsRow: () -> Unit,
    searchOptions: PersianArabicSearchEngine.SearchOptions,
    onSearchOptionsChange: (PersianArabicSearchEngine.SearchOptions) -> Unit,
    searchMatches: List<PersianArabicSearchEngine.SearchMatch>,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    activeMatch: PersianArabicSearchEngine.SearchMatch?,
    annotations: List<HighlightAnnotation>,
    selectedParagraphIndex: Int?,
    activeSelectionRange: TextRange?,
    onSelectionChange: (Int, TextRange?) -> Unit,
    onCopySelection: (String) -> Unit,
    onHighlightSelection: (Int, Int, String, Long) -> Unit,
    onShareSelection: (String) -> Unit,
    readerListState: androidx.compose.foundation.lazy.LazyListState,
    onDismissKeyboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Document Header & Mode Switcher (Smart Text Mode vs Visual PDF/Image Mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeMode.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${document.format.badgeText} · ${document.paragraphs.size} بند · ${document.fileSize}",
                    fontSize = 11.sp,
                    color = themeMode.mutedTextColor,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Mode Toggle for PDF / IMAGE documents
                if (document.format == DocumentFormat.PDF || document.format == DocumentFormat.IMAGE) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeMode.borderColor.copy(alpha = 0.5f))
                            .padding(2.dp)
                    ) {
                        val isTextSelected = !showVisualMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTextSelected) themeMode.cardColor else Color.Transparent)
                                .clickable { onToggleVisualMode(false) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "متن و کپی",
                                fontSize = 11.sp,
                                fontWeight = if (isTextSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTextSelected) themeMode.accentColor else themeMode.mutedTextColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showVisualMode) themeMode.cardColor else Color.Transparent)
                                .clickable { onToggleVisualMode(true) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (document.format == DocumentFormat.IMAGE) "نمای تصویر" else "نمای صفحه",
                                fontSize = 11.sp,
                                fontWeight = if (showVisualMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (showVisualMode) themeMode.accentColor else themeMode.mutedTextColor
                            )
                        }
                    }
                }

                // Search Bar Toggle
                FilledTonalIconButton(
                    onClick = onToggleSearchBar,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isSearchBarExpanded) themeMode.accentColor.copy(alpha = 0.16f) else themeMode.cardColor,
                        contentColor = if (isSearchBarExpanded) themeMode.accentColor else themeMode.textColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو در سند",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Smart Persian & Arabic Search Bar with Normalization Controls
        AnimatedVisibility(visible = isSearchBarExpanded) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = themeMode.cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, themeMode.borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "جستجوی فارسی و عربی (مثلاً: کتاب، بسم الله، می شود، علی)...",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = themeMode.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "پاک کردن",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onDismissKeyboard() }),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )

                        // Match Counter & Next/Prev Controls
                        if (searchQuery.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = themeMode.bgColor,
                                modifier = Modifier.border(1.dp, themeMode.borderColor, RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (searchMatches.isEmpty()) "۰/۰"
                                        else "${currentMatchIndex + 1}/${searchMatches.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (searchMatches.isEmpty()) Color(0xFFDC2626) else themeMode.accentColor
                                    )
                                    IconButton(
                                        onClick = onPrevMatch,
                                        enabled = searchMatches.isNotEmpty(),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "قبلی",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onNextMatch,
                                        enabled = searchMatches.isNotEmpty(),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "بعدی",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Filter options toggle button
                        IconButton(
                            onClick = onToggleSearchOptionsRow,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "تنظیمات جستجوی فارسی و عربی",
                                tint = if (showSearchOptionsRow) themeMode.accentColor else themeMode.mutedTextColor
                            )
                        }
                    }

                    // Quick Test Search Suggestions + Normalization Toggles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تست سریع:",
                            fontSize = 11.sp,
                            color = themeMode.mutedTextColor
                        )
                        val quickTerms = listOf(
                            "کتاب",
                            "بسم الله",
                            "می شود",
                            "علی",
                            "مسئله",
                            "۵۴۸۹۲۰"
                        )
                        quickTerms.forEach { term ->
                            val isSelected = searchQuery == term
                            Surface(
                                onClick = { onSearchQueryChange(if (isSelected) "" else term) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) themeMode.accentColor else themeMode.bgColor,
                                contentColor = if (isSelected) Color.White else themeMode.textColor,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isSelected) themeMode.accentColor else themeMode.borderColor,
                                    RoundedCornerShape(6.dp)
                                )
                            ) {
                                Text(
                                    text = term,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Expandable Normalization Options Row
                    if (showSearchOptionsRow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchOptionToggleButton(
                                label = "یکسان‌سازی ک/ك و ی/ي/ئ",
                                active = searchOptions.unifyPersianArabicChars,
                                accentColor = themeMode.accentColor,
                                onClick = {
                                    onSearchOptionsChange(
                                        searchOptions.copy(unifyPersianArabicChars = !searchOptions.unifyPersianArabicChars)
                                    )
                                }
                            )
                            SearchOptionToggleButton(
                                label = "حذف اعراب و تشدید",
                                active = searchOptions.ignoreDiacritics,
                                accentColor = themeMode.accentColor,
                                onClick = {
                                    onSearchOptionsChange(
                                        searchOptions.copy(ignoreDiacritics = !searchOptions.ignoreDiacritics)
                                    )
                                }
                            )
                            SearchOptionToggleButton(
                                label = "نیم‌فاصله هوشمند",
                                active = searchOptions.ignoreZwnj,
                                accentColor = themeMode.accentColor,
                                onClick = {
                                    onSearchOptionsChange(
                                        searchOptions.copy(ignoreZwnj = !searchOptions.ignoreZwnj)
                                    )
                                }
                            )
                            SearchOptionToggleButton(
                                label = "یکسان‌سازی اعداد",
                                active = searchOptions.unifyDigits,
                                accentColor = themeMode.accentColor,
                                onClick = {
                                    onSearchOptionsChange(
                                        searchOptions.copy(unifyDigits = !searchOptions.unifyDigits)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Main Reader Content Area (Either Visual Canvas or Smart Selectable Text List)
        if (showVisualMode) {
            VisualPageViewer(
                document = document,
                isDarkSurface = themeMode.isDark,
                onSwitchToTextLayer = { onToggleVisualMode(false) },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Helpful instruction bar when no selection is active
            if (selectedParagraphIndex == null || activeSelectionRange == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = themeMode.accentColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = themeMode.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "برای انتخاب دقیق بدون پرش کرسر، روی هر کلمه نگه‌دارید (Long Press) یا دوبار بزنید.",
                                fontSize = 11.sp,
                                color = themeMode.textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                val firstPara = document.paragraphs.firstOrNull()
                                if (firstPara != null && firstPara.text.isNotEmpty()) {
                                    onSelectionChange(
                                        0,
                                        TextRange(0, firstPara.text.length.coerceAtMost(29))
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "فعال‌سازی کرسر",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeMode.accentColor
                            )
                        }
                    }
                }
            }

            // Document Paragraphs List
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = themeMode.cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, themeMode.borderColor, RoundedCornerShape(14.dp))
            ) {
                LazyColumn(
                    state = readerListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = document.paragraphs,
                        key = { "${document.id}_p_${it.index}" }
                    ) { paragraph ->
                        val paraMatches = remember(searchMatches, paragraph.index) {
                            searchMatches.filter { it.paragraphIndex == paragraph.index }
                        }
                        val paraAnnotations = remember(annotations, paragraph.index) {
                            annotations.filter { it.paragraphIndex == paragraph.index }
                        }
                        val isSelectedPara = selectedParagraphIndex == paragraph.index

                        SmartSelectableParagraph(
                            paragraphIndex = paragraph.index,
                            text = paragraph.text,
                            style = paragraph.style,
                            fontSizeSp = fontSizeSp,
                            lineHeightMultiplier = lineHeightMultiplier,
                            textColor = themeMode.textColor,
                            accentColor = themeMode.accentColor,
                            searchMatches = paraMatches,
                            activeMatch = activeMatch,
                            annotations = paraAnnotations,
                            isActiveSelectionParagraph = isSelectedPara,
                            selectionRange = if (isSelectedPara) activeSelectionRange else null,
                            onSelectionChange = { newRange ->
                                onSelectionChange(paragraph.index, newRange)
                            },
                            onCopySelection = onCopySelection,
                            onHighlightSelection = { start, end, selText, colorHex ->
                                onHighlightSelection(paragraph.index, start, end, selText, colorHex)
                            },
                            onShareSelection = onShareSelection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchOptionToggleButton(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) accentColor.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier.border(
            1.dp,
            if (active) accentColor else Color(0xFF94A3B8).copy(alpha = 0.4f),
            RoundedCornerShape(8.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (active) accentColor else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) accentColor else Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}

/**
 * Library Screen: Displays all PDF, Word, Image, and Text documents with client-side format filtering
 * and options to import real files from the device or create a new text document.
 */
@Composable
private fun LibraryScreen(
    documents: List<ReaderDocument>,
    activeDocId: String,
    themeMode: ReaderThemeMode,
    onSelectDocument: (ReaderDocument) -> Unit,
    onOpenFilePicker: () -> Unit,
    onCreateNewTextNote: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<DocumentFormat?>(null) }

    val filteredDocs = remember(documents, selectedFilter) {
        if (selectedFilter == null) documents
        else documents.filter { it.format == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Action Banner for Opening Local PDF / Word / Image / Text Files
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = themeMode.cardColor,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, themeMode.borderColor, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "مدیریت و باز کردن اسناد (PDF, Word, PIC, TXT)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = themeMode.textColor
                )
                Text(
                    text = "فایل‌های خود را از حافظه گوشی انتخاب کنید یا متن جدیدی برای جستجو و کپی دقیق بسازید.",
                    fontSize = 12.sp,
                    color = themeMode.mutedTextColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onOpenFilePicker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeMode.accentColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انتخاب فایل از دستگاه", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onCreateNewTextNote,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ایجاد متن جدید", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        // Format Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTabButton(
                title = "همه (${documents.size})",
                selected = selectedFilter == null,
                themeMode = themeMode,
                onClick = { selectedFilter = null }
            )
            DocumentFormat.entries.forEach { format ->
                val count = documents.count { it.format == format }
                FilterTabButton(
                    title = "${format.labelFa} ($count)",
                    selected = selectedFilter == format,
                    themeMode = themeMode,
                    onClick = { selectedFilter = format }
                )
            }
        }

        // Document List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredDocs, key = { it.id }) { doc ->
                val isCurrent = doc.id == activeDocId
                Surface(
                    onClick = { onSelectDocument(doc) },
                    shape = RoundedCornerShape(14.dp),
                    color = themeMode.cardColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) themeMode.accentColor else themeMode.borderColor,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Format Icon Box
                        val iconTint = when (doc.format) {
                            DocumentFormat.PDF -> Color(0xFFDC2626)
                            DocumentFormat.WORD -> Color(0xFF2563EB)
                            DocumentFormat.IMAGE -> Color(0xFFD97706)
                            DocumentFormat.TEXT -> Color(0xFF0D9488)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconTint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = doc.format.badgeText,
                                color = iconTint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = doc.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeMode.textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = doc.subtitle,
                                fontSize = 12.sp,
                                color = themeMode.mutedTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.author} · ${doc.pageCount} صفحه · ${doc.fileSize} · ${doc.dateAdded}",
                                fontSize = 11.sp,
                                color = themeMode.mutedTextColor
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "مشاهده",
                            tint = themeMode.mutedTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTabButton(
    title: String,
    selected: Boolean,
    themeMode: ReaderThemeMode,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) themeMode.accentColor else themeMode.cardColor,
        contentColor = if (selected) Color.White else themeMode.textColor,
        modifier = Modifier.border(
            1.dp,
            if (selected) themeMode.accentColor else themeMode.borderColor,
            RoundedCornerShape(8.dp)
        )
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 1
        )
    }
}

/**
 * Global Search Lab Screen: Searches across ALL loaded documents (PDF, Word, Image OCR, Text)
 * simultaneously with Persian/Arabic normalization and displays highlighted snippets.
 */
@Composable
private fun GlobalSearchLabScreen(
    documents: List<ReaderDocument>,
    themeMode: ReaderThemeMode,
    searchOptions: PersianArabicSearchEngine.SearchOptions,
    onSearchOptionsChange: (PersianArabicSearchEngine.SearchOptions) -> Unit,
    onJumpToMatch: (String, PersianArabicSearchEngine.SearchMatch, String) -> Unit
) {
    var globalQuery by remember { mutableStateOf("کتاب") }

    val allDocMatches = remember(documents, globalQuery, searchOptions) {
        if (globalQuery.isBlank()) emptyList()
        else {
            documents.flatMap { doc ->
                val matches = PersianArabicSearchEngine.searchInParagraphs(
                    doc.paragraphs.map { it.text },
                    globalQuery,
                    searchOptions
                )
                matches.map { match -> doc to match }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "جستجوی سراسری هوشمند در تمام اسناد فارسی و عربی",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = themeMode.textColor
        )

        OutlinedTextField(
            value = globalQuery,
            onValueChange = { globalQuery = it },
            placeholder = { Text("عبارت مورد نظر را به فارسی یا عربی بنویسید...", fontSize = 13.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.ManageSearch, contentDescription = null, tint = themeMode.accentColor) },
            trailingIcon = {
                if (globalQuery.isNotEmpty()) {
                    IconButton(onClick = { globalQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Normalization Options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchOptionToggleButton(
                label = "ک/ك و ی/ي/ئ",
                active = searchOptions.unifyPersianArabicChars,
                accentColor = themeMode.accentColor,
                onClick = {
                    onSearchOptionsChange(
                        searchOptions.copy(unifyPersianArabicChars = !searchOptions.unifyPersianArabicChars)
                    )
                }
            )
            SearchOptionToggleButton(
                label = "حذف اعراب (َ ِ ُ ّ)",
                active = searchOptions.ignoreDiacritics,
                accentColor = themeMode.accentColor,
                onClick = {
                    onSearchOptionsChange(
                        searchOptions.copy(ignoreDiacritics = !searchOptions.ignoreDiacritics)
                    )
                }
            )
            SearchOptionToggleButton(
                label = "نیم‌فاصله (می‌شود = می شود)",
                active = searchOptions.ignoreZwnj,
                accentColor = themeMode.accentColor,
                onClick = {
                    onSearchOptionsChange(
                        searchOptions.copy(ignoreZwnj = !searchOptions.ignoreZwnj)
                    )
                }
            )
            SearchOptionToggleButton(
                label = "ارقام فارسی/عربی/انگلیسی",
                active = searchOptions.unifyDigits,
                accentColor = themeMode.accentColor,
                onClick = {
                    onSearchOptionsChange(
                        searchOptions.copy(unifyDigits = !searchOptions.unifyDigits)
                    )
                }
            )
        }

        Text(
            text = "نتایج یافت‌شده در کل اسناد: ${allDocMatches.size} مورد",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = themeMode.accentColor
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(allDocMatches) { (doc, match) ->
                Surface(
                    onClick = { onJumpToMatch(doc.id, match, globalQuery) },
                    shape = RoundedCornerShape(12.dp),
                    color = themeMode.cardColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, themeMode.borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${doc.format.badgeText} · ${doc.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeMode.accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "بند ${match.paragraphIndex + 1}",
                                fontSize = 11.sp,
                                color = themeMode.mutedTextColor
                            )
                        }

                        val highlightedSnippet = remember(match) {
                            buildAnnotatedString {
                                append(match.snippetText)
                                if (match.matchStartInSnippet < match.matchEndInSnippet) {
                                    addStyle(
                                        SpanStyle(
                                            background = Color(0xFFFACC15).copy(alpha = 0.55f),
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        start = match.matchStartInSnippet,
                                        end = match.matchEndInSnippet
                                    )
                                }
                            }
                        }

                        Text(
                            text = highlightedSnippet,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = themeMode.textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Saved Highlights & Copied Excerpts Screen.
 */
@Composable
private fun BookmarksAndAnnotationsScreen(
    annotations: List<HighlightAnnotation>,
    documents: List<ReaderDocument>,
    themeMode: ReaderThemeMode,
    onCopyText: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onJumpToAnnotation: (HighlightAnnotation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "هایلایت‌ها و بریده‌های متنی ذخیره‌شده (${annotations.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = themeMode.textColor
        )

        if (annotations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "هنوز متنی را هایلایت نکرده‌اید. در بخش مطالعه روی هر کلمه نگه‌دارید و دایره رنگی را بزنید.",
                    fontSize = 13.sp,
                    color = themeMode.mutedTextColor
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(annotations, key = { it.id }) { ann ->
                    val docTitle = documents.find { it.id == ann.docId }?.title ?: "سند"
                    Surface(
                        onClick = { onJumpToAnnotation(ann) },
                        shape = RoundedCornerShape(12.dp),
                        color = themeMode.cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeMode.borderColor, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(ann.colorHex))
                                    )
                                    Text(
                                        text = "$docTitle · بند ${ann.paragraphIndex + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeMode.mutedTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onCopyText(ann.selectedText) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "کپی",
                                            tint = themeMode.accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteAnnotation(ann.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "حذف",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "«${ann.selectedText}»",
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = themeMode.textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheetContent(
    themeMode: ReaderThemeMode,
    onSelectTheme: (ReaderThemeMode) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onLineHeightChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "تنظیمات محیط مطالعه و تایپوگرافی",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = themeMode.textColor
        )

        // Theme Selector
        Text(text = "تم رنگی صفحه:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderThemeMode.entries.forEach { mode ->
                val isSelected = mode == themeMode
                Surface(
                    onClick = { onSelectTheme(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = mode.bgColor,
                    contentColor = mode.textColor,
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) themeMode.accentColor else mode.borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.labelFa,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Font Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("اندازه قلم (فونت):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${fontSizeSp.toInt()} پیکسل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeMode.accentColor)
        }
        Slider(
            value = fontSizeSp,
            onValueChange = onFontSizeChange,
            valueRange = 13f..26f
        )

        // Line Height Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("فاصله خطوط:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(String.format("%.1f برابر", lineHeightMultiplier), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeMode.accentColor)
        }
        Slider(
            value = lineHeightMultiplier,
            onValueChange = onLineHeightChange,
            valueRange = 1.4f..2.4f
        )

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeMode.accentColor)
        ) {
            Text("اعمال و بستن", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CreateCustomTextDialog(
    themeMode: ReaderThemeMode,
    onDismiss: () -> Unit,
    onCreateDocument: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeMode.cardColor,
        titleContentColor = themeMode.textColor,
        textContentColor = themeMode.textColor,
        title = {
            Text("ایجاد یا جای‌گذاری متن جدید", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان سند") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("متن فارسی یا عربی خود را اینجا وارد کنید...") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onCreateDocument(title, content)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeMode.accentColor)
            ) {
                Text("ذخیره و باز کردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
