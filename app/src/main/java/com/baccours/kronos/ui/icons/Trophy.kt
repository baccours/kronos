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
val Icons.Trophy: ImageVector
    get() {
        val currentIcon = trophy
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Trophy",
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
            moveTo(19.0f, 5.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineTo(4.0f)
            curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
            horizontalLineTo(8.0f)
            curveTo(7.45f, 3.0f, 7.0f, 3.45f, 7.0f, 4.0f)
            verticalLineToRelative(1.0f)
            horizontalLineTo(5.0f)
            curveTo(3.9f, 5.0f, 3.0f, 5.9f, 3.0f, 7.0f)
            verticalLineToRelative(1.0f)
            curveToRelative(0.0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
            curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
            verticalLineTo(19.0f)
            horizontalLineTo(8.0f)
            curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
            verticalLineToRelative(0.0f)
            curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
            horizontalLineToRelative(8.0f)
            curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
            verticalLineToRelative(0.0f)
            curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
            horizontalLineToRelative(-3.0f)
            verticalLineToRelative(-3.1f)
            curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
            curveTo(19.08f, 12.63f, 21.0f, 10.55f, 21.0f, 8.0f)
            verticalLineTo(7.0f)
            curveTo(21.0f, 5.9f, 20.1f, 5.0f, 19.0f, 5.0f)
            close()
            moveTo(5.0f, 8.0f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(3.82f)
            curveTo(5.84f, 10.4f, 5.0f, 9.3f, 5.0f, 8.0f)
            close()
            moveTo(19.0f, 8.0f)
            curveToRelative(0.0f, 1.3f, -0.84f, 2.4f, -2.0f, 2.82f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(8.0f)
            close()
        }.build().also { trophy = it }
    }
private var trophy: ImageVector? = null
