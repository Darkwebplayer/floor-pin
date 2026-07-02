package dev.infyplus.floorpin.ui.components

internal actual fun flattenImageWithStrokes(
    imageBytes: ByteArray,
    displayWidth: Int,
    displayHeight: Int,
    strokes: List<DrawnStroke>,
): ByteArray {
    // TODO: iOS — use UIGraphicsImageRenderer to composite strokes onto UIImage
    return imageBytes
}
