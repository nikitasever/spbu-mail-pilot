package ru.local.spbumailpilot

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AvatarPalette(val container: Color, val content: Color)

internal object NativeIcons {

    private val avatarPalettes = listOf(
        0xFF6D4599,0xFF245EAD,0xFF006F68,0xFF3D7029,
        0xFF875008,0xFF9E3B68,0xFFA53E36,0xFF4B50A0
    ).map { AvatarPalette(Color(it),Color.White) }

    fun avatarPalette(seed: String): AvatarPalette {
        val clean=seed.trim().lowercase(java.util.Locale.ROOT)
        val hash=clean.hashCode()
        return avatarPalettes[Math.floorMod(hash xor (hash ushr 16),avatarPalettes.size)]
    }

    fun fileBadgePalette(ext: String): AvatarPalette = when (ext.uppercase()) {
        "DOC", "DOCX", "RTF", "ODT" -> AvatarPalette(Color(0xFF1E3A5F), Color(0xFF82BFFF))
        "PPT", "PPTX", "KEY", "ODP" -> AvatarPalette(Color(0xFF542D1E), Color(0xFFFFA985))
        "XLS", "XLSX", "CSV", "ODS" -> AvatarPalette(Color(0xFF1B4332), Color(0xFF7CE8AE))
        "PDF" -> AvatarPalette(Color(0xFF58222B), Color(0xFFFF9EAB))
        "ZIP", "RAR", "7Z", "TAR", "GZ" -> AvatarPalette(Color(0xFF4A381B), Color(0xFFF5D08A))
        else -> AvatarPalette(Color(0xFF2A2444), Color(0xFFC4B5FD))
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

    val ChevronDown: ImageVector by lazy {
        vector("ChevronDown") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
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
                moveTo(19f, 21f)
                lineTo(19f, 19f)
                quadTo(19f, 15f, 15f, 15f)
                lineTo(9f, 15f)
                quadTo(5f, 15f, 5f, 19f)
                lineTo(5f, 21f)
                moveTo(16f, 7f)
                arcTo(4f, 4f, 0f, true, true, 8f, 7f)
                arcTo(4f, 4f, 0f, true, true, 16f, 7f)
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

    val Quill: ImageVector by lazy {
        vector("Quill") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.24f, 12.24f)
                arcTo(6f, 6f, 0f, false, false, 11.75f, 3.75f)
                lineTo(5f, 10.5f)
                lineTo(5f, 19f)
                lineTo(13.5f, 19f)
                lineTo(20.24f, 12.24f)
                close()
                moveTo(16f, 8f)
                lineTo(2f, 22f)
                moveTo(17.5f, 15f)
                lineTo(9f, 15f)
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

    val Sliders: ImageVector by lazy {
        vector("Sliders") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 21f)
                lineTo(4f, 14f)
                moveTo(4f, 10f)
                lineTo(4f, 3f)
                moveTo(12f, 21f)
                lineTo(12f, 12f)
                moveTo(12f, 8f)
                lineTo(12f, 3f)
                moveTo(20f, 21f)
                lineTo(20f, 16f)
                moveTo(20f, 12f)
                lineTo(20f, 3f)
                moveTo(1f, 14f)
                lineTo(7f, 14f)
                moveTo(9f, 8f)
                lineTo(15f, 8f)
                moveTo(17f, 16f)
                lineTo(23f, 16f)
            }
        }
    }

    val Template: ImageVector by lazy {
        vector("Template") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 9f)
                lineTo(4f, 9f)
                close()
                moveTo(4f, 13f)
                lineTo(11f, 13f)
                lineTo(11f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(15f, 13f)
                lineTo(20f, 13f)
                moveTo(15f, 17f)
                lineTo(20f, 17f)
            }
        }
    }

    val Signature: ImageVector by lazy {
        vector("Signature") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 17f)
                quadTo(6f, 11f, 9f, 15f)
                quadTo(12f, 19f, 15f, 13f)
                quadTo(17f, 9f, 21f, 14f)
                moveTo(3f, 21f)
                lineTo(21f, 21f)
            }
        }
    }

    val Cloud: ImageVector by lazy {
        vector("Cloud") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 10f)
                lineTo(16.74f, 10f)
                arcTo(6f, 6f, 0f, true, false, 7f, 18f)
                lineTo(18f, 18f)
                arcTo(4f, 4f, 0f, false, false, 18f, 10f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        vector("Check") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }
    }

    val Close: ImageVector by lazy {
        vector("Close") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        MoreHorizontal
    }
}

private fun sphereInitialsFromSeed(seed: String): String {
    val clean = seed.trim()
    if (clean.isEmpty()) return "?"
    val parts = clean.split(Regex("[\\s._@-]+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(java.util.Locale.ROOT)
        parts.size == 1 -> parts[0].take(2).uppercase(java.util.Locale.ROOT)
        else -> "?"
    }
}

/**
 * 3D Glass-Metallic Sphere Avatar matching the flagship concept visual.
 */
@Composable
internal fun GlassSphereAvatar(
    letters: String = "",
    initials: String = letters,
    seed: String = initials.ifBlank { letters },
    size: Dp = 46.dp,
    fontSize: TextUnit = 14.sp,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    val raw = initials.ifBlank { letters }
    val displayLetters = if (raw.isNotBlank() && raw.length <= 2) {
        raw.uppercase(java.util.Locale.ROOT)
    } else {
        sphereInitialsFromSeed(seed.ifBlank { raw })
    }
    val palette = NativeIcons.avatarPalette(seed.ifBlank { displayLetters })
    val baseColor = if (selected) nativeColors.primary else palette.container
    val clickMod = if (onClick != null) {
        Modifier.clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(clickMod)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // Outer ambient 3D shadow / rim glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        nativeColors.primary.copy(alpha = if (selected) 0.55f else 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(center.x, center.y + r * 0.14f),
                    radius = r * 1.12f
                ),
                radius = r * 1.08f,
                center = Offset(center.x, center.y + r * 0.08f)
            )
            // Main 3D metallic-glass sphere body with off-center specular focal point
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.lerp(baseColor,Color.White,0.12f),
                        baseColor,
                        androidx.compose.ui.graphics.lerp(baseColor,Color.Black,0.25f)
                    ),
                    center = Offset(center.x - r * 0.35f, center.y - r * 0.38f),
                    radius = r * 1.38f
                ),
                radius = r * 0.94f,
                center = center
            )
            // Top-left glass specular highlight crescent
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - r * 0.32f, center.y - r * 0.36f),
                    radius = r * 0.58f
                ),
                radius = r * 0.92f,
                center = center
            )
            // Outer glass rim border (top-left bright, bottom-right violet bounce)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color(0xFF93A5CF).copy(alpha = 0.25f),
                        nativeColors.primary.copy(alpha = 0.55f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height)
                ),
                radius = r * 0.94f,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
        Text(
            text = if (selected) "✓" else displayLetters,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = palette.content
        )
    }
}

/**
 * Living 3D AI Particle Sphere (AI Orb) with rotating Fibonacci particle lattice & neon aura.
 */
@Composable
internal fun AiParticleOrb(
    label: String? = null,
    size: Dp = 44.dp,
    orbSize: Dp = size,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "aiOrb")
    val phase by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = (0.65f + 2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbPhase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val effectiveOrbSize = if (!label.isNullOrBlank()) orbSize.coerceAtLeast(42.dp) else orbSize.coerceAtLeast(32.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
        modifier = modifier.then(clickMod)
    ) {
        if (!label.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = nativeColors.primaryContainer.copy(alpha = 0.90f),
                border = BorderStroke(
                    0.8.dp,
                    Brush.horizontalGradient(
                        listOf(
                            nativeColors.primary.copy(alpha = 0.62f),
                            nativeColors.primary.copy(alpha = 0.40f)
                        )
                    )
                )
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(shape = CircleShape, color = nativeColors.primary, modifier = Modifier.size(5.dp)) {}
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = nativeColors.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
        Canvas(Modifier.size(effectiveOrbSize)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.50f
            val cy = h * 0.50f
            val r = (minOf(w, h) * 0.42f) * pulse

            // Deep violet-indigo nebula glow behind the 3D sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        nativeColors.primary.copy(alpha = 0.65f),
                        Color(0xFF6366F1).copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.48f
                ),
                radius = r * 1.48f,
                center = Offset(cx, cy)
            )

            // Inner 3D dark-glass sphere core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4C1D95).copy(alpha = 0.72f),
                        Color(0xFF1E1B4B).copy(alpha = 0.85f),
                        Color(0xFF0F0D1D).copy(alpha = 0.92f)
                    ),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r * 1.15f
                ),
                radius = r * 0.96f,
                center = Offset(cx, cy)
            )

            // Outer translucent energy shell
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFDDD6FE).copy(alpha = 0.80f),
                        nativeColors.primary.copy(alpha = 0.42f),
                        Color(0xFF38BDF8).copy(alpha = 0.65f)
                    ),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                ),
                radius = r * 0.96f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 3D Fibonacci sphere particle cloud
            val n = 96
            val goldenAngle = PI * (3.0 - sqrt(5.0))
            val cosRot = cos(phase.toDouble())
            val sinRot = sin(phase.toDouble())
            val tilt = 0.38
            val cosTilt = cos(tilt)
            val sinTilt = sin(tilt)

            for (i in 0 until n) {
                val y0 = 1.0 - (i.toDouble() / (n - 1).toDouble()) * 2.0
                val radiusAtY = sqrt((1.0 - y0 * y0).coerceAtLeast(0.0))
                val theta = goldenAngle * i
                val x0 = cos(theta) * radiusAtY
                val z0 = sin(theta) * radiusAtY

                // Rotate around Y then tilt around X
                val x1 = x0 * cosRot - z0 * sinRot
                val z1 = x0 * sinRot + z0 * cosRot
                val y2 = y0 * cosTilt - z1 * sinTilt
                val z2 = y0 * sinTilt + z1 * cosTilt

                // Depth factor in [0, 1]
                val depth = ((z2 + 1.0) * 0.5).toFloat().coerceIn(0f, 1f)
                val px = cx + (x1.toFloat() * r * 0.90f)
                val py = cy + (y2.toFloat() * r * 0.90f)
                val dotRadius = (0.7f + depth * 1.4f).dp.toPx()
                val dotColor = when {
                    depth > 0.75f -> Color(0xFFFFFFFF).copy(alpha = 0.96f)
                    depth > 0.42f -> nativeColors.primary.copy(alpha = 0.85f)
                    else -> Color(0xFF6366F1).copy(alpha = 0.40f)
                }
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * 3D Frosted Glass Squircle Icon Button (used in Top Bars & Toolbars).
 */
@Composable
internal fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    active: Boolean = accent,
    tint: Color? = null,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (active) {
                        listOf(
                            nativeColors.primary.copy(alpha = 0.42f),
                            nativeColors.primaryContainer.copy(alpha = 0.92f)
                        )
                    } else {
                        listOf(
                            nativeColors.surfaceContainerHigh.copy(alpha = 0.88f),
                            nativeColors.surface.copy(alpha = 0.92f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (active) {
                        listOf(
                            Color(0xFFD8B4FE).copy(alpha = 0.75f),
                            nativeColors.primary.copy(alpha = 0.45f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            nativeColors.primary.copy(alpha = 0.24f)
                        )
                    }
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: if (!enabled) nativeColors.outline else if (active) nativeColors.onPrimaryContainer else nativeColors.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Premium 3D Glowing Capsule FAB ("Написать" / "New Message") with specular glass rim.
 */
@Composable
internal fun GlowingCapsuleFab(
    text: String = "",
    label: String = text,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    val displayLabel = label.ifBlank { text }
    val shape = RoundedCornerShape(24.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .shadow(elevation = 16.dp, shape = shape, ambientColor = nativeColors.primary, spotColor = nativeColors.primary)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF9366FF),
                        nativeColors.primary,
                        Color(0xFF5B3CF0)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.62f),
                        nativeColors.primary.copy(alpha = 0.28f),
                        Color(0xFF38BDF8).copy(alpha = 0.45f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = displayLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Interactive 3D Zero Inbox Scene (floating glass island & 3D paper plane that tilts on drag).
 */
@Composable
internal fun ZeroInbox3DScene(
    title: String = "Zero Inbox",
    subtitle: String = "Все письма разобраны",
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { tiltX = 0f; tiltY = 0f },
                    onDragCancel = { tiltX = 0f; tiltY = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    tiltX = (tiltX + dragAmount.x * 0.15f).coerceIn(-25f, 25f)
                    tiltY = (tiltY + dragAmount.y * 0.15f).coerceIn(-25f, 25f)
                }
            }
    ) {
        Canvas(Modifier.size(140.dp)) {
            val cx = size.width / 2f + tiltX
            val cy = size.height / 2f + tiltY
            val r = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        nativeColors.primary.copy(alpha = 0.35f),
                        Color(0xFF38BDF8).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.4f
                ),
                radius = r * 1.4f,
                center = Offset(cx, cy)
            )
            // Floating 3D glass island disc
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        nativeColors.surfaceContainerHigh.copy(alpha = 0.9f),
                        nativeColors.surface.copy(alpha = 0.95f)
                    )
                ),
                topLeft = Offset(cx - r * 0.95f, cy + r * 0.25f),
                size = Size(r * 1.9f, r * 0.48f),
                cornerRadius = CornerRadius(r * 0.24f, r * 0.24f)
            )
            // 3D Glowing Paper Plane above the island
            val plane = Path().apply {
                moveTo(cx + r * 0.55f, cy - r * 0.45f)
                lineTo(cx - r * 0.55f, cy - r * 0.05f)
                lineTo(cx - r * 0.08f, cy + r * 0.12f)
                lineTo(cx + r * 0.15f, cy + r * 0.35f)
                close()
            }
            drawPath(
                path = plane,
                brush = Brush.linearGradient(
                    colors = listOf(nativeColors.primary, nativeColors.primary)
                )
            )
        }
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = nativeColors.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = nativeColors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 3D Glowing Circular Floating Action Button ("+") for Calendar, Contacts, and Tasks.
 */
@Composable
internal fun GlowingCircleFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val nativeColors=MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(54.dp)
            .shadow(elevation = 16.dp, shape = shape, ambientColor = nativeColors.primary, spotColor = nativeColors.primary)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF9366FF),
                        nativeColors.primary,
                        Color(0xFF5B3CF0)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.62f),
                        nativeColors.primary.copy(alpha = 0.28f),
                        Color(0xFF38BDF8).copy(alpha = 0.45f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Subtle cosmic bokeh and ambient violet light spots for the top header area.
 */
@Composable
internal fun CosmicHeaderGlow(modifier: Modifier = Modifier) {
    val nativeColors=MaterialTheme.colorScheme
    // Clean header area without overlapping bokeh circles or decorative particles
}

