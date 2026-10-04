package dev.motiko.nativeocr

/** The ML Kit text recognition models. Each reads one script; every model also reads Latin text. */
enum class Script {
    LATIN,
    CHINESE,
    DEVANAGARI,
    JAPANESE,
    KOREAN,
}

object Languages {
    /**
     * Languages ML Kit's Latin model lists, as plain language subtags.
     * See https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
     */
    private val latin = listOf(
        "af", "sq", "ca", "hr", "cs", "da", "nl", "en", "et", "fil", "fi", "fr", "de", "hu", "is", "id", "it",
        "lv", "lt", "ms", "no", "pl", "pt", "ro", "sr-Latn", "sk", "sl", "es", "sv", "tr", "vi", "az", "eu",
        "bs", "ceb", "eo", "gl", "ht", "ga", "jv", "la", "mt", "sw", "uz", "cy", "zu",
    )

    private val byScript = mapOf(
        Script.LATIN to latin,
        Script.CHINESE to listOf("zh-Hans", "zh-Hant"),
        Script.DEVANAGARI to listOf("hi", "mr", "ne", "sa"),
        Script.JAPANESE to listOf("ja"),
        Script.KOREAN to listOf("ko"),
    )

    /** The tags the enabled models cover, Latin first. */
    fun supported(enabled: Set<Script>): List<String> =
        Script.entries.filter { it == Script.LATIN || it in enabled }.flatMap { byScript.getValue(it) }

    /** The model that reads a supported tag. */
    fun script(tag: String): Script =
        byScript.entries.first { (_, tags) -> tag in tags }.key

    /**
     * Maps requested tags to supported ones. A tag that isn't listed as written falls back to
     * the first supported tag for the same language: `de-DE` or `de-AT` → `de`, `zh` → `zh-Hans`.
     */
    fun resolve(requested: List<String>, supported: List<String>): List<String> {
        fun normalized(tag: String) = tag.replace('_', '-').lowercase()
        fun baseLanguage(tag: String) = normalized(tag).split('-').first()

        val resolved = mutableListOf<String>()
        for (tag in requested) {
            val match = supported.firstOrNull { normalized(it) == normalized(tag) }
                ?: supported.firstOrNull { baseLanguage(it) == baseLanguage(tag) }
                ?: throw OcrException.UnsupportedLanguage(
                    "Language $tag is not supported. Supported: ${supported.joinToString(", ")}.",
                )
            if (match !in resolved) {
                resolved.add(match)
            }
        }
        return resolved
    }

    /**
     * The model for a list of resolved tags: the first non-Latin script requested, since those
     * models read Latin text too; otherwise Latin.
     */
    fun scriptFor(resolved: List<String>): Script =
        resolved.map(::script).firstOrNull { it != Script.LATIN } ?: Script.LATIN
}
