package com.derek.portfolio.ui.portfolio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.model.SkillGroup
import com.derek.portfolio.data.model.projectsForCareer
import com.derek.portfolio.ui.components.SectionTitle
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.rememberPressState
import com.derek.portfolio.ui.components.screenWidthDp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

/** 0: 전체, 1: 직접 개발, 2: AI 활용 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillsSection(
    profile: ProfileData,
    projects: List<ProjectData>,
    isWide: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenCareer: (CareerHistory, List<ProjectData>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(horizontal = if (isWide) 80.dp else 20.dp, vertical = 70.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionTitle(title = "보유 기술 스택", subtitle = "SKILLS & COMPETENCIES")
            Spacer(Modifier.height(24.dp))
            Text(
                "10년 이상의 실무를 통해 직접 구축해온 모바일 네이티브 엔지니어링 기술과\n" +
                    "최신 AI Agent 및 Claude Vibe Coding을 활용한 클라우드/데이터 아키텍처 스택입니다.",
                textAlign = TextAlign.Center,
                style = AppFonts.sans(14.5f, color = AppColors.TextSecondary, height = 1.75f),
            )
            Spacer(Modifier.height(32.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkillFilterTab("전체 기술 스택", Icons.Rounded.AutoAwesomeMotion, selectedTab == 0) { onSelectTab(0) }
                SkillFilterTab("직접 개발 부문", Icons.Rounded.Code, selectedTab == 1) { onSelectTab(1) }
                SkillFilterTab("AI 활용 부문", Icons.Rounded.Psychology, selectedTab == 2) { onSelectTab(2) }
            }
            Spacer(Modifier.height(36.dp))

            if (selectedTab == 0 || selectedTab == 1) {
                SkillSectionSheet(
                    title = "직접 개발 부문 (Core Development)",
                    subtitle = "모바일 네이티브 (iOS / Android / Flutter) & 하드웨어 제어 솔루션",
                    badgeLabel = "CORE ENGINEERING",
                    accentColor = AppColors.Primary,
                    groups = profile.directSkillGroups,
                )
                if (selectedTab == 0) Spacer(Modifier.height(36.dp))
            }
            if (selectedTab == 0 || selectedTab == 2) {
                SkillSectionSheet(
                    title = "AI 활용 부문 (AI Integration & Vibe Coding)",
                    subtitle = "OpenAI RealTime 음성 에이전트, Gemini LLM & Claude 기반 풀스택 데이터 아키텍처",
                    badgeLabel = "AI & VIBE CODING",
                    accentColor = AppColors.Accent,
                    groups = profile.aiSkillGroups,
                )
            }

            Spacer(Modifier.height(64.dp))

            SectionTitle(title = "경력 이력 요약", subtitle = "PROFESSIONAL EXPERIENCE")
            Spacer(Modifier.height(32.dp))
            profile.careers.forEach { career ->
                val matched = remember(career, projects) { projectsForCareer(career, projects) }
                CareerRecord(career = career, projects = matched, onClick = { onOpenCareer(career, matched) })
            }
        }
    }
}

@Composable
private fun SkillFilterTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val background by animateColorAsState(
        when {
            selected -> AppColors.Primary
            pressed -> AppColors.SurfaceHover
            else -> AppColors.SurfaceCard
        },
        tween(150),
        label = "skillTab",
    )
    Row(
        Modifier
            .then(if (selected) Modifier.paperShadow(shape) else Modifier)
            .background(background, shape)
            .border(1.2.dp, if (selected) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else AppColors.TextSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = AppFonts.sans(
                13f,
                if (selected) FontWeight.Bold else FontWeight.Medium,
                if (selected) Color.White else AppColors.TextPrimary,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillSectionSheet(
    title: String,
    subtitle: String,
    badgeLabel: String,
    accentColor: Color,
    groups: List<SkillGroup>,
) {
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val badgeShape = RoundedCornerShape(3.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .paperShadow(shape)
            .background(AppColors.SurfaceCard, shape)
            .border(1.2.dp, AppColors.Border, shape)
            .clip(shape),
    ) {
        // Section Header Bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.PaperTape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .background(accentColor, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppFonts.serif(16f, FontWeight.Bold, AppColors.TextPrimary))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = AppFonts.sans(12f, color = AppColors.TextMuted))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                badgeLabel,
                modifier = Modifier
                    .background(accentColor.copy(alpha = 0.08f), badgeShape)
                    .border(1.dp, accentColor.copy(alpha = 0.31f), badgeShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                style = AppFonts.mono(10.5f, FontWeight.Bold, accentColor, letterSpacing = 0.5f),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = AppColors.Border)

        // Group List
        Column(Modifier.padding(20.dp)) {
            groups.forEachIndexed { index, group ->
                // 점은 첫 줄 높이에 맞춰 두고, 분류명이 길면 줄바꿈한다.
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(5.dp)
                            .background(accentColor, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        group.category,
                        modifier = Modifier.weight(1f),
                        style = AppFonts.serif(14f, FontWeight.Bold, AppColors.TextPrimary, height = 1.4f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.skills.forEach { PaperSkillChip(it, accentColor) }
                }
                if (index != groups.lastIndex) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = AppColors.RuleLine)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PaperSkillChip(label: String, accentColor: Color) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(3.dp)
    Text(
        label,
        modifier = Modifier
            .background(if (pressed) accentColor.copy(alpha = 0.08f) else AppColors.PaperChip, shape)
            .border(1.dp, if (pressed) accentColor else AppColors.Border, shape)
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = AppFonts.sans(
            12.5f,
            if (pressed) FontWeight.SemiBold else FontWeight.Medium,
            if (pressed) accentColor else AppColors.TextPrimary,
        ),
    )
}

/**
 * 경력 한 건.
 *
 * 이 회사에서 진행한 프로젝트가 없으면 눌러도 볼 것이 없으므로 누르기 자체를 막고
 * 건수 배지도 띄우지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CareerRecord(career: CareerHistory, projects: List<ProjectData>, onClick: () -> Unit) {
    val tappable = projects.isNotEmpty()
    val (source, pressed) = rememberPressState()
    val highlight = pressed && tappable
    // 좁은 화면에서는 화살표를 접는다. 누를 수 있다는 신호는 건수 배지가 이미 하고 있다.
    val width = screenWidthDp()
    val showChevron = tappable && width >= 480
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val badgeShape = RoundedCornerShape(3.dp)

    val background by animateColorAsState(
        if (highlight) AppColors.PaperPressed else AppColors.SurfaceCard,
        tween(150),
        label = "careerBg",
    )
    val borderColor by animateColorAsState(
        if (highlight) AppColors.Primary else AppColors.Border,
        tween(150),
        label = "careerBorder",
    )

    Row(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .then(if (highlight) Modifier.paperShadow(shape) else Modifier)
            .background(background, shape)
            .border(1.2.dp, borderColor, shape)
            .clip(shape)
            .then(
                if (tappable) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        onClickLabel = "${career.company} 프로젝트 ${projects.size}건 보기",
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(AppColors.PaperIconBox, RoundedCornerShape(4.dp))
                .border(1.dp, AppColors.BorderLight, RoundedCornerShape(4.dp))
                .padding(8.dp),
        ) {
            Icon(Icons.Rounded.BookmarkBorder, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    career.company,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = AppFonts.serif(16f, FontWeight.Bold, AppColors.TextPrimary),
                )
                Text(
                    career.duration,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .background(AppColors.PaperBadge, badgeShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    style = AppFonts.mono(11f, FontWeight.SemiBold, AppColors.TextSecondary),
                )
                if (tappable) {
                    Text(
                        "프로젝트 ${projects.size}",
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .background(if (highlight) AppColors.Primary else AppColors.SurfaceCard, badgeShape)
                            .border(1.dp, if (highlight) AppColors.Primary else AppColors.Border, badgeShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        style = AppFonts.mono(11f, FontWeight.SemiBold, if (highlight) Color.White else AppColors.Primary),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(career.role, style = AppFonts.sans(13f, color = AppColors.TextSecondary))
        }
        Spacer(Modifier.width(12.dp))
        // 좁은 화면에서는 기간이 줄바꿈되도록 폭을 묶는다. 묶지 않으면 회사/직무가 들어갈 자리가 사라진다.
        Text(
            career.period,
            modifier = Modifier.widthIn(max = if (width >= 600) 200.dp else 92.dp),
            textAlign = TextAlign.End,
            style = AppFonts.mono(12f, FontWeight.Medium, AppColors.TextMuted),
        )
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (highlight) AppColors.Primary else AppColors.TextMuted,
                modifier = Modifier
                    .offset(x = if (highlight) 3.dp else 0.dp)
                    .size(20.dp),
            )
        }
    }
}
