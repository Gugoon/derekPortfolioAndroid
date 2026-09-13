package com.derek.portfolio.data.visit

import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// 방문 한 건에 대해 알 수 있는 값들.
//
// 기기가 스스로 아는 것(VisitClientInfo)과, 기기는 모르고 외부 조회로만 알 수 있는
// 것(VisitGeoInfo)을 나눠 둔다. 뒤쪽은 조회에 실패할 수 있어서 없어도 기록은 남는다.

/** 앱에서 곧바로 읽어낼 수 있는 값들. 웹의 브라우저 정보 자리를 채운다. */
data class VisitClientInfo(
    val path: String = "",
    val referrer: String = "",
    val userAgent: String = "",
    val language: String = "",
    /** '1080x2400' 형태의 화면 크기. */
    val screen: String = "",
)

/**
 * IP 로 알아낸 대략적인 위치.
 *
 * IP 기반 추정이라 도시까지 정확히 맞지는 않는다. 국가는 거의 맞고, 시/도는
 * 통신사 회선이 묶여 나가는 지역으로 잡히는 일이 잦다.
 */
data class VisitGeoInfo(
    val ip: String,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    /** 통신사/기관 이름. 'LG POWERCOMM' 같은 값이 들어온다. */
    val org: String = "",
    val timezone: String = "",
) {
    companion object {
        /**
         * 조회처 응답(JSON 문자열)을 읽는다. 알아볼 수 없으면 null.
         *
         * ipwho.is 와 geojs 두 곳을 같은 모양으로 맞춘다. 키 이름이 대부분 겹치고
         * (ip, country, region, city), 갈리는 건 통신사와 시간대뿐이다. ipwho.is 는
         * connection.isp / timezone.id 로 중첩해 넣고, geojs 는 organization_name /
         * timezone 으로 평평하게 넣는다.
         */
        fun tryParse(body: String, json: Json = Json): VisitGeoInfo? {
            val decoded = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return null

            // ipwho.is 는 한도 초과나 조회 실패도 200 + success:false 로 돌려준다.
            if ((decoded["success"] as? JsonPrimitive)?.booleanOrNull == false) return null

            val ip = clipVisitText(decoded.text("ip"))
            if (ip.isEmpty()) return null

            val connection = decoded["connection"] as? JsonObject
            val timezone = decoded["timezone"]

            return VisitGeoInfo(
                ip = ip,
                country = clipVisitText(decoded.text("country")),
                countryCode = clipVisitText(decoded.text("country_code")),
                region = clipVisitText(decoded.text("region")),
                city = clipVisitText(decoded.text("city")),
                org = clipVisitText(connection?.text("isp") ?: decoded.text("organization_name")),
                timezone = clipVisitText(
                    if (timezone is JsonObject) timezone.text("id") else (timezone as? JsonPrimitive)?.content,
                ),
            )
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
    }
}

/**
 * 문서에 넣기 전에 문자열을 다듬는다.
 *
 * 길이를 자르는 건 firestore.rules 의 검사와 짝이다. 규칙이 512자를 넘는 값을
 * 거절하므로, 유난히 긴 값 하나 때문에 기록이 통째로 날아가지 않도록 먼저 줄인다.
 */
fun clipVisitText(value: Any?, max: Int = 300): String {
    if (value == null) return ""
    val text = value.toString().trim()
    return if (text.length <= max) text else text.substring(0, max)
}

/** 쓰기 제한의 시간 칸 크기. firestore.rules 의 10000 과 반드시 같아야 한다. */
const val VISIT_SLOT_MILLIS = 10_000L

private val VisitorIdPattern = Regex("^[0-9a-f]{16}$")

fun isValidVisitorId(id: String?): Boolean = id != null && VisitorIdPattern.matches(id)

/**
 * 기기를 구분하는 임의의 ID 를 새로 만든다.
 *
 * 16자리 16진수. 방문 문서 ID 의 앞부분으로 들어가고 firestore.rules 가 이 모양을
 * 검사하므로, 형식을 바꾸려면 규칙도 함께 고쳐야 한다.
 */
fun newVisitorId(): String {
    val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * 방문 문서의 ID. '기기ID_시간칸' 모양이다.
 *
 * 이것 하나가 10초 쓰기 제한의 전부다. 같은 기기가 같은 10초 안에 두 번 쓰려고 하면
 * ID 가 똑같이 나오는데, 그 문서는 이미 있으므로 두 번째 쓰기는 create 가 아니라
 * update 로 취급된다. 규칙이 update 를 막아 두었으니 거절된다.
 */
fun visitDocId(visitorId: String, nowMillis: Long): String =
    "${visitorId}_${nowMillis / VISIT_SLOT_MILLIS}"

/** Firestore `visits/{id}` 문서 한 건. */
data class VisitRecord(
    val id: String,
    /** 서버 시각. 방금 쓴 문서를 곧바로 읽으면 아직 비어 있을 수 있어 nullable 이다. */
    val at: Instant?,
    val ip: String,
    val country: String,
    val countryCode: String,
    val region: String,
    val city: String,
    val org: String,
    val timezone: String,
    val path: String,
    val referrer: String,
    val userAgent: String,
    val language: String,
    val screen: String,
) {
    /**
     * 'South Korea · Seoul · Seo-gu' 처럼 아는 만큼만 이어 붙인다.
     *
     * 서울처럼 시·도와 도시 이름이 같은 곳이 흔해서, 같은 값이 두 번 나오면 하나만 남긴다.
     */
    val location: String
        get() = listOf(country, region, city)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")

    /** 유입 경로를 도메인만 남겨 짧게 보여 준다. */
    val source: String
        get() {
            if (referrer.isEmpty()) return "직접 방문"
            val host = runCatching { URI(referrer).host }.getOrNull().orEmpty()
            return host.ifEmpty { referrer }
        }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>) = VisitRecord(
            id = id,
            at = map["createdAt"] as? Instant,
            ip = clipVisitText(map["ip"]),
            country = clipVisitText(map["country"]),
            countryCode = clipVisitText(map["countryCode"]),
            region = clipVisitText(map["region"]),
            city = clipVisitText(map["city"]),
            org = clipVisitText(map["org"]),
            timezone = clipVisitText(map["timezone"]),
            path = clipVisitText(map["path"]),
            referrer = clipVisitText(map["referrer"]),
            userAgent = clipVisitText(map["userAgent"]),
            language = clipVisitText(map["language"]),
            screen = clipVisitText(map["screen"]),
        )
    }
}

/** 기록 정리 결과. 상한에 걸려 아직 남은 게 있으면 [hasMore] 가 true 다. */
data class CleanupResult(val deleted: Int, val hasMore: Boolean)

private val TimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** '2026-09-10 14:22' 형태. */
fun formatVisitTime(at: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
    at?.atZone(zone)?.format(TimeFormatter) ?: "시각 미확인"

/**
 * 크롤러를 가려내는 표식.
 *
 * 그냥 'bot' 이 들어 있는지만 보면 안 된다. CUBOT 처럼 이름에 bot 이 든 안드로이드
 * 기기가 실제로 있어서, 멀쩡한 방문자가 봇으로 찍힌다. 크롤러들이 쓰는 모양
 * (Googlebot/2.1, bingbot/2.0, Slackbot-LinkExpanding) 은 bot 뒤에 구분자가
 * 붙으므로 거기까지 함께 본다.
 */
private val BotMarkers = listOf(
    "bot/", "bot;", "bot-", "crawler", "spider", "slurp",
    "facebookexternalhit", "headlesschrome", "python-requests", "curl/", "wget/",
)

/** 이 앱이 방문 기록에 싣는 userAgent 의 머리말. */
const val APP_USER_AGENT_PREFIX = "DerekPortfolioApp"

/**
 * userAgent 에서 브라우저와 OS 만 뽑아 한 줄로 줄인다.
 *
 * 원문은 200자가 넘어가는 데다 대부분이 호환성 때문에 붙은 껍데기라, 그대로
 * 보여 주면 목록이 읽히지 않는다.
 */
fun describeUserAgent(userAgent: String): String {
    if (userAgent.isEmpty()) return ""

    val lower = userAgent.lowercase()
    // 검색엔진 크롤러는 사람 방문과 섞이면 통계가 흐려지므로 따로 표시한다.
    if (BotMarkers.any { lower.contains(it) }) return "봇/크롤러"

    // 순서가 중요하다. 엣지와 오페라는 자기 이름 뒤에 Chrome 도 함께 달고 다니고,
    // 크롬은 Safari 를 달고 다닌다. 좁은 것부터 확인한다.
    val browsers = linkedMapOf(
        APP_USER_AGENT_PREFIX to "포트폴리오 앱",
        "Edg/" to "Edge",
        "OPR/" to "Opera",
        "SamsungBrowser" to "Samsung Internet",
        "Whale" to "Whale",
        "Firefox" to "Firefox",
        "Chrome" to "Chrome",
        "Safari" to "Safari",
    )
    val systems = linkedMapOf(
        "iPhone" to "iPhone",
        "iPad" to "iPad",
        "Android" to "Android",
        "Windows" to "Windows",
        "Mac OS X" to "macOS",
        "Linux" to "Linux",
    )

    val parts = listOfNotNull(
        browsers.entries.firstOrNull { userAgent.contains(it.key) }?.value,
        systems.entries.firstOrNull { userAgent.contains(it.key) }?.value,
    )
    return if (parts.isEmpty()) "알 수 없는 브라우저" else parts.joinToString(" · ")
}
