package com.derek.portfolio.ui.admin

// 관리자 화면의 '방문 기록' 탭.
//
// 방문자가 남긴 visits 문서를 최근 순으로 보여 준다. 쓰기는 방문자 기기가 하고
// (VisitLogger), 여기서는 읽기와 정리만 한다.

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.derek.portfolio.data.visit.VisitRecord
import com.derek.portfolio.data.visit.describeUserAgent
import com.derek.portfolio.data.visit.formatVisitTime
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** 기록 정리에서 '오래된 기록' 으로 치는 기준. */
private val Retention = 90.days

@Composable
fun VisitsView(
    state: VisitsState,
    onReload: () -> Unit,
    onCleanUp: (age: Duration) -> Unit,
) {
    var showCleanup by remember { mutableStateOf(false) }
    val records = state.records

    Column(Modifier.fillMaxSize()) {
        Toolbar(
            records = records,
            busy = state.loading || state.cleaning,
            onReload = onReload,
            onCleanUp = { showCleanup = true },
        )
        HorizontalDivider(thickness = 1.dp, color = AppColors.Border)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val error = state.error
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                    color = AppColors.Primary,
                    strokeWidth = 2.4.dp,
                )
                error != null -> Placeholder(Icons.Rounded.ErrorOutline, "방문 기록을 불러오지 못했습니다.\n$error")
                records.isEmpty() -> Placeholder(
                    Icons.Rounded.TravelExplore,
                    "아직 남은 방문 기록이 없습니다.\n" +
                        "사이트를 다른 브라우저나 시크릿 창으로 열거나, 로그아웃한 기기에서 앱을 열면 한 줄이 쌓입니다.\n" +
                        "(관리자로 로그인한 상태의 접속은 기록하지 않습니다.)",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(records, key = { it.id }) { VisitTile(it) }
                }
            }
        }
    }

    if (showCleanup) {
        AlertDialog(
            onDismissRequest = { showCleanup = false },
            containerColor = AppColors.SurfaceCard,
            title = { Text("방문 기록 정리") },
            text = {
                Text(
                    "${Retention.inWholeDays}일 이전 · 오래된 기록만 지웁니다.\n" +
                        "전체 삭제 · 지금까지 쌓인 방문 기록을 모두 지웁니다.\n\n" +
                        "어느 쪽이든 되돌릴 수 없습니다.",
                    style = AppFonts.sans(14f, color = AppColors.TextSecondary, height = 1.7f),
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showCleanup = false
                        onCleanUp(Retention)
                    }) { Text("${Retention.inWholeDays}일 이전") }
                    TextButton(
                        onClick = {
                            showCleanup = false
                            onCleanUp(Duration.ZERO)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Accent),
                    ) { Text("전체 삭제") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanup = false }) { Text("취소") }
            },
        )
    }
}

/** 상단 요약 + 동작 버튼. 좁은 화면에서는 요약과 버튼이 위아래로 나뉜다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    records: List<VisitRecord>,
    busy: Boolean,
    onReload: () -> Unit,
    onCleanUp: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayCount = records.count { it.at?.atZone(zone)?.toLocalDate() == today }
    val uniqueIps = records.map { it.ip }.filter { it.isNotEmpty() }.toSet().size
    val countries = records.map { it.country }.filter { it.isNotEmpty() }.toSet().size

    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Stat("불러온 기록", "${records.size}")
            Stat("오늘", "$todayCount")
            Stat("고유 IP", "$uniqueIps")
            Stat("국가", "$countries")
        }
        Row(
            Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdminButton(label = "새로고침", onClick = if (busy) null else onReload, icon = Icons.Rounded.Refresh, outlined = true)
            AdminButton(
                label = "기록 정리",
                onClick = if (busy) null else onCleanUp,
                icon = Icons.Rounded.DeleteSweep,
                outlined = true,
                color = AppColors.Accent,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = AppFonts.sans(11.5f, color = AppColors.TextMuted))
        Spacer(Modifier.height(2.dp))
        Text(value, style = AppFonts.mono(16f, FontWeight.Bold, AppColors.TextPrimary))
    }
}

@Composable
private fun VisitTile(record: VisitRecord) {
    val location = record.location
    val detail = listOf(record.org, record.timezone).filter { it.isNotEmpty() }

    AdminListItem(
        heading = formatVisitTime(record.at),
        actions = {
            // 첫 화면('/')이 대부분이라 그건 굳이 표시하지 않는다.
            if (record.path.isNotEmpty() && record.path != "/") PathChip(record.path)
        },
    ) {
        SelectionContainer {
            Text(
                record.ip.ifEmpty { "IP 확인 실패" },
                style = AppFonts.mono(
                    14f,
                    FontWeight.SemiBold,
                    if (record.ip.isEmpty()) AppColors.TextMuted else AppColors.TextPrimary,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            location.ifEmpty { "위치를 확인하지 못했습니다." },
            style = AppFonts.sans(
                13.5f,
                color = if (location.isEmpty()) AppColors.TextMuted else AppColors.TextSecondary,
                height = 1.5f,
            ),
        )
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(detail.joinToString(" · "), style = AppFonts.sans(12f, color = AppColors.TextMuted))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOf("유입 ${record.source}", describeUserAgent(record.userAgent))
                .filter { it.isNotEmpty() }
                .joinToString("  ·  "),
            style = AppFonts.sans(12f, color = AppColors.TextMuted, height = 1.5f),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PathChip(path: String) {
    Text(
        path,
        modifier = Modifier
            .background(AppColors.SurfaceHover, RoundedCornerShape(AppDimens.RadiusSm))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = AppFonts.mono(11f, color = AppColors.TextSecondary),
    )
}

@Composable
private fun Placeholder(icon: ImageVector, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = AppFonts.sans(13f, color = AppColors.TextMuted, height = 1.7f),
        )
    }
}
