import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Search,
  FolderOpen,
  BookOpen,
  FileText,
  Image as ImageIcon,
  FileCode,
  ChevronUp,
  ChevronDown,
  Copy,
  Trash2,
  Plus,
  SlidersHorizontal,
  CheckCircle2,
  Circle,
  Sparkles,
  Bookmark,
  Layers,
  Eye,
  X,
} from 'lucide-react';
import {
  DocumentFormat,
  HighlightAnnotation,
  INITIAL_DOCUMENTS,
  ParagraphStyle,
  ReaderDocument,
} from './data/sampleDocuments';
import {
  SearchMatch,
  SearchOptions,
  searchInParagraphs,
} from './utils/persianArabicSearch';
import { parseUploadedFile } from './utils/fileParser';
import {
  SelectionRange,
  SmartSelectableParagraph,
} from './components/SmartSelectableParagraph';
import { VisualPageViewer } from './components/VisualPageViewer';

type ThemeMode = 'light' | 'sepia' | 'dark';
type ActiveSection = 'reader' | 'library' | 'global_search' | 'highlights';

export default function App() {
  const [documents, setDocuments] = useState<ReaderDocument[]>(INITIAL_DOCUMENTS);
  const [activeDocId, setActiveDocId] = useState<string>(INITIAL_DOCUMENTS[0].id);
  const [activeSection, setActiveSection] = useState<ActiveSection>('reader');
  const [themeMode, setThemeMode] = useState<ThemeMode>('light');

  // Typography & Display Controls
  const [fontSizePx, setFontSizePx] = useState<number>(17);
  const [lineHeight, setLineHeight] = useState<number>(1.95);
  const [showVisualMode, setShowVisualMode] = useState<boolean>(false);
  const [showNewTextModal, setShowNewTextModal] = useState<boolean>(false);
  const [newDocTitle, setNewDocTitle] = useState<string>('');
  const [newDocContent, setNewDocContent] = useState<string>('');

  // Smart Anti-Jump Cursor Selection State (Locked to 1 active paragraph at a time)
  const [selectedParagraphIndex, setSelectedParagraphIndex] = useState<number | null>(2);
  const [activeSelectionRange, setActiveSelectionRange] = useState<SelectionRange | null>({
    start: 0,
    end: 45,
  });

  // Search State & Persian/Arabic Normalization Options
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [globalSearchQuery, setGlobalSearchQuery] = useState<string>('کتاب');
  const [currentMatchIndex, setCurrentMatchIndex] = useState<number>(0);
  const [showSearchOptions, setShowSearchOptions] = useState<boolean>(true);
  const [searchOptions, setSearchOptions] = useState<SearchOptions>({
    unifyPersianArabicChars: true,
    ignoreDiacritics: true,
    ignoreZwnj: true,
    unifyDigits: true,
    caseSensitive: false,
    wholeWordOnly: false,
  });

  // Saved Highlights & Excerpts
  const [annotations, setAnnotations] = useState<HighlightAnnotation[]>([
    {
      id: 'ann_initial_1',
      docId: 'doc_pdf_persian_arabic',
      paragraphIndex: 2,
      startOffset: 0,
      endOffset: 75,
      selectedText:
        'یکی از مهم‌ترین چالش‌ها در جستجوی اسناد PDF و فایل‌های Word، تفاوت کدهای یونیکد',
      colorHex: '#FACC15',
      createdAt: '۱۴۰۴/۰۸/۰۷',
    },
  ]);

  // Toast feedback state
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const paragraphRefs = useRef<Record<number, HTMLDivElement | null>>({});

  // Sync `.dark` class on `document.documentElement`
  useEffect(() => {
    const root = document.documentElement;
    if (themeMode === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [themeMode]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((prev) => (prev === msg ? null : prev));
    }, 2600);
  };

  const activeDocument = useMemo(
    () => documents.find((d) => d.id === activeDocId) || documents[0],
    [documents, activeDocId]
  );

  // Compute live search matches in active document
  const searchMatches: SearchMatch[] = useMemo(() => {
    if (!searchQuery.trim()) return [];
    return searchInParagraphs(
      activeDocument.paragraphs.map((p) => p.text),
      searchQuery,
      searchOptions
    );
  }, [activeDocument, searchQuery, searchOptions]);

  useEffect(() => {
    if (currentMatchIndex >= searchMatches.length) {
      setCurrentMatchIndex(0);
    }
  }, [searchMatches.length, currentMatchIndex]);

  const activeMatch: SearchMatch | null = searchMatches[currentMatchIndex] || null;

  // Scroll to active search match paragraph when navigating matches
  useEffect(() => {
    if (activeMatch && activeSection === 'reader' && !showVisualMode) {
      const el = paragraphRefs.current[activeMatch.paragraphIndex];
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, [activeMatch, activeSection, showVisualMode]);

  // Compute global search results across all documents
  const globalMatches = useMemo(() => {
    if (!globalSearchQuery.trim()) return [];
    const list: Array<{ doc: ReaderDocument; match: SearchMatch }> = [];
    documents.forEach((doc) => {
      const m = searchInParagraphs(
        doc.paragraphs.map((p) => p.text),
        globalSearchQuery,
        searchOptions
      );
      m.forEach((match) => list.push({ doc, match }));
    });
    return list;
  }, [documents, globalSearchQuery, searchOptions]);

  // Handle Real File Upload (PDF, DOCX, Images, TXT)
  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const parsedDoc = await parseUploadedFile(file);
      setDocuments((prev) => [parsedDoc, ...prev]);
      setActiveDocId(parsedDoc.id);
      setSelectedParagraphIndex(0);
      const firstLen = parsedDoc.paragraphs[0]?.text.length || 0;
      setActiveSelectionRange(firstLen > 0 ? { start: 0, end: Math.min(35, firstLen) } : null);
      setShowVisualMode(parsedDoc.format === DocumentFormat.IMAGE);
      setActiveSection('reader');
      showToast(`سند «${parsedDoc.title}» با موفقیت بارگذاری شد`);
    } catch {
      showToast('خطا در خواندن فایل انتخاب‌شده');
    } finally {
      e.target.value = '';
    }
  };

  // Copy helper with fallback
  const copyToClipboard = async (textToCopy: string) => {
    try {
      await navigator.clipboard.writeText(textToCopy);
      showToast(`متن انتخاب‌شده کپی شد (${textToCopy.length} کاراکتر)`);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = textToCopy;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      showToast(`متن انتخاب‌شده کپی شد (${textToCopy.length} کاراکتر)`);
    }
  };

  const handleShareText = async (textToShare: string) => {
    if (navigator.share) {
      try {
        await navigator.share({ text: textToShare });
        return;
      } catch {
        // Fallback to copy
      }
    }
    await copyToClipboard(textToShare);
  };

  // Theme surface classes
  const canvasBgClass =
    themeMode === 'sepia'
      ? 'bg-[#FDF8EE] text-[#292524]'
      : 'bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100';

  const surfaceCardClass =
    themeMode === 'sepia'
      ? 'bg-[#F6EFE2] border-[#E5DCCB] text-[#292524]'
      : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-900 dark:text-slate-100';

  const [libraryFilter, setLibraryFilter] = useState<DocumentFormat | 'ALL'>('ALL');
  const filteredLibraryDocs = useMemo(
    () =>
      libraryFilter === 'ALL'
        ? documents
        : documents.filter((d) => d.format === libraryFilter),
    [documents, libraryFilter]
  );

  return (
    <div dir="rtl" className={`min-h-screen flex flex-col ${canvasBgClass} transition-colors duration-150`}>
      {/* Hidden File Input for PDF, Word (.docx), Image, and Text */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.docx,.doc,.txt,.md,image/*"
        onChange={handleFileUpload}
        className="hidden"
      />

      {/* Top Navigation Bar (Strict 3-Zone Contract) */}
      <header className="sticky top-0 z-40 flex items-center justify-between gap-8 px-6 py-3.5 border-b border-slate-200 dark:border-slate-800 bg-white/95 dark:bg-slate-900/95 backdrop-blur-md">
        {/* Zone 1: Single text element wordmark */}
        <a
          href="#reader"
          onClick={(e) => {
            e.preventDefault();
            setActiveSection('reader');
          }}
          className="text-lg font-extrabold tracking-tight text-slate-900 dark:text-white whitespace-nowrap shrink-0"
        >
          پارس‌ریدر
        </a>

        {/* Zone 2: 4 concise single-line text navigation links */}
        <nav className="hidden md:flex items-center gap-7 text-sm font-medium text-slate-600 dark:text-slate-300">
          <button
            type="button"
            onClick={() => setActiveSection('reader')}
            className={`hover:text-teal-600 dark:hover:text-teal-400 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeSection === 'reader'
                ? 'text-teal-600 dark:text-teal-400 font-bold underline underline-offset-8 decoration-2'
                : ''
            }`}
          >
            محیط مطالعه و کپی
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('library')}
            className={`hover:text-teal-600 dark:hover:text-teal-400 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeSection === 'library'
                ? 'text-teal-600 dark:text-teal-400 font-bold underline underline-offset-8 decoration-2'
                : ''
            }`}
          >
            کتابخانه اسناد
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('global_search')}
            className={`hover:text-teal-600 dark:hover:text-teal-400 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeSection === 'global_search'
                ? 'text-teal-600 dark:text-teal-400 font-bold underline underline-offset-8 decoration-2'
                : ''
            }`}
          >
            جستجوی سراسری
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('highlights')}
            className={`hover:text-teal-600 dark:hover:text-teal-400 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeSection === 'highlights'
                ? 'text-teal-600 dark:text-teal-400 font-bold underline underline-offset-8 decoration-2'
                : ''
            }`}
          >
            هایلایت‌ها
          </button>
        </nav>

        {/* Zone 3: 1 primary action */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="px-4 py-2 text-xs font-bold text-white bg-teal-600 hover:bg-teal-500 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer flex items-center gap-2"
          >
            <FolderOpen className="w-4 h-4" />
            <span>باز کردن فایل</span>
          </button>
        </div>
      </header>

      {/* Main Desktop + Mobile Responsive Workspace Container */}
      <main className="flex-1 max-w-[1400px] w-full mx-auto px-4 sm:px-6 py-5 flex flex-col lg:flex-row gap-6">
        {/* Right Sidebar (Document Switcher, Typography Controls & Search Normalization Status) */}
        <aside className="w-full lg:w-72 shrink-0 flex flex-col gap-4">
          {/* Quick Document Switcher */}
          <div className={`rounded-xl border p-4 ${surfaceCardClass}`}>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-bold flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-teal-600 dark:text-teal-400" />
                <span>اسناد فعال ({documents.length})</span>
              </h2>
              <button
                type="button"
                onClick={() => setShowNewTextModal(true)}
                className="text-xs font-semibold text-teal-600 dark:text-teal-400 hover:underline flex items-center gap-1 cursor-pointer whitespace-nowrap shrink-0"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>متن جدید</span>
              </button>
            </div>

            <div className="space-y-2">
              {documents.map((doc) => {
                const isCurrent = doc.id === activeDocId;
                return (
                  <button
                    key={doc.id}
                    type="button"
                    onClick={() => {
                      setActiveDocId(doc.id);
                      setSelectedParagraphIndex(0);
                      const firstParaLen = doc.paragraphs[0]?.text.length || 0;
                      setActiveSelectionRange(
                        firstParaLen > 0 ? { start: 0, end: Math.min(30, firstParaLen) } : null
                      );
                      setShowVisualMode(false);
                      setActiveSection('reader');
                    }}
                    className={`w-full text-right p-2.5 rounded-lg border transition-colors cursor-pointer flex items-start gap-2.5 ${
                      isCurrent
                        ? 'border-teal-600 dark:border-teal-400 bg-teal-500/[0.07]'
                        : 'border-transparent hover:bg-slate-500/[0.05]'
                    }`}
                  >
                    <FormatIcon format={doc.format} />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-bold truncate">{doc.title}</p>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 truncate">
                        {doc.formatLabelFa} · {doc.fileSize} · {doc.pageCount} صفحه
                      </p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Reader Display & Theme Controls */}
          <div className={`rounded-xl border p-4 ${surfaceCardClass}`}>
            <h2 className="text-sm font-bold flex items-center gap-2 mb-3">
              <SlidersHorizontal className="w-4 h-4 text-teal-600 dark:text-teal-400" />
              <span>تنظیمات محیط مطالعه</span>
            </h2>

            {/* Theme Mode Selector */}
            <div className="grid grid-cols-3 gap-1.5 p-1 rounded-lg bg-slate-100 dark:bg-slate-800 mb-4">
              {(
                [
                  { id: 'light', label: 'روشن' },
                  { id: 'sepia', label: 'کاغذی' },
                  { id: 'dark', label: 'تاریک' },
                ] as const
              ).map((m) => (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => setThemeMode(m.id)}
                  className={`py-1.5 px-2 rounded-md text-xs font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                    themeMode === m.id
                      ? 'bg-white dark:bg-slate-900 text-teal-600 dark:text-teal-400 shadow-xs'
                      : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>

            {/* Font Size Control */}
            <div className="space-y-3 text-xs">
              <div>
                <div className="flex justify-between mb-1">
                  <span className="text-slate-500 dark:text-slate-400">اندازه قلم (فونت):</span>
                  <span className="font-mono-num font-bold text-teal-600 dark:text-teal-400">
                    {fontSizePx}px
                  </span>
                </div>
                <input
                  type="range"
                  min={14}
                  max={26}
                  step={1}
                  value={fontSizePx}
                  onChange={(e) => setFontSizePx(Number(e.target.value))}
                  className="w-full accent-teal-600 cursor-pointer"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="text-slate-500 dark:text-slate-400">فاصله خطوط:</span>
                  <span className="font-mono-num font-bold text-teal-600 dark:text-teal-400">
                    {lineHeight.toFixed(2)}x
                  </span>
                </div>
                <input
                  type="range"
                  min={1.5}
                  max={2.4}
                  step={0.05}
                  value={lineHeight}
                  onChange={(e) => setLineHeight(Number(e.target.value))}
                  className="w-full accent-teal-600 cursor-pointer"
                />
              </div>
            </div>
          </div>

          {/* Smart Cursor Guide Card */}
          <div className={`rounded-xl border p-4 ${surfaceCardClass}`}>
            <h3 className="text-xs font-bold text-teal-600 dark:text-teal-400 mb-1.5 flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" />
              <span>موتور انتخاب ضدپرش کرسر</span>
            </h3>
            <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
              روی هر کلمه در متن کلیک یا لمس کنید؛ دستگیره راست (شروع) و دستگیره چپ (پایان) با قفل
              مختصات محلی دقیقاً در جهت حرکت انگشت شما حرکت کرده و هرگز کل متون بالای صفحه را به کپی
              اضافه نمی‌کنند.
            </p>
          </div>
        </aside>

        {/* Central Content Viewport */}
        <section className="flex-1 min-w-0 flex flex-col gap-4">
          {activeSection === 'reader' && (
            <>
              {/* Document Header & Search Bar Panel */}
              <div className={`rounded-xl border p-4 ${surfaceCardClass}`}>
                <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-200/80 dark:border-slate-800">
                  <div>
                    <h1 className="text-base sm:text-lg font-extrabold text-balance">
                      {activeDocument.title}
                    </h1>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                      {activeDocument.formatLabelFa} · {activeDocument.author} ·{' '}
                      {activeDocument.paragraphs.length} بند · {activeDocument.fileSize}
                    </p>
                  </div>

                  {/* Mode Switcher: Smart Text & Selection vs Visual Page / Image Zoom */}
                  <div className="flex items-center gap-1 p-1 rounded-lg bg-slate-100 dark:bg-slate-800">
                    <button
                      type="button"
                      onClick={() => setShowVisualMode(false)}
                      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        !showVisualMode
                          ? 'bg-white dark:bg-slate-900 text-teal-600 dark:text-teal-400 shadow-xs'
                          : 'text-slate-600 dark:text-slate-400'
                      }`}
                    >
                      <Layers className="w-3.5 h-3.5" />
                      <span>متن هوشمند و انتخاب دقیق</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowVisualMode(true)}
                      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-bold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        showVisualMode
                          ? 'bg-white dark:bg-slate-900 text-teal-600 dark:text-teal-400 shadow-xs'
                          : 'text-slate-600 dark:text-slate-400'
                      }`}
                    >
                      <Eye className="w-3.5 h-3.5" />
                      <span>
                        {activeDocument.format === DocumentFormat.IMAGE
                          ? 'نمای تصویر و زوم'
                          : 'نمای گرافیکی صفحه'}
                      </span>
                    </button>
                  </div>
                </div>

                {/* Persian & Arabic Smart Search Input Row */}
                <div className="mt-3 space-y-2.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="relative flex-1 min-w-[230px]">
                      <Search className="w-4 h-4 text-teal-600 dark:text-teal-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => {
                          setSearchQuery(e.target.value);
                          setCurrentMatchIndex(0);
                        }}
                        placeholder="جستجوی تمام کلمات و حروف فارسی و عربی (مثلاً: کتاب، بسم الله، می شود، علی، ۵۴۸۹۲۰)..."
                        className="w-full pr-9 pl-8 py-2 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 text-xs sm:text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
                      />
                      {searchQuery && (
                        <button
                          type="button"
                          onClick={() => setSearchQuery('')}
                          className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      )}
                    </div>

                    {/* Match Counter & Next/Prev Navigation */}
                    {searchQuery.trim() !== '' && (
                      <div className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-950">
                        <span
                          className={`font-mono-num text-xs font-bold px-1.5 ${
                            searchMatches.length === 0
                              ? 'text-red-600 dark:text-red-400'
                              : 'text-teal-600 dark:text-teal-400'
                          }`}
                        >
                          {searchMatches.length === 0
                            ? '0 / 0'
                            : `${currentMatchIndex + 1} / ${searchMatches.length}`}
                        </span>
                        <button
                          type="button"
                          disabled={searchMatches.length === 0}
                          onClick={() =>
                            setCurrentMatchIndex(
                              (i) => (i - 1 + searchMatches.length) % searchMatches.length
                            )
                          }
                          className="p-1 rounded hover:bg-slate-200 dark:hover:bg-slate-800 disabled:opacity-40 cursor-pointer"
                          title="نتیجه قبلی"
                        >
                          <ChevronUp className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          disabled={searchMatches.length === 0}
                          onClick={() =>
                            setCurrentMatchIndex((i) => (i + 1) % searchMatches.length)
                          }
                          className="p-1 rounded hover:bg-slate-200 dark:hover:bg-slate-800 disabled:opacity-40 cursor-pointer"
                          title="نتیجه بعدی"
                        >
                          <ChevronDown className="w-4 h-4" />
                        </button>
                      </div>
                    )}

                    <button
                      type="button"
                      onClick={() => setShowSearchOptions((v) => !v)}
                      className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      {showSearchOptions ? 'بستن فیلترهای املایی' : 'فیلترهای فارسی/عربی'}
                    </button>
                  </div>

                  {/* Quick One-Tap Persian & Arabic Search Presets */}
                  <div className="flex flex-wrap items-center gap-1.5 text-xs">
                    <span className="text-slate-500 dark:text-slate-400 ml-1">آزمایش سریع جستجو:</span>
                    {[
                      { label: 'کتاب (ک/ك)', query: 'کتاب' },
                      { label: 'بسم الله (بدون اعراب)', query: 'بسم الله' },
                      { label: 'می شود (نیم‌فاصله)', query: 'می شود' },
                      { label: 'علی (ی/ي/ى)', query: 'علی' },
                      { label: 'مسئله / مسأله', query: 'مسئله' },
                      { label: 'حکمه (ة/ه)', query: 'حکمه' },
                    ].map((preset) => {
                      const active = searchQuery === preset.query;
                      return (
                        <button
                          key={preset.query}
                          type="button"
                          onClick={() => setSearchQuery(active ? '' : preset.query)}
                          className={`px-2.5 py-1 rounded-md text-xs font-medium border transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                            active
                              ? 'bg-teal-600 text-white border-teal-600'
                              : 'border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                          }`}
                        >
                          {preset.label}
                        </button>
                      );
                    })}
                  </div>

                  {/* Normalization Toggles */}
                  {showSearchOptions && (
                    <div className="pt-2 border-t border-slate-200/70 dark:border-slate-800 flex flex-wrap items-center gap-2">
                      <NormalizationToggle
                        label="یکسان‌سازی ک/ك و ی/ي/ئ/ى و أ/إ/آ/ا و ة/ه"
                        active={searchOptions.unifyPersianArabicChars}
                        onClick={() =>
                          setSearchOptions((s) => ({
                            ...s,
                            unifyPersianArabicChars: !s.unifyPersianArabicChars,
                          }))
                        }
                      />
                      <NormalizationToggle
                        label="نادیده گرفتن اعراب و حرکات (َ ِ ُ ً ّ)"
                        active={searchOptions.ignoreDiacritics}
                        onClick={() =>
                          setSearchOptions((s) => ({
                            ...s,
                            ignoreDiacritics: !s.ignoreDiacritics,
                          }))
                        }
                      />
                      <NormalizationToggle
                        label="هوشمندی نیم‌فاصله (می‌شود = می شود = میشود)"
                        active={searchOptions.ignoreZwnj}
                        onClick={() =>
                          setSearchOptions((s) => ({
                            ...s,
                            ignoreZwnj: !s.ignoreZwnj,
                          }))
                        }
                      />
                      <NormalizationToggle
                        label="یکسان‌سازی اعداد فارسی/عربی/انگلیسی"
                        active={searchOptions.unifyDigits}
                        onClick={() =>
                          setSearchOptions((s) => ({
                            ...s,
                            unifyDigits: !s.unifyDigits,
                          }))
                        }
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Reader Viewport: Either Visual Page/Image Zoom OR Smart Selectable Paragraphs */}
              {showVisualMode ? (
                <VisualPageViewer
                  document={activeDocument}
                  onSwitchToTextLayer={() => setShowVisualMode(false)}
                />
              ) : (
                <div className={`rounded-xl border p-5 sm:p-7 space-y-4 ${surfaceCardClass}`}>
                  {activeDocument.paragraphs.map((para) => {
                    const paraMatches = searchMatches.filter(
                      (m) => m.paragraphIndex === para.index
                    );
                    const paraAnnotations = annotations.filter(
                      (a) => a.docId === activeDocument.id && a.paragraphIndex === para.index
                    );
                    const isSelectedPara = selectedParagraphIndex === para.index;

                    return (
                      <div
                        key={`${activeDocument.id}_${para.index}`}
                        ref={(el) => {
                          paragraphRefs.current[para.index] = el;
                        }}
                      >
                        <SmartSelectableParagraph
                          paragraphIndex={para.index}
                          text={para.text}
                          style={para.style}
                          fontSizePx={fontSizePx}
                          lineHeight={lineHeight}
                          searchMatches={paraMatches}
                          activeMatch={
                            activeMatch?.paragraphIndex === para.index ? activeMatch : null
                          }
                          annotations={paraAnnotations}
                          isActiveSelectionParagraph={isSelectedPara}
                          selectionRange={isSelectedPara ? activeSelectionRange : null}
                          onSelectionChange={(newRange) => {
                            if (newRange === null) {
                              setSelectedParagraphIndex(null);
                              setActiveSelectionRange(null);
                            } else {
                              setSelectedParagraphIndex(para.index);
                              setActiveSelectionRange(newRange);
                            }
                          }}
                          onCopySelection={copyToClipboard}
                          onHighlightSelection={(start, end, selectedText, colorHex) => {
                            const newAnn: HighlightAnnotation = {
                              id: `ann_${Date.now()}`,
                              docId: activeDocument.id,
                              paragraphIndex: para.index,
                              startOffset: start,
                              endOffset: end,
                              selectedText,
                              colorHex,
                              createdAt: 'امروز',
                            };
                            setAnnotations((prev) => [newAnn, ...prev]);
                            showToast('بخش انتخابی هایلایت و ذخیره شد');
                          }}
                          onShareSelection={handleShareText}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}

          {/* Library Section */}
          {activeSection === 'library' && (
            <div className={`rounded-xl border p-6 space-y-5 ${surfaceCardClass}`}>
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h1 className="text-lg font-extrabold">
                    کتابخانه اسناد (PDF, Word, PIC, Text)
                  </h1>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                    فایل‌های خود را از دستگاه بارگذاری کنید یا متن جدیدی برای مطالعه و جستجوی هوشمند بسازید.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="px-4 py-2 rounded-lg bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold flex items-center gap-2 cursor-pointer whitespace-nowrap shrink-0"
                  >
                    <FolderOpen className="w-4 h-4" />
                    <span>انتخاب فایل از دستگاه</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowNewTextModal(true)}
                    className="px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-xs font-bold flex items-center gap-2 cursor-pointer whitespace-nowrap shrink-0"
                  >
                    <Plus className="w-4 h-4" />
                    <span>ایجاد سند متنی</span>
                  </button>
                </div>
              </div>

              {/* Format Filter Segmented Control */}
              <div className="flex flex-wrap items-center gap-1.5 p-1 rounded-lg bg-slate-100 dark:bg-slate-800 w-fit">
                {(
                  [
                    { id: 'ALL', label: `همه (${documents.length})` },
                    { id: DocumentFormat.PDF, label: 'سند PDF' },
                    { id: DocumentFormat.WORD, label: 'فایل Word' },
                    { id: DocumentFormat.IMAGE, label: 'تصویر (PIC)' },
                    { id: DocumentFormat.TEXT, label: 'متن (TXT)' },
                  ] as const
                ).map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setLibraryFilter(tab.id)}
                    className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                      libraryFilter === tab.id
                        ? 'bg-white dark:bg-slate-900 text-teal-600 dark:text-teal-400 shadow-xs'
                        : 'text-slate-600 dark:text-slate-400'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {filteredLibraryDocs.map((doc) => (
                  <div
                    key={doc.id}
                    onClick={() => {
                      setActiveDocId(doc.id);
                      setShowVisualMode(false);
                      setActiveSection('reader');
                    }}
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-teal-500 transition-colors cursor-pointer flex items-start gap-3.5"
                  >
                    <FormatIcon format={doc.format} large />
                    <div className="flex-1 min-w-0">
                      <h3 className="text-sm font-bold truncate">{doc.title}</h3>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 line-clamp-2">
                        {doc.subtitle}
                      </p>
                      <div className="mt-3 flex items-center gap-2 text-[11px] text-slate-500 dark:text-slate-400">
                        <span>{doc.formatLabelFa}</span>
                        <span>·</span>
                        <span>{doc.pageCount} صفحه</span>
                        <span>·</span>
                        <span>{doc.fileSize}</span>
                        <span>·</span>
                        <span>{doc.dateAdded}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Global Search Across All Documents */}
          {activeSection === 'global_search' && (
            <div className={`rounded-xl border p-6 space-y-5 ${surfaceCardClass}`}>
              <div>
                <h1 className="text-lg font-extrabold">
                  جستجوی سراسری هوشمند در تمام اسناد فارسی و عربی
                </h1>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  جستجوی همزمان در تمامی فایل‌های PDF، Word، تصاویر دارای متن و متون ساده با یکسان‌سازی کامل حروف و اعراب.
                </p>
              </div>

              <div className="relative">
                <Search className="w-4 h-4 text-teal-600 absolute right-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={globalSearchQuery}
                  onChange={(e) => setGlobalSearchQuery(e.target.value)}
                  placeholder="عبارت مورد نظر را وارد کنید (مثلاً: کتاب، قیمه، بسم الله، ۵۴۸۹۲۰)..."
                  className="w-full pr-10 pl-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
                />
              </div>

              <div className="text-xs font-bold text-teal-600 dark:text-teal-400">
                تعداد نتایج یافت‌شده در کل اسناد: <span className="font-mono-num">{globalMatches.length}</span> مورد
              </div>

              <div className="space-y-3">
                {globalMatches.map(({ doc, match }, idx) => (
                  <div
                    key={`${doc.id}_${match.paragraphIndex}_${idx}`}
                    onClick={() => {
                      setActiveDocId(doc.id);
                      setSearchQuery(globalSearchQuery);
                      setShowVisualMode(false);
                      setActiveSection('reader');
                    }}
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-teal-500 transition-colors cursor-pointer space-y-2"
                  >
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-teal-600 dark:text-teal-400">
                        {doc.formatLabelFa} · {doc.title}
                      </span>
                      <span className="text-slate-500 font-mono-num">
                        بند {match.paragraphIndex + 1}
                      </span>
                    </div>
                    <p className="text-sm leading-relaxed">
                      {match.snippetText.slice(0, match.matchStartInSnippet)}
                      <mark className="bg-yellow-300 text-slate-950 font-bold px-1 rounded-xs">
                        {match.snippetText.slice(
                          match.matchStartInSnippet,
                          match.matchEndInSnippet
                        )}
                      </mark>
                      {match.snippetText.slice(match.matchEndInSnippet)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Saved Highlights & Copied Excerpts */}
          {activeSection === 'highlights' && (
            <div className={`rounded-xl border p-6 space-y-5 ${surfaceCardClass}`}>
              <div className="flex items-center justify-between">
                <div>
                  <h1 className="text-lg font-extrabold flex items-center gap-2">
                    <Bookmark className="w-5 h-5 text-teal-600" />
                    <span>هایلایت‌ها و بریده‌های ذخیره‌شده ({annotations.length})</span>
                  </h1>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                    تمامی بخش‌هایی که با دستگیره هوشمند انتخاب و هایلایت کرده‌اید در اینجا قابل کپی و مرور هستند.
                  </p>
                </div>
              </div>

              {annotations.length === 0 ? (
                <div className="py-12 text-center text-sm text-slate-500">
                  هنوز متنی را هایلایت نکرده‌اید. در محیط مطالعه روی هر کلمه کلیک کرده و یکی از دایره‌های رنگی را بزنید.
                </div>
              ) : (
                <div className="space-y-3">
                  {annotations.map((ann) => {
                    const doc = documents.find((d) => d.id === ann.docId);
                    return (
                      <div
                        key={ann.id}
                        className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 space-y-2"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2 text-xs text-slate-500">
                            <span
                              className="w-3 h-3 rounded-full inline-block"
                              style={{ backgroundColor: ann.colorHex }}
                            />
                            <span className="font-bold text-slate-700 dark:text-slate-300">
                              {doc?.title || 'سند'}
                            </span>
                            <span>·</span>
                            <span>بند {ann.paragraphIndex + 1}</span>
                          </div>

                          <div className="flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => copyToClipboard(ann.selectedText)}
                              className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-teal-600 cursor-pointer"
                              title="کپی"
                            >
                              <Copy className="w-4 h-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                setAnnotations((prev) => prev.filter((a) => a.id !== ann.id))
                              }
                              className="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-red-500 cursor-pointer"
                              title="حذف"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>

                        <p className="text-sm leading-relaxed font-medium">
                          «{ann.selectedText}»
                        </p>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </section>
      </main>

      {/* Modal for Creating Custom Persian/Arabic Text Document */}
      {showNewTextModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className={`max-w-lg w-full rounded-2xl border p-6 space-y-4 shadow-2xl ${surfaceCardClass}`}>
            <div className="flex items-center justify-between">
              <h3 className="text-base font-extrabold">ایجاد یا جای‌گذاری متن جدید</h3>
              <button
                type="button"
                onClick={() => setShowNewTextModal(false)}
                className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <input
                type="text"
                value={newDocTitle}
                onChange={(e) => setNewDocTitle(e.target.value)}
                placeholder="عنوان سند (مثلاً: جزوه حقوق مدنی یا شعر حافظ)"
                className="w-full px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-700 bg-transparent text-sm"
              />
              <textarea
                rows={6}
                value={newDocContent}
                onChange={(e) => setNewDocContent(e.target.value)}
                placeholder="متن فارسی یا عربی خود را اینجا وارد کنید تا قابلیت جستجوی هوشمند و انتخاب دقیق بدون پرش کرسر روی آن فعال شود..."
                className="w-full px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-700 bg-transparent text-sm leading-relaxed"
              />
            </div>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowNewTextModal(false)}
                className="px-4 py-2 rounded-lg text-xs font-semibold text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              >
                انصراف
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!newDocContent.trim()) return;
                  const lines = newDocContent
                    .split(/\r?\n/)
                    .map((l) => l.trim())
                    .filter(Boolean);
                  const newDoc: ReaderDocument = {
                    id: `doc_custom_${Date.now()}`,
                    title: newDocTitle.trim() || 'یادداشت متنی جدید',
                    subtitle: 'متن ایجادشده توسط کاربر',
                    author: 'کاربر',
                    format: DocumentFormat.TEXT,
                    formatLabelFa: 'متن ساده',
                    dateAdded: 'امروز',
                    fileSize: `${newDocContent.length * 2} B`,
                    pageCount: 1,
                    paragraphs: lines.map((line, idx) => ({
                      index: idx,
                      text: line,
                      style: idx === 0 ? ParagraphStyle.TITLE : ParagraphStyle.BODY,
                      pageNumber: 1,
                    })),
                  };
                  setDocuments((prev) => [newDoc, ...prev]);
                  setActiveDocId(newDoc.id);
                  setNewDocTitle('');
                  setNewDocContent('');
                  setShowNewTextModal(false);
                  setActiveSection('reader');
                  showToast('سند متنی جدید ایجاد و باز شد');
                }}
                className="px-4 py-2 rounded-lg bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold cursor-pointer"
              >
                ذخیره و مطالعه
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Non-intrusive Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-5 left-5 z-50 px-4 py-2.5 rounded-xl bg-slate-900 text-white text-xs font-bold shadow-xl border border-slate-700 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-teal-400" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}

function NormalizationToggle({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-medium border transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
        active
          ? 'border-teal-600/60 bg-teal-500/10 text-teal-700 dark:text-teal-300 font-bold'
          : 'border-slate-200 dark:border-slate-700 text-slate-500'
      }`}
    >
      {active ? (
        <CheckCircle2 className="w-3.5 h-3.5 text-teal-600 dark:text-teal-400" />
      ) : (
        <Circle className="w-3.5 h-3.5 text-slate-400" />
      )}
      <span>{label}</span>
    </button>
  );
}

function FormatIcon({
  format,
  large = false,
}: {
  format: DocumentFormat;
  large?: boolean;
}) {
  const sizeClass = large ? 'w-11 h-11 text-xs' : 'w-8 h-8 text-[10px]';
  switch (format) {
    case DocumentFormat.PDF:
      return (
        <div
          className={`${sizeClass} rounded-lg bg-red-500/10 text-red-600 dark:text-red-400 font-extrabold flex flex-col items-center justify-center shrink-0`}
        >
          <FileText className={large ? 'w-4 h-4 mb-0.5' : 'w-3.5 h-3.5'} />
          <span>PDF</span>
        </div>
      );
    case DocumentFormat.WORD:
      return (
        <div
          className={`${sizeClass} rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400 font-extrabold flex flex-col items-center justify-center shrink-0`}
        >
          <FileText className={large ? 'w-4 h-4 mb-0.5' : 'w-3.5 h-3.5'} />
          <span>DOCX</span>
        </div>
      );
    case DocumentFormat.IMAGE:
      return (
        <div
          className={`${sizeClass} rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400 font-extrabold flex flex-col items-center justify-center shrink-0`}
        >
          <ImageIcon className={large ? 'w-4 h-4 mb-0.5' : 'w-3.5 h-3.5'} />
          <span>PIC</span>
        </div>
      );
    case DocumentFormat.TEXT:
      return (
        <div
          className={`${sizeClass} rounded-lg bg-teal-500/10 text-teal-600 dark:text-teal-400 font-extrabold flex flex-col items-center justify-center shrink-0`}
        >
          <FileCode className={large ? 'w-4 h-4 mb-0.5' : 'w-3.5 h-3.5'} />
          <span>TXT</span>
        </div>
      );
  }
}
