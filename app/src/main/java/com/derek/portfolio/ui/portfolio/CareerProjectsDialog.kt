package com.derek.portfolio.ui.portfolio

// 경력 이력 요약에서 회사를 고르면 그 회사에서 한 프로젝트를 모아 보여 주는 모달.
//
// 카드는 프로젝트 아카이브와 같은 ProjectSheet 를 쓴다. 같은 내용을 다른 모양으로
// 두 번 그리면 어느 쪽이 최신인지 헷갈리기 때문이다. 다만 여기서는 상세 실무 내역을
// 펼친 채로 연다 - 회사를 눌러 들어온 사람은 그걸 보러 온 것이다.

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.ui.components.screenHeightDp
import com.derek.portfolio.ui.components.screenWidthDp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareerProjectsDialog(
    career: CareerHistory,
    projects: List<ProjectData>,
    onDismiss: () -> Unit,
) {
    val compact = screenWidthDp() < 640
    val maxHeight = (screenHeightDp() - if (compact) 32 else 64).dp
    val shape = RoundedCornerShape(AppDimens.RadiusLg)
    val bodyPadding = if (compact) 16.dp else 24.dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = if (compact) 12.dp else 32.dp, vertical = if (compact) 16.dp else 32.dp)
                .widthIn(max = 780.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(AppColors.Surface, shape)
                .border(1.dp, AppColors.Border, shape)
                .clip(shape),
        ) {
            Row(
                Modifier.padding(start = if (compact) 16.dp else 24.dp, top = 18.dp, end = 12.dp, bottom = 18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            career.company,
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = AppFonts.serif(if (compact) 18f else 21f, FontWeight.Bold, AppColors.TextPrimary, height = 1.2f, letterSpacing = -0.5f),
                        )
                        if (career.duration.isNotBlank()) {
                            Text(
                                career.duration,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .background(AppColors.PaperBadge, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                style = AppFonts.mono(11f, FontWeight.SemiBold, AppColors.TextSecondary),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOf(career.period, career.role).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = AppFonts.sans(12.5f, color = AppColors.TextSecondary, height = 1.5f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "프로젝트 ${projects.size}건",
                        style = AppFonts.mono(11.5f, FontWeight.SemiBold, AppColors.Primary),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "닫기", tint = AppColors.TextMuted)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = AppColors.Border)

            if (projects.isEmpty()) {
                EmptyNotice()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(AppColors.Background),
                    contentPadding = PaddingValues(start = bodyPadding, end = bodyPadding, top = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(projects) { project ->
                        ProjectSheet(project = project, initiallyExpanded = true, showCompany = false)
                    }
                }
            }
        }
    }
}

/**
 * 회사는 눌렀는데 걸린 프로젝트가 없는 경우.
 *
 * 경력 항목은 프로젝트가 있을 때만 눌리도록 해 두었으므로 평소에는 보이지 않지만,
 * 관리자 화면에서 회사명이 바뀌어 짝이 어긋나면 이 화면이 나온다.
 */
@Composable
private fun EmptyNotice() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Background)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.FolderOff, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "등록된 프로젝트가 없습니다.",
            textAlign = TextAlign.Center,
            style = AppFonts.sans(13f, color = AppColors.TextSecondary),
        )
    }
}
