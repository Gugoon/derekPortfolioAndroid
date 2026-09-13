package com.derek.portfolio.ui.resume

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.resume.ResumeFiles
import com.derek.portfolio.resume.ResumePdfService
import com.derek.portfolio.ui.components.readableMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 이력서 미리보기 다이얼로그의 상태.
 *
 * 조판 결과는 ResumePdfService 가 콘텐츠 단위로 캐시하므로, 미리보기·저장·공유·
 * 인쇄가 모두 같은 문서를 쓰고 두 번째부터는 조판을 다시 하지 않는다.
 */
@HiltViewModel
class ResumePdfViewModel @Inject constructor(
    private val service: ResumePdfService,
    private val files: ResumeFiles,
) : ViewModel() {

    sealed interface Preview {
        data object Loading : Preview
        data class Error(val message: String) : Preview
        class Ready(val pages: List<ImageBitmap>) : Preview
    }

    data class UiState(
        val preview: Preview = Preview.Loading,
        /** 저장·공유·인쇄가 실패했을 때 다이얼로그 안에 띄울 문구. */
        val actionError: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var content: SiteContent? = null
    private var previewJob: Job? = null

    fun open(content: SiteContent, pageWidthPx: Int) {
        if (this.content === content && _state.value.preview !is Preview.Error) return
        this.content = content
        previewJob?.cancel()
        _state.value = UiState()
        previewJob = viewModelScope.launch {
            val preview = try {
                Preview.Ready(files.renderPages(service.get(content), pageWidthPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Preview.Error(e.readableMessage())
            }
            _state.update { it.copy(preview = preview) }
        }
    }

    /** 다이얼로그를 닫으면 쪽 비트맵을 놓아 준다. PDF 자체는 서비스 캐시에 남는다. */
    fun close() {
        previewJob?.cancel()
        content = null
        _state.value = UiState()
    }

    suspend fun awaitBytes(): ByteArray? {
        val current = content ?: return null
        _state.update { it.copy(actionError = null, notice = null) }
        return try {
            service.get(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showActionError("PDF를 만들지 못했습니다: ${e.readableMessage()}")
            null
        }
    }

    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            val bytes = awaitBytes() ?: return@launch
            try {
                files.writeTo(uri, bytes)
                _state.update { it.copy(notice = "PDF를 저장했습니다.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showActionError("PDF를 내려받지 못했습니다: ${e.readableMessage()}")
            }
        }
    }

    suspend fun shareUri(fileName: String): Uri? {
        val bytes = awaitBytes() ?: return null
        return try {
            files.shareUri(bytes, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showActionError("PDF를 공유하지 못했습니다: ${e.readableMessage()}")
            null
        }
    }

    fun showActionError(message: String) {
        _state.update { it.copy(actionError = message, notice = null) }
    }
}
