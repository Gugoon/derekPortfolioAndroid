package com.derek.portfolio.data.repository

import com.derek.portfolio.data.remote.FirestoreRestClient
import com.derek.portfolio.data.visit.CleanupResult
import com.derek.portfolio.data.visit.VisitClientInfo
import com.derek.portfolio.data.visit.VisitGeoInfo
import com.derek.portfolio.data.visit.VisitRecord
import com.derek.portfolio.data.visit.clipVisitText
import com.derek.portfolio.data.visit.visitDocId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Firestore 컬렉션 구조
 *
 *   visits/{기기ID_시간칸}  → 방문 한 건.
 *       쓰기는 누구나(방문자 본인), 읽기는 관리자만.
 *       문서 ID 가 10초 쓰기 제한 역할을 한다. visitDocId 참고.
 */
@Singleton
class VisitLogRepository @Inject constructor(
    private val firestore: FirestoreRestClient,
    private val auth: AuthRepository,
) {
    /**
     * 방문 한 건을 쓴다. 로그인 없이 쓴다.
     *
     * 아무 ID 나 받는 대신 visitDocId 가 정한 ID 에 직접 쓴다. 그 ID 가 곧 쓰기
     * 제한이다 — 같은 기기가 같은 10초 안에 다시 부르면 이미 있는 문서를 건드리게
     * 되고, 규칙이 그런 쓰기를 거절한다.
     */
    suspend fun add(visitorId: String, client: VisitClientInfo, geo: VisitGeoInfo?) {
        val fields = mapOf(
            "ip" to (geo?.ip ?: ""),
            "country" to (geo?.country ?: ""),
            "countryCode" to (geo?.countryCode ?: ""),
            "region" to (geo?.region ?: ""),
            "city" to (geo?.city ?: ""),
            "org" to (geo?.org ?: ""),
            "timezone" to (geo?.timezone ?: ""),
            "path" to clipVisitText(client.path),
            "referrer" to clipVisitText(client.referrer),
            "userAgent" to clipVisitText(client.userAgent),
            "language" to clipVisitText(client.language),
            "screen" to clipVisitText(client.screen),
        )
        val write = firestore.setWrite(
            path = "$VISITS/${visitDocId(visitorId, System.currentTimeMillis())}",
            fields = fields,
            // 기기 시계는 믿을 수 없고, firestore.rules 도 서버 시각만 받는다.
            serverTimestampFields = listOf("createdAt"),
        )
        firestore.commit(listOf(write), idToken = null)
    }

    /** 최근 방문부터 [limit] 건. 관리자만 읽을 수 있다. */
    suspend fun loadRecent(limit: Int = 300): List<VisitRecord> = auth.authorized { token ->
        firestore.runQuery(recentQuery(limit), token).map { VisitRecord.fromMap(it.id, it.fields) }
    }

    /**
     * [age] 보다 오래된 기록을 지운다. [age] 가 0 이면 전부 지운다.
     *
     * 한 번에 다 지우지 않고 배치로 끊어 돈다(commit 한 번에 500건 제한). 한없이
     * 도는 일이 없도록 상한을 두었고, 상한에 걸려 아직 남은 게 있으면 hasMore 로
     * 알린다 — 지운 건수만 돌려주면 "8000건 지웠습니다" 를 보고 정리가 끝난 줄
     * 알게 된다.
     */
    suspend fun deleteOlderThan(age: Duration): CleanupResult {
        // 전체 삭제는 시각으로 거르지 않는다. createdAt 은 서버 시각이라, 기기 시계가
        // 조금만 늦어도 방금 쌓인 기록이 '지금보다 이전' 조건에서 빠져 남는다.
        val cutoff = if (age == Duration.ZERO) null else Instant.now().minusMillis(age.inWholeMilliseconds)
        var deleted = 0
        var hasMore = true
        var round = 0

        while (round < MAX_CLEANUP_ROUNDS && hasMore) {
            round++
            val docs = auth.authorized { token -> firestore.runQuery(cleanupQuery(cutoff), token) }

            // 묶음이 꽉 차서 왔으면 뒤에 더 있을 수 있다.
            hasMore = docs.size == CLEANUP_BATCH
            if (docs.isEmpty()) break

            auth.authorized { token ->
                firestore.commit(docs.map { firestore.deleteWrite("$VISITS/${it.id}") }, token)
            }
            deleted += docs.size
        }
        return CleanupResult(deleted = deleted, hasMore = hasMore)
    }

    private fun recentQuery(limit: Int): JsonObject = buildJsonObject {
        put("from", buildJsonArray { add(buildJsonObject { put("collectionId", VISITS) }) })
        put(
            "orderBy",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("field", buildJsonObject { put("fieldPath", "createdAt") })
                        put("direction", "DESCENDING")
                    },
                )
            },
        )
        put("limit", limit)
    }

    /** 지울 문서의 이름만 받아 온다. [cutoff] 가 null 이면 시각으로 거르지 않는다. */
    private fun cleanupQuery(cutoff: Instant?): JsonObject = buildJsonObject {
        put("from", buildJsonArray { add(buildJsonObject { put("collectionId", VISITS) }) })
        put(
            "select",
            buildJsonObject {
                put("fields", buildJsonArray { add(buildJsonObject { put("fieldPath", "__name__") }) })
            },
        )
        if (cutoff != null) {
            put(
                "where",
                buildJsonObject {
                    put(
                        "fieldFilter",
                        buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", "createdAt") })
                            put("op", "LESS_THAN")
                            put("value", buildJsonObject { put("timestampValue", cutoff.toString()) })
                        },
                    )
                },
            )
        }
        put("limit", CLEANUP_BATCH)
    }

    private companion object {
        const val VISITS = "visits"

        /** 기록 정리 한 묶음의 크기와, 한 번에 돌 최대 묶음 수. */
        const val CLEANUP_BATCH = 400
        const val MAX_CLEANUP_ROUNDS = 20
    }
}
