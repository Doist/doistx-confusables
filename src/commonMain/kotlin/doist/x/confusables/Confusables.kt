package doist.x.confusables

import doist.x.normalize.Form
import doist.x.normalize.normalize

/**
 * Returns whether this string is visually confusable with [other], based on UTS #39 skeletons.
 */
public fun String.isConfusable(other: String): Boolean = toSkeleton() == other.toSkeleton()

/**
 * Returns the UTS #39 confusable skeleton (specifically, `internalSkeleton`) for this string.
 *
 * This follows the UTS #39 `internalSkeleton` algorithm:
 * - Normalize to NFD
 * - Remove `Default_Ignorable_Code_Point`
 * - Replace each code point with its confusable prototype from `confusables.txt` (if any)
 * - Normalize to NFD again
 *
 * Note: This implementation relies on `confusables.txt` being transitively closed (mapping targets do not contain
 * further mappable code points). The test suite asserts this for the pinned Unicode data.
 *
 * Input is treated as UTF-16. Unpaired surrogates are treated as individual code units and passed through as-is.
 *
 * Note: The returned value is intended for internal comparison only. It is not suitable for display and should not be
 * treated as a general “normalization” of identifiers.
 */
public fun String.toSkeleton(): String {
    val inputNfd = normalize(Form.NFD)
    val skeleton = StringBuilder(inputNfd.length)

    inputNfd.forEachCodePoint { codePoint ->
        if (DefaultIgnorables.isDefaultIgnorable(codePoint)) {
            return@forEachCodePoint
        }

        val prototype = ConfusablesData.prototypeOf(codePoint)
        if (prototype != null) {
            skeleton.append(prototype)
        } else {
            skeleton.appendCodePoint(codePoint)
        }
    }

    return skeleton.toString().normalize(Form.NFD)
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

private fun StringBuilder.appendCodePoint(codePoint: Int) {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
        return
    }

    val adjusted = codePoint - 0x10000
    val high = (adjusted / 0x400) + 0xD800
    val low = (adjusted % 0x400) + 0xDC00
    append(high.toChar())
    append(low.toChar())
}

private fun Char.isHighSurrogate(): Boolean = this in '\uD800'..'\uDBFF'

private fun Char.isLowSurrogate(): Boolean = this in '\uDC00'..'\uDFFF'

private fun toCodePoint(highSurrogate: Char, lowSurrogate: Char): Int {
    val high = highSurrogate.code - 0xD800
    val low = lowSurrogate.code - 0xDC00
    return (high shl 10) + low + 0x10000
}
