package com.derek.portfolio.ui.admin

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derek.portfolio.data.model.ProjectCategory
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsEditor(
    state: ProjectsEditorState,
    scrollState: ScrollState,
    onSaveOne: (ProjectDraft) -> Unit,
    onSaveAll: () -> Unit,
    onDelete: (ProjectDraft) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProjectDraft?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        AdminCard(
            title = "프로젝트 ${state.drafts.size}건",
            subtitle = "카드를 눌러 펼치면 내용을 수정할 수 있습니다. 위/아래 화살표로 바꾼 순서는 즉시 저장됩니다.",
            trailing = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdminButton(label = "새 프로젝트", onClick = state::addNew, icon = Icons.Rounded.Add, outlined = true)
                    AdminButton(label = "전체 저장", onClick = onSaveAll, icon = Icons.Rounded.Save, busy = state.savingAll)
                }
            },
        )

        state.drafts.forEachIndexed { index, draft ->
            key(draft.key) {
                ProjectCard(
                    draft = draft,
                    index = index,
                    isLast = index == state.drafts.lastIndex,
                    busy = draft.key in state.savingKeys,
                    onMove = onMove,
                    onDelete = { pendingDelete = draft },
                    onSave = { onSaveOne(draft) },
                )
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = AppColors.SurfaceCard,
            title = { Text("프로젝트 삭제") },
            text = {
                Text(
                    "\"${draft.title.ifEmpty { "제목 없음" }}\" 을(를) 삭제합니다.\n이 작업은 되돌릴 수 없습니다.",
                    style = AppFonts.sans(14f, color = AppColors.TextSecondary, height = 1.6f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(draft)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Accent),
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    draft: ProjectDraft,
    index: Int,
    isLast: Boolean,
    busy: Boolean,
    onMove: (index: Int, delta: Int) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val unsaved = draft.id == null

    Column(
        Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .paperShadow(shape)
            .background(AppColors.SurfaceCard, shape)
            .border(1.2.dp, if (unsaved) AppColors.Accent else AppColors.Border, shape)
            .clip(shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { draft.expanded = !draft.expanded }
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (draft.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (draft.expanded) "접기" else "펼치기",
                tint = AppColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    draft.title.ifEmpty { "(제목 없음)" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppFonts.sans(14.5f, FontWeight.Bold, AppColors.TextPrimary),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append("${index + 1}. ${draft.category.label}")
                        if (draft.company.isNotEmpty()) append(" · ${draft.company}")
                        if (unsaved) append("  · 저장 안 됨")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppFonts.mono(11f, color = if (unsaved) AppColors.Accent else AppColors.TextMuted),
                )
            }
            AdminIconButton(Icons.Rounded.ArrowUpward, "위로", if (index == 0) null else ({ onMove(index, -1) }))
            AdminIconButton(Icons.Rounded.ArrowDownward, "아래로", if (isLast) null else ({ onMove(index, 1) }))
            AdminIconButton(Icons.Rounded.DeleteOutline, "삭제", onDelete, color = AppColors.Accent)
        }

        if (draft.expanded) {
            HorizontalDivider(thickness = 1.dp, color = AppColors.BorderLight)
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
                AdminField("제목", draft.title, { draft.title = it }, required = true)
                AdminField("소속 / 회사", draft.company, { draft.company = it })
                AdminField("기간", draft.period, { draft.period = it }, hint = "예: 2025.10 - 2026.02")
                AdminField("역할", draft.role, { draft.role = it })
                CategoryField(draft)
                AdminField("설명", draft.description, { draft.description = it }, maxLines = 5)
                AdminField("기술 스택", draft.techText, { draft.techText = it }, hint = "한 줄에 하나씩", maxLines = 8)
                AdminField("주요 성과", draft.achieveText, { draft.achieveText = it }, hint = "한 줄에 하나씩", maxLines = 8)
                AdminField("대표 링크 (liveUrl)", draft.liveUrl, { draft.liveUrl = it }, hint = "비워 두면 버튼이 숨겨집니다", keyboardType = KeyboardType.Uri)
                AdminField("App Store URL", draft.appStoreUrl, { draft.appStoreUrl = it }, keyboardType = KeyboardType.Uri)
                AdminField("Play Store URL", draft.playStoreUrl, { draft.playStoreUrl = it }, keyboardType = KeyboardType.Uri)
                AdminField("GitHub URL", draft.githubUrl, { draft.githubUrl = it }, keyboardType = KeyboardType.Uri)
                AdminField("이미지 URL", draft.imageUrl, { draft.imageUrl = it }, keyboardType = KeyboardType.Uri)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AdminButton(label = "이 프로젝트 저장", onClick = onSave, icon = Icons.Rounded.Save, busy = busy)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryField(draft: ProjectDraft) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text("카테고리", style = AppFonts.sans(12.5f, FontWeight.SemiBold, AppColors.TextSecondary))
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectCategory.entries.forEach { category ->
                AdminChoiceChip(
                    label = category.label,
                    selected = draft.category == category,
                    onClick = { draft.category = category },
                )
            }
        }
    }
}
