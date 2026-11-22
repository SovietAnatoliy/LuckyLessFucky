package com.luckylessfucky.app.scanner

import com.luckylessfucky.app.domain.model.PokemonVariant
import com.luckylessfucky.app.domain.model.ScanResult
import kotlin.math.max
import kotlin.math.min

object VariantMatcher {

    private const val CONCLUSIVE_CONFIDENCE_THRESHOLD = 0.85f

    private val UI_NOISE_WORDS = setOf(
        "cp", "hp", "weight", "height", "stardust", "power", "up", "evolve",
        "new", "attack", "transfer", "favorite", "appraise", "trade", "lucky",
        "pokemon", "pokémon", "shiny", "purified", "shadow", "xl", "xs", "kg", "m"
    )

    private val GENERIC_COSTUME_INDICATORS = setOf(
        "costume", "event", "special event", "hat", "crown", "glasses",
        "sunglasses", "visor", "scarf", "bow", "ribbon", "clone", "armored",
        "fashion", "holiday", "party", "witch", "detective", "flower"
    )

    /**
     * Resolves raw OCR text lines (and optional sprite headroom signature) from a Pokémon GO screen
     * into either a [ScanResult.Conclusive] or [ScanResult.Inconclusive].
     */
    fun matchScreenText(
        ocrLines: List<String>,
        catalog: List<PokemonVariant>,
        detectedSpriteSignatureHex: String? = null
    ): ScanResult {
        val cleanedLines = ocrLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanedLines.isEmpty()) {
            return ScanResult.Inconclusive(
                reason = "No text detected on screen",
                rawText = ""
            )
        }
        if (catalog.isEmpty()) {
            return ScanResult.Inconclusive(
                reason = "Pokédex catalog is not loaded",
                rawText = cleanedLines.joinToString(" | ")
            )
        }

        val fullTextLower = cleanedLines.joinToString("\n").lowercase()
        val variantsBySpecies = catalog.groupBy { normalizeSpecies(it.speciesName) }

        // 1. High-reliability species extraction from "<SPECIES> CANDY" or "<SPECIES> CANDY XL"
        var matchedSpeciesKey: String? = null
        var speciesConfidence = 0f
        var matchedLineText = ""

        val candyRegex = Regex("""([a-zA-Z♀♂'.\- ]+?)\s+candy(?:\s+xl)?\b""", RegexOption.IGNORE_CASE)
        for (line in cleanedLines) {
            val match = candyRegex.find(line)
            if (match != null) {
                val rawCandidate = match.groupValues[1].trim()
                val resolved = findBestSpeciesMatch(rawCandidate, variantsBySpecies.keys)
                if (resolved != null && resolved.second >= CONCLUSIVE_CONFIDENCE_THRESHOLD) {
                    matchedSpeciesKey = resolved.first
                    speciesConfidence = resolved.second
                    matchedLineText = line
                    break
                }
            }
        }

        // 2. Fallback: Match species name from nameplate / header / encounter / trade lines
        if (matchedSpeciesKey == null) {
            for (line in cleanedLines) {
                val strippedLine = stripCpAndUiNoise(line)
                if (strippedLine.isBlank()) continue

                // Direct or token species match
                val resolved = findBestSpeciesMatch(strippedLine, variantsBySpecies.keys)
                if (resolved != null && resolved.second > speciesConfidence) {
                    matchedSpeciesKey = resolved.first
                    speciesConfidence = resolved.second
                    matchedLineText = line
                }
            }
        }

        if (matchedSpeciesKey == null || speciesConfidence < CONCLUSIVE_CONFIDENCE_THRESHOLD) {
            return ScanResult.Inconclusive(
                reason = "Could not conclusively identify a Pokémon species on screen",
                rawText = cleanedLines.joinToString(" | ")
            )
        }

        val speciesVariants = variantsBySpecies[matchedSpeciesKey].orEmpty()
        if (speciesVariants.isEmpty()) {
            return ScanResult.Inconclusive(
                reason = "Matched species has no catalog entries",
                rawText = matchedLineText
            )
        }

        // If only one variant exists for this species, return it conclusively
        if (speciesVariants.size == 1) {
            return ScanResult.Conclusive(
                variant = speciesVariants.first(),
                confidence = speciesConfidence,
                matchedText = matchedLineText
            )
        }

        // 3. Disambiguate between Base Form and Event / Costume / Form Variants
        val nonBaseVariants = speciesVariants.filter { !it.formOrEventName.isNullOrBlank() }
        val baseVariant = speciesVariants.firstOrNull { it.formOrEventName.isNullOrBlank() }
            ?: speciesVariants.first()

        // 3a. Check if OCR text explicitly names a form or event keyword
        val textMatchedVariants = nonBaseVariants.mapNotNull { variant ->
            val formName = variant.formOrEventName?.lowercase()?.trim().orEmpty()
            val extraKeywords = variant.ocrKeywords
                .map { it.lowercase().trim() }
                .filter { it.isNotEmpty() && normalizeSpecies(it) != matchedSpeciesKey }

            val matchedForm = formName.isNotEmpty() && fullTextLower.contains(formName)
            val matchedKeyword = extraKeywords.any { kw -> fullTextLower.contains(kw) }

            when {
                matchedForm -> variant to 0.98f
                matchedKeyword -> variant to 0.92f
                else -> null
            }
        }.sortedByDescending { it.second }

        if (textMatchedVariants.size == 1) {
            val (winner, formConf) = textMatchedVariants.first()
            return ScanResult.Conclusive(
                variant = winner,
                confidence = min(1.0f, (speciesConfidence + formConf) / 2f),
                matchedText = "${winner.displayName} ($matchedLineText)"
            )
        }

        // 3b. Check sprite signature if provided
        if (!detectedSpriteSignatureHex.isNullOrBlank()) {
            val sigMatches = nonBaseVariants.filter {
                it.spriteSignatureHex != null &&
                    it.spriteSignatureHex.equals(detectedSpriteSignatureHex, ignoreCase = true)
            }
            if (sigMatches.size == 1) {
                val winner = sigMatches.first()
                return ScanResult.Conclusive(
                    variant = winner,
                    confidence = speciesConfidence,
                    matchedText = "${winner.displayName} [Sprite Match]"
                )
            }
        }

        // 3c. If multiple event variants matched equally or generic costume indicator is present
        // without disambiguation, return Inconclusive per design specification
        if (textMatchedVariants.size > 1) {
            return ScanResult.Inconclusive(
                reason = "Ambiguous event variant for ${baseVariant.speciesName}; multiple costumes matched",
                rawText = cleanedLines.joinToString(" | ")
            )
        }

        val hasGenericCostumeHint = GENERIC_COSTUME_INDICATORS.any { indicator ->
            Regex("""\b${Regex.escape(indicator)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullTextLower)
        }
        if (hasGenericCostumeHint && nonBaseVariants.isNotEmpty()) {
            return ScanResult.Inconclusive(
                reason = "Costume/event detected for ${baseVariant.speciesName}, but specific event variant is inconclusive",
                rawText = cleanedLines.joinToString(" | ")
            )
        }

        // 3d. Default to the standard/base form when no event/costume cues are present
        return ScanResult.Conclusive(
            variant = baseVariant,
            confidence = speciesConfidence,
            matchedText = matchedLineText
        )
    }

    private fun stripCpAndUiNoise(line: String): String {
        val noCp = line
            .replace(Regex("""\bCP\s*\d+\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b\d+\s*/\s*\d+\s*HP\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[0-9★☆•/\\|()+]+"""), " ")
            .trim()

        val tokens = noCp.split(Regex("""\s+"""))
            .filter { token ->
                val clean = token.lowercase().trim('.', ',', ':', ';', '-', '_')
                clean.length >= 2 && clean !in UI_NOISE_WORDS
            }
        return tokens.joinToString(" ")
    }

    private fun findBestSpeciesMatch(
        candidateText: String,
        speciesKeys: Set<String>
    ): Pair<String, Float>? {
        val normCandidate = normalizeSpecies(candidateText)
        if (normCandidate.isBlank()) return null

        // Exact match on whole normalized string
        if (normCandidate in speciesKeys) {
            return normCandidate to 1.0f
        }

        // Check if any known species appears as a whole word inside candidateText
        for (speciesKey in speciesKeys) {
            if (speciesKey.length >= 3 &&
                Regex("""\b${Regex.escape(speciesKey)}\b""").containsMatchIn(normCandidate)
            ) {
                return speciesKey to 0.94f
            }
        }

        // Fuzzy Levenshtein similarity against individual tokens and whole candidate
        val candidateForms = buildList {
            add(normCandidate)
            addAll(normCandidate.split(" ").filter { it.length >= 3 })
        }

        var bestKey: String? = null
        var bestScore = 0f

        for (form in candidateForms) {
            for (speciesKey in speciesKeys) {
                val score = similarity(form, speciesKey)
                if (score > bestScore) {
                    bestScore = score
                    bestKey = speciesKey
                }
            }
        }

        return if (bestKey != null && bestScore >= CONCLUSIVE_CONFIDENCE_THRESHOLD) {
            bestKey to bestScore
        } else {
            null
        }
    }

    fun normalizeSpecies(name: String): String {
        return name.lowercase()
            .replace("♀", "f")
            .replace("♂", "m")
            .replace("'", "")
            .replace(".", "")
            .replace("-", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(a, b)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}
