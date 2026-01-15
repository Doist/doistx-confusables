package doist.x.confusables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfusablesTest {
    @Test
    fun skeletonPaypalExample() {
        assertEquals("paypal", "paypal".toSkeleton())
        assertEquals("paypal", "p\u0430yp\u0430l".toSkeleton())
        assertTrue("paypal".isConfusable("p\u0430yp\u0430l"))
    }

    @Test
    fun skeletonScopeExample() {
        assertEquals("scope", "\u0455\u0441\u043E\u0440\u0435".toSkeleton())
        assertTrue("scope".isConfusable("\u0455\u0441\u043E\u0440\u0435"))
    }

    @Test
    fun skeletonExpandsToMultipleCodePoints() {
        assertEquals("rn", "m".toSkeleton())
        assertTrue("m".isConfusable("rn"))
    }

    @Test
    fun removesDefaultIgnorables() {
        assertEquals("ab", "a\u200Db".toSkeleton())
    }

    @Test
    fun skeletonIsIdempotentForTypicalInput() {
        val input = "p\u0430y\u200Dpal"
        assertEquals(input.toSkeleton(), input.toSkeleton().toSkeleton())
    }

    @Test
    fun nonConfusableStrings() {
        assertFalse("hello".isConfusable("world"))
    }

    @Test
    fun emptyString() {
        assertEquals("", "".toSkeleton())
        assertTrue("".isConfusable(""))
    }

    @Test
    fun astralPlaneCodePoints() {
        val mathematicalDoubleStruckA = "\uD835\uDD38" // U+1D538
        assertEquals("A", mathematicalDoubleStruckA.toSkeleton())
        assertTrue("A".isConfusable(mathematicalDoubleStruckA))
    }
}
