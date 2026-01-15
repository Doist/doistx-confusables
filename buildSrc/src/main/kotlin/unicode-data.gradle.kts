/*
 * Generate Unicode data tables used by the library.
 *
 * - `generateUnicodeData` is wired into compilation and runs offline, using `unicode-data/` inputs.
 * - `updateUnicodeData` refreshes `unicode-data/` from unicode.org (network required) and regenerates.
 */

import java.io.File
import java.net.URL
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private val outputPackage = "doist.x.confusables"
private val unicodeDataDir = "unicode-data"
private val confusablesFileName = "confusables.txt"
private val derivedCorePropertiesFileName = "DerivedCoreProperties.txt"
private val generatedSourcesDir = "generated/unicode-data/commonMain/kotlin"

val confusablesInputFile = layout.projectDirectory.file("$unicodeDataDir/$confusablesFileName")
val derivedCorePropertiesInputFile = layout.projectDirectory.file("$unicodeDataDir/$derivedCorePropertiesFileName")
val generatedKotlinRoot = layout.buildDirectory.dir(generatedSourcesDir)

extensions.configure<KotlinMultiplatformExtension>("kotlin") {
    sourceSets.named("commonMain") {
        kotlin.srcDir(generatedKotlinRoot)
    }
}

val generateUnicodeData = tasks.register("generateUnicodeData") {
    group = "build setup"
    description = "Generate Kotlin tables from Unicode data files in `unicode-data/`."

    inputs.file(confusablesInputFile)
    inputs.file(derivedCorePropertiesInputFile)
    outputs.dir(generatedKotlinRoot)

    doLast {
        val confusablesText = readRequiredText(confusablesInputFile.asFile, confusablesInputFile.asFile.path)
        val derivedText = readRequiredText(derivedCorePropertiesInputFile.asFile, derivedCorePropertiesInputFile.asFile.path)
        val unicodeVersion = unicodeVersionFromConfusables(confusablesText)

        writeKotlinTables(
            outputRoot = generatedKotlinRoot.get().asFile,
            packageName = outputPackage,
            unicodeVersion = unicodeVersion,
            confusablesText = confusablesText,
            derivedCorePropertiesText = derivedText,
        )
    }
}

tasks.register("updateUnicodeData") {
    group = "build setup"
    description = "Download Unicode data files into `unicode-data/` and regenerate Kotlin tables (network required)."

    val unicodeVersionProperty = providers.gradleProperty("unicodeVersion")

    outputs.file(confusablesInputFile)
    outputs.file(derivedCorePropertiesInputFile)
    outputs.dir(generatedKotlinRoot)

    doLast {
        val version = unicodeVersionProperty.orNull
            ?: pinnedUnicodeVersion(confusablesInputFile.asFile)
            ?: error("Missing -PunicodeVersion and no existing `${confusablesInputFile.asFile.path}` to infer from.")

        val confusablesUrl = "https://www.unicode.org/Public/security/$version/confusables.txt"
        val derivedCorePropertiesUrl = "https://www.unicode.org/Public/$version/ucd/DerivedCoreProperties.txt"

        val confusablesText = fetchText(confusablesUrl)
        val headerVersion = unicodeVersionFromConfusables(confusablesText)
        require(headerVersion == version) {
            "confusables.txt version header did not match $version (got $headerVersion); " +
                "did you mean to pass -PunicodeVersion=? (url=$confusablesUrl)"
        }

        val derivedText = fetchText(derivedCorePropertiesUrl)

        confusablesInputFile.asFile.parentFile.mkdirs()
        confusablesInputFile.asFile.writeText(confusablesText, Charsets.UTF_8)
        derivedCorePropertiesInputFile.asFile.writeText(derivedText, Charsets.UTF_8)

        writeKotlinTables(
            outputRoot = generatedKotlinRoot.get().asFile,
            packageName = outputPackage,
            unicodeVersion = version,
            confusablesText = confusablesText,
            derivedCorePropertiesText = derivedText,
        )
    }
}

tasks.matching { it.name.startsWith("compileKotlin") || it.name.startsWith("compileTestKotlin") }.configureEach {
    dependsOn(generateUnicodeData)
}

tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(generateUnicodeData)
}

private fun fetchText(url: String): String {
    return URL(url).openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun pinnedUnicodeVersion(confusablesFile: File): String? {
    if (!confusablesFile.exists()) return null
    val text = confusablesFile.readText(Charsets.UTF_8)
    return unicodeVersionFromConfusables(text)
}

private fun unicodeVersionFromConfusables(confusablesText: String): String {
    val match = Regex("^#\\s*Version:\\s*(.+)\\s*$", setOf(RegexOption.MULTILINE))
        .find(confusablesText)
        ?: error("Could not find a '# Version:' header in confusables.txt")
    return match.groupValues[1].trim()
}

private fun readRequiredText(file: File, pathHint: String): String {
    require(file.exists()) {
        "Missing required file `$pathHint`. Run `./gradlew updateUnicodeData -PunicodeVersion=<version>` to create it."
    }
    return file.readText(Charsets.UTF_8)
}

private fun writeKotlinTables(
    outputRoot: File,
    packageName: String,
    unicodeVersion: String,
    confusablesText: String,
    derivedCorePropertiesText: String,
) {
    val mappings = parseConfusables(confusablesText)
    val defaultIgnorables = parseDefaultIgnorables(derivedCorePropertiesText)

    val packageDir = outputRoot.resolve(packageName.replace('.', '/'))
    packageDir.mkdirs()

    packageDir.resolve("ConfusablesData.kt").writeText(
        generateConfusablesData(packageName, unicodeVersion, mappings),
        Charsets.UTF_8,
    )
    packageDir.resolve("DefaultIgnorables.kt").writeText(
        generateDefaultIgnorables(packageName, unicodeVersion, defaultIgnorables),
        Charsets.UTF_8,
    )

    println("Wrote ConfusablesData.kt with ${mappings.size} mappings")
    println("Wrote DefaultIgnorables.kt with ${defaultIgnorables.size} merged ranges")
}

private data class ConfusableMapping(val source: Int, val target: List<Int>)

private fun parseConfusables(text: String): List<ConfusableMapping> {
    val mappings = mutableListOf<ConfusableMapping>()
    text.lineSequence().forEach { rawLine ->
        val line = rawLine.substringBefore("#").trim()
        if (line.isEmpty()) return@forEach

        val parts = line.split(";")
        if (parts.size < 3) return@forEach

        val sourceHex = parts[0].trim()
        require(!sourceHex.contains(" ")) { "Unexpected multi-codepoint source in confusables.txt: $sourceHex" }

        val targetHex = parts[1].trim()
        val source = sourceHex.toInt(radix = 16)
        val target = if (targetHex.isEmpty()) emptyList() else targetHex.split(Regex("\\s+")).map { it.toInt(radix = 16) }
        mappings.add(ConfusableMapping(source, target))
    }

    val sorted = mappings.sortedBy { it.source }
    for (i in 1 until sorted.size) {
        require(sorted[i - 1].source < sorted[i].source) { "Duplicate source in confusables.txt: 0x${sorted[i].source.toString(16)}" }
    }
    return sorted
}

private data class IntRangePair(val start: Int, val end: Int)

private fun parseDefaultIgnorables(text: String): List<IntRangePair> {
    val ranges = mutableListOf<IntRangePair>()
    text.lineSequence().forEach { rawLine ->
        val line = rawLine.substringBefore("#").trim()
        if (line.isEmpty() || !line.contains(";")) return@forEach

        val (rangePart, propertyName) = line.split(";", limit = 2).map(String::trim)
        if (propertyName != "Default_Ignorable_Code_Point") return@forEach

        val (start, end) = if (rangePart.contains("..")) {
            val (startHex, endHex) = rangePart.split("..", limit = 2)
            startHex.toInt(radix = 16) to endHex.toInt(radix = 16)
        } else {
            val cp = rangePart.toInt(radix = 16)
            cp to cp
        }

        ranges.add(IntRangePair(start, end))
    }

    val sorted = ranges.sortedBy { it.start }
    val merged = mutableListOf<IntRangePair>()
    for (range in sorted) {
        val last = merged.lastOrNull()
        if (last == null || range.start > last.end + 1) {
            merged.add(range)
        } else if (range.end > last.end) {
            merged[merged.size - 1] = IntRangePair(last.start, range.end)
        }
    }

    return merged
}

private fun generateConfusablesData(packageName: String, unicodeVersion: String, mappings: List<ConfusableMapping>): String {
    // Keep chunk initializers small enough for JVM (avoid MethodTooLargeException on <clinit>).
    val chunkSize = 512
    val chunks = mappings.chunked(chunkSize)

    val chunkEnds = chunks.joinToString(separator = ", ") { "0x${it.last().source.toString(16).uppercase()}" }
    val dispatchCases = chunks.indices.joinToString(separator = "\n") { index ->
        "            $index -> ConfusablesDataChunk$index.prototypeOf(codePoint)"
    }
    val forEachCalls = chunks.indices.joinToString(separator = "\n") { index ->
        "        ConfusablesDataChunk$index.forEachMapping(action)"
    }

    val chunkObjects = chunks.mapIndexed { index, chunk ->
        val sources = chunk.map { "0x${it.source.toString(16).uppercase()}" }
        val targets = chunk.map { "\"${toUtf16Escapes(it.target)}\"" }
        val sourcesBlock = sources.chunked(12).joinToString(separator = ",\n") { "        " + it.joinToString(separator = ", ") } + ","
        val targetsBlock = targets.chunked(4).joinToString(separator = ",\n") { "        " + it.joinToString(separator = ", ") } + ","

        """
private object ConfusablesDataChunk$index {
    private val sources: IntArray = intArrayOf(
$sourcesBlock
    )

    private val targets: Array<String> = arrayOf(
$targetsBlock
    )

    private fun findSourceIndex(codePoint: Int): Int {
        var low = 0
        var high = sources.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = sources[mid]
            when {
                midVal < codePoint -> low = mid + 1
                midVal > codePoint -> high = mid - 1
                else -> return mid
            }
        }
        return -low - 1
    }

    internal fun prototypeOf(codePoint: Int): String? {
        val index = findSourceIndex(codePoint)
        return if (index >= 0) targets[index] else null
    }

    internal fun forEachMapping(action: (sourceCodePoint: Int, target: String) -> Unit) {
        for (i in sources.indices) {
            action(sources[i], targets[i])
        }
    }
}
""".trim()
    }.joinToString(separator = "\n\n")

    return """
// This file is generated by the `generateUnicodeData` Gradle task.
// Source: https://www.unicode.org/Public/security/$unicodeVersion/confusables.txt
// Unicode version: $unicodeVersion
// Do not edit by hand.

package $packageName

$chunkObjects

internal object ConfusablesData {
    internal const val UNICODE_VERSION: String = "$unicodeVersion"
    internal const val MAPPING_COUNT: Int = ${mappings.size}

    private val chunkEnds: IntArray = intArrayOf($chunkEnds)

    private fun findChunkIndex(codePoint: Int): Int {
        var low = 0
        var high = chunkEnds.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val end = chunkEnds[mid]
            if (end < codePoint) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (low < chunkEnds.size) low else -1
    }

    internal fun prototypeOf(codePoint: Int): String? {
        return when (findChunkIndex(codePoint)) {
$dispatchCases
            else -> null
        }
    }

    internal fun forEachMapping(action: (sourceCodePoint: Int, target: String) -> Unit) {
$forEachCalls
    }
}
""".trimStart()
}

private fun toUtf16Escapes(codePoints: List<Int>): String {
    val out = StringBuilder()
    for (codePoint in codePoints) {
        if (codePoint <= 0xFFFF) {
            out.append("\\u").append(codePoint.toString(16).uppercase().padStart(4, '0'))
            continue
        }

        val adjusted = codePoint - 0x10000
        val high = (adjusted / 0x400) + 0xD800
        val low = (adjusted % 0x400) + 0xDC00
        out.append("\\u").append(high.toString(16).uppercase().padStart(4, '0'))
        out.append("\\u").append(low.toString(16).uppercase().padStart(4, '0'))
    }
    return out.toString()
}

private fun generateDefaultIgnorables(packageName: String, unicodeVersion: String, ranges: List<IntRangePair>): String {
    val flatRanges = buildList {
        ranges.forEach { (start, end) ->
            add("0x${start.toString(16).uppercase()}")
            add("0x${end.toString(16).uppercase()}")
        }
    }
    val rangesBlock = flatRanges.chunked(8).joinToString(separator = ",\n") { "        " + it.joinToString(separator = ", ") } + ","

    return """
// This file is generated by the `generateUnicodeData` Gradle task.
// Source: https://www.unicode.org/Public/$unicodeVersion/ucd/DerivedCoreProperties.txt (Default_Ignorable_Code_Point)
// Unicode version: $unicodeVersion
// Do not edit by hand.

package $packageName

internal object DefaultIgnorables {
    internal const val UNICODE_VERSION: String = "$unicodeVersion"

    // Inclusive ranges: [start0, end0, start1, end1, ...]
    private val ranges: IntArray = intArrayOf(
$rangesBlock
    )

    internal fun isDefaultIgnorable(codePoint: Int): Boolean {
        var low = 0
        var high = (ranges.size / 2) - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = ranges[mid * 2]
            val end = ranges[mid * 2 + 1]
            when {
                codePoint < start -> high = mid - 1
                codePoint > end -> low = mid + 1
                else -> return true
            }
        }
        return false
    }
}
""".trimStart()
}
