package com.derek.portfolio.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.derek.portfolio.ui.components.paperShadow
import com.derek.portfolio.ui.components.screenWidthDp
import com.derek.portfolio.ui.theme.AppColors
import com.derek.portfolio.ui.theme.AppDimens
import com.derek.portfolio.ui.theme.AppFonts
import kotlin.math.min

/** 관리자 화면의 기본 묶음 카드. 좁은 화면에서는 동작 버튼을 제목 아래로 내린다. */
@Composable
fun AdminCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(AppDimens.RadiusMd)
    val stacked = screenWidthDp() < 600
    Column(
        modifier
            .padding(bottom = 24.dp)
            .fillMaxWidth()
            .paperShadow(shape)
            .background(AppColors.SurfaceCard, shape)
            .border(1.2.dp, AppColors.Border, shape)
            .padding(if (stacked) 16.dp else 24.dp),
    ) {
        if (trailing == null || stacked) {
            CardHeading(title, subtitle)
            if (trailing != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { CardHeading(title, subtitle) }
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun CardHeading(title: String, subtitle: String?) {
    Column {
        Text(title, style = AppFonts.sans(16f, FontWeight.Bold, AppColors.TextPrimary))
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = AppFonts.sans(12.5f, color = AppColors.TextMuted, height = 1.6f))
        }
    }
}

@Composable
fun adminTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Border,
    focusedContainerColor = AppColors.Surface,
    unfocusedContainerColor = AppColors.Surface,
    cursorColor = AppColors.Primary,
    focusedLabelColor = AppColors.Primary,
)

/** 라벨이 붙은 텍스트 입력. */
@Composable
fun AdminField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    maxLines: Int = 1,
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AppFonts.sans(12.5f, FontWeight.SemiBold, AppColors.TextSecondary))
            if (required) {
                Text(" *", style = AppFonts.sans(12.5f, FontWeight.Bold, AppColors.Accent))
            }
            if (hint != null) {
                Spacer(Modifier.width(8.dp))
                Text(hint, modifier = Modifier.weight(1f), style = AppFonts.sans(11.5f, color = AppColors.TextMuted))
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = AppFonts.sans(14f, color = AppColors.TextPrimary, height = 1.6f),
            singleLine = maxLines == 1,
            minLines = if (maxLines > 1) min(maxLines, 3) else 1,
            maxLines = maxLines,
            shape = RoundedCornerShape(AppDimens.RadiusSm),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (maxLines == 1) ImeAction.Next else ImeAction.Default,
            ),
            colors = adminTextFieldColors(),
        )
    }
}

/** 관리자 화면의 주 동작 버튼. */
@Composable
fun AdminButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    outlined: Boolean = false,
    color: Color = AppColors.Primary,
    busy: Boolean = false,
) {
    val enabled = onClick != null && !busy
    val foreground = if (outlined) color else Color.White
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (outlined) AppColors.SurfaceCard else color, shape)
            .border(1.2.dp, color, shape)
            .clickable(enabled = enabled, role = Role.Button) { onClick?.invoke() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = foreground, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        }
        if (busy || icon != null) Spacer(Modifier.width(8.dp))
        Text(label, style = AppFonts.sans(13.5f, FontWeight.SemiBold, foreground))
    }
}

/** 리스트 항목 하나를 감싸는 박스. 우측 상단에 삭제/이동 버튼을 붙인다. */
@Composable
fun AdminListItem(
    heading: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.RadiusSm)
    Column(
        modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .background(AppColors.Surface, shape)
            .border(1.2.dp, AppColors.BorderLight, shape)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(heading, modifier = Modifier.weight(1f), style = AppFonts.mono(11.5f, FontWeight.SemiBold, AppColors.TextMuted))
            actions()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 작은 아이콘 버튼 (삭제, 위/아래 이동 등). [onClick] 이 null 이면 비활성이다. */
@Composable
fun AdminIconButton(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)?,
    color: Color = AppColors.TextMuted,
) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (onClick != null) color else color.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 하나만 고르는 칩. */
@Composable
fun AdminChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AppColors.Primary else AppColors.Surface, shape)
            .border(1.dp, if (selected) AppColors.Primary else AppColors.Border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = AppFonts.sans(12.5f, FontWeight.SemiBold, if (selected) Color.White else AppColors.TextSecondary),
    )
}
