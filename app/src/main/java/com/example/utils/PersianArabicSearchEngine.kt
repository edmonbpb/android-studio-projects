package com.example.utils

/**
 * Advanced Persian & Arabic Text Normalizer and Search Engine.
 * Handles unified character mapping (ك/ک, ي/ی/ئ/ى, أ/إ/آ/ا, ة/ه, ؤ/و),
 * optional Erab/Tashkeel removal, ZWNJ (Nim-Faseleh) normalization,
 * and Persian/Arabic/English digit unification while maintaining an exact
 * character index map back to the original string for precise highlighting and selection!
 */
object PersianArabicSearchEngine {

    data class SearchOptions(
        val unifyPersianArabicChars: Boolean = true,
        val ignoreDiacritics: Boolean = true,
        val ignoreZwnj: Boolean = true,
        val unifyDigits: Boolean = true,
        val caseSensitive: Boolean = false,
        val wholeWordOnly: Boolean = false
    )

    data class SearchMatch(
        val paragraphIndex: Int,
        val startOffset: Int, // Inclusive index in original paragraph text
        val endOffset: Int,   // Exclusive index in original paragraph text
        val matchedText: String,
        val snippetText: String,
        val matchStartInSnippet: Int,
        val matchEndInSnippet: Int
    )

    // Diacritics (Tashkeel / Erab) & Tatweel (Kashida)
    private val DIACRITICS_SET = setOf(
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
        '\u0640'  // Tatweel / Kashida (ـ)
    )

    // Zero-width characters (ZWNJ / Nim-faseleh, ZWJ, etc.)
    private val ZERO_WIDTH_SET = setOf(
        '\u200C', // ZWNJ (نیم‌فاصله)
        '\u200B', // ZWSP
        '\u200D', // ZWJ
        '\uFEFF'  // BOM
    )

    /**
     * Normalizes a single character according to the provided options.
     * Returns null if the character should be stripped during normalization
     * (such as diacritics or ZWNJ when ignore flags are active).
     */
    fun normalizeChar(ch: Char, options: SearchOptions): Char? {
        if (options.ignoreDiacritics && ch in DIACRITICS_SET) {
            return null
        }
        if (options.ignoreZwnj && ch in ZERO_WIDTH_SET) {
            return null
        }

        var c = ch

        if (options.unifyPersianArabicChars) {
            c = when (c) {
                // Yeh variations -> Standard Persian Yeh (ی \u06CC)
                '\u064A', // Arabic Yeh ي
                '\u0649', // Alef Maksura ى
                '\u0626', // Yeh with Hamza above ئ
                '\u06D2', // Yeh Barree ے
                '\u06CC'  // Persian Yeh ی
                -> '\u06CC'

                // Kaf variations -> Standard Persian Keheh (ک \u06A9)
                '\u0643', // Arabic Kaf ك
                '\u06AC', // Kaf with dot
                '\u06AD', // Ng
                '\u06A9'  // Persian Keheh ک
                -> '\u06A9'

                // Alef variations -> Standard Alef (ا \u0627)
                '\u0622', // Alef with Madda above آ
                '\u0623', // Alef with Hamza above أ
                '\u0625', // Alef with Hamza below إ
                '\u0671', // Alef Wasla ٱ
                '\u0627'  // Alef ا
                -> '\u0627'

                // Heh / Teh Marbuta -> Standard Heh (ه \u0647)
                '\u0629', // Teh Marbuta ة
                '\u06C0', // Heh with Yeh above ۀ
                '\u06BE', // Heh Doachashmee ھ
                '\u06D5', // Ae
                '\u0647'  // Heh ه
                -> '\u0647'

                // Waw variations -> Standard Waw (و \u0648)
                '\u0624', // Waw with Hamza above ؤ
                '\u0648'  // Waw و
                -> '\u0648'

                else -> c
            }
        }

        if (options.unifyDigits) {
            c = when (c) {
                // Persian digits ۰-۹ (\u06F0 - \u06F9)
                in '\u06F0'..'\u06F9' -> ('0'.code + (c.code - '\u06F0'.code)).toChar()
                // Arabic-Indic digits ٠-٩ (\u0660 - \u0669)
                in '\u0660'..'\u0669' -> ('0'.code + (c.code - '\u0660'.code)).toChar()
                else -> c
            }
        }

        if (!options.caseSensitive) {
            c = c.lowercaseChar()
        }

        return c
    }

    /**
     * Normalized result with bidirectional index mapping back to the original string.
     */
    data class NormalizedStringMap(
        val normalizedText: String,
        val normToOrigIndex: IntArray // normToOrigIndex[i] = index in original string
    )

    fun normalizeWithMapping(original: String, options: SearchOptions): NormalizedStringMap {
        val sb = StringBuilder(original.length)
        val indexMap = IntArray(original.length)
        var normCount = 0

        for (origIdx in original.indices) {
            val ch = original[origIdx]
            if (options.ignoreZwnj && ch == ' ') {
                // Collapse multiple spaces or treat space flexibly if needed,
                // but to allow "می شود" to match "می‌شود" and "میشود", if ignoreZwnj is true,
                // we keep spaces unless we do space-flexible matching. Let's handle ZWNJ vs Space smartly!
            }
            val normCh = normalizeChar(ch, options)
            if (normCh != null) {
                sb.append(normCh)
                indexMap[normCount] = origIdx
                normCount++
            }
        }

        return NormalizedStringMap(
            normalizedText = sb.toString(),
            normToOrigIndex = indexMap.copyOf(normCount)
        )
    }

    /**
     * Searches a list of paragraphs for all occurrences of [rawQuery] using [options].
     * Also supports matching across space/ZWNJ boundaries when `ignoreZwnj` is enabled
     * (e.g. searching "می شود" matches "می‌شود" and "میشود" and vice versa).
     */
    fun searchInParagraphs(
        paragraphs: List<String>,
        rawQuery: String,
        options: SearchOptions = SearchOptions()
    ): List<SearchMatch> {
        val trimmedQuery = rawQuery.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        // Prepare normalized query.
        // Note: if ignoreZwnj is true, users often type a normal space ' ' instead of ZWNJ (e.g., "می شود" instead of "می‌شود").
        // To make search truly intelligent for Persian/Arabic compound words, we build a regex pattern on the normalized text
        // where a space in the query can match an optional space in the normalized text, OR we match both exact normalized and space-agnostic!
        val results = mutableListOf<SearchMatch>()

        // Build pattern from query characters
        val queryPatternBuilder = StringBuilder()
        var hasValidChars = false
        var lastWasSpace = false

        for (ch in trimmedQuery) {
            if (ch.isWhitespace() || ch in ZERO_WIDTH_SET) {
                if (!lastWasSpace && hasValidChars) {
                    if (options.ignoreZwnj) {
                        // Space or ZWNJ in query can match zero or more spaces in normalized text
                        queryPatternBuilder.append("\\s*")
                    } else {
                        queryPatternBuilder.append("\\s+")
                    }
                    lastWasSpace = true
                }
            } else {
                val normCh = normalizeChar(ch, options)
                if (normCh != null) {
                    // If ignoreZwnj is true, allow optional whitespace between characters?
                    // Wait: allowing \s* between EVERY character might match across unrelated words (e.g., "اب" matching "ا ب").
                    // Instead, in Persian compound prefixes/suffixes (می, نمی, ها, تر, ترین, ای, اش, ام, ات, شان, مان, تان)
                    // or wherever the user or text has a space/ZWNJ, let's handle space/ZWNJ equivalence cleanly!
                    queryPatternBuilder.append(Regex.escape(normCh.toString()))
                    hasValidChars = true
                    lastWasSpace = false
                }
            }
        }

        if (!hasValidChars) return emptyList()

        // If ignoreZwnj is enabled, we keep ZWNJ mapped as empty in normalizeChar, AND what if the document has a space "می شود"
        // while the user searches "میشود" or "می‌شود"?
        // Let's make sure common Persian prefixes/suffixes (می، نمی، ها، تر، ترین، بی) work seamlessly even if the text has a space!
        val finalPatternStr = if (options.ignoreZwnj) {
            buildPersianCompoundFlexiblePattern(trimmedQuery, options)
        } else {
            queryPatternBuilder.toString()
        }

        val regexOptions = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = try {
            Regex(finalPatternStr, regexOptions)
        } catch (e: Exception) {
            return emptyList()
        }

        paragraphs.forEachIndexed { pIndex, paragraphText ->
            if (paragraphText.isBlank()) return@forEachIndexed

            val normMap = normalizeWithMapping(paragraphText, options)
            if (normMap.normalizedText.isEmpty()) return@forEachIndexed

            val matches = regex.findAll(normMap.normalizedText)
            for (matchResult in matches) {
                val normStart = matchResult.range.first
                val normEndInclusive = matchResult.range.last
                if (normStart > normEndInclusive || normStart >= normMap.normToOrigIndex.size) continue

                val origStart = normMap.normToOrigIndex[normStart]
                var origEndExclusive = normMap.normToOrigIndex[normEndInclusive] + 1

                // Include any trailing combining diacritics in the original text that belong to the last matched character
                while (origEndExclusive < paragraphText.length &&
                    paragraphText[origEndExclusive] in DIACRITICS_SET
                ) {
                    origEndExclusive++
                }

                if (options.wholeWordOnly) {
                    val beforeChar = if (origStart > 0) paragraphText[origStart - 1] else ' '
                    val afterChar = if (origEndExclusive < paragraphText.length) paragraphText[origEndExclusive] else ' '
                    if (beforeChar.isLetterOrDigit() || afterChar.isLetterOrDigit()) {
                        continue
                    }
                }

                val matchedStr = paragraphText.substring(origStart, origEndExclusive)

                // Create contextual snippet around the match
                val snippetRadius = 42
                val snippetStart = (origStart - snippetRadius).coerceAtLeast(0)
                val snippetEnd = (origEndExclusive + snippetRadius).coerceAtMost(paragraphText.length)
                val prefix = if (snippetStart > 0) "…" else ""
                val suffix = if (snippetEnd < paragraphText.length) "…" else ""
                val rawSnippet = paragraphText.substring(snippetStart, snippetEnd).replace('\n', ' ')
                val fullSnippet = "$prefix$rawSnippet$suffix"
                val matchStartInSnippet = prefix.length + (origStart - snippetStart)
                val matchEndInSnippet = matchStartInSnippet + (origEndExclusive - origStart)

                results.add(
                    SearchMatch(
                        paragraphIndex = pIndex,
                        startOffset = origStart,
                        endOffset = origEndExclusive,
                        matchedText = matchedStr,
                        snippetText = fullSnippet,
                        matchStartInSnippet = matchStartInSnippet.coerceIn(0, fullSnippet.length),
                        matchEndInSnippet = matchEndInSnippet.coerceIn(0, fullSnippet.length)
                    )
                )
            }
        }

        return results
    }

    /**
     * Builds a regex pattern where spaces/ZWNJs in the query match `\s*` in normalized text,
     * AND common Persian compound boundaries (like می / نمی / ها / تر) allow optional `\s*`
     * so searching "میشود", "می‌شود", or "می شود" all find each other!
     */
    private fun buildPersianCompoundFlexiblePattern(rawQuery: String, options: SearchOptions): String {
        val normChars = mutableListOf<Char>()
        for (ch in rawQuery) {
            if (ch.isWhitespace() || ch in ZERO_WIDTH_SET) {
                if (normChars.isNotEmpty() && normChars.last() != ' ') {
                    normChars.add(' ')
                }
            } else {
                val n = normalizeChar(ch, options)
                if (n != null) {
                    normChars.add(n)
                }
            }
        }
        val cleaned = normChars.joinToString("").trim()
        if (cleaned.isEmpty()) return ""

        // Escape each character and allow optional \s* where a space exists or after common prefixes
        val sb = StringBuilder()
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]
            if (c == ' ') {
                sb.append("\\s*")
                i++
            } else {
                sb.append(Regex.escape(c.toString()))
                // If we just completed "می" or "نمی" at the start of a word, allow optional space
                val subSoFar = cleaned.substring(0, i + 1)
                if ((subSoFar.endsWith("می") || subSoFar.endsWith("نمی")) &&
                    i + 1 < cleaned.length && cleaned[i + 1] != ' '
                ) {
                    sb.append("\\s*")
                }
                i++
            }
        }
        return sb.toString()
    }
}
