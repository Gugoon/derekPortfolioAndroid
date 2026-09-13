package com.derek.portfolio.ui.portfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.derek.portfolio.data.model.ProjectCategory
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.ui.components.SectionTitle
import com.derek.portfolio.ui.components.openExternal
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.rememberPressState
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

/** 프로젝트 아카이브 제목과 카테고리 색인 탭. 카드들은 PortfolioScreen 의 목록이 이어 그린다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsHeader(
    projects: List<ProjectData>,
    selected: ProjectCategory?,
    onSelect: (ProjectCategory?) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.Background)
            .padding(start = horizontalPadding, end = horizontalPadding, top = 70.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionTitle(title = "프로젝트 아카이브", subtitle = "PROJECT PORTFOLIO ARCHIVE")
        Spacer(Modifier.height(32.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IndexFilterTab("전체 (${projects.size})", selected == null) { onSelect(null) }
            ProjectCategory.entries.forEach { category ->
                val count = projects.count { it.category == category }
                IndexFilterTab("${category.label} ($count)", selected == category) { onSelect(category) }
            }
        }
    }
}

@Composable
private fun IndexFilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    val background by animateColorAsState(
        when {
            selected -> AppColors.Primary
            pressed -> AppColors.SurfaceHover
            else -> AppColors.SurfaceCard
        },
        tween(150),
        label = "indexTab",
    )
    Text(
        label,
        modifier = Modifier
            .then(if (selected) Modifier.paperShadow(shape) else Modifier)
            .background(background, shape)
            .border(1.2.dp, if (selected) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = AppFonts.sans(
            13f,
            if (selected) FontWeight.Bold else FontWeight.Medium,
            if (selected) Color.White else AppColors.TextSecondary,
        ),
    )
}

private fun categoryColor(category: ProjectCategory): Color = when (category) {
    ProjectCategory.MOBILE -> Color(0xFF2E6347) // 세이지 잉크 그린
    ProjectCategory.AI -> Color(0xFFB33939) // 인주 레드
    ProjectCategory.WEB -> Color(0xFF1F3A60) // 만년필 블루
    ProjectCategory.BACKEND -> Color(0xFF8C531B) // 빈티지 브라운
    ProjectCategory.OTHER -> Color(0xFF5E3F71) // 고문서 퍼플
}

/**
 * 프로젝트 한 건을 담는 종이 시트.
 *
 * 프로젝트 아카이브 목록과 경력 항목 모달이 같은 카드를 쓴다.
 *
 * @param initiallyExpanded 상세 실무 내역을 펼친 채로 열지. 경력에서 골라 들어온
 *   모달은 그 내역을 보러 온 것이므로 펼쳐서 연다.
 * @param showCompany 회사명 배지 노출 여부. 경력 모달은 이미 제목이 그 회사다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectSheet(
    project: ProjectData,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    showCompany: Boolean = true,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(project.id, project.title) { mutableStateOf(initiallyExpanded) }
    val categoryColor = categoryColor(project.category)
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val badgeShape = RoundedCornerShape(3.dp)

    Column(
        modifier
            .fillMaxWidth()
            .paperShadow(shape)
            .background(AppColors.SurfaceCard, shape)
            .border(1.2.dp, AppColors.Border, shape)
            .clip(shape),
    ) {
        // Top category stripe
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(categoryColor),
        )

        Column(Modifier.padding(24.dp)) {
            // Header: Category, Company & Period
            Row(verticalAlignment = Alignment.Top) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        project.category.label,
                        modifier = Modifier
                            .background(categoryColor.copy(alpha = 0.08f), badgeShape)
                            .border(1.dp, categoryColor.copy(alpha = 0.31f), badgeShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = AppFonts.sans(11f, FontWeight.Bold, categoryColor),
                    )
                    if (showCompany) {
                        Text(
                            project.company,
                            modifier = Modifier
                                .background(Color(0xFFF5EFE6), badgeShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            style = AppFonts.sans(11f, FontWeight.SemiBold, AppColors.TextSecondary),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(project.period, style = AppFonts.mono(11f, FontWeight.Medium, AppColors.TextMuted))
            }
            Spacer(Modifier.height(14.dp))

            Text(project.title, style = AppFonts.serif(18f, FontWeight.Bold, AppColors.TextPrimary, height = 1.4f))
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    project.role,
                    modifier = Modifier.weight(1f),
                    style = AppFonts.sans(13f, FontWeight.SemiBold, AppColors.Primary),
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(project.description, style = AppFonts.sans(13.5f, color = AppColors.TextSecondary, height = 1.75f))
            Spacer(Modifier.height(16.dp))

            // Tech Stack (Typewriter / Mono style chips)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                project.techStack.forEach { tech ->
                    Text(
                        tech,
                        modifier = Modifier
                            .background(AppColors.PaperIconBox, badgeShape)
                            .border(1.dp, AppColors.BorderLight, badgeShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = AppFonts.mono(11f, FontWeight.Medium, AppColors.TextPrimary),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Expand / Collapse Achievements
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "상세 실무 내역 접기" else "상세 실무 구현 및 주요 성과",
                    style = AppFonts.sans(13f, FontWeight.SemiBold, AppColors.Primary),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
            ) {
                Column(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(AppColors.PaperChip, RoundedCornerShape(4.dp))
                        .border(1.dp, AppColors.RuleLine, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                ) {
                    project.achievements.forEach { achievement ->
                        Row(Modifier.padding(bottom = 8.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 9.dp)
                                    .size(4.dp)
                                    .background(AppColors.Accent, CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                achievement,
                                modifier = Modifier.weight(1f),
                                style = AppFonts.sans(13f, color = AppColors.TextSecondary, height = 1.65f),
                            )
                        }
                    }
                }
            }

            // Links
            val hasLinks = listOf(project.githubUrl, project.liveUrl, project.appStoreUrl, project.playStoreUrl)
                .any { it != null }
            if (hasLinks) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = AppColors.RuleLine)
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    project.appStoreUrl?.let { url ->
                        PaperLinkButton(Icons.Rounded.PhoneIphone, "App Store") { context.openExternal(url) }
                    }
                    project.playStoreUrl?.let { url ->
                        PaperLinkButton(Icons.Rounded.Android, "Google Play") { context.openExternal(url) }
                    }
                    if (project.liveUrl != null && project.appStoreUrl == null && project.playStoreUrl == null) {
                        PaperLinkButton(Icons.AutoMirrored.Rounded.OpenInNew, "서비스 바로가기") {
                            context.openExternal(project.liveUrl)
                        }
                    }
                    project.githubUrl?.let { url ->
                        PaperLinkButton(Icons.Rounded.Code, "GitHub") { context.openExternal(url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperLinkButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val (source, pressed) = rememberPressState()
    val shape = RoundedCornerShape(4.dp)
    val foreground = if (pressed) AppColors.Primary else AppColors.TextSecondary
    Row(
        Modifier
            .background(if (pressed) Color(0xFFF2ECE0) else AppColors.PaperTape, shape)
            .border(1.dp, if (pressed) AppColors.Primary else AppColors.Border, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = AppFonts.sans(12f, FontWeight.SemiBold, foreground))
    }
}
