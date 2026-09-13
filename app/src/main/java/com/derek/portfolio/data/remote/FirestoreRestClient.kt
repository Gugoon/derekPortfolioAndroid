package com.derek.portfolio.data.remote

import com.derek.portfolio.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Firestore 문서 한 건. [id] 는 경로의 마지막 조각이다. */
data class FirestoreDocument(
    val id: String,
    val name: String,
    val fields: Map<String, Any?>,
)

/**
 * Firestore 가 돌려준 오류.
 *
 * [status] 는 'PERMISSION_DENIED', 'NOT_FOUND' 같은 gRPC 상태 이름이다.
 */
class FirestoreException(
    val httpStatus: Int,
    val status: String,
    detail: String,
) : IOException(if (status.isEmpty()) detail else "$status: $detail")

/**
 * Firestore REST API(v1) 를 Ktor 로 직접 부르는 얇은 클라이언트.
 *
 * SDK 가 해 주던 일 중 이 앱이 쓰는 것만 옮겼다.
 *   문서 읽기(get), 쿼리(runQuery), 덮어쓰기(set), 삭제(delete),
 *   원자적 일괄 쓰기(batch → commit)
 *
 * 새 문서는 SDK 의 add() 대신 [newDocumentId] 로 ID 를 먼저 정해 commit 에 담는다.
 * 여러 문서를 한 번에 쓰면서 새 문서도 섞어 넣을 수 있고, 재시도해도 두 번 생기지 않는다.
 *
 * 로그인이 필요한 요청은 [idToken] 을 넘기면 Authorization 헤더로 실린다. 규칙
 * (firestore.rules) 의 request.auth 는 이 토큰으로 채워진다.
 */
@Singleton
class FirestoreRestClient @Inject constructor(
    private val http: HttpClient,
    private val json: Json,
) {
    private val documentsRoot =
        "projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"
    private val baseUrl = "https://firestore.googleapis.com/v1/$documentsRoot"
    private val random = SecureRandom()

    /** 'projects/abc' 같은 상대 경로를 API 가 쓰는 전체 문서 이름으로 바꾼다. */
    fun documentName(path: String): String = "$documentsRoot/$path"

    /** 문서 하나를 읽는다. 없으면 null. */
    suspend fun get(path: String, idToken: String? = null): FirestoreDocument? {
        val response = http.get("$baseUrl/$path") { authorize(idToken) }
        if (response.status == HttpStatusCode.NotFound) return null
        return parseDocument(response.successBody() as JsonObject)
    }

    /**
     * 구조화 쿼리를 실행한다. [structuredQuery] 는 API 의 StructuredQuery JSON 이다.
     *
     * 응답은 결과마다 한 줄씩 온다. 결과가 없으면 readTime 만 담긴 줄 하나가 오므로
     * document 가 있는 줄만 추린다.
     */
    suspend fun runQuery(
        structuredQuery: JsonObject,
        idToken: String? = null,
    ): List<FirestoreDocument> {
        val response = http.post("$baseUrl:runQuery") {
            authorize(idToken)
            jsonBody(buildJsonObject { put("structuredQuery", structuredQuery) })
        }
        val rows = response.successBody() as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            ((row as? JsonObject)?.get("document") as? JsonObject)?.let(::parseDocument)
        }
    }

    /**
     * 문서를 통째로 덮어쓴다. SDK 의 set().
     *
     * updateMask 없이 PATCH 하면 본문에 없는 필드는 지워지고, 문서가 없으면 새로 생긴다.
     */
    suspend fun set(path: String, fields: Map<String, Any?>, idToken: String?) {
        http.patch("$baseUrl/$path") {
            authorize(idToken)
            jsonBody(buildJsonObject { put("fields", FirestoreValues.encodeFields(fields)) })
        }.successBody()
    }

    suspend fun delete(path: String, idToken: String?) {
        http.delete("$baseUrl/$path") { authorize(idToken) }.successBody()
    }

    /** 여러 쓰기를 한 번에 원자적으로 반영한다. SDK 의 batch.commit(). 한 번에 500건까지. */
    suspend fun commit(writes: List<JsonObject>, idToken: String?) {
        if (writes.isEmpty()) return
        http.post("$baseUrl:commit") {
            authorize(idToken)
            jsonBody(buildJsonObject { put("writes", JsonArray(writes)) })
        }.successBody()
    }

    // ------------------------------------------------------------------
    // commit 에 넣을 쓰기 조각
    // ------------------------------------------------------------------

    /**
     * 문서 쓰기 한 건.
     *
     * @param updateMask 주면 그 필드만 고친다(SDK 의 update). 없으면 덮어쓰기(set).
     * @param mustExist true 면 문서가 이미 있을 때만 성공한다. update() 가 이렇게 동작한다.
     * @param serverTimestampFields 서버 시각(REQUEST_TIME)으로 채울 필드.
     *   SDK 의 FieldValue.serverTimestamp() 와 같고, 규칙의 request.time 과 같은 값이 들어간다.
     */
    fun setWrite(
        path: String,
        fields: Map<String, Any?>,
        updateMask: List<String>? = null,
        mustExist: Boolean = false,
        serverTimestampFields: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put(
            "update",
            buildJsonObject {
                put("name", documentName(path))
                put("fields", FirestoreValues.encodeFields(fields))
            },
        )
        if (updateMask != null) {
            put(
                "updateMask",
                buildJsonObject {
                    put("fieldPaths", buildJsonArray { updateMask.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
        if (mustExist) {
            put("currentDocument", buildJsonObject { put("exists", true) })
        }
        if (serverTimestampFields.isNotEmpty()) {
            put(
                "updateTransforms",
                buildJsonArray {
                    serverTimestampFields.forEach { field ->
                        add(
                            buildJsonObject {
                                put("fieldPath", field)
                                put("setToServerValue", "REQUEST_TIME")
                            },
                        )
                    }
                },
            )
        }
    }

    fun deleteWrite(path: String): JsonObject = buildJsonObject {
        put("delete", documentName(path))
    }

    /** 클라이언트에서 만드는 20자 문서 ID. SDK 의 collection.doc() 와 같은 모양이다. */
    fun newDocumentId(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(20) { repeat(20) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    // ------------------------------------------------------------------

    private fun HttpRequestBuilder.authorize(idToken: String?) {
        parameter("key", FirebaseConfig.API_KEY)
        if (idToken != null) bearerAuth(idToken)
    }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.successBody(): JsonElement {
        val text = bodyAsText()
        val element = if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()
        if (status.isSuccess()) return element ?: JsonObject(emptyMap())
        throw errorFrom(status.value, element, text)
    }

    private fun parseDocument(obj: JsonObject): FirestoreDocument {
        val name = (obj["name"] as? JsonPrimitive)?.content.orEmpty()
        return FirestoreDocument(
            id = name.substringAfterLast('/'),
            name = name,
            fields = FirestoreValues.decodeFields(obj["fields"] as? JsonObject),
        )
    }

    private fun errorFrom(httpStatus: Int, element: JsonElement?, raw: String): FirestoreException {
        // 오류는 {"error": {...}} 로 오지만 runQuery 는 [{"error": {...}}] 로 감싸 올 때도 있다.
        val container = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            else -> null
        }
        val error = container?.get("error") as? JsonObject
        val status = (error?.get("status") as? JsonPrimitive)?.content.orEmpty()
        val message = (error?.get("message") as? JsonPrimitive)?.content
            ?: raw.take(200).ifBlank { "HTTP $httpStatus" }
        return FirestoreException(httpStatus, status, message)
    }
}
