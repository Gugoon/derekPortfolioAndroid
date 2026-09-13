package com.derek.portfolio.resume

import android.content.Context
import com.derek.portfolio.data.model.ProfileData
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * 이력서 PDF 를 만들고, 마지막 결과를 들고 있는다.
 *
 * 콘텐츠는 SiteContent 인스턴스 하나로 들고 다니므로 동일성 비교만으로 "같은
 * 이력서인가" 를 판별한다. 미리보기를 닫았다 다시 열 때 조판을 반복하지 않고,
 * 미리보기·저장·공유·인쇄가 모두 같은 문서를 쓰게 하기 위한 캐시다.
 */
@Singleton
class ResumePdfService @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** 폰트 파싱은 수백 KB 를 훑는 작업이라 한 번만 한다. */
    private val composer by lazy { ResumePdfComposer(ResumeFonts.load(context)) }

    private var cachedFor: SiteContent? = null
    private var cached: Deferred<ByteArray>? = null

    /** [content] 를 이력서 PDF 바이트로 만든다. 같은 콘텐츠면 이전 결과를 돌려준다. */
    suspend fun get(content: SiteContent): ByteArray = build(content).await()

    /**
     * 미리보기를 열기 전에 미리 조판해 둔다.
     *
     * 버튼을 누른 뒤에 시작하면 그만큼 빈 화면을 본다. 실패는 여기서 드러나지 않고,
     * 캐시가 비워지므로 실제로 열 때 다시 시도한다.
     */
    fun warmUp(content: SiteContent) {
        build(content)
    }

    @Synchronized
    private fun build(content: SiteContent): Deferred<ByteArray> {
        val current = cached
        if (current != null && cachedFor === content) return current

        val next = scope.async(Dispatchers.Default) { composer.compose(content) }
        cachedFor = content
        cached = next

        // 실패한 결과를 캐시에 남겨 두면 다시 열어도 같은 실패만 돌아온다.
        next.invokeOnCompletion { cause ->
            if (cause != null) {
                synchronized(this) {
                    if (cached === next) {
                        cached = null
                        cachedFor = null
                    }
                }
            }
        }
        return next
    }

    companion object {
        fun fileName(profile: ProfileData) = "${profile.name}_이력서.pdf"
    }
}
