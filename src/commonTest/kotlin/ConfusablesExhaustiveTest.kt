package doist.x.confusables

import doist.x.normalize.Form
import doist.x.normalize.normalize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfusablesExhaustiveTest {
    @Test
    fun allMappingsMatchSkeletons() {
        val skipped = skippedMappingSources

        var mappingCount = 0
        var skippedCount = 0
        var stableSourceCount = 0
        var defaultIgnorableSourceCount = 0
        var mismatchCount = 0
        val mismatchSamples = ArrayList<String>(20)

        fun recordMismatch(message: String) {
            mismatchCount++
            if (mismatchSamples.size < 20) {
                mismatchSamples.add(message)
            }
        }

        ConfusablesData.forEachMapping { sourceCodePoint, target ->
            mappingCount++
            if (sourceCodePoint in skipped) {
                skippedCount++
                return@forEachMapping
            }

            val lookupTarget = ConfusablesData.prototypeOf(sourceCodePoint)
            if (lookupTarget != target) {
                recordMismatch(
                    "prototypeOf mismatch for ${sourceCodePoint.toUPlusString()}: " +
                        "expected=${target.toUPlusStringSequence()} got=${lookupTarget?.toUPlusStringSequence()}",
                )
                return@forEachMapping
            }

            val targetMappedCodePoint = target.firstMappedCodePoint()
            if (targetMappedCodePoint != null) {
                recordMismatch(
                    "mapping target contains a mappable code point for ${sourceCodePoint.toUPlusString()}: " +
                        "target=${target.toUPlusStringSequence()} " +
                        "mappable=${targetMappedCodePoint.toUPlusString()} -> " +
                        "${ConfusablesData.prototypeOf(targetMappedCodePoint)?.toUPlusStringSequence()}",
                )
            }

            val sourceString = sourceCodePoint.toStringFromCodePoint()
            val sourceSkeleton = sourceString.toSkeleton()
            if (sourceSkeleton.normalize(Form.NFD) != sourceSkeleton) {
                recordMismatch("skeleton is not NFD for ${sourceCodePoint.toUPlusString()}: ${sourceSkeleton.toUPlusStringSequence()}")
            }

            val defaultIgnorable = sourceSkeleton.firstDefaultIgnorableCodePoint()
            if (defaultIgnorable != null) {
                recordMismatch(
                    "default-ignorable code point present in skeleton for ${sourceCodePoint.toUPlusString()}: " +
                        "${defaultIgnorable.toUPlusString()} in ${sourceSkeleton.toUPlusStringSequence()}",
                )
            }

            if (DefaultIgnorables.isDefaultIgnorable(sourceCodePoint)) {
                defaultIgnorableSourceCount++
                if (sourceSkeleton.isNotEmpty()) {
                    recordMismatch(
                        "default-ignorable source not removed for ${sourceCodePoint.toUPlusString()}: " +
                            "skeleton=${sourceSkeleton.toUPlusStringSequence()}",
                    )
                }
                return@forEachMapping
            }

            if (sourceString.normalize(Form.NFD) != sourceString) {
                return@forEachMapping
            }

            stableSourceCount++
            val expectedSkeleton = target.normalize(Form.NFD)
            if (sourceSkeleton != expectedSkeleton) {
                recordMismatch(
                    "stable-source skeleton mismatch for ${sourceCodePoint.toUPlusString()}: " +
                        "target=${target.toUPlusStringSequence()} " +
                        "expected=${expectedSkeleton.toUPlusStringSequence()} " +
                        "got=${sourceSkeleton.toUPlusStringSequence()}",
                )
            }
        }

        assertEquals(ConfusablesData.MAPPING_COUNT, mappingCount, "ConfusablesData.forEachMapping must cover all entries")

        val testedCount = mappingCount - skippedCount
        assertTrue(
            mismatchCount == 0,
            "Found $mismatchCount mismatches (tested=$testedCount stableSources=$stableSourceCount defaultIgnorableSources=$defaultIgnorableSourceCount skipped=$skippedCount). " +
                "First ${mismatchSamples.size}:\n${mismatchSamples.joinToString(separator = "\n")}",
        )
    }

    @Test
    fun unicodeVersionsMatch() {
        assertEquals(ConfusablesData.UNICODE_VERSION, DefaultIgnorables.UNICODE_VERSION)
    }
}

private fun Int.toStringFromCodePoint(): String {
    if (this <= 0xFFFF) return this.toChar().toString()
    val adjusted = this - 0x10000
    val high = (adjusted / 0x400) + 0xD800
    val low = (adjusted % 0x400) + 0xDC00
    return charArrayOf(high.toChar(), low.toChar()).concatToString()
}

private fun Int.toUPlusString(): String = "U+" + toString(radix = 16).uppercase().padStart(4, '0')

private fun String.toUPlusStringSequence(): String {
    val out = StringBuilder()
    out.append('[')
    var first = true
    forEachCodePoint { codePoint ->
        if (!first) out.append(' ')
        first = false
        out.append(codePoint.toUPlusString())
    }
    out.append(']')
    return out.toString()
}

private fun String.firstDefaultIgnorableCodePoint(): Int? {
    var index = 0
    while (index < length) {
        val first = this[index]
        val codePoint: Int
        if (first.isHighSurrogate() && index + 1 < length) {
            val second = this[index + 1]
            if (second.isLowSurrogate()) {
                codePoint = toCodePoint(first, second)
                index += 2
            } else {
                codePoint = first.code
                index++
            }
        } else {
            codePoint = first.code
            index++
        }

        if (DefaultIgnorables.isDefaultIgnorable(codePoint)) return codePoint
    }
    return null
}

private fun String.firstMappedCodePoint(): Int? {
    var index = 0
    while (index < length) {
        val first = this[index]
        val codePoint: Int
        if (first.isHighSurrogate() && index + 1 < length) {
            val second = this[index + 1]
            if (second.isLowSurrogate()) {
                codePoint = toCodePoint(first, second)
                index += 2
            } else {
                codePoint = first.code
                index++
            }
        } else {
            codePoint = first.code
            index++
        }

        if (ConfusablesData.prototypeOf(codePoint) != null) return codePoint
    }
    return null
}

private inline fun String.forEachCodePoint(action: (codePoint: Int) -> Unit) {
    var index = 0
    while (index < length) {
        val first = this[index]
        if (first.isHighSurrogate() && index + 1 < length) {
            val second = this[index + 1]
            if (second.isLowSurrogate()) {
                action(toCodePoint(first, second))
                index += 2
                continue
            }
        }
        action(first.code)
        index++
    }
}

private fun Char.isHighSurrogate(): Boolean = this in '\uD800'..'\uDBFF'

private fun Char.isLowSurrogate(): Boolean = this in '\uDC00'..'\uDFFF'

private fun toCodePoint(highSurrogate: Char, lowSurrogate: Char): Int {
    val high = highSurrogate.code - 0xD800
    val low = lowSurrogate.code - 0xDC00
    return (high shl 10) + low + 0x10000
}
