package com.derek.portfolio.data.remote

import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Firestore REST 의 "타입이 붙은 값" 표현과 Kotlin 값을 오가는 코덱.
 *
 * REST API 는 필드 하나하나를 {"stringValue": "..."}, {"integerValue": "3"} 처럼
 * 타입 이름으로 감싸 주고받는다. 모델 쪽은 SDK 를 쓸 때처럼 Map<String, Any?> 만
 * 다루도록 여기서 풀고 감싼다.
 *
 * 읽은 값의 Kotlin 타입
 *   stringValue → String, integerValue → Long, doubleValue → Double,
 *   booleanValue → Boolean, timestampValue → Instant, nullValue → null,
 *   arrayValue → List<Any?>, mapValue → Map<String, Any?>
 */
object FirestoreValues {

    fun encodeFields(fields: Map<String, Any?>): JsonObject = buildJsonObject {
        fields.forEach { (key, value) -> put(key, encode(value)) }
    }

    fun encode(value: Any?): JsonObject = when (value) {
        null -> buildJsonObject { put("nullValue", JsonNull) }
        is String -> buildJsonObject { put("stringValue", value) }
        is Boolean -> buildJsonObject { put("booleanValue", value) }
        // 64비트 정수는 JSON 숫자로 정확히 담기지 않을 수 있어 API 가 문자열로 받는다.
        is Int, is Long, is Short, is Byte -> buildJsonObject { put("integerValue", value.toString()) }
        is Float, is Double -> buildJsonObject { put("doubleValue", (value as Number).toDouble()) }
        is Instant -> buildJsonObject { put("timestampValue", value.toString()) }
        is Enum<*> -> buildJsonObject { put("stringValue", value.name) }
        is List<*> -> buildJsonObject {
            put(
                "arrayValue",
                // 빈 배열은 values 키 자체를 빼야 한다.
                if (value.isEmpty()) JsonObject(emptyMap())
                else buildJsonObject { put("values", JsonArray(value.map(::encode))) },
            )
        }
        is Map<*, *> -> buildJsonObject {
            put(
                "mapValue",
                buildJsonObject {
                    put(
                        "fields",
                        buildJsonObject {
                            value.forEach { (k, v) -> put(k.toString(), encode(v)) }
                        },
                    )
                },
            )
        }
        else -> throw IllegalArgumentException("Firestore 로 보낼 수 없는 값입니다: ${value::class.java}")
    }

    fun decodeFields(fields: JsonObject?): Map<String, Any?> =
        fields?.mapValues { (_, value) -> decode(value) } ?: emptyMap()

    fun decode(value: JsonElement): Any? {
        val obj = value as? JsonObject ?: return null
        val (type, raw) = obj.entries.firstOrNull() ?: return null
        val primitive = raw as? JsonPrimitive
        return when (type) {
            "nullValue" -> null
            "stringValue" -> primitive?.content
            "booleanValue" -> primitive?.booleanOrNull
            "integerValue" -> primitive?.content?.toLongOrNull()
            "doubleValue" -> primitive?.doubleOrNull
            "timestampValue" -> primitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
            "arrayValue" -> ((raw as? JsonObject)?.get("values") as? JsonArray)?.map(::decode) ?: emptyList<Any?>()
            "mapValue" -> decodeFields((raw as? JsonObject)?.get("fields") as? JsonObject)
            "referenceValue", "bytesValue" -> primitive?.content
            "geoPointValue" -> decodeGeoPoint(raw as? JsonObject)
            else -> null
        }
    }

    private fun decodeGeoPoint(raw: JsonObject?): Map<String, Any?>? = raw?.let {
        mapOf(
            "latitude" to (it["latitude"] as? JsonPrimitive)?.doubleOrNull,
            "longitude" to (it["longitude"] as? JsonPrimitive)?.doubleOrNull,
        )
    }
}
