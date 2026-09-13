package com.derek.portfolio.resume

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이력서 PDF 를 화면에 그리고, 저장하고, 공유하기 위한 파일 작업.
 *
 * 웹에서는 브라우저 내장 PDF 뷰어가 미리보기를, 다운로드 링크가 저장을 맡았다.
 * 앱에서는 PdfRenderer 로 쪽마다 비트맵을 그리고, 저장은 시스템 파일 선택기(SAF),
 * 공유는 FileProvider 로 한다.
 */
@Singleton
class ResumeFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.cacheDir, "resume").apply { mkdirs() }

    /** 쪽마다 [pageWidthPx] 폭의 비트맵으로 그린다. */
    suspend fun renderPages(bytes: ByteArray, pageWidthPx: Int): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val file = File(directory, "preview.pdf").apply { writeBytes(bytes) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {
                List(renderer.pageCount) { index ->
                    val page = renderer.openPage(index)
                    try {
                        val height = (pageWidthPx.toLong() * page.height / page.width).toInt()
                        val bitmap = Bitmap.createBitmap(pageWidthPx, height, Bitmap.Config.ARGB_8888)
                        // 렌더러는 투명 배경에 그린다. 종이는 흰색이어야 한다.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
    }

    /** 사용자가 파일 선택기에서 고른 위치에 쓴다. */
    suspend fun writeTo(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("저장 위치를 열 수 없습니다.")
        stream.use { it.write(bytes) }
    }

    /** 다른 앱에 넘길 수 있는 content:// 주소를 만든다. */
    suspend fun shareUri(bytes: ByteArray, fileName: String): Uri = withContext(Dispatchers.IO) {
        val file = File(directory, fileName).apply { writeBytes(bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

/** 이미 만들어 둔 PDF 바이트를 그대로 인쇄 서비스에 넘기는 어댑터. */
class PdfBytesPrintAdapter(
    private val bytes: ByteArray,
    private val documentName: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        }
    }
}
