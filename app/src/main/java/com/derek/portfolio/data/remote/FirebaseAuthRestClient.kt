package com.derek.portfolio.data.remote

import com.derek.portfolio.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Firebase Authentication 실패.
 *
 * [code] 는 웹 SDK(FirebaseAuthException.code) 와 같은 이름으로 맞춘다. 화면의 안내
 * 문구가 이 이름으로 갈린다.
 */
class AuthException(val code: String, detail: String = code) : IOException(detail) {
    /** 저장된 로그인이 더는 쓸 수 없는 경우. 다시 로그인해야 한다. */
    val isSessionExpired: Boolean
        get() = code == "user-token-expired" || code == "user-disabled" || code == "user-not-found"
}

@Serializable
data class SignInResponse(
    val localId: String,
    val email: String? = null,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
)

@Serializable
data class RefreshTokenResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String,
    @SerialName("user_id") val userId: String,
)

/**
 * 기본값을 두지 않는다. Json 은 기본값과 같은 필드를 싣지 않으므로(encodeDefaults=false),
 * returnSecureToken 에 기본값을 주면 요청에서 빠지고, 서버는 이를 false 로 보아
 * refreshToken·expiresIn 없이 응답한다.
 */
@Serializable
private data class SignInRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean,
)

@Serializable
private data class OobCodeRequest(
    val requestType: String,
    val email: String,
)

/**
 * Firebase Authentication REST API 클라이언트.
 *
 *   이메일/비밀번호 로그인      identitytoolkit  accounts:signInWithPassword
 *   비밀번호 재설정 메일        identitytoolkit  accounts:sendOobCode
 *   ID 토큰 갱신               securetoken      token (refresh_token)
 *
 * ID 토큰은 1시간짜리라 [AuthRepository] 가 만료 전에 갱신한다.
 */
@Singleton
class FirebaseAuthRestClient @Inject constructor(
    private val http: HttpClient,
    private val json: Json,
) {
    private val identityBase = "https://identitytoolkit.googleapis.com/v1"
    private val tokenUrl = "https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}"

    suspend fun signInWithPassword(email: String, password: String): SignInResponse {
        val response = http.post("$identityBase/accounts:signInWithPassword?key=${FirebaseConfig.API_KEY}") {
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(email = email, password = password, returnSecureToken = true))
        }
        response.ensureSuccess()
        return response.body()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        http.post("$identityBase/accounts:sendOobCode?key=${FirebaseConfig.API_KEY}") {
            contentType(ContentType.Application.Json)
            setBody(OobCodeRequest(requestType = "PASSWORD_RESET", email = email))
        }.ensureSuccess()
    }

    suspend fun refresh(refreshToken: String): RefreshTokenResponse {
        val response = http.submitForm(
            url = tokenUrl,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        response.ensureSuccess()
        return response.body()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.isSuccess()) return
        val text = bodyAsText()
        val error = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?.get("error") as? JsonObject
        // message 는 'TOO_MANY_ATTEMPTS_TRY_LATER : ...' 처럼 뒤에 설명이 붙기도 한다.
        val message = (error?.get("message") as? JsonPrimitive)?.content.orEmpty()
        val reason = message.substringBefore(' ').substringBefore(':').trim()
        throw AuthException(codeFor(reason), message.ifBlank { "HTTP ${status.value}" })
    }

    private fun codeFor(reason: String): String = when (reason) {
        "INVALID_EMAIL" -> "invalid-email"
        "EMAIL_NOT_FOUND" -> "user-not-found"
        "INVALID_PASSWORD" -> "wrong-password"
        "INVALID_LOGIN_CREDENTIALS" -> "invalid-credential"
        "USER_DISABLED" -> "user-disabled"
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> "too-many-requests"
        "OPERATION_NOT_ALLOWED", "PASSWORD_LOGIN_DISABLED" -> "operation-not-allowed"
        "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "INVALID_GRANT_TYPE", "MISSING_REFRESH_TOKEN" -> "user-token-expired"
        "USER_NOT_FOUND" -> "user-not-found"
        "" -> "unknown"
        else -> reason.lowercase().replace('_', '-')
    }
}
