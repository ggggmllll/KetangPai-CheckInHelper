package com.ketangpai.checkin

import java.nio.ByteBuffer

/** Copies only the Y plane; respects padding, pixel stride, crop and buffer position. */
internal fun copyQrLuminance(
    buffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    output: ByteArray,
) {
    require(width > 0 && height > 0 && left >= 0 && top >= 0)
    require(pixelStride > 0 && rowStride >= (left + width - 1) * pixelStride + 1)
    require(output.size == width * height)
    val source = buffer.duplicate()
    val base = source.position() + top * rowStride + left * pixelStride
    require(base.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride < source.limit())
    for (row in 0 until height) {
        val offset = base + row * rowStride
        if (pixelStride == 1) {
            source.position(offset)
            source.get(output, row * width, width)
        } else {
            for (column in 0 until width) output[row * width + column] = source.get(offset + column * pixelStride)
        }
    }
}
