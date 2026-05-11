package com.baccours.kronos.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter")
val Icons.Close: ImageVector
    get() {
        val currentIcon = close
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).path(
            fill = SolidColor(Color(0xFF000000)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(18.3f, 5.71f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
            lineTo(12.0f, 10.59f)
            lineTo(7.11f, 5.7f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
            lineTo(10.59f, 12.0f)
            lineTo(5.7f, 16.89f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
            lineTo(12.0f, 13.41f)
            lineToRelative(4.89f, 4.89f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
            lineTo(13.41f, 12.0f)
            lineToRelative(4.89f, -4.89f)
            curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0.0f, -1.4f)
            close()
        }.build().also { close = it }
    }
private var close: ImageVector? = null
