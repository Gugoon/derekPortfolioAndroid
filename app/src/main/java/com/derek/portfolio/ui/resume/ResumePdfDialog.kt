package com.derek.portfolio.ui.resume

// 이력서 PDF 미리보기 다이얼로그.
//
// 웹에서는 브라우저 내장 PDF 뷰어에 미리보기를 맡겼다. 앱에서는 PdfRenderer 로 쪽마다
// 그린 이미지를 세로로 늘어놓는다. 저장은 시스템 파일 선택기로 위치를 고르게 하고,
// 공유 시트와 인쇄(PrintManager)도 함께 연다.

import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.derek.portfolio.data.model.SiteContent
import com.derek.portfolio.resume.PdfBytesPrintAdapter
import com.derek.portfolio.resume.ResumePdfService
import com.derek.portfolio.ui.components.findActivity
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.readableMessage
import com.derek.portfolio.ui.components.screenWidthDp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts
import kotlinx.coroutines.launch

@Composable
fun ResumePdfDialog(
    content: SiteContent,
    onDismiss: () -> Unit,
    viewModel: ResumePdfViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthDp = screenWidthDp()
    val compact = widthDp < 640
    val fileName = ResumePdfService.fileName(content.profile)
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 화면 폭에 맞춰 그리되, 큰 태블릿에서 비트맵이 지나치게 커지지 않게 묶는다.
    val pageWidthPx = with(density) { widthDp.coerceAtMost(900).dp.roundToPx() }.coerceIn(480, 1600)

    LaunchedEffect(content, pageWidthPx) { viewModel.open(content, pageWidthPx) }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> if (uri != null) viewModel.saveTo(uri) }

    val onPrint: () -> Unit = {
        scope.launch {
            val bytes = viewModel.awaitBytes() ?: return@launch
            // PrintManager 는 Activity 에서 얻은 것이어야 인쇄 화면을 띄울 수 있다.
            val activity = context.findActivity() ?: return@launch
            try {
                activity.getSystemService(PrintManager::class.java).print(
                    fileName,
                    PdfBytesPrintAdapter(bytes, fileName),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
                )
            } catch (e: Exception) {
                viewModel.showActionError("인쇄를 시작하지 못했습니다: ${e.readableMessage()}")
            }
        }
    }
    val onShare: () -> Unit = {
        scope.launch {
            val uri = viewModel.shareUri(fileName) ?: return@launch
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(send, "이력서 PDF 공유"))
            } catch (e: Exception) {
                viewModel.showActionError("PDF를 공유하지 못했습니다: ${e.readableMessage()}")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(AppDimens.RadiusLg)
        Column(
            Modifier
                .padding(horizontal = if (compact) 12.dp else 32.dp, vertical = if (compact) 16.dp else 32.dp)
                .widthIn(max = 940.dp)
                .fillMaxSize()
                .background(AppColors.Surface, shape)
                .border(1.dp, AppColors.Border, shape)
                .clip(shape),
        ) {
            Header(fileName = fileName, compact = compact, onClose = onDismiss)
            HorizontalDivider(thickness = 1.dp, color = AppColors.Border)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AppColors.Background),
            ) {
                when (val preview = state.preview) {
                    ResumePdfViewModel.Preview.Loading -> PreviewLoading()
                    is ResumePdfViewModel.Preview.Error -> PreviewError(preview.message)
                    is ResumePdfViewModel.Preview.Ready -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        itemsIndexed(preview.pages) { index, page ->
                            Image(
                                bitmap = page,
                                contentDescription = "이력서 ${index + 1}쪽",
                                modifier = Modifier
                                    .widthIn(max = 720.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(page.width.toFloat() / page.height)
                                    .paperShadow(RectangleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = AppColors.Border)
            Actions(
                compact = compact,
                actionError = state.actionError,
                notice = state.notice,
                onPrint = onPrint,
                onShare = onShare,
                onDownload = { saveLauncher.launch(fileName) },
            )
        }
    }
}

@Composable
private fun Header(fileName: String, compact: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.padding(start = if (compact) 16.dp else 24.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "이력서 미리보기",
                style = AppFonts.serif(if (compact) 16f else 18f, FontWeight.Bold, AppColors.TextPrimary, height = 1.2f, letterSpacing = -0.5f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "A4 · $fileName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppFonts.sans(11.5f, color = AppColors.TextMuted),
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "닫기", tint = AppColors.TextMuted)
        }
    }
}

@Composable
private fun Actions(
    compact: Boolean,
    actionError: String?,
    notice: String?,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 12.dp),
    ) {
        // 스낵바는 쓰지 않는다. 뒤쪽 화면에 뜨는데, 이 다이얼로그가 화면을 거의 덮고
        // 있어 실패 사실이 그대로 묻힌다. 버튼을 눌러도 아무 일이 없는 것처럼 보이므로
        // 안에서 알린다.
        when {
            actionError != null -> {
                StatusLine(Icons.Rounded.ErrorOutline, actionError, AppColors.Accent)
                Spacer(Modifier.height(12.dp))
            }
            notice != null -> {
                StatusLine(Icons.Rounded.CheckCircleOutline, notice, AppColors.Primary)
                Spacer(Modifier.height(12.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    "미리보기를 확인한 뒤 PDF로 저장하거나 공유할 수 있습니다.",
                    modifier = Modifier.weight(1f),
                    style = AppFonts.sans(12f, color = AppColors.TextMuted),
                )
            }
            ActionTextButton("인쇄", Icons.Outlined.Print, onPrint)
            ActionTextButton("공유", Icons.Rounded.Share, onShare)
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(AppDimens.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("PDF 저장", style = AppFonts.sans(13f, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun ActionTextButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextSecondary),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppFonts.sans(13f, FontWeight.SemiBold))
    }
}

@Composable
private fun StatusLine(icon: ImageVector, message: String, tint: Color) {
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.06f), shape)
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, modifier = Modifier.weight(1f), style = AppFonts.sans(12f, color = AppColors.TextSecondary, height = 1.5f))
    }
}

@Composable
private fun PreviewLoading() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = AppColors.Primary, strokeWidth = 2.4.dp)
        Spacer(Modifier.height(14.dp))
        Text("이력서를 조판하는 중입니다…", style = AppFonts.sans(12.5f, color = AppColors.TextMuted))
    }
}

@Composable
private fun PreviewError(message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "미리보기를 표시하지 못했습니다.\n아래 버튼으로 PDF를 바로 저장할 수 있습니다.",
            textAlign = TextAlign.Center,
            style = AppFonts.sans(13f, color = AppColors.TextSecondary, height = 1.6f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = AppFonts.sans(11f, color = AppColors.TextMuted),
        )
    }
}
