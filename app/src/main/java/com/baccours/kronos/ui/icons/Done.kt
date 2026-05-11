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
val Icons.Done: ImageVector
    get() {
        val currentIcon = done
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Done",
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
            moveTo(9.0f, 16.2f)
            lineToRelative(-3.5f, -3.5f)
            curveToRelative(-0.39f, -0.39f, -1.01f, -0.39f, -1.4f, 0.0f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.01f, 0.0f, 1.4f)
            lineToRelative(4.19f, 4.19f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
            lineTo(20.3f, 7.7f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
            curveToRelative(-0.39f, -0.39f, -1.01f, -0.39f, -1.4f, 0.0f)
            lineTo(9.0f, 16.2f)
            close()
        }.build().also { done = it }
    }
private var done: ImageVector? = null
