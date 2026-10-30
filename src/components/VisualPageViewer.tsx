import React, { useState } from 'react';
import {
  ZoomIn,
  ZoomOut,
  RotateCw,
  RotateCcw,
  FileText,
  ChevronRight,
  ChevronLeft,
} from 'lucide-react';
import { DocumentFormat, ReaderDocument } from '../data/sampleDocuments';

interface VisualPageViewerProps {
  document: ReaderDocument;
  onSwitchToTextLayer: () => void;
}

export const VisualPageViewer: React.FC<VisualPageViewerProps> = ({
  document,
  onSwitchToTextLayer,
}) => {
  const [scale, setScale] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragOrigin, setDragOrigin] = useState({ x: 0, y: 0 });

  const totalPages = Math.max(1, document.pageCount);
  const pageParagraphs = document.paragraphs.filter((p) => p.pageNumber === currentPage);
  const visibleParagraphs =
    pageParagraphs.length > 0 ? pageParagraphs : document.paragraphs.slice(0, 4);

  return (
    <div className="flex flex-col gap-3 h-full">
      {/* Control Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl bg-slate-100 dark:bg-slate-800/90 border border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setScale((s) => Math.min(3.5, +(s + 0.25).toFixed(2)))}
            className="p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="بزرگنمایی"
          >
            <ZoomIn className="w-4 h-4" />
          </button>
          <span className="font-mono-num text-xs font-bold px-2">
            {Math.round(scale * 100)}%
          </span>
          <button
            type="button"
            onClick={() => {
              setScale((s) => {
                const next = Math.max(0.75, +(s - 0.25).toFixed(2));
                if (next === 1) setPan({ x: 0, y: 0 });
                return next;
              });
            }}
            className="p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="کوچک‌نمایی"
          >
            <ZoomOut className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={() => setRotation((r) => (r + 90) % 360)}
            className="p-2 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="چرخش ۹۰ درجه"
          >
            <RotateCw className="w-4 h-4" />
          </button>
          {(scale !== 1 || rotation !== 0 || pan.x !== 0 || pan.y !== 0) && (
            <button
              type="button"
              onClick={() => {
                setScale(1);
                setRotation(0);
                setPan({ x: 0, y: 0 });
              }}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-medium text-teal-600 dark:text-teal-400 hover:bg-teal-500/10 transition-colors cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>بازنشانی دید</span>
            </button>
          )}
        </div>

        {/* Page Navigation */}
        {totalPages > 1 && (
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              className="p-1.5 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 disabled:opacity-40 cursor-pointer"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
            <span className="text-xs font-semibold">
              صفحه <span className="font-mono-num">{currentPage}</span> از{' '}
              <span className="font-mono-num">{totalPages}</span>
            </span>
            <button
              type="button"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              className="p-1.5 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 disabled:opacity-40 cursor-pointer"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
          </div>
        )}

        <button
          type="button"
          onClick={onSwitchToTextLayer}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold transition-colors whitespace-nowrap shrink-0 cursor-pointer"
        >
          <FileText className="w-4 h-4" />
          <span>لایه متن هوشمند و کپی دقیق</span>
        </button>
      </div>

      {/* Interactive Pan & Zoom Canvas */}
      <div
        onPointerDown={(e) => {
          setIsDragging(true);
          setDragOrigin({ x: e.clientX - pan.x, y: e.clientY - pan.y });
        }}
        onPointerMove={(e) => {
          if (!isDragging) return;
          setPan({
            x: Math.max(-450, Math.min(450, e.clientX - dragOrigin.x)),
            y: Math.max(-450, Math.min(450, e.clientY - dragOrigin.y)),
          });
        }}
        onPointerUp={() => setIsDragging(false)}
        onPointerCancel={() => setIsDragging(false)}
        className="relative flex-1 min-h-[460px] rounded-2xl bg-slate-200/80 dark:bg-slate-950 border border-slate-300 dark:border-slate-800 overflow-hidden flex items-center justify-center cursor-grab active:cursor-grabbing select-none"
      >
        <div
          style={{
            transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale}) rotate(${rotation}deg)`,
            transition: isDragging ? 'none' : 'transform 150ms cubic-bezier(0.16, 1, 0.3, 1)',
          }}
          className="max-w-2xl w-full p-6"
        >
          {document.imageUrl ? (
            <div className="rounded-xl overflow-hidden shadow-2xl border-4 border-white dark:border-slate-800 bg-white">
              <img
                src={document.imageUrl}
                alt={document.title}
                referrerPolicy="no-referrer"
                className="w-full h-auto max-h-[540px] object-contain mx-auto"
              />
            </div>
          ) : (
            /* High-Detail Illuminated Persian Manuscript / PDF Page Plate */
            <div className="rounded-xl bg-[#FAF6EC] text-slate-900 p-6 shadow-2xl border-2 border-teal-700/80 relative">
              <div className="border border-amber-600/70 rounded-lg p-6 flex flex-col justify-between min-h-[420px]">
                {/* Illuminated Arch Header */}
                <div className="flex flex-col items-center text-center border-b border-amber-600/30 pb-4">
                  <div className="px-5 py-1 rounded-b-xl bg-teal-800 text-amber-100 text-xs font-bold mb-3">
                    {document.format === DocumentFormat.IMAGE
                      ? 'لوح تصویری اسکن‌شده (OCR)'
                      : `نمای گرافیکی صفحه ${currentPage}`}
                  </div>
                  <h3 className="text-lg font-extrabold text-slate-900">{document.title}</h3>
                </div>

                {/* Page Body Preview */}
                <div className="my-6 space-y-4 text-right">
                  {visibleParagraphs.map((para) => (
                    <p
                      key={para.index}
                      className={`leading-relaxed ${
                        para.index === 0 ? 'font-bold text-base text-teal-950' : 'text-sm text-slate-800'
                      }`}
                    >
                      {para.text}
                    </p>
                  ))}
                </div>

                {/* Page Footer */}
                <div className="pt-3 border-t border-amber-600/30 flex items-center justify-between text-xs text-slate-600">
                  <span>پشتیبانی از بزرگنمایی و جابجایی صفحه (Pan & Zoom)</span>
                  <span className="font-bold text-teal-800 font-mono-num">
                    صفحه {currentPage} از {totalPages}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
