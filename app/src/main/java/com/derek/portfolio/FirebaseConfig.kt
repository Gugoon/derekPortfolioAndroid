package com.derek.portfolio

/**
 * Firebase 프로젝트 설정.
 *
 * Firebase SDK 를 쓰지 않고 Firestore / Identity Toolkit REST API 를 직접 부르므로,
 * 필요한 것은 웹 앱과 같은 API 키와 프로젝트 ID 뿐이다. 웹의 firebase_options.dart 와
 * 같은 값이다. API 키는 요청을 프로젝트에 묶는 식별자일 뿐 비밀이 아니고, 실제 접근
 * 통제는 firestore.rules 가 한다.
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyAuM91o-Et2BBcZ6ScnXcpusHV2pXSUbT0"
    const val PROJECT_ID = "gubosung-portfolio"

    /**
     * 설정이 비어 있으면 원격 연동을 아예 시도하지 않고 내장 데이터로만 그린다.
     * 웹의 firebaseReady 플래그와 같은 역할이다.
     */
    val isConfigured: Boolean
        get() = API_KEY.isNotBlank() && PROJECT_ID.isNotBlank()
}
