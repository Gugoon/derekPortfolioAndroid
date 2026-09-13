package com.derek.portfolio.data.model

// 포트폴리오 콘텐츠 모델.
//
// 모든 값은 Firestore 에서 내려받아 채워지고, 네트워크 오류 등으로 읽기에 실패하면
// DefaultContent.kt 의 DefaultProfile / DefaultProjects 로 폴백한다. 즉 그 상수들은
// "초기 시드 데이터 + 최후의 폴백" 두 가지 역할을 한다.

// ---------------------------------------------------------------
// Firestore 맵 파싱 헬퍼
// 콘솔이나 관리자 화면에서 필드가 비거나 타입이 어긋나도 앱이 죽지 않도록,
// 모든 파싱은 기본값으로 흡수한다.
// ---------------------------------------------------------------

private fun str(value: Any?, fallback: String = ""): String = value as? String ?: fallback

private fun strOrNull(value: Any?): String? =
    (value as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun strList(value: Any?): List<String> =
    (value as? List<*>)
        ?.mapNotNull { it?.toString()?.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

private fun mapList(value: Any?): List<Map<String, Any?>> =
    (value as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { map -> map.entries.associate { (k, v) -> k.toString() to v } }
        ?: emptyList()

enum class ProjectCategory(val label: String) {
    MOBILE("모바일"),
    AI("AI / ML"),
    WEB("웹"),
    BACKEND("백엔드 / 데이터"),
    OTHER("기타");

    /** Firestore 에 저장하는 이름. 웹 앱(Dart enum.name)과 같은 소문자 표기다. */
    val storedName: String get() = name.lowercase()

    companion object {
        /** 저장된 문자열을 enum 으로 되돌린다. 알 수 없는 값은 [OTHER] 로 흡수한다. */
        fun fromName(name: Any?): ProjectCategory =
            entries.firstOrNull { it.storedName == name } ?: OTHER
    }
}

data class ProjectData(
    /**
     * Firestore 문서 ID. 관리자 화면에서 수정/삭제 대상을 지목할 때만 쓰이고,
     * 문서 본문에는 저장하지 않는다. 폴백 상수 데이터에서는 null 이다.
     */
    val id: String? = null,
    val title: String,
    val company: String,
    val description: String,
    val period: String,
    val techStack: List<String>,
    val role: String,
    val achievements: List<String>,
    val imageUrl: String? = null,
    val githubUrl: String? = null,
    val liveUrl: String? = null,
    val appStoreUrl: String? = null,
    val playStoreUrl: String? = null,
    val category: ProjectCategory,
    /** 목록 정렬 순서. 값이 작을수록 위에 노출된다. */
    val order: Int = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "company" to company,
        "description" to description,
        "period" to period,
        "techStack" to techStack,
        "role" to role,
        "achievements" to achievements,
        "imageUrl" to imageUrl,
        "githubUrl" to githubUrl,
        "liveUrl" to liveUrl,
        "appStoreUrl" to appStoreUrl,
        "playStoreUrl" to playStoreUrl,
        "category" to category.storedName,
        "order" to order,
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>) = ProjectData(
            id = id,
            title = str(map["title"]),
            company = str(map["company"]),
            description = str(map["description"]),
            period = str(map["period"]),
            techStack = strList(map["techStack"]),
            role = str(map["role"]),
            achievements = strList(map["achievements"]),
            imageUrl = strOrNull(map["imageUrl"]),
            githubUrl = strOrNull(map["githubUrl"]),
            liveUrl = strOrNull(map["liveUrl"]),
            appStoreUrl = strOrNull(map["appStoreUrl"]),
            playStoreUrl = strOrNull(map["playStoreUrl"]),
            category = ProjectCategory.fromName(map["category"]),
            order = (map["order"] as? Number)?.toInt() ?: 0,
        )
    }
}

data class CareerHistory(
    val company: String,
    val period: String,
    val duration: String,
    val role: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "company" to company,
        "period" to period,
        "duration" to duration,
        "role" to role,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>) = CareerHistory(
            company = str(map["company"]),
            period = str(map["period"]),
            duration = str(map["duration"]),
            role = str(map["role"]),
        )
    }
}

data class SkillGroup(
    val category: String,
    val skills: List<String>,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "category" to category,
        "skills" to skills,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>) = SkillGroup(
            category = str(map["category"]),
            skills = strList(map["skills"]),
        )
    }
}

data class ProfileData(
    val name: String,
    val title: String,
    val experience: String,
    val bio: String,
    val email: String,
    val phone: String,
    val github: String? = null,
    val notion: String? = null,
    val certificate: String,
    val directSkillGroups: List<SkillGroup>,
    val aiSkillGroups: List<SkillGroup>,
    val skills: List<String>,
    val careers: List<CareerHistory>,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "title" to title,
        "experience" to experience,
        "bio" to bio,
        "email" to email,
        "phone" to phone,
        "github" to github,
        "notion" to notion,
        "certificate" to certificate,
        "directSkillGroups" to directSkillGroups.map { it.toMap() },
        "aiSkillGroups" to aiSkillGroups.map { it.toMap() },
        "skills" to skills,
        "careers" to careers.map { it.toMap() },
    )

    companion object {
        fun fromMap(map: Map<String, Any?>) = ProfileData(
            name = str(map["name"]),
            title = str(map["title"]),
            experience = str(map["experience"]),
            bio = str(map["bio"]),
            email = str(map["email"]),
            phone = str(map["phone"]),
            github = strOrNull(map["github"]),
            notion = strOrNull(map["notion"]),
            certificate = str(map["certificate"]),
            directSkillGroups = mapList(map["directSkillGroups"]).map(SkillGroup::fromMap),
            aiSkillGroups = mapList(map["aiSkillGroups"]).map(SkillGroup::fromMap),
            skills = strList(map["skills"]),
            careers = mapList(map["careers"]).map(CareerHistory::fromMap),
        )
    }
}

/**
 * 화면 한 벌을 그리는 데 필요한 콘텐츠 묶음.
 *
 * data class 가 아니라 일반 클래스다. 이력서 PDF 캐시가 "같은 인스턴스인가" 로
 * 같은 콘텐츠인지 판별하므로, 내용이 같아도 새로 읽어 온 묶음은 다른 것으로 본다.
 */
class SiteContent(
    val profile: ProfileData,
    val projects: List<ProjectData>,
    /**
     * Firestore 에서 실제로 읽어온 값이면 true, 폴백 상수면 false.
     * 새로고침에 실패했을 때 멀쩡한 화면을 폴백으로 덮어쓰지 않는 데 쓴다.
     */
    val fromFirestore: Boolean,
) {
    companion object {
        /** 네트워크 실패나 최초 시드 이전 상태에서 쓰는 하드코딩 폴백. */
        val Fallback = SiteContent(
            profile = DefaultProfile,
            projects = DefaultProjects,
            fromFirestore = false,
        )
    }
}
