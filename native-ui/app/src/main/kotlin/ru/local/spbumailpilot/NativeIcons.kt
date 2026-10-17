package ru.local.spbumailpilot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class AvatarPalette(val container: Color, val content: Color)

internal object NativeIcons {

    private val darkAvatarPalettes = listOf(
        AvatarPalette(Color(0xFF3B2654), Color(0xFFE5C7FF)), // Amethyst
        AvatarPalette(Color(0xFF1D3858), Color(0xFFAED4FF)), // Sapphire
        AvatarPalette(Color(0xFF1A4336), Color(0xFF9BE8C5)), // Emerald
        AvatarPalette(Color(0xFF52283C), Color(0xFFFFBDD6)), // Rose
        AvatarPalette(Color(0xFF4F361B), Color(0xFFFFD89B)), // Amber
        AvatarPalette(Color(0xFF1B424C), Color(0xFF9AE8F7)), // Teal
        AvatarPalette(Color(0xFF2B2D63), Color(0xFFC4C8FF))  // Indigo
    )

    fun avatarPalette(seed: String): AvatarPalette {
        val clean = seed.trim()
        if (clean.isEmpty()) return darkAvatarPalettes[0]
        val idx = abs(clean.hashCode()) % darkAvatarPalettes.size
        return darkAvatarPalettes[idx]
    }

    fun fileBadgePalette(ext: String): AvatarPalette = when (ext.uppercase()) {
        "DOC", "DOCX", "RTF", "ODT" -> AvatarPalette(Color(0xFF1E3A5F), Color(0xFF9AC8FF))
        "PPT", "PPTX", "KEY", "ODP" -> AvatarPalette(Color(0xFF542D1E), Color(0xFFFFB899))
        "XLS", "XLSX", "CSV", "ODS" -> AvatarPalette(Color(0xFF1B4332), Color(0xFF95E5B8))
        "PDF" -> AvatarPalette(Color(0xFF58222B), Color(0xFFFFB3BE))
        "ZIP", "RAR", "7Z", "TAR", "GZ" -> AvatarPalette(Color(0xFF4A381B), Color(0xFFF5D08A))
        else -> AvatarPalette(Color(0xFF352443), Color(0xFFCEACF3))
    }

    private fun vector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    val ArrowBack: ImageVector by lazy {
        vector("ArrowBack") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        vector("Search") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 11f)
                arcTo(8f, 8f, 0f, true, true, 3f, 11f)
                arcTo(8f, 8f, 0f, true, true, 19f, 11f)
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        vector("Refresh") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 4f)
                lineTo(21f, 10f)
                lineTo(15f, 10f)
                moveTo(20.49f, 15f)
                arcTo(9f, 9f, 0f, true, true, 18.36f, 5.64f)
                lineTo(21f, 10f)
            }
        }
    }

    val Mail: ImageVector by lazy {
        vector("Mail") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                quadTo(22f, 4f, 22f, 6f)
                lineTo(22f, 18f)
                quadTo(22f, 20f, 20f, 20f)
                lineTo(4f, 20f)
                quadTo(2f, 20f, 2f, 18f)
                lineTo(2f, 6f)
                quadTo(2f, 4f, 4f, 4f)
                close()
                moveTo(22f, 6f)
                lineTo(12f, 13f)
                lineTo(2f, 6f)
            }
        }
    }

    val Tasks: ImageVector by lazy {
        vector("Tasks") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 11f)
                lineTo(12f, 14f)
                lineTo(22f, 4f)
                moveTo(21f, 12f)
                lineTo(21f, 19f)
                quadTo(21f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                quadTo(3f, 21f, 3f, 19f)
                lineTo(3f, 5f)
                quadTo(3f, 3f, 5f, 3f)
                lineTo(16f, 3f)
            }
        }
    }

    val Calendar: ImageVector by lazy {
        vector("Calendar") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                quadTo(21f, 4f, 21f, 6f)
                lineTo(21f, 20f)
                quadTo(21f, 22f, 19f, 22f)
                lineTo(5f, 22f)
                quadTo(3f, 22f, 3f, 20f)
                lineTo(3f, 6f)
                quadTo(3f, 4f, 5f, 4f)
                close()
                moveTo(16f, 2f)
                lineTo(16f, 6f)
                moveTo(8f, 2f)
                lineTo(8f, 6f)
                moveTo(3f, 10f)
                lineTo(21f, 10f)
            }
        }
    }

    val Contacts: ImageVector by lazy {
        vector("Contacts") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 21f)
                lineTo(17f, 19f)
                quadTo(17f, 15f, 13f, 15f)
                lineTo(5f, 15f)
                quadTo(1f, 15f, 1f, 19f)
                lineTo(1f, 21f)
                moveTo(13f, 7f)
                arcTo(4f, 4f, 0f, true, true, 5f, 7f)
                arcTo(4f, 4f, 0f, true, true, 13f, 7f)
                moveTo(23f, 21f)
                lineTo(23f, 19f)
                quadTo(23f, 16f, 20f, 15.2f)
                moveTo(16f, 3.13f)
                quadTo(19f, 4f, 19f, 7f)
                quadTo(19f, 10f, 16f, 10.87f)
            }
        }
    }

    val MoreHorizontal: ImageVector by lazy {
        vector("MoreHorizontal") {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                moveTo(5f, 12f)
                arcTo(1.8f, 1.8f, 0f, true, true, 5f, 12.01f)
                close()
                moveTo(12f, 12f)
                arcTo(1.8f, 1.8f, 0f, true, true, 12f, 12.01f)
                close()
                moveTo(19f, 12f)
                arcTo(1.8f, 1.8f, 0f, true, true, 19f, 12.01f)
                close()
            }
        }
    }

    val ComposePen: ImageVector by lazy {
        vector("ComposePen") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 20f)
                lineTo(21f, 20f)
                moveTo(16.5f, 3.5f)
                quadTo(18.5f, 1.5f, 20.5f, 3.5f)
                quadTo(22.5f, 5.5f, 20.5f, 7.5f)
                lineTo(7f, 21f)
                lineTo(3f, 21f)
                lineTo(3f, 17f)
                lineTo(16.5f, 3.5f)
                close()
            }
        }
    }

    val Paperclip: ImageVector by lazy {
        vector("Paperclip") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21.44f, 11.05f)
                lineTo(12.25f, 20.24f)
                arcTo(5.5f, 5.5f, 0f, false, true, 4.47f, 12.46f)
                lineTo(13.66f, 3.27f)
                arcTo(3.5f, 3.5f, 0f, false, true, 18.61f, 8.22f)
                lineTo(9.41f, 17.41f)
                arcTo(1.5f, 1.5f, 0f, false, true, 7.29f, 15.29f)
                lineTo(15.78f, 6.8f)
            }
        }
    }

    val Send: ImageVector by lazy {
        vector("Send") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 2f)
                lineTo(11f, 13f)
                moveTo(22f, 2f)
                lineTo(15f, 22f)
                lineTo(11f, 13f)
                lineTo(2f, 9f)
                lineTo(22f, 2f)
                close()
            }
        }
    }

    val Clock: ImageVector by lazy {
        vector("Clock") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                moveTo(12f, 7f)
                lineTo(12f, 12f)
                lineTo(15.5f, 14f)
            }
        }
    }

    val Download: ImageVector by lazy {
        vector("Download") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 15f)
                lineTo(21f, 19f)
                quadTo(21f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                quadTo(3f, 21f, 3f, 19f)
                lineTo(3f, 15f)
                moveTo(7f, 10f)
                lineTo(12f, 15f)
                lineTo(17f, 10f)
                moveTo(12f, 15f)
                lineTo(12f, 3f)
            }
        }
    }

    val Share: ImageVector by lazy {
        vector("Share") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12f)
                lineTo(4f, 20f)
                quadTo(4f, 21f, 5f, 21f)
                lineTo(19f, 21f)
                quadTo(20f, 21f, 20f, 20f)
                lineTo(20f, 12f)
                moveTo(16f, 6f)
                lineTo(12f, 2f)
                lineTo(8f, 6f)
                moveTo(12f, 2f)
                lineTo(12f, 15f)
            }
        }
    }

    val Reply: ImageVector by lazy {
        vector("Reply") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 17f)
                lineTo(4f, 12f)
                lineTo(9f, 7f)
                moveTo(20f, 18f)
                lineTo(20f, 16f)
                quadTo(20f, 12f, 16f, 12f)
                lineTo(4f, 12f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        vector("Plus") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        vector("Trash") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                moveTo(19f, 6f)
                lineTo(18f, 20f)
                quadTo(18f, 21f, 16f, 21f)
                lineTo(8f, 21f)
                quadTo(6f, 21f, 6f, 20f)
                lineTo(5f, 6f)
                moveTo(9f, 6f)
                lineTo(9f, 4f)
                quadTo(9f, 3f, 10f, 3f)
                lineTo(14f, 3f)
                quadTo(15f, 3f, 15f, 4f)
                lineTo(15f, 6f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        vector("Copy") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 9f)
                lineTo(19f, 9f)
                quadTo(21f, 9f, 21f, 11f)
                lineTo(21f, 20f)
                quadTo(21f, 22f, 19f, 22f)
                lineTo(10f, 22f)
                quadTo(8f, 22f, 8f, 20f)
                lineTo(8f, 11f)
                quadTo(8f, 9f, 10f, 9f)
                close()
                moveTo(5f, 15f)
                lineTo(4f, 15f)
                quadTo(2f, 15f, 2f, 13f)
                lineTo(2f, 4f)
                quadTo(2f, 2f, 4f, 2f)
                lineTo(13f, 2f)
                quadTo(15f, 2f, 15f, 4f)
                lineTo(15f, 5f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        vector("Folder") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 19f)
                quadTo(22f, 21f, 20f, 21f)
                lineTo(4f, 21f)
                quadTo(2f, 21f, 2f, 19f)
                lineTo(2f, 5f)
                quadTo(2f, 3f, 4f, 3f)
                lineTo(9f, 3f)
                lineTo(11f, 6f)
                lineTo(20f, 6f)
                quadTo(22f, 6f, 22f, 8f)
                lineTo(22f, 19f)
                close()
            }
        }
    }
}
