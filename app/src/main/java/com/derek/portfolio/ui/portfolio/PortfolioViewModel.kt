package com.derek.portfolio.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.derek.portfolio.FirebaseConfig
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.data.repository.ContentRepository
import com.derek.portfolio.resume.ResumePdfService
import com.derek.portfolio.ui.components.AppMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val resumePdf: ResumePdfService,
) : ViewModel() {

    data class UiState(
        /** Firestore 로드 결과. 로딩 중에는 null 이다. */
        val content: SiteContent? = null,
        /** 관리자 화면에서 돌아온 뒤 다시 읽는 중인지. */
        val isRefreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // 화면이 잠시 구독을 끊은 사이에 나온 알림도 잃지 않도록 채널로 둔다.
    private val messageChannel = Channel<AppMessage>(Channel.BUFFERED)
    val messages: Flow<AppMessage> = messageChannel.receiveAsFlow()

    /** Firebase 설정이 없으면 관리자 화면을 열어도 할 수 있는 일이 없다. 진입 버튼을 감춘다. */
    val isAdminAvailable: Boolean = FirebaseConfig.isConfigured

    /**
     * 진행 중인 읽기. 관리자 화면을 여러 번 드나들면 읽기가 겹칠 수 있는데(읽기
     * 제한 시간이 8초라 창이 실제로 있다), 늦게 끝난 쪽이 오래된 내용을 덮어쓰지
     * 않도록 새 읽기를 시작할 때 이전 것을 취소한다.
     */
    private var loadJob: Job? = null
    private var refreshOnReturn = false

    init {
        loadJob = viewModelScope.launch {
            // Firebase 가 없으면 굳이 기다리지 않고 내장 데이터로 바로 그린다.
            // load() 는 실패해도 폴백을 돌려주므로 예외를 던지지 않는다.
            val content = if (FirebaseConfig.isConfigured) contentRepository.load() else SiteContent.Fallback
            applyContent(content)
        }
    }

    fun onAdminOpened() {
        refreshOnReturn = true
    }

    /**
     * 화면이 다시 앞으로 나왔다. 관리자 화면에 다녀온 길이면 그 사이 고친 내용이
     * 있을 수 있으므로 다시 읽는다. 앱 전환 등으로 돌아온 경우는 건너뛴다.
     */
    fun onResumed() {
        if (!refreshOnReturn) return
        refreshOnReturn = false
        refresh()
    }

    /**
     * 관리자 화면에서 돌아왔을 때의 새로고침.
     *
     * 첫 로드와 달리 이미 보여 주고 있는 내용이 있으므로 화면을 비우지 않는다.
     * 읽기에 실패하면 지금 내용을 그대로 두고 알린다. 실패를 폴백으로 덮어쓰면 멀쩡히
     * 보고 있던 내용이 기본값으로 바뀌어 버린다.
     */
    private fun refresh() {
        if (!FirebaseConfig.isConfigured) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val loaded = contentRepository.loadOrNull()

            _state.update {
                // 첫 로드가 취소된 채 새로고침마저 실패했으면 빈 화면 대신 폴백을 그린다.
                it.copy(isRefreshing = false, content = loaded ?: it.content ?: SiteContent.Fallback)
            }
            if (loaded != null) resumePdf.warmUp(loaded)

            messageChannel.trySend(
                AppMessage(
                    if (loaded == null) "최신 내용을 불러오지 못했습니다. 화면은 이전 내용 그대로입니다."
                    else "최신 내용을 불러왔습니다.",
                ),
            )
        }
    }

    private fun applyContent(content: SiteContent) {
        _state.update { it.copy(content = content) }
        // 이력서 PDF 조판을 미리 돌려 둔다. 버튼을 누른 뒤에 시작하면 그만큼 빈
        // 미리보기를 보게 된다. 조판은 백그라운드 스레드에서 돈다.
        resumePdf.warmUp(content)
    }
}
