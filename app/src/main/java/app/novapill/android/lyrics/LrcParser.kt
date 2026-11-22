package app.novapill.android.lyrics

/** One timestamped line from a user-imported LRC file. */
data class LyricLine(val timestampMs: Long, val text: String)

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

    fun parse(source: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in source.lineSequence()) {
            val matches = timestamp.findAll(raw).toList()
            if (matches.isEmpty()) continue

            val lyricText = timestamp.replace(raw, "").trim()
            val metadataLine = listOf("ti:", "ar:", "al:", "by:", "re:", "ve:")
                .any { lyricText.startsWith(it, ignoreCase = true) }
            if (metadataLine) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds > 59L) continue
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                result += LyricLine(minutes * 60_000L + seconds * 1_000L + millis, lyricText)
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    /** Returns -1 before the first line, otherwise the last line at/before position. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val target = (positionMs - offsetMs).coerceAtLeast(0L)
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= target) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
