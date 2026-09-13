package com.derek.portfolio.data.repository

import com.derek.portfolio.data.model.DefaultProfile
import com.derek.portfolio.data.model.DefaultProjects
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.ProjectData
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.data.remote.FirestoreDocument
import com.derek.portfolio.data.remote.FirestoreRestClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 읽기가 제한 시간 안에 끝나지 않았다. */
class ReadTimeoutException(what: String, seconds: Long) :
    IOException("$what 읽기가 ${seconds}초 안에 끝나지 않았습니다.")

/**
 * Firestore 컬렉션 구조
 *
 *   site/profile          → ProfileData 문서 하나
 *   projects/{autoId}     → ProjectData 문서, order 오름차순 정렬
 *   admins/{uid}          → 편집 권한이 있는 계정 (문서가 있기만 하면 된다)
 */
@Singleton
class ContentRepository @Inject constructor(
    private val firestore: FirestoreRestClient,
    private val auth: AuthRepository,
) {
    // ------------------------------------------------------------------
    // 읽기
    // ------------------------------------------------------------------

    /**
     * 프로필과 프로젝트를 한 번에 읽어온다.
     * 어떤 이유로든 실패하면 [SiteContent.Fallback] 을 돌려주며 예외를 던지지 않는다.
     */
    suspend fun load(): SiteContent = loadOrNull() ?: SiteContent.Fallback

    /**
     * [load] 와 같지만 읽기에 실패하면 null 을 돌려준다.
     *
     * 폴백만 돌려받으면 '아직 시드 전이라 폴백' 과 '읽지 못해서 폴백' 을 구분할 수 없다.
     * 이미 화면을 그리고 있는 쪽은 뒤의 경우에만 지금 내용을 지켜야 하므로 이것을 쓴다.
     */
    suspend fun loadOrNull(): SiteContent? = try {
        coroutineScope {
            val profileRead = async { limited("프로필") { firestore.get(PROFILE_PATH) } }
            val projectsRead = async { limited("프로젝트") { firestore.runQuery(projectsQuery()) } }
            val profileDoc = profileRead.await()
            val projects = projectsRead.await().map(::toProject)

            // 문서가 하나도 없으면 아직 시드 전이다. 빈 포트폴리오 대신 폴백을 보여준다.
            // 프로필은 있는데 프로젝트만 없으면 관리자가 모두 지운 것이므로 빈 목록 그대로 둔다.
            if (profileDoc == null && projects.isEmpty()) {
                SiteContent.Fallback
            } else {
                SiteContent(
                    profile = profileDoc?.let { ProfileData.fromMap(it.fields) } ?: DefaultProfile,
                    projects = projects,
                    fromFirestore = true,
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** 관리자 화면 전용. 폴백으로 감추지 않고 실패를 그대로 던진다. */
    suspend fun loadProfileStrict(): ProfileData {
        val doc = limited("프로필") { firestore.get(PROFILE_PATH) }
        return doc?.let { ProfileData.fromMap(it.fields) } ?: DefaultProfile
    }

    /** 관리자 화면 전용. 실패를 그대로 던진다. */
    suspend fun loadProjectsStrict(): List<ProjectData> =
        limited("프로젝트") { firestore.runQuery(projectsQuery()) }.map(::toProject)

    /**
     * 이 계정이 관리자인지 확인한다. 관리자 화면 전용이라 실패를 감추지 않는다.
     *
     * 문서가 없으면 false 이고, 그 밖의 실패(네트워크·권한)는 던진다. 실패를 false 로
     * 뭉개면 '연결이 안 됐다' 가 '권한이 없다' 로 둔갑한다.
     */
    suspend fun isAdmin(uid: String): Boolean = limited("관리자 권한") {
        auth.authorized { token -> firestore.get("$ADMINS/$uid", token) != null }
    }

    // ------------------------------------------------------------------
    // 쓰기 (관리자 인증 필요 — firestore.rules 참고)
    // ------------------------------------------------------------------

    suspend fun saveProfile(profile: ProfileData) {
        auth.authorized { token -> firestore.set(PROFILE_PATH, profile.toMap(), token) }
    }

    /**
     * 프로젝트 목록을 한 번의 commit 으로 저장한다.
     *
     * [ordered] 는 편집 목록 그대로이고, order 는 목록 인덱스로 다시 매긴다. 한 건만 저장할
     * 때도 나머지의 order 를 함께 고친다. 목록 맨 위에 새 항목을 끼운 뒤 한 건만 저장하면,
     * 그 한 건만 새 인덱스로 쓰이고 나머지는 옛 order 를 그대로 가져 순서가 겹치기 때문이다.
     *
     * @param fullWrite 문서 전체를 쓸 항목의 인덱스. 새 항목(id 없음)은 여기서 ID 를 받는다.
     *   나머지는 이미 저장된 문서의 order 만 고치고, 아직 저장되지 않은 항목은 건너뛴다.
     * @return fullWrite 인덱스 → 저장된 문서 ID
     */
    suspend fun saveProjects(ordered: List<ProjectData>, fullWrite: Set<Int>): Map<Int, String> {
        val savedIds = mutableMapOf<Int, String>()
        val writes = ordered.mapIndexedNotNull { index, project ->
            if (index in fullWrite) {
                // ID 를 먼저 정해 두어야 새 문서까지 commit 한 번에 담을 수 있다.
                // 토큰 만료로 다시 보내도 같은 ID 라 문서가 두 번 생기지 않는다.
                val id = project.id ?: firestore.newDocumentId()
                savedIds[index] = id
                firestore.setWrite("$PROJECTS/$id", project.copy(order = index).toMap())
            } else {
                project.id?.let { id ->
                    firestore.setWrite(
                        path = "$PROJECTS/$id",
                        fields = mapOf("order" to index),
                        updateMask = listOf("order"),
                        mustExist = true,
                    )
                }
            }
        }
        auth.authorized { token -> firestore.commit(writes, token) }
        return savedIds
    }

    /** 목록 순서를 리스트 인덱스대로 다시 매긴다. 아직 저장되지 않은 항목은 건너뛴다. */
    suspend fun saveProjectOrder(projects: List<ProjectData>) {
        saveProjects(projects, fullWrite = emptySet())
    }

    suspend fun deleteProject(id: String) {
        auth.authorized { token -> firestore.delete("$PROJECTS/$id", token) }
    }

    /**
     * 하드코딩 상수를 Firestore 로 밀어 넣는 최초 시드.
     *
     * [replaceExisting] 이 true 면 기존 프로젝트 문서를 모두 지우고 새로 쓴다.
     * false 면 이미 프로젝트가 있을 때 아무것도 하지 않는다.
     *
     * @return 실제로 썼으면 true, 이미 프로젝트가 있어 건너뛰었으면 false.
     */
    suspend fun seedFromDefaults(replaceExisting: Boolean): Boolean = auth.authorized { token ->
        val existing = firestore.runQuery(namesOnlyQuery(PROJECTS), token)
        if (existing.isNotEmpty() && !replaceExisting) return@authorized false

        val writes = buildList {
            if (replaceExisting) existing.forEach { add(firestore.deleteWrite("$PROJECTS/${it.id}")) }
            add(firestore.setWrite(PROFILE_PATH, DefaultProfile.toMap()))
            DefaultProjects.forEachIndexed { index, project ->
                add(
                    firestore.setWrite(
                        path = "$PROJECTS/${firestore.newDocumentId()}",
                        fields = project.copy(order = index).toMap(),
                    ),
                )
            }
        }
        firestore.commit(writes, token)
        true
    }

    // ------------------------------------------------------------------

    private fun toProject(doc: FirestoreDocument) = ProjectData.fromMap(doc.id, doc.fields)

    private fun projectsQuery(): JsonObject = buildJsonObject {
        put("from", buildJsonArray { add(buildJsonObject { put("collectionId", PROJECTS) }) })
        put(
            "orderBy",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("field", buildJsonObject { put("fieldPath", "order") })
                        put("direction", "ASCENDING")
                    },
                )
            },
        )
    }

    companion object {
        private const val PROFILE_PATH = "site/profile"
        private const val PROJECTS = "projects"
        private const val ADMINS = "admins"

        /**
         * 읽기 하나에 허용하는 시간. 방문자 화면에서는 이 시간을 넘기면 폴백으로
         * 넘어가고, 관리자 화면에서는 오류로 올라가 '다시 시도' 를 띄운다. 어느
         * 쪽이든 기다리다 멈춰 있지 않는 것이 목적이라 넉넉하지 않게 잡는다.
         */
        private const val READ_TIMEOUT_SECONDS = 8L

        /** 문서 이름만 받아 오는 쿼리. 지울 대상을 고를 때 본문까지 내려받을 이유가 없다. */
        internal fun namesOnlyQuery(collection: String): JsonObject = buildJsonObject {
            put("from", buildJsonArray { add(buildJsonObject { put("collectionId", collection) }) })
            put(
                "select",
                buildJsonObject {
                    put("fields", buildJsonArray { add(buildJsonObject { put("fieldPath", "__name__") }) })
                },
            )
        }

        /**
         * 읽기 하나에 제한 시간을 건다.
         *
         * withTimeout 이 던지는 TimeoutCancellationException 은 취소 예외라, 실패를 삼키는
         * load() 에서 취소와 구분되지 않는다. 그래서 일반 예외로 바꿔 던진다.
         */
        internal suspend fun <T> limited(what: String, block: suspend () -> T): T {
            class Box(val value: T)
            val box = withTimeoutOrNull(READ_TIMEOUT_SECONDS * 1000) { Box(block()) }
                ?: throw ReadTimeoutException(what, READ_TIMEOUT_SECONDS)
            return box.value
        }
    }
}
