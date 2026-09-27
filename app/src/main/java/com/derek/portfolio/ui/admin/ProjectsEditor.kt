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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
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
    onReorder: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProjectDraft?>(null) }
    val drag = remember { ProjectDragState() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 끌고 있는 카드를 오프셋만큼 옮기고, 이웃 카드의 절반을 넘으면 목록에서 자리를 바꾼다.
    fun dragBy(dy: Float) {
        drag.offset += dy
        val drafts = state.drafts
        var index = drafts.indexOfFirst { it.key == drag.key }
        if (index < 0) return
        while (index < drafts.lastIndex) {
            val next = drag.heights[drafts[index + 1].key] ?: break
            if (drag.offset < next / 2f) break
            drafts.move(index, 1)
            drag.offset -= next
            drag.swapped = true
            index++
        }
        while (index > 0) {
            val prev = drag.heights[drafts[index - 1].key] ?: break
            if (drag.offset > -prev / 2f) break
            drafts.move(index, -1)
            drag.offset += prev
            drag.swapped = true
            index--
        }
    }

    // 손가락이 화면 위/아래 가장자리에 머물면 목록을 자동으로 스크롤한다.
    LaunchedEffect(drag.key) {
        if (drag.key == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val viewport = drag.viewport ?: continue
            val y = drag.fingerY
            val step = when {
                y < viewport.top + edge -> -maxStep * ((viewport.top + edge - y) / edge).coerceAtMost(1f)
                y > viewport.bottom - edge -> maxStep * ((y - viewport.bottom + edge) / edge).coerceAtMost(1f)
                else -> 0f
            }
            if (step != 0f) {
                val scrolled = scrollState.scrollBy(step)
                if (scrolled != 0f) dragBy(scrolled)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .onGloballyPositioned { drag.viewport = it.boundsInWindow() }
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        AdminCard(
            title = "프로젝트 ${state.drafts.size}건",
            subtitle = "카드를 눌러 펼치면 내용을 수정할 수 있습니다. 오른쪽 손잡이를 끌어 바꾼 순서는 즉시 저장됩니다.",
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
                val dragging = drag.key == draft.key
                // 끌던 카드가 도중에 지워지면 제스처 콜백이 오지 않으니 여기서 드래그를 끝낸다.
                DisposableEffect(draft) {
                    onDispose {
                        if (drag.key == draft.key) drag.reset()
                    }
                }
                ProjectCard(
                    draft = draft,
                    index = index,
                    busy = draft.key in state.savingKeys,
                    modifier = Modifier
                        .onSizeChanged { drag.heights[draft.key] = it.height.toFloat() }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                // 크기를 바꾸면 손잡이 좌표도 함께 바뀌어 카드가 손가락보다 덜 움직인다.
                                translationY = drag.offset
                                alpha = 0.92f
                            }
                        },
                    // 두 손가락으로 다른 카드를 동시에 끌면 먼저 잡은 카드만 따른다.
                    onDragStart = { fingerY ->
                        if (drag.key == null) {
                            drag.key = draft.key
                            drag.fingerY = fingerY
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { dy, fingerY ->
                        if (drag.key == draft.key) {
                            drag.fingerY = fingerY
                            dragBy(dy)
                        }
                    },
                    onDragEnd = {
                        if (drag.key == draft.key) {
                            val moved = drag.swapped
                            drag.reset()
                            if (moved) onReorder()
                        }
                    },
                    // 드래그를 할 수 없는 화면 낭독기 사용자를 위한 이동 동작.
                    onMoveBy = { delta ->
                        val index = state.drafts.indexOf(draft)
                        if (drag.key == null && index + delta in state.drafts.indices) {
                            state.drafts.move(index, delta)
                            onReorder()
                        }
                    },
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
    busy: Boolean,
    modifier: Modifier,
    onDragStart: (fingerY: Float) -> Unit,
    onDrag: (dy: Float, fingerY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveBy: (delta: Int) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val unsaved = draft.id == null
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // pointerInput 은 한 번 시작하면 재시작하지 않으므로 콜백은 늘 최신 것을 부른다.
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)

    Column(
        modifier
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
            AdminIconButton(Icons.Rounded.DeleteOutline, "삭제", onDelete, color = AppColors.Accent)
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = "끌어서 순서 바꾸기",
                tint = AppColors.TextMuted,
                modifier = Modifier
                    .onGloballyPositioned { handleCoords = it }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("위로 이동") { onMoveBy(-1); true },
                            CustomAccessibilityAction("아래로 이동") { onMoveBy(1); true },
                        )
                    }
                    .pointerInput(Unit) {
                        fun windowY(localY: Float) =
                            handleCoords?.localToWindow(Offset(0f, localY))?.y ?: 0f
                        detectDragGestures(
                            onDragStart = { currentDragStart(windowY(it.y)) },
                            onDrag = { change, amount ->
                                change.consume()
                                currentDrag(amount.y, windowY(change.position.y))
                            },
                            onDragEnd = { currentDragEnd() },
                            onDragCancel = { currentDragEnd() },
                        )
                    }
                    .size(36.dp)
                    .padding(8.dp),
            )
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

/** 드래그 중인 카드와, 자리 바꿈·자동 스크롤 계산에 쓰는 측정값. */
private class ProjectDragState {
    var key by mutableStateOf<Int?>(null)
    var offset by mutableFloatStateOf(0f)
    var swapped = false
    var fingerY = 0f
    var viewport: Rect? = null
    val heights = HashMap<Int, Float>()

    fun reset() {
        key = null
        offset = 0f
        swapped = false
    }
}
