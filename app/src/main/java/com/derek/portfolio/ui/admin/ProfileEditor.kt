package com.derek.portfolio.ui.admin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppFonts

@Composable
fun ProfileEditor(
    state: ProfileEditorState,
    scrollState: ScrollState,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        AdminCard(
            title = "기본 정보",
            subtitle = "히어로 영역 상단에 노출되는 이름 · 직함 · 소개 문구입니다.",
            trailing = { AdminButton(label = "프로필 저장", onClick = onSave, icon = Icons.Rounded.Save, busy = state.saving) },
        ) {
            AdminField("이름", state.name, { state.name = it }, required = true)
            AdminField("직함", state.title, { state.title = it })
            AdminField("경력", state.experience, { state.experience = it }, hint = "예: 10년 이상")
            AdminField("소개", state.bio, { state.bio = it }, hint = "줄바꿈이 그대로 반영됩니다", maxLines = 8)
        }

        AdminCard(title = "연락처 & 링크", subtitle = "비워 두면 해당 버튼이 사이트에서 사라집니다.") {
            AdminField("이메일", state.email, { state.email = it }, keyboardType = KeyboardType.Email)
            AdminField("연락처", state.phone, { state.phone = it }, keyboardType = KeyboardType.Phone)
            AdminField("GitHub URL", state.github, { state.github = it }, keyboardType = KeyboardType.Uri)
            AdminField("Notion 경력서 URL", state.notion, { state.notion = it }, keyboardType = KeyboardType.Uri)
            AdminField("자격증", state.certificate, { state.certificate = it })
        }

        AdminCard(title = "대표 기술 태그", subtitle = "한 줄에 하나씩 입력합니다. 히어로 하단 요약 태그로 쓰입니다.") {
            AdminField("기술 태그", state.skillsText, { state.skillsText = it }, maxLines = 10)
        }

        GroupCard(
            title = "직접 개발 기술 스택",
            subtitle = "스킬 섹션의 \"직접 개발\" 탭에 노출됩니다.",
            list = state.direct,
            onAdd = { state.addGroup(state.direct) },
        )
        GroupCard(
            title = "AI 활용 기술 스택",
            subtitle = "스킬 섹션의 \"AI 활용\" 탭에 노출됩니다.",
            list = state.ai,
            onAdd = { state.addGroup(state.ai) },
        )
        CareerCard(state)

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AdminButton(label = "프로필 저장", onClick = onSave, icon = Icons.Rounded.Save, busy = state.saving)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun GroupCard(
    title: String,
    subtitle: String,
    list: SnapshotStateList<GroupDraft>,
    onAdd: () -> Unit,
) {
    AdminCard(
        title = title,
        subtitle = subtitle,
        trailing = { AdminButton(label = "그룹 추가", onClick = onAdd, icon = Icons.Rounded.Add, outlined = true) },
    ) {
        if (list.isEmpty()) {
            Text("아직 그룹이 없습니다.", style = AppFonts.sans(13f, color = AppColors.TextMuted))
        }
        list.forEachIndexed { index, group ->
            key(group.key) {
                AdminListItem(
                    heading = "GROUP ${index + 1}",
                    actions = {
                        AdminIconButton(Icons.Rounded.ArrowUpward, "위로", if (index == 0) null else ({ list.move(index, -1) }))
                        AdminIconButton(Icons.Rounded.ArrowDownward, "아래로", if (index == list.lastIndex) null else ({ list.move(index, 1) }))
                        AdminIconButton(Icons.Rounded.DeleteOutline, "삭제", { list.remove(group) }, color = AppColors.Accent)
                    },
                ) {
                    AdminField("그룹 이름", group.category, { group.category = it })
                    AdminField("기술 목록", group.skillsText, { group.skillsText = it }, hint = "한 줄에 하나씩", maxLines = 8)
                }
            }
        }
    }
}

@Composable
private fun CareerCard(state: ProfileEditorState) {
    val careers = state.careers
    AdminCard(
        title = "경력 사항",
        subtitle = "위에서부터 최신 순으로 노출됩니다.",
        trailing = { AdminButton(label = "경력 추가", onClick = state::addCareer, icon = Icons.Rounded.Add, outlined = true) },
    ) {
        if (careers.isEmpty()) {
            Text("아직 경력이 없습니다.", style = AppFonts.sans(13f, color = AppColors.TextMuted))
        }
        careers.forEachIndexed { index, career ->
            key(career.key) {
                AdminListItem(
                    heading = "CAREER ${index + 1}",
                    actions = {
                        AdminIconButton(Icons.Rounded.ArrowUpward, "위로", if (index == 0) null else ({ careers.move(index, -1) }))
                        AdminIconButton(Icons.Rounded.ArrowDownward, "아래로", if (index == careers.lastIndex) null else ({ careers.move(index, 1) }))
                        AdminIconButton(Icons.Rounded.DeleteOutline, "삭제", { careers.remove(career) }, color = AppColors.Accent)
                    },
                ) {
                    AdminField("회사", career.company, { career.company = it })
                    AdminField("기간", career.period, { career.period = it }, hint = "예: 2024.03 - 2026.08")
                    AdminField("재직 기간", career.duration, { career.duration = it }, hint = "예: 2년 6개월")
                    AdminField("역할", career.role, { career.role = it })
                }
            }
        }
    }
}
