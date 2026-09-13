package com.derek.portfolio.data.visit

// 앱 방문 기록.
//
// 누가 언제 들어왔는지를 관리자 화면에서 보기 위한 최소한의 기록이다. 기기는
// 자기 공인 IP 를 모르기 때문에, 방문 시점에 무료 IP 조회 서비스에 한 번 물어
// IP 와 대략적인 위치(국가/시·도/도시)를 함께 받아 온다.
//
// 개인정보에 대해: IP 는 개인을 특정할 수 있는 정보다. 기록은 관리자만 읽을 수
// 있게 firestore.rules 로 막아 두었고, 관리자 화면에서 오래된 기록을 지울 수 있다.

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.derek.portfolio.BuildConfig
import com.derek.portfolio.data.repository.AuthRepository
import com.derek.portfolio.data.repository.VisitLogRepository
import com.derek.portfolio.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

@Singleton
class VisitLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val repository: VisitLogRepository,
    private val auth: AuthRepository,
    private val dataStore: DataStore<Preferences>,
    private val http: HttpClient,
    private val json: Json,
) {
    /**
     * 이 프로세스에서 이미 기록을 남겼는지.
     *
     * 웹은 탭 하나당 한 줄(sessionStorage)을 남긴다. 앱에서 같은 단위는 프로세스라,
     * 화면 회전이나 관리자 화면을 드나들 때마다 줄이 늘지 않게 메모리 표식으로 막는다.
     */
    private val claimed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    /** 방문 한 건을 뒤에서 남긴다. 호출한 쪽은 기다리지 않는다. */
    fun recordInBackground() {
        scope.launch { record() }
    }

    /**
     * 방문 한 건을 남긴다.
     *
     * 어떤 이유로 실패하든 예외를 밖으로 내보내지 않는다. 앱이 뜨는 것보다 중요한
     * 일이 아니다.
     */
    suspend fun record() {
        if (claimed.get() || !running.compareAndSet(false, true)) return
        try {
            // 관리자 본인의 접속은 남기지 않는다. 콘텐츠를 고치러 드나들 때마다 한 줄씩
            // 쌓이면 정작 보고 싶은 방문자 기록이 묻힌다. 로그인하는 사람은 관리자뿐이라
            // 로그인 여부만으로 가른다.
            //
            // 제때 확인되지 않으면 남기지 않는다. 세션 복원이 늦어지는 쪽은 대개
            // 관리자이므로, '모르겠으면 방문자' 로 넘기면 거르려던 관리자 IP 가 남는다.
            val restored = withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) { Restored(auth.awaitUser()) } ?: return
            if (restored.user != null) return

            val geo = lookUpGeo()
            repository.add(
                visitorId = readVisitorId(),
                client = readClientInfo(),
                geo = geo,
            )
            // 표식은 쓰기가 실제로 끝난 뒤에 찍는다. 먼저 찍어 두면 규칙에 거절당하거나
            // 네트워크가 끊겼을 때, 이 프로세스는 영영 기록을 남기지 못한다.
            claimed.set(true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 방문 기록이 실패했다고 앱이 흔들릴 이유는 없다.
        } finally {
            running.set(false)
        }
    }

    private class Restored(val user: Any?)

    private suspend fun lookUpGeo(): VisitGeoInfo? {
        for (url in GEO_ENDPOINTS) {
            try {
                val response = http.get(url) { timeout { requestTimeoutMillis = GEO_TIMEOUT_MILLIS } }
                if (response.status != HttpStatusCode.OK) continue
                VisitGeoInfo.tryParse(response.bodyAsText(), json)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 이 조회처는 건너뛰고 다음을 시도한다.
            }
        }
        return null
    }

    private fun readClientInfo(): VisitClientInfo {
        val metrics = context.resources.displayMetrics
        return VisitClientInfo(
            path = "/",
            referrer = "",
            userAgent = "$APP_USER_AGENT_PREFIX/${BuildConfig.VERSION_NAME} " +
                "(Linux; Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})",
            language = Locale.getDefault().toLanguageTag(),
            screen = "${metrics.widthPixels}x${metrics.heightPixels}",
        )
    }

    /**
     * 이 기기를 가리키는 ID. 없으면 만들어 저장해 둔다.
     *
     * 방문 문서 ID 의 앞부분으로 들어가, 같은 기기의 연속 쓰기를 규칙이 걸러 내는
     * 기준이 된다. 저장소를 못 쓰면 매번 새로 만든다.
     */
    private suspend fun readVisitorId(): String = try {
        val saved = dataStore.data.first()[KEY_VISITOR_ID]
        // 저장된 값이 우리가 만든 모양이 아니면 새로 만든다. 규칙이 모양을 검사하므로
        // 이상한 값을 그대로 쓰면 기록이 통째로 거절된다.
        if (isValidVisitorId(saved)) {
            saved!!
        } else {
            newVisitorId().also { id -> dataStore.edit { it[KEY_VISITOR_ID] = id } }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        newVisitorId()
    }

    private companion object {
        /**
         * IP 와 위치를 알려 주는 무료 조회처. 앞에서부터 하나가 답할 때까지 시도한다.
         * 둘 다 키 없이 쓸 수 있고, 어느 쪽도 답하지 않으면 위치 없이 방문 사실만 남는다.
         */
        val GEO_ENDPOINTS = listOf(
            "https://ipwho.is/",
            "https://get.geojs.io/v1/ip/geo.json",
        )

        /** 조회가 길어지면 그냥 포기한다. 방문 기록 때문에 무언가를 기다릴 이유는 없다. */
        const val GEO_TIMEOUT_MILLIS = 5_000L
        const val AUTH_TIMEOUT_MILLIS = 5_000L

        val KEY_VISITOR_ID = stringPreferencesKey("visitor-id")
    }
}
