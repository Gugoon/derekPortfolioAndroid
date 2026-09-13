package com.derek.portfolio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.derek.portfolio.data.remote.AuthException
import com.derek.portfolio.data.remote.FirebaseAuthRestClient
import com.derek.portfolio.data.remote.FirestoreException
import com.derek.portfolio.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AuthUser(val uid: String, val email: String?)

sealed interface AuthState {
    /** 저장된 로그인을 되살리는 중. 웹 SDK 의 authStateChanges 첫 값이 나오기 전과 같다. */
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/**
 * 관리자 로그인 상태와 ID 토큰을 관리한다.
 *
 * 로그인 정보는 DataStore 에 남겨 앱을 다시 켜도 이어진다(웹 SDK 가 IndexedDB 에
 * 세션을 두는 것과 같다). ID 토큰은 1시간이면 만료되므로 요청 직전에 남은 시간을
 * 보고 갱신한다.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val client: FirebaseAuthRestClient,
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private data class Session(
        val uid: String,
        val email: String?,
        val idToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
    ) {
        val user get() = AuthUser(uid, email)
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // 복원·로그인·로그아웃·토큰 갱신이 서로 엇갈려 덮어쓰지 않도록 한 줄로 세운다.
    private val mutex = Mutex()
    private var session: Session? = null

    init {
        scope.launch { restore() }
    }

    private suspend fun restore() = mutex.withLock {
        if (_state.value != AuthState.Restoring) return@withLock
        val saved = try {
            dataStore.data.first().toSession()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        session = saved
        _state.value = saved?.let { AuthState.SignedIn(it.user) } ?: AuthState.SignedOut
    }

    /** 저장된 로그인 복원이 끝날 때까지 기다린 뒤 현재 사용자를 돌려준다. */
    suspend fun awaitUser(): AuthUser? =
        (state.first { it != AuthState.Restoring } as? AuthState.SignedIn)?.user

    suspend fun signIn(email: String, password: String) {
        val response = client.signInWithPassword(email, password)
        val next = Session(
            uid = response.localId,
            email = response.email ?: email,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = expiresAt(response.expiresIn),
        )
        mutex.withLock { persist(next) }
    }

    suspend fun signOut() = mutex.withLock { persist(null) }

    suspend fun sendPasswordResetEmail(email: String) = client.sendPasswordResetEmail(email)

    /**
     * 요청에 실을 ID 토큰. 만료가 가까우면 갱신해서 돌려준다.
     *
     * 갱신 토큰마저 무효가 되었으면(비밀번호 변경, 계정 비활성화) 로그아웃 처리한다.
     */
    suspend fun idToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val current = session ?: throw AuthException("not-signed-in", "로그인이 필요합니다.")
        if (!forceRefresh && current.expiresAtMillis - TOKEN_SLACK_MILLIS > System.currentTimeMillis()) {
            return@withLock current.idToken
        }
        val refreshed = try {
            client.refresh(current.refreshToken)
        } catch (e: AuthException) {
            if (e.isSessionExpired) persist(null)
            throw e
        }
        val next = current.copy(
            idToken = refreshed.idToken,
            refreshToken = refreshed.refreshToken,
            expiresAtMillis = expiresAt(refreshed.expiresIn),
        )
        persist(next)
        next.idToken
    }

    /**
     * 로그인이 필요한 요청을 실행한다.
     *
     * 시계가 어긋나 아직 유효하다고 본 토큰이 서버에서 거절(401)되면 한 번만 강제로
     * 갱신해 다시 시도한다. 403(권한 없음)은 토큰 문제가 아니므로 그대로 올린다.
     */
    suspend fun <T> authorized(block: suspend (idToken: String) -> T): T = try {
        block(idToken())
    } catch (e: FirestoreException) {
        if (e.httpStatus != 401) throw e
        block(idToken(forceRefresh = true))
    }

    private suspend fun persist(next: Session?) {
        dataStore.edit { prefs ->
            if (next == null) {
                listOf(KEY_UID, KEY_EMAIL, KEY_ID_TOKEN, KEY_REFRESH_TOKEN).forEach { prefs.remove(it) }
                prefs.remove(KEY_EXPIRES_AT)
            } else {
                prefs[KEY_UID] = next.uid
                if (next.email != null) prefs[KEY_EMAIL] = next.email else prefs.remove(KEY_EMAIL)
                prefs[KEY_ID_TOKEN] = next.idToken
                prefs[KEY_REFRESH_TOKEN] = next.refreshToken
                prefs[KEY_EXPIRES_AT] = next.expiresAtMillis
            }
        }
        session = next
        _state.value = next?.let { AuthState.SignedIn(it.user) } ?: AuthState.SignedOut
    }

    private fun Preferences.toSession(): Session? {
        val uid = this[KEY_UID] ?: return null
        val refreshToken = this[KEY_REFRESH_TOKEN] ?: return null
        return Session(
            uid = uid,
            email = this[KEY_EMAIL],
            idToken = this[KEY_ID_TOKEN].orEmpty(),
            refreshToken = refreshToken,
            expiresAtMillis = this[KEY_EXPIRES_AT] ?: 0L,
        )
    }

    private fun expiresAt(expiresInSeconds: String): Long =
        System.currentTimeMillis() + (expiresInSeconds.toLongOrNull() ?: 3600L) * 1000L

    private companion object {
        /** 만료 직전에 보낸 요청이 서버에 닿을 때쯤 만료되지 않도록 여유를 둔다. */
        const val TOKEN_SLACK_MILLIS = 60_000L

        val KEY_UID = stringPreferencesKey("auth.uid")
        val KEY_EMAIL = stringPreferencesKey("auth.email")
        val KEY_ID_TOKEN = stringPreferencesKey("auth.idToken")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("auth.refreshToken")
        val KEY_EXPIRES_AT = longPreferencesKey("auth.expiresAt")
    }
}
