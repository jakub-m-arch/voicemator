package io.github.jakub_m_arch.voicemator.text

/** A dictionary entry: whenever [heard] is recognised as a whole word or phrase, write [written]. */
data class Replacement(val heard: String, val written: String)

/**
 * How a whole dictation begins and ends. Chat messages often start with a small letter and have
 * no full stop at the end; emails keep both.
 */
data class TextStyle(val capitalizeFirst: Boolean = true, val endWithPeriod: Boolean = true)

/**
 * Cleans up a transcription: removes hesitation sounds such as "yyy" or "um", applies the
 * user's dictionary and then the [style]. Matching ignores case and only takes whole words, so
 * "Ania" does not change "Aniak".
 */
class TextPostProcessor(
    replacements: List<Replacement>,
    private val removeFillers: Boolean,
    private val style: TextStyle = TextStyle(),
) {

    private val rules = replacements
        .filter { it.heard.isNotBlank() }
        // Longer phrases first, so "Jan Kowalski" wins over "Jan".
        .sortedByDescending { it.heard.length }
        .map { wholeWords(it.heard.trim()) to it.written }

    fun process(text: String): String {
        var result = text
        if (removeFillers) result = withoutFillers(result)
        for ((pattern, written) in rules) {
            val source = result
            result = pattern.replace(source) { match -> atSentenceStart(source, match.range.first, written) }
        }
        if (!style.endWithPeriod) result = result.replace(FINAL_PERIOD, "")
        if (!style.capitalizeFirst) result = withSmallFirstLetter(result)
        return result
    }

    /** Lowers the first letter, unless the first word is an acronym or a name from the dictionary. */
    private fun withSmallFirstLetter(text: String): String {
        val firstWord = FIRST_WORD.find(text)?.value ?: return text
        val isAcronym = firstWord.length > 1 && firstWord.all { it.isUpperCase() }
        val isName = rules.any { (_, written) -> written.firstOrNull()?.isUpperCase() == true && text.startsWith(written) }
        if (isAcronym || isName) return text
        return text.replaceFirst(firstWord, firstWord.replaceFirstChar { it.lowercase() })
    }

    private fun withoutFillers(text: String): String {
        val removed = FILLER.replace(text, "")
        if (removed == text) return text
        val tidied = removed
            .replace(SPACE_BEFORE_PUNCTUATION, "$1")
            .replace(REPEATED_SPACES, " ")
            .trim()
        // A sentence may now start with what used to be its second word.
        return SENTENCE_START.replace(tidied) { it.value.uppercase() }
    }

    private companion object {
        const val WORD_CHAR = """[\p{L}\p{N}]"""

        /** Hesitation sounds in Polish and English, with a comma that follows them. */
        val FILLER = Regex(
            """(?<!$WORD_CHAR)(y{2,}|e{2,}m*|e+m{2,}|m{2,}|h+m+|u+h+m*|u+m+|e+r+m+)(?!$WORD_CHAR)[,]?\s*""",
            RegexOption.IGNORE_CASE,
        )
        val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.!?;:])""")
        val REPEATED_SPACES = Regex("""\s{2,}""")
        val SENTENCE_START = Regex("""(?:^|(?<=[.!?]\s))\p{Ll}""")

        /** A single full stop at the very end; an ellipsis stays. */
        val FINAL_PERIOD = Regex("""(?<!\.)\.\s*$""")
        val FIRST_WORD = Regex("""^\p{L}+""")

        fun wholeWords(phrase: String) =
            Regex("""(?<!$WORD_CHAR)${Regex.escape(phrase)}(?!$WORD_CHAR)""", RegexOption.IGNORE_CASE)

        /** Capitalises [written] when it starts a sentence; otherwise writes it exactly as given. */
        fun atSentenceStart(text: String, index: Int, written: String): String {
            val before = text.substring(0, index).trimEnd()
            val startsSentence = before.isEmpty() || before.last() in ".!?"
            return if (startsSentence) written.replaceFirstChar { it.uppercase() } else written
        }
    }
}
