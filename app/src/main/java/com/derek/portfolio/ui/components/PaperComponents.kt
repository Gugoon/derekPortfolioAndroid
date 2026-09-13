package com.derek.portfolio.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts

/** 실제 종이가 책상 위에 놓인 듯한 부드러운 음영. [glow] 는 눌렀을 때의 들뜬 그림자다. */
fun Modifier.paperShadow(shape: Shape, glow: Boolean = false): Modifier = shadow(
    elevation = if (glow) 12.dp else 5.dp,
    shape = shape,
    clip = false,
    ambientColor = AppColors.ShadowInk.copy(alpha = if (glow) 0.12f else 0.08f),
    spotColor = AppColors.ShadowInk.copy(alpha = if (glow) 0.18f else 0.12f),
)

/**
 * 누르고 있는지를 함께 돌려주는 interaction source.
 *
 * 웹의 마우스 hover 강조를 터치 기기에서는 '누르는 동안' 으로 옮겨 쓴다.
 */
@Composable
fun rememberPressState(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to pressed
}

@Composable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Composable
fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

@Composable
fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = subtitle,
            textAlign = TextAlign.Center,
            style = AppFonts.mono(11f, FontWeight.SemiBold, AppColors.TextMuted, letterSpacing = 2.5f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = AppFonts.serif(26f, FontWeight.Bold, AppColors.TextPrimary, letterSpacing = -0.5f),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(36.dp)
                .height(2.dp)
                .background(AppColors.Primary),
        )
    }
}

// ---------------------------------------------------------------------------
// 스낵바
// ---------------------------------------------------------------------------

data class AppMessage(val text: String, val isError: Boolean = false)

private class AppSnackbarVisuals(
    override val message: String,
    val isError: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? get() = null
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration
        get() = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
}

/** 떠 있는 알림을 걷고 새 알림을 띄운다. */
suspend fun SnackbarHostState.showAppMessage(message: AppMessage) {
    currentSnackbarData?.dismiss()
    showSnackbar(AppSnackbarVisuals(message.text, message.isError))
}

@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        val isError = (data.visuals as? AppSnackbarVisuals)?.isError == true
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(AppDimens.RadiusMd),
            containerColor = if (isError) AppColors.Accent else AppColors.Primary,
            contentColor = Color.White,
        )
    }
}

// ---------------------------------------------------------------------------
// 기타
// ---------------------------------------------------------------------------

/** 외부 링크를 연다. 열 수 있는 앱이 없으면 조용히 넘어간다. */
fun Context.openExternal(url: String) {
    val uri = Uri.parse(url)
    val intent = when (uri.scheme) {
        "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
        "tel" -> Intent(Intent.ACTION_DIAL, uri)
        else -> Intent(Intent.ACTION_VIEW, uri)
    }
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 메일 앱이 없는 기기 등. 누를 수 있는 곳에서 앱이 죽을 이유는 없다.
    }
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 사용자에게 보여 줄 오류 문구. 메시지가 없는 예외는 이름이라도 남긴다. */
fun Throwable.readableMessage(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
