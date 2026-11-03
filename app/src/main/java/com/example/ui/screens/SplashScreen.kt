package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

private val CoverCreamBg = Color(0xFFFEEFD0)
private val CoverOrangeRed = Color(0xFFE05B3B)
private val CoverBadgeOrange = Color(0xFFDA4E2A)
private val CoverDarkMaroon = Color(0xFF3B0A0A)
private val CoverBottomBarDark = Color(0xFF280505)

/**
 * First launch screen (SplashScreen) backed by the unmodified 01.png illustration (`R.drawable.bg_splash_01`).
 *
 * Preserves the exact 1:1 original aspect ratio of 01.png without stretching or distortion,
 * keeps the King and chessboard inside the frame, and places all Persian typography as independent
 * Compose Text elements matching the reference layout of 01-2.png without covering the King or chessboard.
 */
@Composable
fun SplashScreen(
    onStartClicked: () -> Unit
) {
    val context = LocalContext.current
    val splashBitmap = remember(context) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, R.drawable.bg_splash_01, opts)?.asImageBitmap()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CoverCreamBg)
                .testTag("splash_screen")
        ) {
            val screenWidthDp = maxWidth
            val screenHeightDp = maxHeight
            val isLandscape = screenWidthDp > screenHeightDp

            if (!isLandscape) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Upper cover area containing 01.png and the book cover texts
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .testTag("splash_artwork_viewport")
                    ) {
                        val density = LocalDensity.current
                        val artW = maxWidth
                        val artH = maxHeight
                        val artWPx = with(density) { artW.toPx() }
                        val artHPx = with(density) { artH.toPx() }

                        // Original 01.png dimensions: 1096 x 1435 (aspect = 0.76376)
                        // In 01.png:
                        // - King top cross starts at y = 633 / 1435 (0.4411 from top, 0.5589 from bottom)
                        // - Chessboard horizon starts at y = 1041 / 1435 (0.7254 from top, 0.2746 from bottom)
                        // - King horizontal span is x = 0.383 .. 0.617
                        val imgW = splashBitmap?.width?.toFloat() ?: 1096f
                        val imgH = splashBitmap?.height?.toFloat() ?: 1435f
                        val imgAspect = imgW / imgH

                        // Scale 01.png with 1:1 uniform scale so its full width is preserved and
                        // the King's top cross stays at or below 50% of artH (leaving >=50% open cream sky above the King).
                        val heightAtFullWidth = artWPx / imgAspect
                        val maxHeightForKingClearance = (artHPx * 0.50f) / 0.5589f
                        val renderedImgHPx = minOf(heightAtFullWidth, maxHeightForKingClearance)
                        val renderedImgWPx = renderedImgHPx * imgAspect
                        val imgLeftPx = ((artWPx - renderedImgWPx) / 2f).roundToInt()
                        val imgTopPx = (artHPx - renderedImgHPx).roundToInt()

                        val kingTopYDp = with(density) { (artHPx - renderedImgHPx * 0.5589f).toDp() }
                        val horizonYDp = with(density) { (artHPx - renderedImgHPx * 0.2746f).toDp() }

                        // Render 01.png at exact 1:1 aspect ratio anchored to the bottom of the cover area,
                        // seamlessly extending the top sky strip (y = 0..16px of 01.png) above imgTopPx when artH > renderedImgH.
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onStartClicked
                                )
                                .testTag("splash_background_image")
                        ) {
                            drawRect(color = CoverCreamBg)
                            splashBitmap?.let { bmp ->
                                if (imgTopPx > 0) {
                                    drawImage(
                                        image = bmp,
                                        srcOffset = IntOffset(0, 0),
                                        srcSize = IntSize(bmp.width, 16.coerceAtMost(bmp.height)),
                                        dstOffset = IntOffset(imgLeftPx, 0),
                                        dstSize = IntSize(renderedImgWPx.roundToInt(), imgTopPx + 2)
                                    )
                                }
                                drawImage(
                                    image = bmp,
                                    srcOffset = IntOffset(0, 0),
                                    srcSize = IntSize(bmp.width, bmp.height),
                                    dstOffset = IntOffset(imgLeftPx, imgTopPx),
                                    dstSize = IntSize(renderedImgWPx.roundToInt(), renderedImgHPx.roundToInt())
                                )
                            }
                        }

                        // 1. Upper Title & Author Block (strictly above kingTopYDp so it never touches the King)
                        val headerZoneHeight = (kingTopYDp - 6.dp).coerceAtLeast(140.dp)
                        val authorFontSp = (artW.value * 0.066f).coerceIn(19f, 28f).sp
                        val aliasFontSp = (artW.value * 0.039f).coerceIn(12f, 16.5f).sp
                        val mainTitleFontSp = (artW.value * 0.090f).coerceIn(25f, 38f).sp

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerZoneHeight)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "لوی روزمن",
                                    color = CoverOrangeRed,
                                    fontSize = authorFontSp,
                                    lineHeight = (authorFontSp.value * 1.2f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier.testTag("splash_author_name")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "معروف به گاتهام‌چس",
                                    color = CoverDarkMaroon,
                                    fontSize = aliasFontSp,
                                    lineHeight = (aliasFontSp.value * 1.25f).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    modifier = Modifier.testTag("splash_author_alias")
                                )
                            }

                            Text(
                                text = "چگونه در شطرنج برنده شویم",
                                color = CoverOrangeRed,
                                fontSize = mainTitleFontSp,
                                lineHeight = (mainTitleFontSp.value * 1.22f).sp,
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
                                        color = CoverDarkMaroon.copy(alpha = 0.14f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 3f
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .testTag("splash_main_title")
                            )
                        }

                        // 2. Subtitle positioned to the right of the King and above the chessboard horizon (matching 01-2.png)
                        // In RTL layout, Alignment.TopStart is the physical RIGHT edge of the screen!
                        val sideColumnWidth = (artW * 0.35f).coerceAtLeast(104.dp)
                        val subtitleTopOffset = kingTopYDp + ((horizonYDp - kingTopYDp) * 0.14f).coerceAtLeast(4.dp)
                        val subtitleFontSp = (artW.value * 0.035f).coerceIn(11f, 14.5f).sp

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart) // Physical right side in RTL
                                .offset(y = subtitleTopOffset)
                                .width(sideColumnWidth)
                                .padding(start = 10.dp, end = 4.dp)
                        ) {
                            Text(
                                text = "راهنمای جامع برای مبتدیان و فراتر از آن",
                                color = CoverDarkMaroon,
                                fontSize = subtitleFontSp,
                                lineHeight = (subtitleFontSp.value * 1.32f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("splash_subtitle")
                            )
                        }

                        // 3. Circular YouTube Badge in the lower-right area matching 01-2.png (away from the central King)
                        val badgeDiameter = (artW * 0.25f).coerceIn(76.dp, 104.dp)
                        val badgeFontSp = (artW.value * 0.0285f).coerceIn(9.2f, 12f).sp
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart) // Physical bottom-right in RTL (matching 01-2.png)
                                .padding(start = 12.dp, bottom = 10.dp)
                                .size(badgeDiameter)
                                .background(color = CoverBadgeOrange, shape = CircleShape)
                                .padding(6.dp)
                                .testTag("splash_youtube_badge_container"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "مدرس شمارهٔ ۱ شطرنج در یوتیوب",
                                color = Color.White,
                                fontSize = badgeFontSp,
                                lineHeight = (badgeFontSp.value * 1.22f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier.testTag("splash_youtube_badge")
                            )
                        }
                    }

                    // Dedicated bottom CTA bar below the chessboard so the button never overlaps the King or chessboard
                    Surface(
                        color = CoverBottomBarDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("splash_bottom_bar")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onStartClicked,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoverOrangeRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("splash_start_btn")
                            ) {
                                Text(
                                    text = "برای شروع لمس کنید",
                                    fontSize = (screenWidthDp.value * 0.044f).coerceIn(14.5f, 18f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // Responsive Landscape layout: preserves 1:1 aspect ratio of 01.png on one side and full text/CTA on the other
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "لوی روزمن",
                                color = CoverOrangeRed,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("splash_author_name")
                            )
                            Text(
                                text = "معروف به گاتهام‌چس",
                                color = CoverDarkMaroon,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("splash_author_alias")
                            )
                        }

                        Text(
                            text = "چگونه در شطرنج برنده شویم",
                            color = CoverOrangeRed,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("splash_main_title")
                        )

                        Text(
                            text = "راهنمای جامع برای مبتدیان و فراتر از آن",
                            color = CoverDarkMaroon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("splash_subtitle")
                        )

                        Surface(
                            color = CoverBadgeOrange,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("splash_youtube_badge_container")
                        ) {
                            Text(
                                text = "مدرس شمارهٔ ۱ شطرنج در یوتیوب",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("splash_youtube_badge")
                            )
                        }

                        Button(
                            onClick = onStartClicked,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoverOrangeRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("splash_start_btn")
                        ) {
                            Text(
                                text = "برای شروع لمس کنید",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .testTag("splash_artwork_viewport"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val density = LocalDensity.current
                        val boxWPx = with(density) { maxWidth.toPx() }
                        val boxHPx = with(density) { maxHeight.toPx() }
                        val imgW = splashBitmap?.width?.toFloat() ?: 1096f
                        val imgH = splashBitmap?.height?.toFloat() ?: 1435f
                        val s = minOf(boxWPx / imgW, boxHPx / imgH)
                        val drawW = (imgW * s).roundToInt()
                        val drawH = (imgH * s).roundToInt()
                        val left = ((boxWPx - drawW) / 2f).roundToInt()
                        val top = (boxHPx - drawH).roundToInt()

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("splash_background_image")
                        ) {
                            drawRect(color = CoverCreamBg)
                            splashBitmap?.let { bmp ->
                                drawImage(
                                    image = bmp,
                                    srcOffset = IntOffset(0, 0),
                                    srcSize = IntSize(bmp.width, bmp.height),
                                    dstOffset = IntOffset(left, top),
                                    dstSize = IntSize(drawW, drawH)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
