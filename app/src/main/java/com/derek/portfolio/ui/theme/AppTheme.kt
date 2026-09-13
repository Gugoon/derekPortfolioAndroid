package com.derek.portfolio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derek.portfolio.R

object AppColors {
    // Paper & Parchment Backgrounds
    val Background = Color(0xFFF6F3EC) // 미색 웜 코튼 페이퍼
    val Surface = Color(0xFFFCFBF8) // 깔끔한 백지 시트
    val SurfaceCard = Color(0xFFFFFFFF) // 카드 시트
    val SurfaceHover = Color(0xFFF0EBE0) // 부드러운 종이 터치
    val Border = Color(0xFFE2DDD3) // 섬세한 연필 재단선
    val BorderLight = Color(0xFFEFEAE1)
    val RuleLine = Color(0xFFE8E2D5) // 노트 줄눈

    // Ink Typography Colors
    val TextPrimary = Color(0xFF1E1C1A) // 진한 인쇄 잉크
    val TextSecondary = Color(0xFF48443F) // 만년필 챠콜 잉크
    val TextMuted = Color(0xFF868077) // 연필 스케치 그레이

    // Accent Inks & Stamp Colors
    val Primary = Color(0xFF1F3A60) // 클래식 만년필 블루블랙
    val PrimaryLight = Color(0xFF385E8E)
    val PrimaryDark = Color(0xFF13253E)
    val Accent = Color(0xFFB33939) // 붉은 인주 스탬프

    // 종이 위 소품들
    val PaperTape = Color(0xFFFAF7F0) // 헤더 띠
    val PaperChip = Color(0xFFFAF8F3)
    val PaperBadge = Color(0xFFF3ECE1)
    val PaperIconBox = Color(0xFFF7F4EE)
    val PaperPressed = Color(0xFFFBF9F4)

    /** 종이 그림자의 잉크 색. 알파는 쓰는 곳에서 준다. */
    val ShadowInk = Color(0xFF2E2419)
}

object AppDimens {
    // 인쇄물 특유의 단정하고 반듯한 모서리
    val RadiusSm = 4.dp
    val RadiusMd = 8.dp
    val RadiusLg = 12.dp
}

/**
 * 앱에 번들된 서브셋 폰트.
 *
 * KS X 1001 한글 2,350자 + 사용 음절로 서브셋한 파일이다(웹과 같은 파일). 서브셋에
 * 없는 글자는 Android 가 시스템 폰트로 알아서 대체하므로 두부(□)로 보이지 않는다.
 */
object AppFonts {
    val Sans = FontFamily(
        Font(R.font.noto_sans_kr_400, FontWeight.Normal),
        Font(R.font.noto_sans_kr_500, FontWeight.Medium),
        Font(R.font.noto_sans_kr_600, FontWeight.SemiBold),
        Font(R.font.noto_sans_kr_700, FontWeight.Bold),
    )
    val Serif = FontFamily(
        Font(R.font.noto_serif_kr_700, FontWeight.Bold),
        Font(R.font.noto_serif_kr_800, FontWeight.ExtraBold),
    )
    val Mono = FontFamily(
        Font(R.font.fira_code_500, FontWeight.Medium),
        Font(R.font.fira_code_600, FontWeight.SemiBold),
        Font(R.font.fira_code_700, FontWeight.Bold),
    )

    /**
     * @param height 글자 크기에 곱하는 줄 높이 배수. 웹(Flutter) TextStyle.height 와 같은 뜻이다.
     */
    fun sans(
        size: Float,
        weight: FontWeight = FontWeight.Normal,
        color: Color = Color.Unspecified,
        height: Float? = null,
        letterSpacing: Float? = null,
        decoration: TextDecoration? = null,
    ) = style(Sans, size, weight, color, height, letterSpacing, decoration)

    fun serif(
        size: Float,
        weight: FontWeight = FontWeight.Bold,
        color: Color = Color.Unspecified,
        height: Float? = null,
        letterSpacing: Float? = null,
    ) = style(Serif, size, weight, color, height, letterSpacing, null)

    fun mono(
        size: Float,
        weight: FontWeight = FontWeight.Medium,
        color: Color = Color.Unspecified,
        height: Float? = null,
        letterSpacing: Float? = null,
    ) = style(Mono, size, weight, color, height, letterSpacing, null)

    private fun style(
        family: FontFamily,
        size: Float,
        weight: FontWeight,
        color: Color,
        height: Float?,
        letterSpacing: Float?,
        decoration: TextDecoration?,
    ) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        lineHeight = height?.let { (size * it).sp } ?: TextUnit.Unspecified,
        letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
        textDecoration = decoration,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

private val PaperColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    secondary = AppColors.Accent,
    onSecondary = Color.White,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHover,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainerLowest = AppColors.SurfaceCard,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.SurfaceCard,
    surfaceContainerHigh = AppColors.SurfaceCard,
    surfaceContainerHighest = AppColors.Surface,
    outline = AppColors.Border,
    outlineVariant = AppColors.BorderLight,
    error = AppColors.Accent,
)

private fun TextStyle.withSans() = copy(fontFamily = AppFonts.Sans)

private val PaperTypography = Typography().let {
    Typography(
        displayLarge = it.displayLarge.withSans(),
        displayMedium = it.displayMedium.withSans(),
        displaySmall = it.displaySmall.withSans(),
        headlineLarge = it.headlineLarge.withSans(),
        headlineMedium = it.headlineMedium.withSans(),
        headlineSmall = it.headlineSmall.withSans(),
        titleLarge = it.titleLarge.withSans(),
        titleMedium = it.titleMedium.withSans(),
        titleSmall = it.titleSmall.withSans(),
        bodyLarge = it.bodyLarge.withSans(),
        bodyMedium = it.bodyMedium.withSans(),
        bodySmall = it.bodySmall.withSans(),
        labelLarge = it.labelLarge.withSans(),
        labelMedium = it.labelMedium.withSans(),
        labelSmall = it.labelSmall.withSans(),
    )
}

@Composable
fun PortfolioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        typography = PaperTypography,
        content = content,
    )
}
