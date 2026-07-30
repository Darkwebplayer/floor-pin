package dev.infyplus.floorpin

import dev.infyplus.floorpin.ui.screens.sniffImageMime
import kotlin.test.Test
import kotlin.test.assertEquals

/** A wrong byte offset here yields a data URI the print WebView silently refuses to render. */
class SniffImageMimeTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun jpeg() = assertEquals("image/jpeg", sniffImageMime(bytes(0xFF, 0xD8, 0xFF, 0xE0)))

    @Test fun png() = assertEquals(
        "image/png",
        sniffImageMime(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    )

    // "RIFF" + 4 size bytes + "WEBP"
    @Test fun webp() = assertEquals(
        "image/webp",
        sniffImageMime(bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)),
    )

    @Test fun gif() = assertEquals("image/gif", sniffImageMime(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)))

    @Test fun shortInputFallsBackInsteadOfThrowing() =
        assertEquals("image/jpeg", sniffImageMime(bytes(0xFF)))
}
