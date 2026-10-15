package com.ketangpai.checkin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeChatQrDecoderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val payload = "https://www.ketangpai.com/checkIn/checkinCodeResult?courseid=test-course&ticketid=test-ticket&expire=1999999999&sign=" + "abcdef0123456789".repeat(4)

    @Test fun smallDenseCodeSurvivesAllCameraRotations() {
        WeChatQrDecoder(context).use { decoder ->
            val image = qrFrame(scale = 3)
            for (rotation in listOf(0, 90, 180, 270)) {
                val start = SystemClock.elapsedRealtime()
                // One full-frame pass and one central pass, as in the live analyzer.
                val values = decoder.decode(image, 1920, 1080, rotation) + decoder.decode(image, 1920, 1080, rotation)
                Log.i("CheckInQrTest", "small dense code rotation=$rotation: ${SystemClock.elapsedRealtime() - start}ms for two passes")
                assertTrue("No result at rotation $rotation", payload in values)
            }
        }
    }

    @Test fun fullFramePassFindsCodeOutsideGuide() {
        WeChatQrDecoder(context).use { decoder ->
            assertTrue(payload in decoder.decode(qrFrame(scale = 4, nearEdge = true), 1920, 1080, 0))
        }
    }

    @Test fun blankFramesAndReopeningDecoderRemainUsable() {
        repeat(3) {
            WeChatQrDecoder(context).use { decoder ->
                assertEquals(emptyList<String>(), decoder.decode(ByteArray(1920 * 1080) { 127 }, 1920, 1080, 0))
                assertTrue(payload in decoder.decode(qrFrame(scale = 4), 1920, 1080, 0))
            }
        }
    }

    private fun qrFrame(scale: Int, nearEdge: Boolean = false): ByteArray {
        val qr = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
        val side = qr.width * scale
        val left = if (nearEdge) 30 else (1920 - side) / 2
        val top = if (nearEdge) 30 else (1080 - side) / 2
        return ByteArray(1920 * 1080) { 255.toByte() }.also { image ->
            for (y in 0 until side) for (x in 0 until side) {
                image[(top + y) * 1920 + left + x] = if (qr[x / scale, y / scale]) 0 else 255.toByte()
            }
        }
    }
}
