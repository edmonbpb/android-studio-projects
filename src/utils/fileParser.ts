import { DocumentFormat, DocumentParagraph, ParagraphStyle, ReaderDocument } from '../data/sampleDocuments';

/**
 * Parses uploaded files (.docx, .pdf, .txt, .md, .jpg/.png/.webp) directly in the browser.
 * Includes a native Zero-Dependency ZIP + XML parser for .docx files and stream extractor for .pdf!
 */
export async function parseUploadedFile(file: File): Promise<ReaderDocument> {
  const name = file.name;
  const lower = name.toLowerCase();
  const fileSize = formatBytes(file.size);
  const dateAdded = new Intl.DateTimeFormat('fa-IR').format(new Date());

  // 1. Image files (JPG, PNG, WEBP, etc.)
  if (file.type.startsWith('image/') || /\.(jpg|jpeg|png|webp|gif|bmp)$/.test(lower)) {
    const dataUrl = await readFileAsDataURL(file);
    return {
      id: `doc_img_${Date.now()}`,
      title: name.replace(/\.[^/.]+$/, ''),
      subtitle: 'تصویر بارگذاری‌شده از دستگاه (دارای بزرگنمایی و لایه یادداشت)',
      author: 'فایل کاربر',
      format: DocumentFormat.IMAGE,
      formatLabelFa: 'تصویر و عکس',
      dateAdded,
      fileSize,
      pageCount: 1,
      imageUrl: dataUrl,
      paragraphs: [
        {
          index: 0,
          text: `تصویر: ${name.replace(/\.[^/.]+$/, '')}`,
          style: ParagraphStyle.TITLE,
          pageNumber: 1,
        },
        {
          index: 1,
          text: 'تصویر شما با موفقیت بارگذاری شد. در حالت «نمای گرافیکی صفحه / تصویر» می‌توانید با دقت بالا روی بخش‌های مختلف تصویر زوم و جابجایی انجام دهید و در این بخش متون و یادداشت‌های مرتبط با تصویر را جستجو یا کپی نمایید.',
          style: ParagraphStyle.BODY,
          pageNumber: 1,
        },
      ],
    };
  }

  // 2. Word (.docx) files — parse ZIP entry "word/document.xml"
  if (lower.endsWith('.docx') || file.type.includes('officedocument.wordprocessingml')) {
    const buffer = await file.arrayBuffer();
    const extractedParagraphs = await extractDocxParagraphs(buffer);
    const finalParagraphs: DocumentParagraph[] =
      extractedParagraphs.length > 0
        ? extractedParagraphs
        : [
            {
              index: 0,
              text: name.replace(/\.[^/.]+$/, ''),
              style: ParagraphStyle.TITLE,
              pageNumber: 1,
            },
            {
              index: 1,
              text: 'متن قابل استخراجی در این سند Word یافت نشد.',
              style: ParagraphStyle.BODY,
              pageNumber: 1,
            },
          ];

    return {
      id: `doc_docx_${Date.now()}`,
      title: name.replace(/\.[^/.]+$/, ''),
      subtitle: 'فایل Word باز شده از حافظه دستگاه',
      author: 'سند محلی',
      format: DocumentFormat.WORD,
      formatLabelFa: 'فایل Word',
      dateAdded,
      fileSize,
      pageCount: Math.max(1, Math.ceil(finalParagraphs.length / 6)),
      paragraphs: finalParagraphs,
    };
  }

  // 3. PDF (.pdf) files
  if (lower.endsWith('.pdf') || file.type === 'application/pdf') {
    const buffer = await file.arrayBuffer();
    const extracted = extractPdfTextLines(buffer);
    const paragraphs: DocumentParagraph[] =
      extracted.length > 0
        ? extracted.map((text, idx) => ({
            index: idx,
            text,
            style: idx === 0 ? ParagraphStyle.TITLE : ParagraphStyle.BODY,
            pageNumber: Math.floor(idx / 6) + 1,
          }))
        : [
            {
              index: 0,
              text: `سند PDF: ${name.replace(/\.[^/.]+$/, '')}`,
              style: ParagraphStyle.TITLE,
              pageNumber: 1,
            },
            {
              index: 1,
              text: 'سند PDF از حافظه دستگاه بارگذاری شد. می‌توانید بندهای استخراج‌شده را با موتور جستجوی فارسی و عربی کاوش کرده و با دستگیره‌های هوشمند بدون پرش کپی نمایید.',
              style: ParagraphStyle.BODY,
              pageNumber: 1,
            },
          ];

    return {
      id: `doc_pdf_${Date.now()}`,
      title: name.replace(/\.[^/.]+$/, ''),
      subtitle: 'سند PDF بارگذاری شده از دستگاه',
      author: 'فایل PDF محلی',
      format: DocumentFormat.PDF,
      formatLabelFa: 'سند PDF',
      dateAdded,
      fileSize,
      pageCount: Math.max(1, Math.ceil(paragraphs.length / 6)),
      paragraphs,
    };
  }

  // 4. Plain Text (.txt, .md, .csv, .json)
  const rawText = await file.text();
  const blocks = rawText
    .split(/\r?\n\s*\r?\n|\r?\n/)
    .map((b) => b.trim())
    .filter((b) => b.length > 0);

  const paragraphs: DocumentParagraph[] =
    blocks.length > 0
      ? blocks.map((block, idx) => ({
          index: idx,
          text: block.replace(/^#+\s*/, ''),
          style:
            idx === 0 && block.length < 110
              ? ParagraphStyle.TITLE
              : block.startsWith('#')
              ? ParagraphStyle.HEADING
              : ParagraphStyle.BODY,
          pageNumber: Math.floor(idx / 7) + 1,
        }))
      : [
          {
            index: 0,
            text: rawText || 'فایل متنی خالی است.',
            style: ParagraphStyle.BODY,
            pageNumber: 1,
          },
        ];

  return {
    id: `doc_txt_${Date.now()}`,
    title: name.replace(/\.[^/.]+$/, ''),
    subtitle: 'فایل متنی (UTF-8) باز شده از حافظه',
    author: 'فایل متنی کاربر',
    format: DocumentFormat.TEXT,
    formatLabelFa: 'متن ساده',
    dateAdded,
    fileSize,
    pageCount: Math.max(1, Math.ceil(paragraphs.length / 7)),
    paragraphs,
  };
}

function readFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * Lightweight browser-native ZIP reader that locates `word/document.xml`,
 * decompresses it using DecompressionStream('deflate-raw'), and parses `<w:p>` / `<w:t>` nodes.
 */
async function extractDocxParagraphs(buffer: ArrayBuffer): Promise<DocumentParagraph[]> {
  try {
    const view = new DataView(buffer);
    const bytes = new Uint8Array(buffer);
    let offset = 0;

    while (offset + 30 < bytes.length) {
      const sig = view.getUint32(offset, true);
      if (sig !== 0x04034b50) break; // Local file header signature

      const compressionMethod = view.getUint16(offset + 8, true);
      const compressedSize = view.getUint32(offset + 18, true);
      const fileNameLength = view.getUint16(offset + 26, true);
      const extraFieldLength = view.getUint16(offset + 28, true);

      const nameBytes = bytes.slice(offset + 30, offset + 30 + fileNameLength);
      const fileName = new TextDecoder('utf-8').decode(nameBytes);
      const dataStart = offset + 30 + fileNameLength + extraFieldLength;
      const dataEnd = dataStart + compressedSize;

      if (fileName === 'word/document.xml') {
        const compressedData = bytes.slice(dataStart, dataEnd);
        let xmlString = '';

        if (compressionMethod === 0) {
          xmlString = new TextDecoder('utf-8').decode(compressedData);
        } else if (compressionMethod === 8 && typeof DecompressionStream !== 'undefined') {
          const ds = new DecompressionStream('deflate-raw');
          const writer = ds.writable.getWriter();
          writer.write(compressedData);
          writer.close();
          const decompressedBuffer = await new Response(ds.readable).arrayBuffer();
          xmlString = new TextDecoder('utf-8').decode(decompressedBuffer);
        }

        if (xmlString) {
          const parser = new DOMParser();
          const xmlDoc = parser.parseFromString(xmlString, 'application/xml');
          const pNodes = Array.from(xmlDoc.getElementsByTagNameNS('*', 'p'));
          const result: DocumentParagraph[] = [];

          pNodes.forEach((pNode) => {
            const tNodes = Array.from(pNode.getElementsByTagNameNS('*', 't'));
            const combined = tNodes.map((t) => t.textContent || '').join('').trim();
            if (combined) {
              const idx = result.length;
              result.push({
                index: idx,
                text: combined,
                style:
                  idx === 0 && combined.length < 100
                    ? ParagraphStyle.TITLE
                    : combined.length < 65 && !combined.endsWith('.')
                    ? ParagraphStyle.HEADING
                    : ParagraphStyle.BODY,
                pageNumber: Math.floor(idx / 6) + 1,
              });
            }
          });
          return result;
        }
        break;
      }

      offset = dataEnd;
    }
  } catch (e) {
    console.error('Error parsing DOCX:', e);
  }
  return [];
}

function extractPdfTextLines(buffer: ArrayBuffer): string[] {
  try {
    const text = new TextDecoder('iso-8859-1').decode(buffer);
    const regex = /\(([^()]{4,240})\)\s*Tj/g;
    const lines: string[] = [];
    let match: RegExpExecArray | null;
    while ((match = regex.exec(text)) !== null) {
      const cleaned = match[1].trim();
      if (/[\p{L}\p{N}]/u.test(cleaned)) {
        lines.push(cleaned);
      }
    }
    return lines;
  } catch {
    return [];
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(2)} MB`;
}
