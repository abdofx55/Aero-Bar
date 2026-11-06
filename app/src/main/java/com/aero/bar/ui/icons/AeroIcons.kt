package com.aero.bar.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Pure Jetpack Compose Vector Icon Library for AeroBar (Design #1 Icon Set).
 * 100% vector-based (`ImageVector`) — zero bitmap/PNG dependencies, infinite resolution crispness.
 */
object AeroIcons {

    private fun buildIcon(
        name: String,
        viewportWidth: Float = 24f,
        viewportHeight: Float = 24f,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = viewportWidth.dp,
            defaultHeight = viewportHeight.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        ).apply(block).build()
    }

    private fun ImageVector.Builder.addFilledPath(
        pathData: String,
        fillType: PathFillType = PathFillType.NonZero
    ) {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            pathFillType = fillType,
            fill = SolidColor(Color.White)
        )
    }

    private fun ImageVector.Builder.addStrokedPath(
        pathData: String,
        strokeWidth: Float = 2.2f,
        strokeCap: StrokeCap = StrokeCap.Round,
        strokeJoin: StrokeJoin = StrokeJoin.Round
    ) {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = strokeWidth,
            strokeLineCap = strokeCap,
            strokeLineJoin = strokeJoin
        )
    }

    /**
     * 1. Back Button: Tesla Solid Chevron Left.
     */
    val IcNavBack: ImageVector by lazy {
        buildIcon("IcNavBack") {
            addFilledPath("M15.8 4.2a1.4 1.4 0 0 1 0 2L9.9 12l5.9 5.8a1.4 1.4 0 1 1-2 2l-6.9-6.8a1.4 1.4 0 0 1 0-2l6.9-6.8a1.4 1.4 0 0 1 2 0z")
        }
    }

    /**
     * 2. Home Button: Solid House with Arched Door Cutout (media_1793946185129.png).
     */
    val IcNavHome: ImageVector by lazy {
        buildIcon("IcNavHome") {
            addFilledPath("M12 3.2L3.5 10.2C3.18 10.46 3 10.85 3 11.26V19.2C3 20.2 3.8 21 4.8 21H9.5V15.2C9.5 14.54 10.04 14 10.7 14H13.3C13.96 14 14.5 14.54 14.5 15.2V21H19.2C20.2 21 21 20.2 21 19.2V11.26C21 10.85 20.82 10.46 20.5 10.2L12 3.2Z")
        }
    }

    /**
     * 3. Recents Button: Google Pixel Solid Card + Right Side Bar.
     */
    val IcNavRecents: ImageVector by lazy {
        buildIcon("IcNavRecents") {
            addFilledPath("M4 5.5A2.5 2.5 0 0 1 6.5 3h8A2.5 2.5 0 0 1 17 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-8A2.5 2.5 0 0 1 4 18.5v-13zM19.2 6a1.1 1.1 0 0 1 2.2 0v12a1.1 1.1 0 0 1-2.2 0V6z")
        }
    }

    /**
     * 4. All Apps Button: Tesla Solid 3x3 Rounded Grid.
     */
    val IcNavAllApps: ImageVector by lazy {
        buildIcon("IcNavAllApps") {
            val cells = listOf(3f, 10f, 17f)
            for (y in cells) {
                for (x in cells) {
                    addFilledPath(
                        "M${x + 1.2f} ${y}h1.6A1.2 1.2 0 0 1 ${x + 4f} ${y + 1.2f}v1.6A1.2 1.2 0 0 1 ${x + 2.8f} ${y + 4f}h-1.6A1.2 1.2 0 0 1 ${x} ${y + 2.8f}v-1.6A1.2 1.2 0 0 1 ${x + 1.2f} ${y}z"
                    )
                }
            }
        }
    }

    /**
     * 5. 360° Camera Circular Arrow Ring (Wraps around "360" text in the center).
     */
    val IcCamera360Orbit: ImageVector by lazy {
        buildIcon("IcCamera360Orbit", viewportWidth = 36f, viewportHeight = 36f) {
            addStrokedPath(
                "M30 18C30 24.6 24.6 30 18 30C11.4 30 6 24.6 6 18C6 11.4 11.4 6 18 6C22.2 6 25.9 8.15 28 11.4",
                strokeWidth = 2.4f
            )
            addStrokedPath("M28.5 6.5V12H23", strokeWidth = 2.4f)
        }
    }

    /**
     * 6. Fan Speed Icon: 4-Blade Precision Automotive Fan (Option A).
     */
    val IcFan4Blade: ImageVector by lazy {
        buildIcon("IcFan4Blade") {
            addFilledPath("M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0-7.5C9 2.5 7.5 5 8.5 8c.4 1.1 1.4 1.8 2.5 2h2c1.1-.2 2.1-.9 2.5-2 1-3-.5-5.5-3.5-5.5zm9.5 9.5c0-3-2.5-4.5-5.5-3.5-1.1.4-1.8 1.4-2 2.5v2c.2 1.1.9 2.1 2 2.5 3 1 5.5-.5 5.5-3.5zM12 21.5c3 0 4.5-2.5 3.5-5.5-.4-1.1-1.4-1.8-2.5-2h-2c-1.1.2-2.1.9-2.5 2-1 3 .5 5.5 3.5 5.5zM2.5 12c0 3 2.5 4.5 5.5 3.5 1.1-.4 1.8-1.4 2-2.5v-2c-.2-1.1-.9-2.1-2-2.5-3-1-5.5.5-5.5 3.5z")
        }
    }

    /**
     * 7. Full Climate & Seats Application Button: Solid Car Seat Profile + 2 Airflow Waves (Option 1).
     */
    val IcClimateSeatWaves: ImageVector by lazy {
        buildIcon("IcClimateSeatWaves", viewportWidth = 28f, viewportHeight = 26f) {
            // Headrest
            addFilledPath("M18.5 2.5C17 2.5 16 3.6 16 5C16 6.4 17 7.5 18.5 7.5H20C21.2 7.5 22 6.5 22 5C22 3.5 21.2 2.5 20 2.5H18.5Z")
            // Seat Backrest & Cushion
            addFilledPath("M17.5 9C16.2 9 15.2 10 14.8 11.4L13 18H7.5C6.1 18 5 19.1 5 20.5C5 21.9 6.1 23 7.5 23H15.5C16.8 23 17.9 22.1 18.3 20.8L20.8 11.8C21.2 10.3 20.1 9 18.5 9H17.5Z")
            // Two Airflow Waves
            addStrokedPath("M2 9.5C4 8 6 11 8 9.5C9.5 8.5 11 9.5 12 9.5", strokeWidth = 2.0f)
            addFilledPath("M11.5 7.5L14.5 9.5L11.5 11.5Z")
            addStrokedPath("M1.5 14.5C3.5 13 5.5 16 7.5 14.5C9 13.5 10.5 14.5 11.5 14.5", strokeWidth = 2.0f)
            addFilledPath("M11 12.5L14 14.5L11 16.5Z")
        }
    }

    /**
     * 8. Front Windshield Defrost: Standard Curved Windshield Sector + 3 Upward Wavy Heat Lines (Option 1).
     */
    val IcWindshieldDefrost: ImageVector by lazy {
        buildIcon("IcWindshieldDefrost", viewportWidth = 26f, viewportHeight = 24f) {
            addStrokedPath("M3 7.5C8.5 4.2 17.5 4.2 23 7.5L20 16.5C15.5 14.2 10.5 14.2 6 16.5L3 7.5Z", strokeWidth = 2.1f)
            addStrokedPath("M8.5 21C7.5 19 9.5 17 8.5 15C7.5 13 9.5 11 8.5 9", strokeWidth = 1.9f)
            addStrokedPath("M13 21C12 19 14 17 13 15C12 13 14 11 13 9", strokeWidth = 1.9f)
            addStrokedPath("M17.5 21C16.5 19 18.5 17 17.5 15C16.5 13 18.5 11 17.5 9", strokeWidth = 1.9f)
        }
    }

    /**
     * 9. Cabin Air Recirculation: Clean Trapezoidal Car Cabin Silhouette + Internal U-Turn Arrow (media_1793946180361.png).
     */
    val IcCarRecirculation: ImageVector by lazy {
        buildIcon("IcCarRecirculation", viewportWidth = 24f, viewportHeight = 24f) {
            // Sleek Car Cabin Silhouette
            addStrokedPath(
                "M3 14.5L5.2 9.2C5.7 8 6.8 7.2 8.1 7.2H15.2C16.5 7.2 17.7 8 18.2 9.2L20.8 14.5C21.3 15.5 20.6 16.8 19.5 16.8H4.3C3.2 16.8 2.5 15.5 3 14.5Z",
                strokeWidth = 2.1f,
                strokeJoin = StrokeJoin.Round
            )
            // Internal U-Turn Arrow
            addStrokedPath(
                "M9.5 13.8H14.5C15.5 13.8 16.2 13.1 16.2 12.1C16.2 11.1 15.5 10.4 14.5 10.4H10.5",
                strokeWidth = 1.9f,
                strokeCap = StrokeCap.Round
            )
            // Arrowhead
            addStrokedPath(
                "M11.8 8.9L9.8 10.4L11.8 11.9",
                strokeWidth = 1.9f,
                strokeCap = StrokeCap.Round,
                strokeJoin = StrokeJoin.Round
            )
        }
    }

    /**
     * Subtle Circular Dismiss X for Recent Apps Card (media_1793942363786.png).
     */
    val IcCloseX: ImageVector by lazy {
        buildIcon("IcCloseX", viewportWidth = 24f, viewportHeight = 24f) {
            addStrokedPath("M17 7L7 17M7 7l10 10", strokeWidth = 2.4f)
        }
    }

    /**
     * 10. Screen Standby / Display Off: Solid Crescent Moon (Option C).
     */
    val IcMoonStandby: ImageVector by lazy {
        buildIcon("IcMoonStandby") {
            addFilledPath("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
        }
    }

    /**
     * 11. Minus Stepper Icon (`−`).
     */
    val IcMinus: ImageVector by lazy {
        buildIcon("IcMinus") {
            addStrokedPath("M6 12H18", strokeWidth = 2.6f)
        }
    }

    /**
     * 12. Plus Stepper Icon (`+`).
     */
    val IcPlus: ImageVector by lazy {
        buildIcon("IcPlus") {
            addStrokedPath("M12 6V18M6 12H18", strokeWidth = 2.6f)
        }
    }
}
