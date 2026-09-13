package com.derek.portfolio.ui.portfolio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.ui.components.SectionTitle
import com.derek.portfolio.ui.components.openExternal
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.rememberPressState
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts
import java.time.Year

@Composable
fun ContactSection(profile: ProfileData, isWide: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(horizontal = if (isWide) 80.dp else 20.dp, vertical = 70.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionTitle(title = "연락처 및 인적 사항", subtitle = "CONTACT & CREDENTIALS")
            Spacer(Modifier.height(24.dp))
            Text(
                "10년 이상의 모바일 및 풀스택 개발 경험을 바탕으로\n성공적인 프로젝트와 서비스를 함께 만들어갈 기회를 언제나 환영합니다.",
                textAlign = TextAlign.Center,
                style = AppFonts.sans(14.5f, color = AppColors.TextSecondary, height = 1.8f),
            )
            Spacer(Modifier.height(36.dp))

            // 관리자 화면 안내대로, 비워 둔 항목은 줄째 감춘다. 빈 줄을 누르면 빈 mailto: 가 열린다.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (profile.email.isNotBlank()) {
                    PaperContactRow(Icons.Rounded.AlternateEmail, "이메일 (Email)", profile.email) {
                        context.openExternal("mailto:${profile.email}")
                    }
                }
                if (profile.phone.isNotBlank()) {
                    PaperContactRow(Icons.Rounded.PhoneIphone, "연락처 (Phone)", profile.phone) {
                        context.openExternal("tel:${profile.phone}")
                    }
                }
                profile.github?.let { url ->
                    PaperContactRow(Icons.Rounded.Code, "GitHub 저장소", url) { context.openExternal(url) }
                }
                profile.notion?.let { url ->
                    PaperContactRow(Icons.AutoMirrored.Outlined.Article, "Notion 상세 경력기술서", "노션 경력기술서 열람하기 ↗") {
                        context.openExternal(url)
                    }
                }
                if (profile.certificate.isNotBlank()) {
                    PaperContactRow(Icons.Outlined.Verified, "공인 자격증 (Certification)", profile.certificate, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun PaperContactRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)?) {
    val clickable = onClick != null
    val (source, pressed) = rememberPressState()
    val highlight = pressed && clickable
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val background by animateColorAsState(
        if (highlight) Color(0xFFFBF8F0) else AppColors.SurfaceCard,
        tween(150),
        label = "contactBg",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (highlight) Modifier.paperShadow(shape) else Modifier)
            .background(background, shape)
            .border(1.2.dp, if (highlight) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(Color(0xFFF6F3EB), RoundedCornerShape(4.dp))
                .border(1.dp, AppColors.BorderLight, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlight) AppColors.Primary else AppColors.TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = AppFonts.mono(11f, FontWeight.SemiBold, AppColors.TextMuted))
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = AppFonts.sans(14f, FontWeight.Medium, if (highlight) AppColors.Primary else AppColors.TextPrimary),
            )
        }
        if (clickable) {
            Icon(
                Icons.Rounded.NorthEast,
                contentDescription = null,
                tint = if (pressed) AppColors.Primary else AppColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * @param onAdminClick 관리자 화면으로 들어가는 콜백. null 이면 진입 버튼을 아예 그리지 않는다.
 *   방문자에게는 의미 없는 버튼이라 눈에 띄지 않게 푸터 맨 아래에 둔다.
 */
@Composable
fun FooterSection(onAdminClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.Background)
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(80.dp)
                .height(1.dp)
                .background(AppColors.Border),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "© ${Year.now().value} 구보성 • PORTFOLIO ARCHIVE",
            style = AppFonts.mono(12f, FontWeight.Medium, AppColors.TextMuted),
        )
        Spacer(Modifier.height(4.dp))
        Text("Typeset with Jetpack Compose & Noto Serif KR", style = AppFonts.sans(11f, color = AppColors.TextMuted))
        if (onAdminClick != null) {
            Spacer(Modifier.height(18.dp))
            AdminEntryButton(onAdminClick)
        }
    }
}

/** 평소에는 배경에 묻히는 회색 글씨로 두고, 누르는 동안만 또렷해진다. */
@Composable
private fun AdminEntryButton(onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val color = if (pressed) AppColors.Primary else AppColors.TextMuted
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    Row(
        Modifier
            .background(if (pressed) AppColors.SurfaceCard else Color.Transparent, shape)
            .border(1.dp, if (pressed) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, onClickLabel = "관리자 화면 열기", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text("관리자", style = AppFonts.mono(11f, FontWeight.SemiBold, color))
    }
}
