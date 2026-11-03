package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Semantic content categories for the Reader page badge.
 * Note: Chessboard square colors remain strictly constant across the entire book
 * (#F0D9B5 light, #B58863 dark) and never change by content type.
 */
enum class SemanticBadgeCategory(
    val badgeId: String,
    val defaultTitleFa: String,
    val iconName: String
) {
    VISUAL_EXAMPLE(
        badgeId = "VISUAL_EXAMPLE",
        defaultTitleFa = "مثال تصویری",
        iconName = "AutoStories"
    ),
    MOVE_REPLAY(
        badgeId = "MOVE_REPLAY",
        defaultTitleFa = "بازپخش حرکات",
        iconName = "FastForward"
    ),
    INTERACTIVE_EXERCISE(
        badgeId = "INTERACTIVE_EXERCISE",
        defaultTitleFa = "تمرین تعاملی",
        iconName = "CheckCircle"
    ),
    POSITION_COMPARISON(
        badgeId = "POSITION_COMPARISON",
        defaultTitleFa = "مقایسه موقعیت",
        iconName = "Info"
    ),
    NEEDS_REVIEW(
        badgeId = "NEEDS_REVIEW",
        defaultTitleFa = "نیازمند بازبینی",
        iconName = "Warning"
    ),
    EDUCATIONAL_TEXT(
        badgeId = "EDUCATIONAL_TEXT",
        defaultTitleFa = "متن آموزشی",
        iconName = "AutoStories"
    );

    val icon: ImageVector
        get() = when (this) {
            VISUAL_EXAMPLE -> AppIcons.AutoStories
            MOVE_REPLAY -> AppIcons.FastForward
            INTERACTIVE_EXERCISE -> AppIcons.CheckCircle
            POSITION_COMPARISON -> AppIcons.Info
            NEEDS_REVIEW -> AppIcons.Warning
            EDUCATIONAL_TEXT -> AppIcons.AutoStories
        }
}

data class SemanticBadgePalette(
    val category: SemanticBadgeCategory,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val titleFa: String,
    val icon: ImageVector
)

/**
 * Centralized Design Tokens for the page-turning Reader experience.
 * Provides soft, low-contrast, eye-friendly semantic palettes for both Light and Dark themes,
 * while keeping chessboard square colors immutable.
 */
object ReaderDesignTokens {

    // Immutable chessboard square palette across all pages and pageTypes (Version 15)
    val BoardLightSquareColor = Color(0xFFECDBBA)
    val BoardDarkSquareColor = Color(0xFFC5A076)
    val BoardOuterBorderColor = Color(0xFF9C7B56)

    // Educational Move Trail & Capture indicator colors (Version 15)
    val MoveTrailPrimaryColor = Color(0xFF438F8A)
    val CaptureIndicatorRedColor = Color(0xFFD9383A)

    // Custom Vector Chess Piece palette (Version 15)
    val WhitePieceFillColor = Color(0xFFFAF3E3)
    val WhitePieceHighlightColor = Color(0xFFFFFDF8)
    val WhitePieceOutlineColor = Color(0xFF282019)
    val BlackPieceFillColor = Color(0xFF282522)
    val BlackPieceOutlineColor = Color(0xFF141210)
    val BlackPieceInnerDetailColor = Color(0xFF968C82)

    // Light Theme Semantic Badge Tokens (Soft, low-contrast surfaces for comfortable reading)
    val VisualExampleLightContainer = Color(0xFFE8F1FA)
    val VisualExampleLightContent = Color(0xFF1E4E79)
    val VisualExampleLightBorder = Color(0xFFB6D4F0)

    val MoveReplayLightContainer = Color(0xFFF2ECF9)
    val MoveReplayLightContent = Color(0xFF4A2E7E)
    val MoveReplayLightBorder = Color(0xFFD7C6F0)

    val InteractiveExerciseLightContainer = Color(0xFFE9F5EC)
    val InteractiveExerciseLightContent = Color(0xFF1E5E31)
    val InteractiveExerciseLightBorder = Color(0xFFBBE0C4)

    val PositionComparisonLightContainer = Color(0xFFFBF3DC)
    val PositionComparisonLightContent = Color(0xFF705100)
    val PositionComparisonLightBorder = Color(0xFFEAD18F)

    val NeedsReviewLightContainer = Color(0xFFFCEAEA)
    val NeedsReviewLightContent = Color(0xFF8C1D18)
    val NeedsReviewLightBorder = Color(0xFFF2B8B5)

    val EducationalTextLightContainer = Color(0xFFF1F3F5)
    val EducationalTextLightContent = Color(0xFF3E464E)
    val EducationalTextLightBorder = Color(0xFFD8DEE4)

    // Dark Theme Semantic Badge Tokens
    val VisualExampleDarkContainer = Color(0xFF172A3A)
    val VisualExampleDarkContent = Color(0xFF9AC6F0)
    val VisualExampleDarkBorder = Color(0xFF2B4C66)

    val MoveReplayDarkContainer = Color(0xFF281D3D)
    val MoveReplayDarkContent = Color(0xFFD0B8F8)
    val MoveReplayDarkBorder = Color(0xFF4A376E)

    val InteractiveExerciseDarkContainer = Color(0xFF163020)
    val InteractiveExerciseDarkContent = Color(0xFF9CE0B0)
    val InteractiveExerciseDarkBorder = Color(0xFF29543A)

    val PositionComparisonDarkContainer = Color(0xFF33270B)
    val PositionComparisonDarkContent = Color(0xFFF2D479)
    val PositionComparisonDarkBorder = Color(0xFF5C4716)

    val NeedsReviewDarkContainer = Color(0xFF3B1818)
    val NeedsReviewDarkContent = Color(0xFFF5B3B0)
    val NeedsReviewDarkBorder = Color(0xFF6B2D2D)

    val EducationalTextDarkContainer = Color(0xFF25282C)
    val EducationalTextDarkContent = Color(0xFFCFD6DE)
    val EducationalTextDarkBorder = Color(0xFF3E444C)

    fun paletteFor(
        category: SemanticBadgeCategory,
        darkTheme: Boolean = false,
        customTitleFa: String? = null
    ): SemanticBadgePalette {
        val title = customTitleFa ?: category.defaultTitleFa
        return if (!darkTheme) {
            when (category) {
                SemanticBadgeCategory.VISUAL_EXAMPLE -> SemanticBadgePalette(
                    category = category,
                    containerColor = VisualExampleLightContainer,
                    contentColor = VisualExampleLightContent,
                    borderColor = VisualExampleLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.MOVE_REPLAY -> SemanticBadgePalette(
                    category = category,
                    containerColor = MoveReplayLightContainer,
                    contentColor = MoveReplayLightContent,
                    borderColor = MoveReplayLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.INTERACTIVE_EXERCISE -> SemanticBadgePalette(
                    category = category,
                    containerColor = InteractiveExerciseLightContainer,
                    contentColor = InteractiveExerciseLightContent,
                    borderColor = InteractiveExerciseLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.POSITION_COMPARISON -> SemanticBadgePalette(
                    category = category,
                    containerColor = PositionComparisonLightContainer,
                    contentColor = PositionComparisonLightContent,
                    borderColor = PositionComparisonLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.NEEDS_REVIEW -> SemanticBadgePalette(
                    category = category,
                    containerColor = NeedsReviewLightContainer,
                    contentColor = NeedsReviewLightContent,
                    borderColor = NeedsReviewLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.EDUCATIONAL_TEXT -> SemanticBadgePalette(
                    category = category,
                    containerColor = EducationalTextLightContainer,
                    contentColor = EducationalTextLightContent,
                    borderColor = EducationalTextLightBorder,
                    titleFa = title,
                    icon = category.icon
                )
            }
        } else {
            when (category) {
                SemanticBadgeCategory.VISUAL_EXAMPLE -> SemanticBadgePalette(
                    category = category,
                    containerColor = VisualExampleDarkContainer,
                    contentColor = VisualExampleDarkContent,
                    borderColor = VisualExampleDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.MOVE_REPLAY -> SemanticBadgePalette(
                    category = category,
                    containerColor = MoveReplayDarkContainer,
                    contentColor = MoveReplayDarkContent,
                    borderColor = MoveReplayDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.INTERACTIVE_EXERCISE -> SemanticBadgePalette(
                    category = category,
                    containerColor = InteractiveExerciseDarkContainer,
                    contentColor = InteractiveExerciseDarkContent,
                    borderColor = InteractiveExerciseDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.POSITION_COMPARISON -> SemanticBadgePalette(
                    category = category,
                    containerColor = PositionComparisonDarkContainer,
                    contentColor = PositionComparisonDarkContent,
                    borderColor = PositionComparisonDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.NEEDS_REVIEW -> SemanticBadgePalette(
                    category = category,
                    containerColor = NeedsReviewDarkContainer,
                    contentColor = NeedsReviewDarkContent,
                    borderColor = NeedsReviewDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
                SemanticBadgeCategory.EDUCATIONAL_TEXT -> SemanticBadgePalette(
                    category = category,
                    containerColor = EducationalTextDarkContainer,
                    contentColor = EducationalTextDarkContent,
                    borderColor = EducationalTextDarkBorder,
                    titleFa = title,
                    icon = category.icon
                )
            }
        }
    }
}
