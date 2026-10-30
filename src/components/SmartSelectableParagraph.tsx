import React, { useEffect, useRef, useState } from 'react';
import { Copy, Check, Share2, Maximize2, X, Sparkles } from 'lucide-react';
import { HighlightAnnotation, ParagraphStyle } from '../data/sampleDocuments';
import { SearchMatch } from '../utils/persianArabicSearch';

export interface SelectionRange {
  start: number;
  end: number;
}

interface SmartSelectableParagraphProps {
  paragraphIndex: number;
  text: string;
  style: ParagraphStyle;
  fontSizePx: number;
  lineHeight: number;
  searchMatches: SearchMatch[];
  activeMatch: SearchMatch | null;
  annotations: HighlightAnnotation[];
  isActiveSelectionParagraph: boolean;
  selectionRange: SelectionRange | null;
  onSelectionChange: (range: SelectionRange | null) => void;
  onCopySelection: (text: string) => void;
  onHighlightSelection: (start: number, end: number, selectedText: string, colorHex: string) => void;
  onShareSelection: (text: string) => void;
}

/**
 * Custom RTL-aware Selectable Paragraph that completely prevents the classic cursor jumping bug!
 *
 * Why standard browser/mobile text selection jumps to the top of the screen:
 * When dragging a native selection handle in RTL text, if the finger or pointer crosses 1px outside
 * the paragraph box or hits a margin gap, the browser/OS selection engine falls back to the root document
 * node (offset 0,0) and highlights all paragraphs above it up to the top of the page.
 *
 * How SmartSelectableParagraph solves this:
 * 1. Native erratic cross-container selection is disabled (`user-select: none`).
 * 2. Every character in the active paragraph is indexed with `data-char-index="i"`.
 * 3. Dragging either the Start Handle (Right in RTL) or End Handle (Left in RTL) uses a 2D Euclidean
 *    nearest-character clamping algorithm strictly restricted to THIS paragraph's character spans!
 *    Even if the user drags their finger 300px outside the box, the cursor smoothly tracks along the
 *    exact direction of finger movement without ever jumping to another paragraph or the top of the page.
 */
export const SmartSelectableParagraph: React.FC<SmartSelectableParagraphProps> = ({
  text,
  style,
  fontSizePx,
  lineHeight,
  searchMatches,
  activeMatch,
  annotations,
  isActiveSelectionParagraph,
  selectionRange,
  onSelectionChange,
  onCopySelection,
  onHighlightSelection,
  onShareSelection,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [draggingHandle, setDraggingHandle] = useState<'start' | 'end' | 'range' | null>(null);
  const [rangeAnchorIdx, setRangeAnchorIdx] = useState<number | null>(null);
  const [handlePositions, setHandlePositions] = useState<{
    startX: number;
    startY: number;
    startHeight: number;
    endX: number;
    endY: number;
    endHeight: number;
  } | null>(null);
  const [copiedFeedback, setCopiedFeedback] = useState(false);

  const normalizedRange: SelectionRange | null = selectionRange
    ? {
        start: Math.max(0, Math.min(selectionRange.start, selectionRange.end)),
        end: Math.min(text.length, Math.max(selectionRange.start, selectionRange.end)),
      }
    : null;

  // Helper: Find word boundaries around a clicked character index
  const getWordRangeAtChar = (charIdx: number): SelectionRange => {
    if (!text.length) return { start: 0, end: 0 };
    const safeIdx = Math.max(0, Math.min(text.length - 1, charIdx));
    let start = safeIdx;
    let end = safeIdx + 1;

    while (start > 0 && !/\s/.test(text[start - 1])) {
      start--;
    }
    while (end < text.length && !/\s/.test(text[end])) {
      end++;
    }
    return { start, end };
  };

  // Helper: Find exact character index inside THIS paragraph closest to pointer (clientX, clientY).
  // Because it only queries spans inside `containerRef.current`, it can NEVER jump to the top of the page!
  const getClampedCharIndexFromPoint = (clientX: number, clientY: number): number | null => {
    const container = containerRef.current;
    if (!container) return null;

    const charSpans = container.querySelectorAll<HTMLElement>('[data-char-index]');
    if (!charSpans.length) return null;

    let bestIndex = 0;
    let bestScore = Number.POSITIVE_INFINITY;

    for (let i = 0; i < charSpans.length; i++) {
      const span = charSpans[i];
      const rect = span.getBoundingClientRect();

      // Calculate vertical distance to the line band and horizontal distance to the character center
      let dy = 0;
      if (clientY < rect.top) {
        dy = rect.top - clientY;
      } else if (clientY > rect.bottom) {
        dy = clientY - rect.bottom;
      }

      const centerX = (rect.left + rect.right) / 2;
      const dx = Math.abs(clientX - centerX);

      // Weight vertical line match strongly so dragging across lines transitions naturally line-by-line
      // while staying strictly clamped inside this paragraph
      const score = dy * 4.5 + dx;
      if (score < bestScore) {
        bestScore = score;
        const idxAttr = span.getAttribute('data-char-index');
        if (idxAttr !== null) {
          bestIndex = parseInt(idxAttr, 10);
        }
      }
    }

    return bestIndex;
  };

  // Measure and update exact pixel positions for the Start & End teardrop handles
  useEffect(() => {
    if (!isActiveSelectionParagraph || !normalizedRange || normalizedRange.start === normalizedRange.end) {
      setHandlePositions(null);
      return;
    }

    const updatePositions = () => {
      const container = containerRef.current;
      if (!container) return;

      const containerRect = container.getBoundingClientRect();
      const startSpan = container.querySelector<HTMLElement>(
        `[data-char-index="${normalizedRange.start}"]`
      );
      const endCharIdx = Math.max(normalizedRange.start, normalizedRange.end - 1);
      const endSpan = container.querySelector<HTMLElement>(`[data-char-index="${endCharIdx}"]`);

      if (startSpan && endSpan) {
        const startRect = startSpan.getBoundingClientRect();
        const endRect = endSpan.getBoundingClientRect();

        // In RTL Persian/Arabic text, the start of a character is its RIGHT edge,
        // and the end of the last selected character is its LEFT edge.
        setHandlePositions({
          startX: startRect.right - containerRect.left,
          startY: startRect.bottom - containerRect.top,
          startHeight: startRect.height || 24,
          endX: endRect.left - containerRect.left,
          endY: endRect.bottom - containerRect.top,
          endHeight: endRect.height || 24,
        });
      }
    };

    updatePositions();
    window.addEventListener('resize', updatePositions);
    return () => window.removeEventListener('resize', updatePositions);
  }, [isActiveSelectionParagraph, normalizedRange?.start, normalizedRange?.end, fontSizePx, lineHeight, text]);

  // Global pointer move / up listeners while dragging a handle or sweeping a selection
  useEffect(() => {
    if (!draggingHandle) return;

    const handlePointerMove = (e: PointerEvent) => {
      e.preventDefault();
      const targetCharIdx = getClampedCharIndexFromPoint(e.clientX, e.clientY);
      if (targetCharIdx === null) return;

      if (draggingHandle === 'start' && normalizedRange) {
        // Move start boundary smoothly in the exact direction of finger movement
        if (targetCharIdx <= normalizedRange.end - 1) {
          onSelectionChange({ start: targetCharIdx, end: normalizedRange.end });
        } else {
          // If dragged past the end handle, flip smoothly around end handle
          onSelectionChange({ start: normalizedRange.end - 1, end: targetCharIdx + 1 });
        }
      } else if (draggingHandle === 'end' && normalizedRange) {
        // Move end boundary smoothly in the exact direction of finger movement
        const newEndExclusive = targetCharIdx + 1;
        if (newEndExclusive >= normalizedRange.start + 1) {
          onSelectionChange({ start: normalizedRange.start, end: newEndExclusive });
        } else {
          onSelectionChange({ start: targetCharIdx, end: normalizedRange.start + 1 });
        }
      } else if (draggingHandle === 'range' && rangeAnchorIdx !== null) {
        const s = Math.min(rangeAnchorIdx, targetCharIdx);
        const endEx = Math.max(rangeAnchorIdx, targetCharIdx) + 1;
        onSelectionChange({ start: s, end: endEx });
      }
    };

    const handlePointerUp = () => {
      setDraggingHandle(null);
      setRangeAnchorIdx(null);
    };

    window.addEventListener('pointermove', handlePointerMove, { passive: false });
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
    };
  }, [draggingHandle, normalizedRange, rangeAnchorIdx, text.length]);

  const selectedString =
    normalizedRange && normalizedRange.start < normalizedRange.end
      ? text.slice(normalizedRange.start, normalizedRange.end)
      : '';

  const handleCopyClick = () => {
    if (!selectedString) return;
    onCopySelection(selectedString);
    setCopiedFeedback(true);
    setTimeout(() => setCopiedFeedback(false), 1800);
  };

  // Precompute character state map (search match, active match, user highlight, active cursor selection)
  const charMeta = React.useMemo(() => {
    const meta = new Array<{
      inSelection: boolean;
      inActiveMatch: boolean;
      inSearchMatch: boolean;
      highlightColor: string | null;
    }>(text.length);

    for (let i = 0; i < text.length; i++) {
      const inSelection =
        isActiveSelectionParagraph &&
        normalizedRange !== null &&
        i >= normalizedRange.start &&
        i < normalizedRange.end;

      const inActiveMatch =
        activeMatch !== null &&
        i >= activeMatch.startOffset &&
        i < activeMatch.endOffset;

      let inSearchMatch = false;
      if (!inActiveMatch && searchMatches.length > 0) {
        for (let m = 0; m < searchMatches.length; m++) {
          if (i >= searchMatches[m].startOffset && i < searchMatches[m].endOffset) {
            inSearchMatch = true;
            break;
          }
        }
      }

      let highlightColor: string | null = null;
      if (annotations.length > 0) {
        for (let a = 0; a < annotations.length; a++) {
          if (i >= annotations[a].startOffset && i < annotations[a].endOffset) {
            highlightColor = annotations[a].colorHex;
            break;
          }
        }
      }

      meta[i] = {
        inSelection,
        inActiveMatch,
        inSearchMatch,
        highlightColor,
      };
    }
    return meta;
  }, [
    text,
    isActiveSelectionParagraph,
    normalizedRange?.start,
    normalizedRange?.end,
    activeMatch,
    searchMatches,
    annotations,
  ]);

  const effectiveFontSize =
    style === ParagraphStyle.TITLE
      ? fontSizePx + 6
      : style === ParagraphStyle.HEADING
      ? fontSizePx + 3
      : style === ParagraphStyle.SUBHEADING
      ? fontSizePx + 1
      : fontSizePx;

  const fontWeightClass =
    style === ParagraphStyle.TITLE
      ? 'font-extrabold'
      : style === ParagraphStyle.HEADING
      ? 'font-bold'
      : style === ParagraphStyle.QUOTE
      ? 'font-medium'
      : 'font-normal';

  return (
    <div
      className={`relative rounded-xl transition-colors duration-150 ${
        isActiveSelectionParagraph && normalizedRange
          ? 'bg-teal-500/[0.04] ring-1 ring-teal-500/40 p-3.5 pb-11'
          : style === ParagraphStyle.QUOTE
          ? 'bg-amber-500/[0.06] dark:bg-amber-500/[0.08] ring-1 ring-amber-500/25 p-3.5'
          : 'p-2 hover:bg-slate-500/[0.03]'
      }`}
    >
      {/* Floating Smart Selection Control Toolbar */}
      {isActiveSelectionParagraph && normalizedRange && selectedString.length > 0 && (
        <div className="mb-3 rounded-xl bg-slate-900 text-white p-2.5 shadow-lg border border-slate-700/80">
          <div className="flex flex-wrap items-center justify-between gap-2">
            {/* Primary Copy & Select All Buttons */}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleCopyClick}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold transition-colors whitespace-nowrap shrink-0 cursor-pointer"
              >
                {copiedFeedback ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>
                  {copiedFeedback ? 'در حافظه کپی شد!' : `کپی متن (${selectedString.length} حرف)`}
                </span>
              </button>

              <button
                type="button"
                onClick={() => onSelectionChange({ start: 0, end: text.length })}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors whitespace-nowrap shrink-0 cursor-pointer"
              >
                <Maximize2 className="w-3.5 h-3.5" />
                <span>انتخاب کل بند</span>
              </button>
            </div>

            {/* Highlight Color Swatches & Share / Close */}
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1.5 px-1.5">
                {[
                  { hex: '#FACC15', label: 'زرد' },
                  { hex: '#4ADE80', label: 'سبز' },
                  { hex: '#38BDF8', label: 'آبی' },
                  { hex: '#F472B6', label: 'صورتی' },
                ].map((c) => (
                  <button
                    key={c.hex}
                    type="button"
                    title={`هایلایت ${c.label}`}
                    onClick={() =>
                      onHighlightSelection(
                        normalizedRange.start,
                        normalizedRange.end,
                        selectedString,
                        c.hex
                      )
                    }
                    className="w-5 h-5 rounded-full border border-white/80 transition-transform hover:scale-110 cursor-pointer"
                    style={{ backgroundColor: c.hex }}
                  />
                ))}
              </div>

              <button
                type="button"
                onClick={() => onShareSelection(selectedString)}
                title="اشتراک‌گذاری متن"
                className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors cursor-pointer"
              >
                <Share2 className="w-3.5 h-3.5" />
              </button>

              <button
                type="button"
                onClick={() => onSelectionChange(null)}
                title="بستن انتخاب"
                className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Bottom Row: Precision +/-1 Character Nudge Controls & Anti-Jump Status */}
          <div className="mt-2 pt-2 border-t border-slate-800 flex flex-wrap items-center justify-between gap-2 text-[11px]">
            <div className="flex items-center gap-1.5">
              <span className="text-slate-400">دستگیره شروع (راست):</span>
              <button
                type="button"
                onClick={() => {
                  if (normalizedRange.start > 0) {
                    onSelectionChange({ start: normalizedRange.start - 1, end: normalizedRange.end });
                  }
                }}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-teal-300 font-mono-num font-bold cursor-pointer"
              >
                -1
              </button>
              <button
                type="button"
                onClick={() => {
                  if (normalizedRange.start + 1 < normalizedRange.end) {
                    onSelectionChange({ start: normalizedRange.start + 1, end: normalizedRange.end });
                  }
                }}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-teal-300 font-mono-num font-bold cursor-pointer"
              >
                +1
              </button>
            </div>

            <div className="flex items-center gap-1 text-sky-400 font-medium">
              <Sparkles className="w-3 h-3" />
              <span className="font-mono-num">
                بازه: {normalizedRange.start} تا {normalizedRange.end}
              </span>
              <span className="text-slate-400">· قفل هوشمند ضدپرش فعال</span>
            </div>

            <div className="flex items-center gap-1.5">
              <span className="text-slate-400">دستگیره پایان (چپ):</span>
              <button
                type="button"
                onClick={() => {
                  if (normalizedRange.end - 1 > normalizedRange.start) {
                    onSelectionChange({ start: normalizedRange.start, end: normalizedRange.end - 1 });
                  }
                }}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-sky-300 font-mono-num font-bold cursor-pointer"
              >
                -1
              </button>
              <button
                type="button"
                onClick={() => {
                  if (normalizedRange.end < text.length) {
                    onSelectionChange({ start: normalizedRange.start, end: normalizedRange.end + 1 });
                  }
                }}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-sky-300 font-mono-num font-bold cursor-pointer"
              >
                +1
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Character-Indexed Paragraph Surface */}
      <div
        ref={containerRef}
        dir="rtl"
        style={{
          fontSize: `${effectiveFontSize}px`,
          lineHeight: lineHeight,
          touchAction: draggingHandle ? 'none' : 'pan-y',
        }}
        onPointerDown={(e) => {
          // Ignore if clicking directly on a handle
          if ((e.target as HTMLElement).closest('[data-handle]')) return;
          const idx = getClampedCharIndexFromPoint(e.clientX, e.clientY);
          if (idx !== null) {
            const word = getWordRangeAtChar(idx);
            onSelectionChange(word);
          }
        }}
        className={`relative select-none cursor-text text-right ${fontWeightClass}`}
      >
        {Array.from(text).map((ch, idx) => {
          const meta = charMeta[idx];
          let bgStyle: React.CSSProperties | undefined;
          let extraClasses = '';

          if (meta.inActiveMatch) {
            extraClasses = 'bg-orange-500 text-white font-bold rounded-xs';
          } else if (meta.inSearchMatch) {
            extraClasses = 'bg-yellow-300/80 text-slate-950 font-semibold rounded-xs';
          } else if (meta.inSelection) {
            extraClasses = 'bg-teal-500/30 dark:bg-teal-400/35';
          } else if (meta.highlightColor) {
            bgStyle = { backgroundColor: `${meta.highlightColor}66` };
          }

          return (
            <span
              key={idx}
              data-char-index={idx}
              style={bgStyle}
              className={`transition-colors duration-75 ${extraClasses}`}
            >
              {ch}
            </span>
          );
        })}

        {/* Custom Draggable Start & End Cursor Handles */}
        {isActiveSelectionParagraph && normalizedRange && handlePositions && (
          <>
            {/* START HANDLE (Right edge in RTL) */}
            <div
              data-handle="start"
              onPointerDown={(e) => {
                e.stopPropagation();
                e.preventDefault();
                setDraggingHandle('start');
              }}
              style={{
                left: `${handlePositions.startX}px`,
                top: `${handlePositions.startY}px`,
              }}
              className="absolute z-30 -translate-x-1/2 select-none touch-none cursor-ew-resize group"
              title="دستگیره شروع انتخاب (بکشید)"
            >
              {/* Vertical Caret Bar */}
              <div
                style={{ height: `${handlePositions.startHeight}px`, top: `-${handlePositions.startHeight}px` }}
                className="absolute left-1/2 -translate-x-1/2 w-[3px] bg-teal-600 dark:bg-teal-400 pointer-events-none rounded-full"
              />
              {/* Teardrop Handle Touch Target (44x44px hit area) */}
              <div className="w-10 h-10 -mt-1 flex flex-col items-center justify-start">
                <div className="w-0 h-0 border-l-[6px] border-l-transparent border-r-[6px] border-r-transparent border-b-[8px] border-b-teal-600 dark:border-b-teal-400" />
                <div className="w-6 h-6 -mt-0.5 rounded-full bg-teal-600 dark:bg-teal-400 border-2 border-white shadow-md flex items-center justify-center group-hover:scale-110 transition-transform">
                  <div className="w-1.5 h-1.5 rounded-full bg-white" />
                </div>
              </div>
            </div>

            {/* END HANDLE (Left edge in RTL) */}
            <div
              data-handle="end"
              onPointerDown={(e) => {
                e.stopPropagation();
                e.preventDefault();
                setDraggingHandle('end');
              }}
              style={{
                left: `${handlePositions.endX}px`,
                top: `${handlePositions.endY}px`,
              }}
              className="absolute z-30 -translate-x-1/2 select-none touch-none cursor-ew-resize group"
              title="دستگیره پایان انتخاب (بکشید)"
            >
              {/* Vertical Caret Bar */}
              <div
                style={{ height: `${handlePositions.endHeight}px`, top: `-${handlePositions.endHeight}px` }}
                className="absolute left-1/2 -translate-x-1/2 w-[3px] bg-sky-600 dark:bg-sky-400 pointer-events-none rounded-full"
              />
              {/* Teardrop Handle Touch Target (44x44px hit area) */}
              <div className="w-10 h-10 -mt-1 flex flex-col items-center justify-start">
                <div className="w-0 h-0 border-l-[6px] border-l-transparent border-r-[6px] border-r-transparent border-b-[8px] border-b-sky-600 dark:border-b-sky-400" />
                <div className="w-6 h-6 -mt-0.5 rounded-full bg-sky-600 dark:bg-sky-400 border-2 border-white shadow-md flex items-center justify-center group-hover:scale-110 transition-transform">
                  <div className="w-1.5 h-1.5 rounded-full bg-white" />
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
