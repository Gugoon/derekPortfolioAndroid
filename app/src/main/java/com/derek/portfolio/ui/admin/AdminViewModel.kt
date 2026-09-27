package com.derek.portfolio.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.derek.portfolio.data.remote.AuthException
import com.derek.portfolio.data.repository.AuthRepository
import com.derek.portfolio.data.repository.AuthState
import com.derek.portfolio.data.repository.AuthUser
import com.derek.portfolio.data.repository.ContentRepository
import com.derek.portfolio.data.repository.ReadTimeoutException
import com.derek.portfolio.data.repository.VisitLogRepository
import com.derek.portfolio.ui.components.AppMessage
import com.derek.portfolio.ui.components.readableMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 관리자 화면.
 *
 * 이메일/비밀번호로 로그인한 뒤, Firestore `admins/{uid}` 문서가 있는 계정만 편집할
 * 수 있다. 같은 조건이 firestore.rules 에도 걸려 있으므로 여기서의 검사는 안내용이고
 * 실제 차단은 서버가 한다.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val content: ContentRepository,
    private val visits: VisitLogRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = auth.state

    private val _gate = MutableStateFlow<AdminGate>(AdminGate.Loading)
    val gate: StateFlow<AdminGate> = _gate.asStateFlow()

    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages: Flow<AppMessage> = messageChannel.receiveAsFlow()

    val signInForm = SignInFormState()

    private var loadJob: Job? = null
    private var visitsJob: Job? = null
    private var loadedUid: String? = null

    /**
     * 프로젝트 쓰기(저장·전체 저장·삭제·순서 저장)를 한 줄로 세운다.
     *
     * 겹쳐 돌면 새 항목을 두 요청이 모두 'ID 없음' 으로 보고 문서를 두 번 만들거나, 저장
     * 중인 새 항목을 지웠는데 저장이 뒤늦게 끝나 목록에 없는 문서가 생긴다. 뒤에 온
     * 요청은 앞의 요청이 끝난 뒤, 그 시점의 목록과 ID 로 다시 계산한다.
     */
    private val projectWrites = Mutex()

    init {
        viewModelScope.launch {
            auth.state.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> if (state.user.uid != loadedUid) {
                        loadedUid = state.user.uid
                        load(state.user)
                    }
                    AuthState.SignedOut -> {
                        loadJob?.cancel()
                        loadedUid = null
                        _gate.value = AdminGate.Loading
                    }
                    AuthState.Restoring -> Unit
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 로그인
    // ------------------------------------------------------------------

    fun signIn() {
        val form = signInForm
        if (form.busy) return
        form.busy = true
        form.error = null
        viewModelScope.launch {
            try {
                auth.signIn(form.email.trim(), form.password)
                form.password = ""
                // 로그인에 성공하면 authState 가 화면을 갈아끼운다.
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                form.error = messageFor(e)
            } catch (e: Exception) {
                form.error = "로그인에 실패했습니다: ${e.readableMessage()}"
            } finally {
                form.busy = false
            }
        }
    }

    fun sendPasswordReset() {
        val email = signInForm.email.trim()
        if (email.isEmpty()) {
            signInForm.error = "먼저 이메일을 입력하세요."
            return
        }
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email)
                notify("$email 로 비밀번호 재설정 메일을 보냈습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                signInForm.error = "재설정 메일 발송에 실패했습니다: ${e.readableMessage()}"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    // ------------------------------------------------------------------
    // 권한 확인 + 콘텐츠 읽기
    // ------------------------------------------------------------------

    fun reload() {
        (auth.state.value as? AuthState.SignedIn)?.user?.let(::load)
    }

    /**
     * 화면에 필요한 것을 한 번에 읽어온다.
     *
     * 세 읽기를 한 줄로 세우면 왕복이 세 번 쌓인다. 권한 확인이 끝나기 전에 콘텐츠를
     * 읽어도 문제가 없으므로(site·projects 는 공개 읽기다) 셋을 동시에 던지고 함께
     * 기다린다. 하나라도 실패하면 '다시 시도' 를 띄운다. 읽기마다 제한 시간이 걸려
     * 있으므로 무한정 기다리는 경우는 없다.
     */
    private fun load(user: AuthUser) {
        loadJob?.cancel()
        _gate.value = AdminGate.Loading
        loadJob = viewModelScope.launch {
            _gate.value = try {
                coroutineScope {
                    val isAdmin = async { content.isAdmin(user.uid) }
                    val profile = async { content.loadProfileStrict() }
                    val projects = async { content.loadProjectsStrict() }
                    if (!isAdmin.await()) {
                        AdminGate.NotAdmin(user)
                    } else {
                        AdminGate.Ready(
                            profile = ProfileEditorState(profile.await()),
                            projects = ProjectsEditorState(projects.await()),
                            visits = VisitsState(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AdminGate.Error(messageForLoadError(e))
            }
        }
    }

    /** 코드에 내장된 기본 데이터를 Firestore 에 넣는다. */
    fun seed(replaceExisting: Boolean) {
        viewModelScope.launch {
            try {
                if (content.seedFromDefaults(replaceExisting)) {
                    notify("기본 데이터를 반영했습니다.")
                    reload()
                } else {
                    notify("이미 프로젝트가 있어 가져오지 않았습니다. 기본 데이터로 바꾸려면 '초기화' 를 쓰세요.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("시드에 실패했습니다: ${e.readableMessage()}", error = true)
            }
        }
    }

    // ------------------------------------------------------------------
    // 프로필
    // ------------------------------------------------------------------

    fun saveProfile(state: ProfileEditorState) {
        if (state.saving) return
        if (state.name.isBlank()) {
            notify("이름은 비워 둘 수 없습니다.", error = true)
            return
        }
        state.saving = true
        viewModelScope.launch {
            try {
                content.saveProfile(state.build())
                notify("프로필을 저장했습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("저장에 실패했습니다: ${e.readableMessage()}", error = true)
            } finally {
                state.saving = false
            }
        }
    }

    // ------------------------------------------------------------------
    // 프로젝트
    // ------------------------------------------------------------------

    /** 한 건을 저장한다. 나머지 프로젝트의 order 도 지금 목록 순서로 함께 맞춘다. */
    fun saveProject(state: ProjectsEditorState, draft: ProjectDraft) {
        if (draft.key in state.savingKeys) return
        if (draft.title.isBlank()) {
            notify("프로젝트 제목은 비워 둘 수 없습니다.", error = true)
            return
        }
        state.savingKeys = state.savingKeys + draft.key
        viewModelScope.launch {
            try {
                projectWrites.withLock {
                    // 기다리는 사이 지워졌으면 저장하지 않는다.
                    val index = state.drafts.indexOf(draft)
                    if (index < 0) return@withLock
                    val ordered = state.drafts.mapIndexed { i, d -> d.toModel(i) }
                    draft.id = content.saveProjects(ordered, fullWrite = setOf(index)).getValue(index)
                    notify("저장했습니다: ${draft.title}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("저장에 실패했습니다: ${e.readableMessage()}", error = true)
            } finally {
                state.savingKeys = state.savingKeys - draft.key
            }
        }
    }

    /** 목록 전체를 한 번에(원자적으로) 저장한다. */
    fun saveAllProjects(state: ProjectsEditorState) {
        if (state.savingAll) return
        if (state.drafts.any { it.title.isBlank() }) {
            notify("제목이 비어 있는 프로젝트가 있습니다.", error = true)
            return
        }
        state.savingAll = true
        viewModelScope.launch {
            try {
                projectWrites.withLock {
                    val snapshot = state.drafts.toList()
                    if (snapshot.any { it.title.isBlank() }) {
                        notify("제목이 비어 있는 프로젝트가 있습니다.", error = true)
                        return@withLock
                    }
                    val ids = content.saveProjects(
                        ordered = snapshot.mapIndexed { i, d -> d.toModel(i) },
                        fullWrite = snapshot.indices.toSet(),
                    )
                    snapshot.forEachIndexed { i, d -> d.id = ids.getValue(i) }
                    notify("${snapshot.size}건을 저장했습니다.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("저장에 실패했습니다: ${e.readableMessage()}", error = true)
            } finally {
                state.savingAll = false
            }
        }
    }

    fun deleteProject(state: ProjectsEditorState, draft: ProjectDraft) {
        viewModelScope.launch {
            try {
                projectWrites.withLock {
                    // 앞서 돌던 저장이 끝난 뒤라, 저장 중이던 새 항목이었어도 ID 가 정해져 있다.
                    draft.id?.let { content.deleteProject(it) }
                    state.drafts.remove(draft)
                }
                notify("삭제했습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("삭제에 실패했습니다: ${e.readableMessage()}", error = true)
            }
        }
    }

    /** 드래그로 바꾼 지금 목록 순서를 곧바로 저장한다. 새 항목은 저장 시점에 순서가 잡힌다. */
    fun saveProjectOrder(state: ProjectsEditorState) {
        viewModelScope.launch {
            try {
                projectWrites.withLock {
                    // 기다리는 사이 순서가 또 바뀌었을 수 있으니 지금 목록으로 계산한다.
                    content.saveProjectOrder(state.drafts.mapIndexed { i, draft -> draft.toModel(i) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("순서 저장에 실패했습니다: ${e.readableMessage()}", error = true)
            }
        }
    }

    // ------------------------------------------------------------------
    // 방문 기록
    // ------------------------------------------------------------------

    fun loadVisitsIfNeeded(state: VisitsState) {
        if (state.loadedOnce) return
        state.loadedOnce = true
        loadVisits(state)
    }

    fun loadVisits(state: VisitsState) {
        visitsJob?.cancel()
        state.loading = true
        state.error = null
        visitsJob = viewModelScope.launch {
            try {
                state.records = visits.loadRecent(limit = VISITS_LOAD_LIMIT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.records = emptyList()
                state.error = e.readableMessage()
            } finally {
                // 뒤이어 시작된 읽기에 밀려 취소된 경우에는 진행 표시를 건드리지 않는다.
                if (currentCoroutineContext().isActive) state.loading = false
            }
        }
    }

    /**
     * 기록을 정리한다. [age] 가 0 이면 전부 지운다.
     *
     * 전체 삭제가 따로 있는 이유: visits 는 로그인 없이 create 가 열려 있어서, 누가
     * 가짜 기록을 밀어 넣으면 그 문서들은 방금 찍힌 시각을 달고 있다. 오래된 것만
     * 지우는 수단뿐이면 90일을 기다려야 치울 수 있다.
     */
    fun cleanUpVisits(state: VisitsState, age: Duration) {
        if (state.cleaning) return
        state.cleaning = true
        viewModelScope.launch {
            try {
                val result = visits.deleteOlderThan(age)
                notify(
                    if (result.hasMore) "${result.deleted}건을 지웠습니다. 아직 남아 있으니 한 번 더 눌러 주세요."
                    else "${result.deleted}건을 지웠습니다.",
                )
                loadVisits(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify("기록을 지우지 못했습니다: ${e.readableMessage()}", error = true)
            } finally {
                state.cleaning = false
            }
        }
    }

    // ------------------------------------------------------------------

    private fun notify(text: String, error: Boolean = false) {
        messageChannel.trySend(AppMessage(text, error))
    }

    private fun messageFor(e: AuthException): String = when (e.code) {
        "invalid-email" -> "이메일 형식이 올바르지 않습니다."
        "user-not-found", "wrong-password", "invalid-credential" -> "이메일 또는 비밀번호가 올바르지 않습니다."
        "too-many-requests" -> "시도가 너무 잦습니다. 잠시 후 다시 시도하세요."
        "operation-not-allowed" -> "Firebase 콘솔에서 이메일/비밀번호 로그인을 활성화해야 합니다."
        else -> "로그인에 실패했습니다 (${e.code})."
    }

    /**
     * 읽기 실패를 화면에 보여 줄 문장으로 바꾼다.
     *
     * 제한 시간에 걸린 경우가 가장 흔하고, 그때 원문을 그대로 내보이면 무엇을 해야
     * 할지 알 수 없다. 원인을 짚어 주는 쪽이 낫다.
     */
    private fun messageForLoadError(error: Throwable): String = when (error) {
        is ReadTimeoutException, is HttpRequestTimeoutException, is SocketTimeoutException ->
            "콘텐츠를 불러오지 못했습니다.\n서버 응답이 제때 오지 않았습니다. 네트워크 상태를 확인하고 다시 시도해 주세요."
        else -> "콘텐츠를 불러오지 못했습니다.\n${error.readableMessage()}"
    }

    private companion object {
        /** 목록을 넘겨 보는 화면이 아니라 최근 흐름을 훑는 화면이므로 넉넉히 한 번만 읽는다. */
        const val VISITS_LOAD_LIMIT = 300
    }
}
