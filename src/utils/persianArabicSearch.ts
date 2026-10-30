/**
 * Advanced Persian & Arabic Text Normalizer and Search Engine.
 * Handles unified character mapping (ك/ک, ي/ی/ئ/ى, أ/إ/آ/ا, ة/ه, ؤ/و),
 * optional Erab/Tashkeel removal, ZWNJ (Nim-Faseleh) normalization,
 * and Persian/Arabic/English digit unification while maintaining an exact
 * character index map back to the original string for precise highlighting and selection!
 */

export interface SearchOptions {
  unifyPersianArabicChars: boolean;
  ignoreDiacritics: boolean;
  ignoreZwnj: boolean;
  unifyDigits: boolean;
  caseSensitive: boolean;
  wholeWordOnly: boolean;
}

export interface SearchMatch {
  paragraphIndex: number;
  startOffset: number; // Inclusive index in original paragraph text
  endOffset: number;   // Exclusive index in original paragraph text
  matchedText: string;
  snippetText: string;
  matchStartInSnippet: number;
  matchEndInSnippet: number;
}

const DIACRITICS_SET = new Set([
  '\u064B', // Fathatan
  '\u064C', // Dammatan
  '\u064D', // Kasratan
  '\u064E', // Fatha
  '\u064F', // Damma
  '\u0650', // Kasra
  '\u0651', // Shadda
  '\u0652', // Sukun
  '\u0653', // Maddah above
  '\u0654', // Hamza above
  '\u0655', // Hamza below
  '\u0656', // Subscript alef
  '\u0670', // Superscript alef
  '\u0640', // Tatweel / Kashida (ـ)
]);

const ZERO_WIDTH_SET = new Set([
  '\u200C', // ZWNJ (نیم‌فاصله)
  '\u200B', // ZWSP
  '\u200D', // ZWJ
  '\uFEFF', // BOM
]);

export function normalizeChar(ch: string, options: SearchOptions): string | null {
  if (options.ignoreDiacritics && DIACRITICS_SET.has(ch)) {
    return null;
  }
  if (options.ignoreZwnj && ZERO_WIDTH_SET.has(ch)) {
    return null;
  }

  let c = ch;

  if (options.unifyPersianArabicChars) {
    switch (c) {
      // Yeh variations -> Standard Persian Yeh (ی \u06CC)
      case '\u064A': // Arabic Yeh ي
      case '\u0649': // Alef Maksura ى
      case '\u0626': // Yeh with Hamza above ئ
      case '\u06D2': // Yeh Barree ے
      case '\u06CC': // Persian Yeh ی
        c = '\u06CC';
        break;

      // Kaf variations -> Standard Persian Keheh (ک \u06A9)
      case '\u0643': // Arabic Kaf ك
      case '\u06AC':
      case '\u06AD':
      case '\u06A9': // Persian Keheh ک
        c = '\u06A9';
        break;

      // Alef variations -> Standard Alef (ا \u0627)
      case '\u0622': // Alef with Madda above آ
      case '\u0623': // Alef with Hamza above أ
      case '\u0625': // Alef with Hamza below إ
      case '\u0671': // Alef Wasla ٱ
      case '\u0627': // Alef ا
        c = '\u0627';
        break;

      // Heh / Teh Marbuta -> Standard Heh (ه \u0647)
      case '\u0629': // Teh Marbuta ة
      case '\u06C0': // Heh with Yeh above ۀ
      case '\u06BE': // Heh Doachashmee ھ
      case '\u06D5': // Ae
      case '\u0647': // Heh ه
        c = '\u0647';
        break;

      // Waw variations -> Standard Waw (و \u0648)
      case '\u0624': // Waw with Hamza above ؤ
      case '\u0648': // Waw و
        c = '\u0648';
        break;
    }
  }

  if (options.unifyDigits) {
    const code = c.charCodeAt(0);
    // Persian digits ۰-۹ (\u06F0 - \u06F9)
    if (code >= 0x06F0 && code <= 0x06F9) {
      c = String.fromCharCode(48 + (code - 0x06F0));
    }
    // Arabic-Indic digits ٠-٩ (\u0660 - \u0669)
    else if (code >= 0x0660 && code <= 0x0669) {
      c = String.fromCharCode(48 + (code - 0x0660));
    }
  }

  if (!options.caseSensitive) {
    c = c.toLowerCase();
  }

  return c;
}

export interface NormalizedStringMap {
  normalizedText: string;
  normToOrigIndex: number[];
}

export function normalizeWithMapping(original: string, options: SearchOptions): NormalizedStringMap {
  const chars: string[] = [];
  const normToOrigIndex: number[] = [];

  for (let origIdx = 0; origIdx < original.length; origIdx++) {
    const ch = original[origIdx];
    const normCh = normalizeChar(ch, options);
    if (normCh !== null) {
      chars.push(normCh);
      normToOrigIndex.push(origIdx);
    }
  }

  return {
    normalizedText: chars.join(''),
    normToOrigIndex,
  };
}

function escapeRegExp(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildFlexibleQueryPattern(rawQuery: string, options: SearchOptions): string {
  const normChars: string[] = [];
  for (let i = 0; i < rawQuery.length; i++) {
    const ch = rawQuery[i];
    if (/\s/.test(ch) || ZERO_WIDTH_SET.has(ch)) {
      if (normChars.length > 0 && normChars[normChars.length - 1] !== ' ') {
        normChars.push(' ');
      }
    } else {
      const n = normalizeChar(ch, options);
      if (n !== null) {
        normChars.push(n);
      }
    }
  }

  const cleaned = normChars.join('').trim();
  if (!cleaned) return '';

  let pattern = '';
  for (let i = 0; i < cleaned.length; i++) {
    const c = cleaned[i];
    if (c === ' ') {
      pattern += options.ignoreZwnj ? '\\s*' : '\\s+';
    } else {
      pattern += escapeRegExp(c);
      const subSoFar = cleaned.slice(0, i + 1);
      if (
        options.ignoreZwnj &&
        (subSoFar.endsWith('می') || subSoFar.endsWith('نمی')) &&
        i + 1 < cleaned.length &&
        cleaned[i + 1] !== ' '
      ) {
        pattern += '\\s*';
      }
    }
  }
  return pattern;
}

export function searchInParagraphs(
  paragraphs: string[],
  rawQuery: string,
  options: SearchOptions
): SearchMatch[] {
  const trimmed = rawQuery.trim();
  if (!trimmed) return [];

  const patternStr = buildFlexibleQueryPattern(trimmed, options);
  if (!patternStr) return [];

  const flags = options.caseSensitive ? 'g' : 'gi';
  let regex: RegExp;
  try {
    regex = new RegExp(patternStr, flags);
  } catch {
    return [];
  }

  const results: SearchMatch[] = [];

  paragraphs.forEach((paragraphText, pIndex) => {
    if (!paragraphText.trim()) return;
    const normMap = normalizeWithMapping(paragraphText, options);
    if (!normMap.normalizedText) return;

    let match: RegExpExecArray | null;
    regex.lastIndex = 0;

    while ((match = regex.exec(normMap.normalizedText)) !== null) {
      if (match[0].length === 0) {
        regex.lastIndex++;
        continue;
      }

      const normStart = match.index;
      const normEndInclusive = normStart + match[0].length - 1;
      if (normStart > normEndInclusive || normStart >= normMap.normToOrigIndex.length) continue;

      const origStart = normMap.normToOrigIndex[normStart];
      let origEndExclusive = normMap.normToOrigIndex[normEndInclusive] + 1;

      while (
        origEndExclusive < paragraphText.length &&
        DIACRITICS_SET.has(paragraphText[origEndExclusive])
      ) {
        origEndExclusive++;
      }

      if (options.wholeWordOnly) {
        const beforeChar = origStart > 0 ? paragraphText[origStart - 1] : ' ';
        const afterChar = origEndExclusive < paragraphText.length ? paragraphText[origEndExclusive] : ' ';
        if (/[\p{L}\p{N}]/u.test(beforeChar) || /[\p{L}\p{N}]/u.test(afterChar)) {
          continue;
        }
      }

      const matchedText = paragraphText.slice(origStart, origEndExclusive);
      const snippetRadius = 46;
      const snippetStart = Math.max(0, origStart - snippetRadius);
      const snippetEnd = Math.min(paragraphText.length, origEndExclusive + snippetRadius);
      const prefix = snippetStart > 0 ? '…' : '';
      const suffix = snippetEnd < paragraphText.length ? '…' : '';
      const rawSnippet = paragraphText.slice(snippetStart, snippetEnd).replace(/\n/g, ' ');
      const fullSnippet = `${prefix}${rawSnippet}${suffix}`;
      const matchStartInSnippet = prefix.length + (origStart - snippetStart);
      const matchEndInSnippet = matchStartInSnippet + (origEndExclusive - origStart);

      results.push({
        paragraphIndex: pIndex,
        startOffset: origStart,
        endOffset: origEndExclusive,
        matchedText,
        snippetText: fullSnippet,
        matchStartInSnippet,
        matchEndInSnippet,
      });
    }
  });

  return results;
}
