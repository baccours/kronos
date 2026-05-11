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
val Icons.Delete: ImageVector
    get() {
        val currentIcon = delete
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Delete",
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
            moveTo(6.0f, 19.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(8.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            verticalLineTo(7.0f)
            horizontalLineTo(6.0f)
            verticalLineToRelative(12.0f)
            close()
            moveTo(19.0f, 4.0f)
            horizontalLineToRelative(-3.5f)
            lineToRelative(-1.0f, -1.0f)
            horizontalLineToRelative(-5.0f)
            lineToRelative(-1.0f, 1.0f)
            horizontalLineTo(5.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(14.0f)
            verticalLineTo(4.0f)
            close()
        }.build().also { delete = it }
    }
private var delete: ImageVector? = null
