package com.uhhno.app

object FillerDetector {

    private val SINGLE_WORD_FILLERS = setOf(
        "uh", "uhh", "uhhh", "uhhhh",
        "ah", "ahh", "ahhh",
        "um", "umm", "ummm", "ummmm",
        "er", "err",
        "hmm", "hm", "hmmm",
        "like"
    )

    private val TWO_WORD_FILLERS = setOf(
        "you know",
        "i mean",
        "kind of",
        "sort of",
        "you see",
        "i guess"
    )

    fun isSingleWordFiller(word: String): Boolean {
        val lower = word.lowercase().trim()
        if (lower in SINGLE_WORD_FILLERS) return true
        // Catch stretched variants and Vosk transcription variants:
        //   "uhh" → Vosk often outputs "ah"/"ahh"
        //   "um+"/"hm+"/"er+" cover stretched spellings
        return lower.matches(Regex("uh+")) ||
               lower.matches(Regex("ah+")) ||
               lower.matches(Regex("um+")) ||
               lower.matches(Regex("hm+")) ||
               lower.matches(Regex("er+"))
    }

    fun isTwoWordFiller(bigram: String): Boolean =
        bigram.lowercase().trim() in TWO_WORD_FILLERS
}
