package com.ketangpai.checkin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class QrLuminanceTest {
    @Test fun removesRowPaddingWithoutReadingPastLastRow() {
        val input = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 99, 99, 4, 5, 6))
        val output = ByteArray(6)
        copyQrLuminance(input, 5, 1, 0, 0, 3, 2, output)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), output)
        assertEquals(0, input.position())
    }

    @Test fun respectsCropAndReadOnlyBufferPosition() {
        val input = ByteBuffer.wrap(byteArrayOf(88, 1, 2, 3, 99, 4, 5, 6, 99, 7, 8, 9)).asReadOnlyBuffer()
        input.position(1)
        val output = ByteArray(4)
        copyQrLuminance(input, 4, 1, 1, 1, 2, 2, output)
        assertArrayEquals(byteArrayOf(5, 6, 8, 9), output)
        assertEquals(1, input.position())
    }

    @Test fun respectsNonUnitPixelStride() {
        val input = ByteBuffer.wrap(byteArrayOf(1, 99, 2, 99, 3, 99, 88, 88, 4, 99, 5, 99, 6))
        val output = ByteArray(4)
        copyQrLuminance(input, 8, 2, 1, 0, 2, 2, output)
        assertArrayEquals(byteArrayOf(2, 3, 5, 6), output)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedPlane() {
        copyQrLuminance(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), 2, 1, 0, 0, 2, 2, ByteArray(4))
    }
}
