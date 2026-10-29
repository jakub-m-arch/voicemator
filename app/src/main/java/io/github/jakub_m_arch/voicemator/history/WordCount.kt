package io.github.jakub_m_arch.voicemator.history

private val WORD = Regex("""[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*""")

/** Counts words: runs of letters or digits, keeping apostrophes and hyphens inside words. */
fun countWords(text: String): Int = WORD.findAll(text).count()
