package io.github.tunlezah.watches.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A small set of hairline icons drawn for this app (24-unit grid, 1.6 stroke), so no icon library is needed. */
object WatchIcons {

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun stroke(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) {
                b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    pathData = nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }

    val Grid = stroke("grid", "M4.5,3.5h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1z",
        "M14.5,3.5h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1z",
        "M4.5,13.5h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1z",
        "M14.5,13.5h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1z")
    val List = stroke("list", "M4,6h16", "M4,12h16", "M4,18h16")
    val Search = stroke("search", circle(11f, 11f, 6.5f), "M16,16l4.5,4.5")
    val Lookup = stroke("lookup", circle(10.5f, 10.5f, 6f), "M15,15l5,5", "M10.5,7.5v6", "M7.5,10.5h6")
    val Service = stroke("service", "M15.5,4.5a4.5,4.5 0 0 0 -4.9,6.1l-6.1,6.1a2,2 0 1 0 2.8,2.8l6.1,-6.1a4.5,4.5 0 0 0 6.1,-4.9l-2.6,2.6l-2.4,-0.6l-0.6,-2.4z")
    val Sliders = stroke("sliders", "M4,7h9", "M17,7h3", "M4,17h3", "M11,17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val Plus = stroke("plus", "M12,5v14", "M5,12h14")
    val Back = stroke("back", "M15,5l-7,7l7,7")
    val Forward = stroke("forward", "M9,5l7,7l-7,7")
    val More = stroke("more", circle(5f, 12f, 1.6f), circle(12f, 12f, 1.6f), circle(19f, 12f, 1.6f), filled = true)
    val Camera = stroke("camera", "M4,8h3l1.5,-2h7l1.5,2h3a1,1 0 0 1 1,1v9a1,1 0 0 1 -1,1h-16a1,1 0 0 1 -1,-1v-9a1,1 0 0 1 1,-1z", circle(12f, 13f, 3.5f))
    val Image = stroke("image", "M4.5,4.5h15a1,1 0 0 1 1,1v13a1,1 0 0 1 -1,1h-15a1,1 0 0 1 -1,-1v-13a1,1 0 0 1 1,-1z", "M3.5,16l5,-5l4,4l3,-3l5,5", circle(15.5f, 8.5f, 1.5f))
    val Crop = stroke("crop", "M6,2v16h16", "M2,6h16v16")
    val Star = stroke("star", "M12,3.5l2.6,5.3l5.9,0.9l-4.3,4.1l1,5.8l-5.2,-2.7l-5.2,2.7l1,-5.8l-4.3,-4.1l5.9,-0.9z")
    val StarFilled = stroke("star-filled", "M12,3.5l2.6,5.3l5.9,0.9l-4.3,4.1l1,5.8l-5.2,-2.7l-5.2,2.7l1,-5.8l-4.3,-4.1l5.9,-0.9z", filled = true)
    val Trash = stroke("trash", "M4,7h16", "M9,7v-2.5h6v2.5", "M6,7l1,13h10l1,-13", "M10,11v5", "M14,11v5")
    val Archive = stroke("archive", "M3.5,4.5h17v4h-17z", "M5,8.5v11h14v-11", "M10,12h4")
    val Copy = stroke("copy", "M8,8h11v11h-11z", "M5,16v-11h11")
    val Edit = stroke("edit", "M4,20h4l10.5,-10.5a2.1,2.1 0 0 0 -3,-3l-10.5,10.5z", "M13.5,7.5l3,3")
    val Check = stroke("check", "M5,12.5l4.5,4.5l9.5,-10")
    val Close = stroke("close", "M6,6l12,12", "M18,6l-12,12")
    val Up = stroke("up", "M12,19v-14", "M6,11l6,-6l6,6")
    val Down = stroke("down", "M12,5v14", "M6,13l6,6l6,-6")
    val Left = stroke("left", "M19,12h-14", "M11,6l-6,6l6,6")
    val Right = stroke("right", "M5,12h14", "M13,6l6,6l-6,6")
    val Sort = stroke("sort", "M8,4v16", "M4,8l4,-4l4,4", "M16,20v-16", "M12,16l4,4l4,-4")
    val Eye = stroke("eye", "M2.5,12s3.5,-6.5 9.5,-6.5s9.5,6.5 9.5,6.5s-3.5,6.5 -9.5,6.5s-9.5,-6.5 -9.5,-6.5z", circle(12f, 12f, 3f))
    val EyeOff = stroke("eye-off", "M2.5,12s3.5,-6.5 9.5,-6.5s9.5,6.5 9.5,6.5s-3.5,6.5 -9.5,6.5s-9.5,-6.5 -9.5,-6.5z", circle(12f, 12f, 3f), "M4,4l16,16")
    val Restore = stroke("restore", "M4,12a8,8 0 1 0 2.3,-5.7", "M4,4v4h4")
    val Watch = stroke("watch", circle(12f, 12f, 6f), "M9,6l0.8,-3h4.4l0.8,3", "M9,18l0.8,3h4.4l0.8,-3", "M12,9v3l2,1.5")
    val Info = stroke("info", circle(12f, 12f, 9f), "M12,11v5", "M12,8v0.2")
    val External = stroke("external", "M14,4h6v6", "M20,4l-9,9", "M18,14v5a1,1 0 0 1 -1,1h-12a1,1 0 0 1 -1,-1v-12a1,1 0 0 1 1,-1h5")
    val Refresh = stroke("refresh", "M20,12a8,8 0 1 1 -2.3,-5.7", "M20,4v4h-4")
    val History = stroke("history", circle(12f, 12f, 8f), "M12,8v4l3,2")
    val Gauge = stroke("gauge", "M4,16a8,8 0 1 1 16,0", "M12,16l4,-5")
    val Backup = stroke("backup", "M12,15v-10", "M8,9l4,-4l4,4", "M4,14v4a2,2 0 0 0 2,2h12a2,2 0 0 0 2,-2v-4")
    val Download = stroke("download", "M12,4v10", "M8,10l4,4l4,-4", "M4,14v4a2,2 0 0 0 2,2h12a2,2 0 0 0 2,-2v-4")
    val Table = stroke("table", "M4.5,4.5h15a1,1 0 0 1 1,1v13a1,1 0 0 1 -1,1h-15a1,1 0 0 1 -1,-1v-13a1,1 0 0 1 1,-1z", "M3.5,9.5h17", "M3.5,14.5h17", "M9.5,9.5v10")
    val Lock = stroke("lock", "M6,10.5h12a1,1 0 0 1 1,1v8a1,1 0 0 1 -1,1h-12a1,1 0 0 1 -1,-1v-8a1,1 0 0 1 1,-1z", "M8,10.5v-3a4,4 0 0 1 8,0v3", "M12,14.5v2")
    val Shield = stroke("shield", "M12,3l7,3v5.5c0,4.5 -3,8 -7,9.5c-4,-1.5 -7,-5 -7,-9.5v-5.5z", "M9,12l2,2l4,-4")
    val Bell = stroke("bell", "M6,16v-5a6,6 0 0 1 12,0v5l1.5,2h-15z", "M10,20.5a2,2 0 0 0 4,0")
    val Palette = stroke("palette", "M12,3.5a8.5,8.5 0 1 0 0,17c1.2,0 1.8,-0.8 1.8,-1.7c0,-1.3 -1,-1.5 -1,-2.6c0,-1 0.8,-1.7 1.8,-1.7h2.1a3.8,3.8 0 0 0 3.8,-3.8c0,-4.2 -3.8,-7.2 -8.5,-7.2z", circle(7.8f, 11f, 1.1f), circle(10f, 7.4f, 1.1f), circle(14.3f, 7.4f, 1.1f))
    val Ruler = stroke("ruler", "M3.5,15.5l12,-12l5,5l-12,12z", "M7,12l2,2", "M10,9l2,2", "M13,6l2,2")
    val Warning = stroke("warning", "M12,4l9,16h-18z", "M12,10v4.5", "M12,17.3v0.2")
    val CheckCircle = stroke("check-circle", circle(12f, 12f, 9f), "M8,12.5l2.8,2.8l5.2,-5.6")
}
