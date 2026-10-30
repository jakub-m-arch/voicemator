package io.github.jakub_m_arch.voicemator.files

import java.util.Locale

/**
 * Turns a [FileTranscript] into SubRip (.srt) subtitles.
 *
 * Times are known only per segment, about ten seconds each, so a segment is split into sentences
 * (and long sentences into shorter lines) whose times are shared out by their length. That is
 * close enough to follow a recording, though not frame-accurate.
 */
object Subtitles {

    fun toSrt(transcript: FileTranscript): String =
        cues(transcript.segments).withIndex().joinToString("\n") { (index, cue) ->
            "${index + 1}\n${timestamp(cue.startMillis)} --> ${timestamp(cue.endMillis)}\n${cue.text}\n"
        }

    /** Splits [segments] into subtitle-sized cues, keeping each within its segment's time. */
    fun cues(segments: List<TranscriptSegment>): List<TranscriptSegment> = segments.flatMap { segment ->
        val pieces = SENTENCE_END.split(segment.text.trim()).filter { it.isNotBlank() }.flatMap(::shortLines)
        val totalLength = pieces.sumOf { it.length }.coerceAtLeast(1)
        val duration = segment.endMillis - segment.startMillis
        var characters = 0
        pieces.map { piece ->
            val start = segment.startMillis + duration * characters / totalLength
            characters += piece.length
            TranscriptSegment(start, segment.startMillis + duration * characters / totalLength, piece)
        }
    }

    /** Breaks a sentence longer than [MAX_CUE_LENGTH] into lines at word boundaries. */
    private fun shortLines(sentence: String): List<String> {
        if (sentence.length <= MAX_CUE_LENGTH) return listOf(sentence)
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in sentence.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > MAX_CUE_LENGTH) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    private fun timestamp(millis: Long): String = String.format(
        Locale.ROOT,
        "%02d:%02d:%02d,%03d",
        millis / 3_600_000,
        millis / 60_000 % 60,
        millis / 1000 % 60,
        millis % 1000,
    )

    private val SENTENCE_END = Regex("""(?<=[.!?…])\s+""")

    /** Two lines of about 42 characters, the usual limit for subtitles. */
    private const val MAX_CUE_LENGTH = 84
}
