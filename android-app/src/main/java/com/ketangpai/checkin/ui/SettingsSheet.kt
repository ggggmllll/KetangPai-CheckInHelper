package com.ketangpai.checkin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.ThemeMode
import com.ketangpai.checkin.iosColors

/**
 * 顶栏设置面板：外观模式 + 扫码后的行为。
 * 用底部 sheet 而不是下拉菜单，是因为设置项要带说明文字，
 * 下拉菜单塞不下两行。
 */
@Composable
internal fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    autoSignOnScan: Boolean,
    onAutoSignOnScanChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    IosSheet(
        title = "设置",
        onDismiss = onDismiss,
        confirmText = "完成",
        onConfirm = onDismiss,
    ) {
        ColCaption("外观")
        IosGroup {
            ThemeMode.entries.forEachIndexed { index, mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onThemeModeChange(mode) }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.label, Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
                    if (mode == themeMode) {
                        Icon(Icons.Filled.Check, "已选择", Modifier.size(18.dp), tint = iosColors.blue)
                    }
                }
                if (index < ThemeMode.entries.lastIndex) IosDivider()
            }
        }

        Spacer(Modifier.height(20.dp))

        ColCaption("扫码签到")
        IosGroup {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("扫到二维码后自动签到", Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
                Switch(
                    checked = autoSignOnScan,
                    onCheckedChange = onAutoSignOnScanChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = iosColors.green,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = iosColors.fill.copy(alpha = 0.45f),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Text(
            if (autoSignOnScan) {
                "扫到码就提交给所有启用账号，结果用顶部提示告知；失败时链接仍会填进待签到页，可以手动重试。"
            } else {
                "只把链接填进待签到页，由你在那里选账号后手动提交。"
            },
            Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iosColors.secondaryLabel,
        )
        Spacer(Modifier.height(18.dp))
    }
}

/** sheet 里的分组小标题，比 [IosSectionHeader] 少一层左右边距。 */
@Composable
private fun ColCaption(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, bottom = 6.dp),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = iosColors.secondaryLabel,
    )
}
