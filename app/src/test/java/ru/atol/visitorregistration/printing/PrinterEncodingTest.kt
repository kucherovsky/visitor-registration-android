package ru.atol.visitorregistration.printing

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import ru.atol.visitorregistration.model.PrinterEncoding

class PrinterEncodingTest {
    @Test
    fun encodesCyrillicWithSelectedPrinterCharset() {
        assertArrayEquals(
            byteArrayOf(0xC8.toByte(), 0xE2.toByte(), 0xE0.toByte(), 0xED.toByte()),
            "Иван".toByteArray(charset(PrinterEncoding.WINDOWS_1251.charsetName))
        )
        assertArrayEquals(
            byteArrayOf(0x88.toByte(), 0xA2.toByte(), 0xA0.toByte(), 0xAD.toByte()),
            "Иван".toByteArray(charset(PrinterEncoding.CP866.charsetName))
        )
    }
}
