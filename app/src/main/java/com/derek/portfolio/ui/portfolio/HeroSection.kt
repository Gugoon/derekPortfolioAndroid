package com.derek.portfolio.ui.portfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.HorizontalDivider
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
import com.derek.portfolio.ui.components.openExternal
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.rememberPressState
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroSection(
    profile: ProfileData,
    isWide: Boolean,
    appearState: MutableTransitionState<Boolean>,
    onViewProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(AppDimens.RadiusMd)

    Box(
        modifier
            .fillMaxWidth()
            .background(AppColors.Background)
            .padding(horizontal = if (isWide) 80.dp else 20.dp, vertical = if (isWide) 70.dp else 40.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visibleState = appearState,
            enter = fadeIn(tween(1000, easing = LinearOutSlowInEasing)) +
                slideInVertically(tween(1000, easing = FastOutSlowInEasing)) { (it * 0.15f).toInt() },
        ) {
            Column(
                Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .paperShadow(shape)
                    .background(AppColors.SurfaceCard, shape)
                    .border(1.5.dp, AppColors.Border, shape),
            ) {
                // Header Tape / Document Index bar
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.PaperTape, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(AppColors.Accent, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "CURRICULUM VITAE • PORTFOLIO",
                            style = AppFonts.mono(11f, FontWeight.SemiBold, AppColors.TextMuted, letterSpacing = 1.5f),
                        )
                    }
                    Text("EDITION 2026", style = AppFonts.mono(11f, FontWeight.Medium, AppColors.TextMuted))
                }
                HorizontalDivider(thickness = 1.dp, color = AppColors.Border)

                // Main Sheet Content
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isWide) 48.dp else 24.dp, vertical = if (isWide) 48.dp else 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        profile.name,
                        textAlign = TextAlign.Center,
                        style = AppFonts.serif(if (isWide) 46f else 32f, FontWeight.ExtraBold, AppColors.TextPrimary, letterSpacing = -1f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        profile.title,
                        textAlign = TextAlign.Center,
                        style = AppFonts.sans(if (isWide) 18f else 15f, FontWeight.SemiBold, AppColors.Primary, letterSpacing = 0.5f),
                    )
                    Spacer(Modifier.height(20.dp))

                    // Double ruled line (Editorial separator)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(AppColors.Border),
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(AppColors.BorderLight),
                        )
                    }
                    Spacer(Modifier.height(24.dp))

                    Text(
                        profile.bio,
                        modifier = Modifier.widthIn(max = 680.dp),
                        textAlign = TextAlign.Center,
                        style = AppFonts.sans(15f, color = AppColors.TextSecondary, height = 1.85f),
                    )
                    Spacer(Modifier.height(36.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PaperSolidButton("프로젝트 열람하기", Icons.AutoMirrored.Rounded.LibraryBooks, onViewProjects)
                        profile.github?.let { url ->
                            PaperOutlineButton("GitHub", Icons.Rounded.Code) { context.openExternal(url) }
                        }
                        profile.notion?.let { url ->
                            PaperOutlineButton("Notion 상세 경력서", Icons.Rounded.Description) { context.openExternal(url) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperSolidButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val background by animateColorAsState(
        if (pressed) AppColors.PrimaryDark else AppColors.Primary,
        tween(150),
        label = "solidButton",
    )
    Row(
        Modifier
            .paperShadow(shape, glow = pressed)
            .background(background, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AppFonts.sans(14f, FontWeight.SemiBold, Color.White))
    }
}

@Composable
private fun PaperOutlineButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val background by animateColorAsState(
        if (pressed) AppColors.SurfaceHover else AppColors.SurfaceCard,
        tween(150),
        label = "outlineBg",
    )
    val foreground = if (pressed) AppColors.Primary else AppColors.TextSecondary
    Row(
        Modifier
            .background(background, shape)
            .border(1.2.dp, if (pressed) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AppFonts.sans(14f, FontWeight.SemiBold, foreground))
    }
}
