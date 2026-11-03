package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.roundToInt

private val IntroLightFallback = Color(0xFFFEFEFE)
private val IntroBrownFallback = Color(0xFF704118)
private val IntroTitleOrangeRed = Color(0xFFDF532E)
private val IntroTitleDarkBrown = Color(0xFF4A2508)
private val IntroSubtitleCream = Color(0xFFFAEBD7)
private val IntroHeadingGold = Color(0xFFFFD88A)
private val IntroBodyIvory = Color(0xFFFFF9F0)
private val IntroCardSurface = Color(0x382A1405)
private val IntroCardBorder = Color(0x4DFFE0B2)

/**
 * Second launch screen (IntroductionScreen) backed by the unmodified 02.png texture (`R.drawable.bg_intro_02`).
 *
 * Renders 02.png at an exact 1:1 uniform scale so its light-to-brown split line (y = 737 / 1435)
 * aligns precisely with the screen's upper light title region and lower brown introduction/guide region,
 * matching the visual hierarchy of 02-1.png.
 */
@Composable
fun IntroductionScreen(
    onEnterBookClicked: () -> Unit
) {
    val context = LocalContext.current
    val introBitmap = remember(context) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, R.drawable.bg_intro_02, opts)?.asImageBitmap()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(IntroBrownFallback)
                .clipToBounds()
                .testTag("introduction_screen")
        ) {
            val density = LocalDensity.current
            val screenW = maxWidth
            val screenH = maxHeight
            val screenWPx = with(density) { screenW.toPx() }
            val screenHPx = with(density) { screenH.toPx() }
            val isLandscape = screenW > screenH

            // In 02.png (1096 x 1435), the horizontal transition from the light top section
            // to the brown bottom section is at y = 737 (51.36% from top, 698px from bottom).
            // We align that exact transition line to splitFraction of the screen height using 1:1 uniform scaling.
            val splitFraction = if (isLandscape) 0.25f else 0.23f
            val splitYPx = screenHPx * splitFraction
            val splitYDp = with(density) { splitYPx.toDp() }

            val imgW = introBitmap?.width?.toFloat() ?: 1096f
            val imgH = introBitmap?.height?.toFloat() ?: 1435f
            val imgSplitY = imgH * (737f / 1435f)
            val imgBottomH = imgH - imgSplitY

            val uniformScale = maxOf(
                screenWPx / imgW,
                splitYPx / imgSplitY,
                (screenHPx - splitYPx) / imgBottomH
            )
            val drawW = (imgW * uniformScale).roundToInt()
            val drawH = (imgH * uniformScale).roundToInt()
            val drawLeft = ((screenWPx - drawW) / 2f).roundToInt()
            val drawTop = (splitYPx - imgSplitY * uniformScale).roundToInt()

            // Full-screen 1:1 aspect-ratio-preserved 02.png background
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("intro_background_image")
            ) {
                drawRect(color = IntroLightFallback, size = size.copy(height = splitYPx))
                drawRect(
                    color = IntroBrownFallback,
                    topLeft = Offset(0f, splitYPx),
                    size = size.copy(height = size.height - splitYPx)
                )
                introBitmap?.let { bmp ->
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset(0, 0),
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(drawLeft, drawTop),
                        dstSize = IntSize(drawW, drawH)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Light Section (Top of 02.png): Book Main Title positioned near the transition line (matching 02-1.png)
                val titleFontSp = (screenW.value * 0.078f).coerceIn(21f, 34f).sp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(splitYDp)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("intro_light_section"),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "چگونه در شطرنج برنده شویم",
                        color = IntroTitleOrangeRed,
                        fontSize = titleFontSp,
                        lineHeight = (titleFontSp.value * 1.2f).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            ),
                            shadow = Shadow(
                                color = IntroTitleDarkBrown.copy(alpha = 0.16f),
                                offset = Offset(0f, 2f),
                                blurRadius = 3f
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .testTag("intro_main_title")
                    )
                }

                // 2. Brown Section (Bottom of 02.png): Subtitle, Author, Welcome, Usage Guide, and Enter Book Button
                val subtitleFontSp = (screenW.value * 0.039f).coerceIn(12.5f, 16.5f).sp
                val authorFontSp = (screenW.value * 0.058f).coerceIn(17.5f, 25f).sp
                val sectionTitleFontSp = (screenW.value * 0.043f).coerceIn(14f, 18f).sp
                val bodyFontSp = (screenW.value * 0.0365f).coerceIn(12.5f, 15.5f).sp
                val isCompactHeight = screenH < 640.dp
                val verticalGap = if (isCompactHeight) 6.dp else 10.dp
                val cardInnerPadding = if (isCompactHeight) 10.dp else 12.dp

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp, vertical = if (isCompactHeight) 6.dp else 10.dp)
                        .testTag("intro_brown_section"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scrollable content area so on short screens or Landscape nothing is clipped while the CTA stays pinned
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(verticalGap)
                    ) {
                        // Subtitle & Author block matching 02-1.png hierarchy
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = "راهنمای جامع برای مبتدیان و فراتر از آن",
                                color = IntroSubtitleCream,
                                fontSize = subtitleFontSp,
                                lineHeight = (subtitleFontSp.value * 1.28f).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("intro_subtitle")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "لوی روزمن",
                                color = Color.White,
                                fontSize = authorFontSp,
                                lineHeight = (authorFontSp.value * 1.22f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("intro_author_name")
                            )
                        }

                        // Welcome Card
                        Surface(
                            color = IntroCardSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, IntroCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardInnerPadding),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "به دنیای شطرنج خوش آمدید",
                                    color = IntroHeadingGold,
                                    fontSize = sectionTitleFontSp,
                                    lineHeight = (sectionTitleFontSp.value * 1.28f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_welcome_title")
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "با این راهنمای گام‌به‌گام، شطرنج را یاد بگیرید و مهارت خود را با مثال‌ها و تمرین‌های تعاملی تقویت کنید.",
                                    color = IntroBodyIvory,
                                    fontSize = bodyFontSp,
                                    lineHeight = (bodyFontSp.value * 1.42f).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_welcome_body")
                                )
                            }
                        }

                        // Usage Guide Card
                        Surface(
                            color = IntroCardSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, IntroCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardInnerPadding),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "چطور از برنامه استفاده کنید؟",
                                    color = IntroHeadingGold,
                                    fontSize = sectionTitleFontSp,
                                    lineHeight = (sectionTitleFontSp.value * 1.28f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_guide_title")
                                )
                                HorizontalDivider(
                                    color = IntroCardBorder,
                                    thickness = 0.8.dp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                                Text(
                                    text = "۱. بخش موردنظر را از فهرست انتخاب کنید.",
                                    color = IntroBodyIvory,
                                    fontSize = bodyFontSp,
                                    lineHeight = (bodyFontSp.value * 1.38f).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_guide_step_1")
                                )
                                Text(
                                    text = "۲. توضیحات و حرکت مهره‌ها را دنبال کنید.",
                                    color = IntroBodyIvory,
                                    fontSize = bodyFontSp,
                                    lineHeight = (bodyFontSp.value * 1.38f).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_guide_step_2")
                                )
                                Text(
                                    text = "۳. تمرین‌ها را حل کنید و پاسخ خود را بررسی کنید.",
                                    color = IntroBodyIvory,
                                    fontSize = bodyFontSp,
                                    lineHeight = (bodyFontSp.value * 1.38f).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Right,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("intro_guide_step_3")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pinned bottom button in safe touch zone
                    Button(
                        onClick = onEnterBookClicked,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IntroTitleOrangeRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("intro_enter_book_btn")
                    ) {
                        Text(
                            text = "ورود به کتاب و شروع یادگیری",
                            fontSize = (screenW.value * 0.043f).coerceIn(14f, 17.5f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
