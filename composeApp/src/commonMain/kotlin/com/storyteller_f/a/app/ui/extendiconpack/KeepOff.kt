package com.storyteller_f.a.app.ui.extendiconpack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.ui.ExtendIconPack

public val ExtendIconPack.KeepOff: ImageVector
    get() {
        if (_keepOff != null) {
            return _keepOff!!
        }
        _keepOff = Builder(name = "KeepOff", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f).apply {
            path(fill = SolidColor(Color(0xFF5f6368)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(680.0f, 120.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(-40.0f)
                verticalLineToRelative(327.0f)
                lineToRelative(-80.0f, -80.0f)
                verticalLineToRelative(-247.0f)
                lineTo(400.0f, 200.0f)
                verticalLineToRelative(87.0f)
                lineToRelative(-87.0f, -87.0f)
                lineToRelative(-33.0f, -33.0f)
                verticalLineToRelative(-47.0f)
                horizontalLineToRelative(400.0f)
                close()
                moveTo(480.0f, 920.0f)
                lineToRelative(-40.0f, -40.0f)
                verticalLineToRelative(-240.0f)
                lineTo(240.0f, 640.0f)
                verticalLineToRelative(-80.0f)
                lineToRelative(80.0f, -80.0f)
                verticalLineToRelative(-46.0f)
                lineTo(56.0f, 168.0f)
                lineToRelative(56.0f, -56.0f)
                lineToRelative(736.0f, 736.0f)
                lineToRelative(-58.0f, 56.0f)
                lineToRelative(-264.0f, -264.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(240.0f)
                lineToRelative(-40.0f, 40.0f)
                close()
                moveTo(354.0f, 560.0f)
                horizontalLineToRelative(92.0f)
                lineToRelative(-44.0f, -44.0f)
                lineToRelative(-2.0f, -2.0f)
                lineToRelative(-46.0f, 46.0f)
                close()
                moveTo(480.0f, 367.0f)
                close()
                moveTo(402.0f, 516.0f)
                close()
            }
        }
            .build()
        return _keepOff!!
    }

private var _keepOff: ImageVector? = null
