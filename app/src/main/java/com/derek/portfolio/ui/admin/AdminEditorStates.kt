package com.derek.portfolio.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.derek.portfolio.data.model.CareerHistory
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.ProjectCategory
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.model.SkillGroup
import com.derek.portfolio.data.repository.AuthUser
import com.derek.portfolio.data.visit.VisitRecord

// 관리자 편집기의 입력 상태.
//
// ViewModel 이 들고 있으므로 화면을 돌리거나 탭을 오가도 입력하던 내용이 남는다.
// 리스트 항목은 넣고 빼도 입력 칸이 제 자리를 지키도록 고정 key 를 들고 다닌다.

/** 여러 줄 입력을 문자열 리스트로 바꾼다. 빈 줄은 버린다. */
fun linesToList(text: String): List<String> =
    text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun listToLines(items: List<String>): String = items.joinToString("\n")

fun <T> SnapshotStateList<T>.move(index: Int, delta: Int) {
    val target = index + delta
    if (index !in indices || target !in indices) return
    add(target, removeAt(index))
}

sealed interface AdminGate {
    data object Loading : AdminGate
    data class Error(val message: String) : AdminGate
    data class NotAdmin(val user: AuthUser) : AdminGate
    class Ready(
        val profile: ProfileEditorState,
        val projects: ProjectsEditorState,
        val visits: VisitsState,
    ) : AdminGate
}

class SignInFormState {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

class GroupDraft(val key: Int, category: String = "", skillsText: String = "") {
    var category by mutableStateOf(category)
    var skillsText by mutableStateOf(skillsText)

    fun toModel() = SkillGroup(category = category.trim(), skills = linesToList(skillsText))
}

class CareerDraft(
    val key: Int,
    company: String = "",
    period: String = "",
    duration: String = "",
    role: String = "",
) {
    var company by mutableStateOf(company)
    var period by mutableStateOf(period)
    var duration by mutableStateOf(duration)
    var role by mutableStateOf(role)

    fun toModel() = CareerHistory(
        company = company.trim(),
        period = period.trim(),
        duration = duration.trim(),
        role = role.trim(),
    )
}

class ProfileEditorState(initial: ProfileData) {
    private var keySeed = 0

    var name by mutableStateOf(initial.name)
    var title by mutableStateOf(initial.title)
    var experience by mutableStateOf(initial.experience)
    var bio by mutableStateOf(initial.bio)
    var email by mutableStateOf(initial.email)
    var phone by mutableStateOf(initial.phone)
    var github by mutableStateOf(initial.github.orEmpty())
    var notion by mutableStateOf(initial.notion.orEmpty())
    var certificate by mutableStateOf(initial.certificate)
    var skillsText by mutableStateOf(listToLines(initial.skills))

    val direct: SnapshotStateList<GroupDraft> = mutableStateListOf<GroupDraft>().apply {
        addAll(initial.directSkillGroups.map { GroupDraft(keySeed++, it.category, listToLines(it.skills)) })
    }
    val ai: SnapshotStateList<GroupDraft> = mutableStateListOf<GroupDraft>().apply {
        addAll(initial.aiSkillGroups.map { GroupDraft(keySeed++, it.category, listToLines(it.skills)) })
    }
    val careers: SnapshotStateList<CareerDraft> = mutableStateListOf<CareerDraft>().apply {
        addAll(initial.careers.map { CareerDraft(keySeed++, it.company, it.period, it.duration, it.role) })
    }

    var saving by mutableStateOf(false)

    fun addGroup(list: SnapshotStateList<GroupDraft>) {
        list.add(GroupDraft(keySeed++))
    }

    /** 경력은 위에서부터 최신 순이라 새 항목을 맨 위에 넣는다. */
    fun addCareer() {
        careers.add(0, CareerDraft(keySeed++))
    }

    fun build() = ProfileData(
        name = name.trim(),
        title = title.trim(),
        experience = experience.trim(),
        bio = bio,
        email = email.trim(),
        phone = phone.trim(),
        github = github.trim().ifEmpty { null },
        notion = notion.trim().ifEmpty { null },
        certificate = certificate.trim(),
        directSkillGroups = direct.map { it.toModel() },
        aiSkillGroups = ai.map { it.toModel() },
        skills = linesToList(skillsText),
        careers = careers.map { it.toModel() },
    )
}

/** 편집 중인 프로젝트 한 건. [id] 가 null 이면 아직 Firestore 에 저장되지 않은 새 항목이다. */
class ProjectDraft(
    val key: Int,
    id: String? = null,
    title: String = "",
    company: String = "",
    description: String = "",
    period: String = "",
    role: String = "",
    techText: String = "",
    achieveText: String = "",
    imageUrl: String = "",
    githubUrl: String = "",
    liveUrl: String = "",
    appStoreUrl: String = "",
    playStoreUrl: String = "",
    category: ProjectCategory = ProjectCategory.MOBILE,
    expanded: Boolean = false,
) {
    var id by mutableStateOf(id)
    var title by mutableStateOf(title)
    var company by mutableStateOf(company)
    var description by mutableStateOf(description)
    var period by mutableStateOf(period)
    var role by mutableStateOf(role)
    var techText by mutableStateOf(techText)
    var achieveText by mutableStateOf(achieveText)
    var imageUrl by mutableStateOf(imageUrl)
    var githubUrl by mutableStateOf(githubUrl)
    var liveUrl by mutableStateOf(liveUrl)
    var appStoreUrl by mutableStateOf(appStoreUrl)
    var playStoreUrl by mutableStateOf(playStoreUrl)
    var category by mutableStateOf(category)
    var expanded by mutableStateOf(expanded)

    fun toModel(order: Int) = ProjectData(
        id = id,
        title = title.trim(),
        company = company.trim(),
        description = description.trim(),
        period = period.trim(),
        role = role.trim(),
        techStack = linesToList(techText),
        achievements = linesToList(achieveText),
        imageUrl = imageUrl.orNullIfBlank(),
        githubUrl = githubUrl.orNullIfBlank(),
        liveUrl = liveUrl.orNullIfBlank(),
        appStoreUrl = appStoreUrl.orNullIfBlank(),
        playStoreUrl = playStoreUrl.orNullIfBlank(),
        category = category,
        order = order,
    )

    private fun String.orNullIfBlank() = trim().ifEmpty { null }

    companion object {
        fun from(key: Int, project: ProjectData) = ProjectDraft(
            key = key,
            id = project.id,
            title = project.title,
            company = project.company,
            description = project.description,
            period = project.period,
            role = project.role,
            techText = listToLines(project.techStack),
            achieveText = listToLines(project.achievements),
            imageUrl = project.imageUrl.orEmpty(),
            githubUrl = project.githubUrl.orEmpty(),
            liveUrl = project.liveUrl.orEmpty(),
            appStoreUrl = project.appStoreUrl.orEmpty(),
            playStoreUrl = project.playStoreUrl.orEmpty(),
            category = project.category,
        )
    }
}

class ProjectsEditorState(initial: List<ProjectData>) {
    private var keySeed = 0

    val drafts: SnapshotStateList<ProjectDraft> = mutableStateListOf<ProjectDraft>().apply {
        addAll(initial.map { ProjectDraft.from(keySeed++, it) })
    }
    var savingAll by mutableStateOf(false)
    var savingKeys by mutableStateOf(emptySet<Int>())

    fun addNew() {
        drafts.add(0, ProjectDraft(key = keySeed++, expanded = true))
    }
}

class VisitsState {
    var records by mutableStateOf<List<VisitRecord>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var cleaning by mutableStateOf(false)

    /** 방문 기록 탭은 처음 열 때 한 번 읽는다. 이후는 새로고침 버튼이 맡는다. */
    var loadedOnce = false
}
