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
val Icons.SkipNext: ImageVector
    get() {
        val currentIcon = skipNext
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "SkipNext",
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
            moveTo(6.0f, 18.0f)
            lineToRelative(8.5f, -6.0f)
            lineTo(6.0f, 6.0f)
            verticalLineToRelative(12.0f)
            close()
            moveTo(16.0f, 6.0f)
            verticalLineToRelative(12.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(6.0f)
            horizontalLineToRelative(-2.0f)
            close()
        }.build().also { skipNext = it }
    }
private var skipNext: ImageVector? = null
