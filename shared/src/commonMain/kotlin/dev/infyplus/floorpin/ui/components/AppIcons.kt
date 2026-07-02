package dev.infyplus.floorpin.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-ported icon set using the prototype's exact SVG path data, parsed at
 * build of the ImageVector. Keeps us off the (unreliable on CMP) Material icons
 * artifact. `Icon(...)` tints these via its `tint`, so the SolidColor below is
 * just a placeholder. Add new icons here as phases need them.
 */
private class IconPart(val d: String, val fill: Boolean = false)

private fun svgIcon(vararg parts: IconPart, strokeWidth: Float = 1.7f): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        parts.forEach { p ->
            addPath(
                pathData = PathParser().parsePathString(p.d).toNodes(),
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = if (p.fill) null else SolidColor(Color.Black),
                strokeLineWidth = if (p.fill) 0f else strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object AppIcons {
    val Pin = svgIcon(
        IconPart("M12 21s7-6.3 7-11a7 7 0 1 0-14 0c0 4.7 7 11 7 11Z"),
        IconPart("M12 7.7a2.3 2.3 0 1 0 0 4.6 2.3 2.3 0 0 0 0-4.6Z", fill = true),
    )
    val Map = svgIcon(IconPart("M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Zm0 0v14m6-12v14"))
    val Users = svgIcon(
        IconPart("M9 4.8a3.2 3.2 0 1 0 0 6.4 3.2 3.2 0 0 0 0-6.4Z"),
        IconPart("M3.5 20a5.5 5.5 0 0 1 11 0M16 5.3a3.2 3.2 0 0 1 0 6M16.5 20a5.5 5.5 0 0 0-2.3-4.5"),
    )
    val Doc = svgIcon(
        IconPart("M6 3h8l4 4v14H6z"),
        IconPart("M14 3v4h4M9 12h6M9 16h6"),
    )
    val SignOut = svgIcon(IconPart("M14 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-2M9 12h11m0 0-3-3m3 3-3 3"))
    val Menu = svgIcon(IconPart("M4 7h16M4 12h16M4 17h16"))
    val Add = svgIcon(IconPart("M12 5v14M5 12h14"), strokeWidth = 2f)
    val Search = svgIcon(
        IconPart("M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13Z"),
        IconPart("m16 16 4.5 4.5"),
    )
    val Close = svgIcon(IconPart("M6 6l12 12M18 6 6 18"), strokeWidth = 1.8f)
    val ChevronDown = svgIcon(IconPart("M6 9l6 6 6-6"), strokeWidth = 2f)
    val ChevronRight = svgIcon(IconPart("M9 6l6 6-6 6"), strokeWidth = 2f)
    val ChevronLeft = svgIcon(IconPart("M15 6l-6 6 6 6"), strokeWidth = 2f)
    val Upload = svgIcon(IconPart("M12 16V4m0 0 4 4m-4-4-4 4M5 16v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2"))
    val Camera = svgIcon(IconPart("M3 16.5V18a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-1.5M12 3v12m0-12 4 4m-4-4-4 4"))
    val Trash = svgIcon(IconPart("M5 7h14M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"), strokeWidth = 1.6f)
    val More = svgIcon(
        IconPart("M12 4.6a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z", fill = true),
        IconPart("M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z", fill = true),
        IconPart("M12 16.4a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z", fill = true),
    )
    val Share = svgIcon(IconPart("M18 8a3 3 0 1 0-2.8-4M6 12a3 3 0 1 0 0 .01M18 19a3 3 0 1 0-2.8-2M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"), strokeWidth = 1.6f)
    val Download = svgIcon(IconPart("M12 3v12m0 0 4-4m-4 4-4-4M5 17v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2"))
    val Eye = svgIcon(IconPart("M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"), IconPart("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z"))
    val EyeOff = svgIcon(IconPart("M3 3l18 18M10.6 10.7a3 3 0 0 0 4 4M9.4 5.2A9.6 9.6 0 0 1 12 5c6 0 10 7 10 7a17 17 0 0 1-3.2 3.8M6.2 6.3A17 17 0 0 0 2 12s4 7 10 7a9.7 9.7 0 0 0 3-.5"))
    val Move = svgIcon(IconPart("M12 3v18M3 12h18M12 3l-2.6 2.6M12 3l2.6 2.6M12 21l-2.6-2.6M12 21l2.6-2.6M3 12l2.6-2.6M3 12l2.6 2.6M21 12l-2.6-2.6M21 12l-2.6 2.6"), strokeWidth = 1.6f)
    val LocationAdd = svgIcon(
        IconPart("M12 21s7-6.3 7-11a7 7 0 0 0-9.3-6.6"),
        IconPart("M5.3 6.2A7 7 0 0 0 5 10c0 4.7 7 11 7 11"),
        IconPart("M3.5 4.5h4M5.5 2.5v4"),
    )
    val DefectAdd = svgIcon(
        IconPart("M11 4.2 3.3 17.8A1.5 1.5 0 0 0 4.6 20h10.8"),
        IconPart("M11 10v3"),
        IconPart("M18 14v6M15 17h6"),
    )
    val FitScreen = svgIcon(IconPart("M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"), strokeWidth = 1.8f)
    val Check = svgIcon(IconPart("M5 13l4 4L19 7"), strokeWidth = 2f)
    val Undo = svgIcon(
        IconPart("M9 14 4 9l5-5"),
        IconPart("M4 9h10.5a5.5 5.5 0 0 1 0 11H12"),
        strokeWidth = 1.8f,
    )
}
