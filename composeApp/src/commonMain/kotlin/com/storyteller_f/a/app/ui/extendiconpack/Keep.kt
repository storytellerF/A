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

public val ExtendIconPack.Keep: ImageVector
    get() {
        if (_keep != null) {
            return _keep!!
        }
        _keep = Builder(name = "Keep", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
                viewportWidth = 960.0f, viewportHeight = 960.0f).apply {
            path(fill = SolidColor(Color(0xFF5f6368)), stroke = null, strokeLineWidth = 0.0f,
                    strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                    pathFillType = NonZero) {
                moveToRelative(640.0f, 480.0f)
                lineToRelative(80.0f, 80.0f)
                verticalLineToRelative(80.0f)
                lineTo(520.0f, 640.0f)
                verticalLineToRelative(240.0f)
                lineToRelative(-40.0f, 40.0f)
                lineToRelative(-40.0f, -40.0f)
                verticalLineToRelative(-240.0f)
                lineTo(240.0f, 640.0f)
                verticalLineToRelative(-80.0f)
                lineToRelative(80.0f, -80.0f)
                verticalLineToRelative(-280.0f)
                horizontalLineToRelative(-40.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(400.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(-40.0f)
                verticalLineToRelative(280.0f)
                close()
                moveTo(354.0f, 560.0f)
                horizontalLineToRelative(252.0f)
                lineToRelative(-46.0f, -46.0f)
                verticalLineToRelative(-314.0f)
                lineTo(400.0f, 200.0f)
                verticalLineToRelative(314.0f)
                lineToRelative(-46.0f, 46.0f)
                close()
                moveTo(480.0f, 560.0f)
                close()
            }
        }
        .build()
        return _keep!!
    }

private var _keep: ImageVector? = null
